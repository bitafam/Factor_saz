package com.example.util.cloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.example.data.database.ConfigEntity
import com.example.data.database.InvoiceEntity
import com.example.data.database.ItemJsonConverter
import com.example.data.model.InvoiceAttachment
import com.example.util.HtmlInvoiceGenerator
import com.example.util.JalaliCalendar
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

data class BatchSyncResult(
    val totalProcessed: Int,
    val newlyUploaded: Int,
    val skippedAlreadyUploaded: Int,
    val failedCount: Int,
    val errors: List<String>,
    val updatedInvoices: List<InvoiceEntity>
)

object InvoiceCloudSyncManager {

    /**
     * Builds S3 client from stored app config.
     */
    fun createClient(config: ConfigEntity?): ArvanCloudS3Client? {
        if (config == null || !config.arvanAutoSync) return null
        val client = ArvanCloudS3Client(
            endpoint = config.arvanEndpoint,
            bucket = config.arvanBucket,
            accessKey = config.arvanAccessKey,
            secretKey = config.arvanSecretKey,
            customDomain = config.arvanCustomDomain
        )
        return if (client.isConfigured) client else null
    }

    /**
     * Constructs a clean, URL-safe folder path for an invoice based on customer name and invoice number.
     * Example: "invoices/رضا_محمدی_INV-5412"
     */
    fun getInvoiceFolderName(buyerName: String, invoiceNo: String): String {
        val safeBuyer = buyerName.trim()
            .replace(Regex("""[\\/:*?"<>|#%&{}\\<>*?/$!'":@+`|=]"""), "_")
            .ifBlank { "مشتری_عمومی" }
        val safeNo = invoiceNo.trim()
            .replace(Regex("""[\\/:*?"<>|#%&{}\\<>*?/$!'":@+`|=]"""), "_")
            .ifBlank { "0" }
        return "invoices/${safeBuyer}_${safeNo}"
    }

    /**
     * Converts an image from Uri to compressed WebP bytes for high quality and compact storage.
     */
    fun convertImageToWebP(context: Context, uri: Uri, quality: Int = 85, maxDimension: Int = 1920): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return null

            val width = original.width
            val height = original.height
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = width.toFloat() / height.toFloat()
                val targetWidth = if (width > height) maxDimension else (maxDimension * ratio).toInt()
                val targetHeight = if (height > width) maxDimension else (maxDimension / ratio).toInt()
                Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
            } else {
                original
            }

            val outputStream = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            scaledBitmap.compress(format, quality, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Uploads an invoice's HTML page to its customer folder in ArvanCloud S3.
     */
    fun uploadInvoiceHtml(client: ArvanCloudS3Client, invoice: InvoiceEntity): Result<String> {
        val folder = getInvoiceFolderName(invoice.buyerName, invoice.invoiceNo)
        val htmlKey = "$folder/index.html"
        val htmlContent = HtmlInvoiceGenerator.generateHtml(invoice)
        val htmlBytes = htmlContent.toByteArray(StandardCharsets.UTF_8)
        return client.putObject(
            key = htmlKey,
            content = htmlBytes,
            contentType = "text/html; charset=utf-8",
            isPublic = true
        )
    }

    /**
     * Uploads an attachment image (WebP) to the invoice folder in ArvanCloud S3.
     */
    fun uploadAttachment(
        client: ArvanCloudS3Client,
        buyerName: String,
        invoiceNo: String,
        title: String,
        webpBytes: ByteArray,
        existingCount: Int
    ): Result<InvoiceAttachment> {
        val folder = getInvoiceFolderName(buyerName, invoiceNo)
        val safeTitle = title.trim()
            .replace(Regex("""[\\/:*?"<>|#%&{}\\<>*?/$!'":@+`|=]"""), "_")
            .ifBlank { "ضمیمه" }
        val timeStamp = System.currentTimeMillis() % 100000
        val fileName = "attachment_${existingCount + 1}_${safeTitle}_$timeStamp.webp"
        val key = "$folder/$fileName"

        val putResult = client.putObject(
            key = key,
            content = webpBytes,
            contentType = "image/webp",
            isPublic = true
        )

        return if (putResult.isSuccess) {
            val url = putResult.getOrThrow()
            val sizeKb = (webpBytes.size / 1024L).coerceAtLeast(1L)
            Result.success(
                InvoiceAttachment(
                    id = java.util.UUID.randomUUID().toString(),
                    title = title.ifBlank { "تصویر پیوست ${existingCount + 1}" },
                    fileName = fileName,
                    cloudUrl = url,
                    localUri = "",
                    uploadDate = JalaliCalendar.getTodayJalali(),
                    fileSizeKb = sizeKb
                )
            )
        } else {
            Result.failure(putResult.exceptionOrNull() ?: Exception("خطا در آپلود ضمیمه"))
        }
    }

    /**
     * Sequential Batch Queue for uploading invoices to ArvanCloud with deduplication and progress feedback.
     */
    suspend fun syncBatchQueue(
        client: ArvanCloudS3Client,
        invoices: List<InvoiceEntity>,
        forceReupload: Boolean = false,
        onProgress: (current: Int, total: Int, invoiceName: String, message: String) -> Unit
    ): BatchSyncResult {
        var newlyUploaded = 0
        var skipped = 0
        var failed = 0
        val errors = mutableListOf<String>()
        val updatedList = mutableListOf<InvoiceEntity>()
        val total = invoices.size

        // Fetch existing cloud objects once to check for deduplication
        val existingObjectsResult = client.listObjects("invoices/")
        val existingKeysSet = if (existingObjectsResult.isSuccess) {
            existingObjectsResult.getOrThrow().map { it.key }.toSet()
        } else {
            emptySet()
        }

        for ((index, invoice) in invoices.withIndex()) {
            val currentNum = index + 1
            val invoiceDesc = "${invoice.buyerName} (${invoice.invoiceNo})"
            val folder = getInvoiceFolderName(invoice.buyerName, invoice.invoiceNo)
            val expectedHtmlKey = "$folder/index.html"

            // Check deduplication
            val alreadyOnCloud = !forceReupload && (
                invoice.cloudHtmlUrl.isNotBlank() || existingKeysSet.contains(expectedHtmlKey)
            )

            if (alreadyOnCloud && invoice.cloudHtmlUrl.isNotBlank()) {
                skipped++
                updatedList.add(invoice)
                onProgress(currentNum, total, invoiceDesc, "قبلاً در صندوقچه موجود بود (رد شد)")
                continue
            }

            if (alreadyOnCloud && invoice.cloudHtmlUrl.isBlank()) {
                // Key exists on cloud, just link URL
                val cloudUrl = client.getPublicUrl(expectedHtmlKey)
                val updated = invoice.copy(cloudHtmlUrl = cloudUrl)
                updatedList.add(updated)
                skipped++
                onProgress(currentNum, total, invoiceDesc, "لینک صندوقچه متصل شد")
                continue
            }

            onProgress(currentNum, total, invoiceDesc, "در حال تولید HTML و بارگذاری در صندوقچه...")

            val uploadResult = uploadInvoiceHtml(client, invoice)
            if (uploadResult.isSuccess) {
                val url = uploadResult.getOrThrow()
                val updated = invoice.copy(cloudHtmlUrl = url)
                updatedList.add(updated)
                newlyUploaded++
                onProgress(currentNum, total, invoiceDesc, "با موفقیت بارگذاری شد")
            } else {
                failed++
                val errMsg = uploadResult.exceptionOrNull()?.message ?: "خطای ناشناخته"
                errors.add("$invoiceDesc: $errMsg")
                updatedList.add(invoice)
                onProgress(currentNum, total, invoiceDesc, "خطا در بارگذاری: $errMsg")
            }
        }

        return BatchSyncResult(
            totalProcessed = total,
            newlyUploaded = newlyUploaded,
            skippedAlreadyUploaded = skipped,
            failedCount = failed,
            errors = errors,
            updatedInvoices = updatedList
        )
    }
}
