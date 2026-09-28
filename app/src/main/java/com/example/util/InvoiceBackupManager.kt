package com.example.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.data.database.ActivatedDeviceEntity
import com.example.data.database.ConfigEntity
import com.example.data.database.InvoiceEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

data class BackupFileInfo(
    val file: File,
    val name: String,
    val sizeFormatted: String,
    val lastModifiedFormatted: String,
    val invoiceCount: Int,
    val isFullBackup: Boolean
)

data class BackupRestoreResult(
    val success: Boolean,
    val invoicesCount: Int,
    val configRestored: Boolean,
    val devicesCount: Int,
    val invoices: List<InvoiceEntity>,
    val config: ConfigEntity?,
    val devices: List<ActivatedDeviceEntity>,
    val errorMessage: String? = null
)

object InvoiceBackupManager {

    private const val ROOT_DIR_NAME = "IranQuartz"
    private const val BACKUP_DIR_NAME = "Backup faktors"
    private const val FULL_BACKUP_DIR_NAME = "بکاپ کلی"
    private const val PDF_DIR_NAME = "faktors"
    private const val SUMMARY_DIR_NAME = "خلاصه فاکتورها"
    private const val LICENSE_DIR_NAME = "ActivatedLicenses"

    /**
     * Gets the public parent folder for this app under the Documents directory.
     * Fallbacks to external storage directory or downloads if needed.
     */
    fun getAppPublicRoot(): File {
        val docDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val root = File(docDir, ROOT_DIR_NAME)
        if (!root.exists()) {
            root.mkdirs()
        }
        return root
    }

    fun getBackupFolder(): File {
        val f = File(getAppPublicRoot(), BACKUP_DIR_NAME)
        if (!f.exists()) f.mkdirs()
        return f
    }

    fun getFullBackupFolder(): File {
        val f = File(getAppPublicRoot(), FULL_BACKUP_DIR_NAME)
        if (!f.exists()) f.mkdirs()
        return f
    }

    fun getPdfFolder(): File {
        val f = File(getAppPublicRoot(), PDF_DIR_NAME)
        if (!f.exists()) f.mkdirs()
        return f
    }

    fun getSummaryFolder(): File {
        val f = File(getAppPublicRoot(), SUMMARY_DIR_NAME)
        if (!f.exists()) f.mkdirs()
        return f
    }

    fun getLicenseFolder(): File {
        val f = File(getAppPublicRoot(), LICENSE_DIR_NAME)
        if (!f.exists()) f.mkdirs()
        return f
    }

    /**
     * Creates a complete, offline JSON package of all app data (invoices, store settings, licenses).
     */
    fun createFullBackupPackage(
        invoices: List<InvoiceEntity>,
        config: ConfigEntity?,
        devices: List<ActivatedDeviceEntity>
    ): String {
        val rootObj = JSONObject()
        rootObj.put("packageType", "IRAN_QUARTZ_FULL_BACKUP")
        rootObj.put("version", 2)
        rootObj.put("appVersion", "2.0")
        rootObj.put("exportTimestamp", System.currentTimeMillis())
        rootObj.put("exportJalaliDate", JalaliCalendar.getTodayJalali())

        // Config section
        if (config != null) {
            val confObj = JSONObject()
            confObj.put("defaultSellerName", config.defaultSellerName)
            confObj.put("defaultSellerPhone", config.defaultSellerPhone)
            confObj.put("defaultSellerAddress", config.defaultSellerAddress)
            confObj.put("defaultInvoiceTitle", config.defaultInvoiceTitle)
            confObj.put("defaultInvoiceSubtitle", config.defaultInvoiceSubtitle)
            confObj.put("defaultManagerSign", config.defaultManagerSign)
            confObj.put("defaultSalesSign", config.defaultSalesSign)
            confObj.put("defaultManagerSignImg", config.defaultManagerSignImg)
            confObj.put("defaultSalesSignImg", config.defaultSalesSignImg)
            confObj.put("licensePrice", config.licensePrice)
            rootObj.put("config", confObj)
        }

        // Invoices Array
        val invArray = JSONArray()
        for (inv in invoices) {
            val invObj = JSONObject()
            invObj.put("id", inv.id)
            invObj.put("invoiceNo", inv.invoiceNo)
            invObj.put("invoiceDate", inv.invoiceDate)
            invObj.put("buyerName", inv.buyerName)
            invObj.put("sellerName", inv.sellerName)
            invObj.put("sellerPhone", inv.sellerPhone)
            invObj.put("sellerAddress", inv.sellerAddress)
            invObj.put("stoneCode", inv.stoneCode)
            invObj.put("stoneType", inv.stoneType)
            invObj.put("managerSign", inv.managerSign)
            invObj.put("salesSign", inv.salesSign)
            invObj.put("itemsJson", inv.itemsJson)
            invObj.put("simpleItemsJson", inv.simpleItemsJson)
            invObj.put("percentageItemsJson", inv.percentageItemsJson)
            invObj.put("invoiceTitle", inv.invoiceTitle)
            invObj.put("invoiceSubtitle", inv.invoiceSubtitle)
            invObj.put("totalAmount", inv.totalAmount)
            invObj.put("createdAt", inv.createdAt)
            invObj.put("isDeleted", inv.isDeleted)
            invObj.put("managerSignImgBase64", inv.managerSignImgBase64)
            invObj.put("salesSignImgBase64", inv.salesSignImgBase64)
            invArray.put(invObj)
        }
        rootObj.put("invoices", invArray)
        rootObj.put("invoicesCount", invoices.size)

        // Activated Devices Array
        val devArray = JSONArray()
        for (dev in devices) {
            val devObj = JSONObject()
            devObj.put("deviceId", dev.deviceId)
            devObj.put("licenseKey", dev.licenseKey)
            devObj.put("activationDate", dev.activationDate)
            devObj.put("isRevoked", dev.isRevoked)
            devObj.put("userName", dev.userName)
            devArray.put(devObj)
        }
        rootObj.put("activatedDevices", devArray)

        return rootObj.toString(4)
    }

    /**
     * Saves a full backup package file (.qzb) in the backup directory.
     */
    fun saveFullBackupToFile(
        invoices: List<InvoiceEntity>,
        config: ConfigEntity?,
        devices: List<ActivatedDeviceEntity>
    ): File? {
        return try {
            val folder = getFullBackupFolder()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val jalaliDate = JalaliCalendar.getTodayJalali().replace("/", "-")
            val fileName = "بکاپ_کامل_کوارتز_${jalaliDate}_${timeStamp}.qzb"
            val file = File(folder, fileName)

            val jsonContent = createFullBackupPackage(invoices, config, devices)
            FileOutputStream(file).use { out ->
                out.write(jsonContent.toByteArray(Charsets.UTF_8))
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parses a backup file content (both Full Package and Single Invoice .qzb).
     */
    fun parseBackupContent(jsonContent: String): BackupRestoreResult {
        return try {
            val trimmed = jsonContent.trim()
            if (!trimmed.startsWith("{")) {
                return BackupRestoreResult(
                    success = false,
                    invoicesCount = 0,
                    configRestored = false,
                    devicesCount = 0,
                    invoices = emptyList(),
                    config = null,
                    devices = emptyList(),
                    errorMessage = "قالب فایل پشتیبان نامعتبر است (ساختار JSON یافت نشد)."
                )
            }

            val rootObj = JSONObject(trimmed)
            val isFullPackage = rootObj.optString("packageType") == "IRAN_QUARTZ_FULL_BACKUP" || rootObj.has("invoices")

            if (isFullPackage) {
                val invoicesList = mutableListOf<InvoiceEntity>()
                val invArray = rootObj.optJSONArray("invoices") ?: JSONArray()
                for (i in 0 until invArray.length()) {
                    val invObj = invArray.getJSONObject(i)
                    val rawTotalStr = invObj.optString("totalAmount", "")
                    val parsedTotal = com.example.util.importer.TextNormalizer.parseNumber(rawTotalStr) ?: invObj.optDouble("totalAmount", 0.0)
                    val entity = InvoiceEntity(
                        id = invObj.optInt("id", 0),
                        invoiceNo = invObj.optString("invoiceNo", ""),
                        invoiceDate = invObj.optString("invoiceDate", ""),
                        buyerName = invObj.optString("buyerName", ""),
                        sellerName = invObj.optString("sellerName", ""),
                        sellerPhone = invObj.optString("sellerPhone", ""),
                        sellerAddress = invObj.optString("sellerAddress", ""),
                        stoneCode = invObj.optString("stoneCode", ""),
                        stoneType = invObj.optString("stoneType", ""),
                        managerSign = invObj.optString("managerSign", ""),
                        salesSign = invObj.optString("salesSign", ""),
                        itemsJson = invObj.optString("itemsJson", "[]"),
                        simpleItemsJson = invObj.optString("simpleItemsJson", "[]"),
                        percentageItemsJson = invObj.optString("percentageItemsJson", "[]"),
                        invoiceTitle = invObj.optString("invoiceTitle", ""),
                        invoiceSubtitle = invObj.optString("invoiceSubtitle", ""),
                        totalAmount = parsedTotal,
                        createdAt = invObj.optLong("createdAt", System.currentTimeMillis()),
                        isDeleted = invObj.optBoolean("isDeleted", false),
                        managerSignImgBase64 = invObj.optString("managerSignImgBase64", ""),
                        salesSignImgBase64 = invObj.optString("salesSignImgBase64", "")
                    )
                    val calcTotal = HtmlInvoiceGenerator.calculateInvoiceTotal(entity)
                    invoicesList.add(if (calcTotal > 0.0) entity.copy(totalAmount = calcTotal) else entity)
                }

                // Config
                var restoredConfig: ConfigEntity? = null
                if (rootObj.has("config")) {
                    val confObj = rootObj.getJSONObject("config")
                    restoredConfig = ConfigEntity(
                        deviceId = "",
                        defaultSellerName = confObj.optString("defaultSellerName", ""),
                        defaultSellerPhone = confObj.optString("defaultSellerPhone", ""),
                        defaultSellerAddress = confObj.optString("defaultSellerAddress", ""),
                        defaultInvoiceTitle = confObj.optString("defaultInvoiceTitle", ""),
                        defaultInvoiceSubtitle = confObj.optString("defaultInvoiceSubtitle", ""),
                        defaultManagerSign = confObj.optString("defaultManagerSign", ""),
                        defaultSalesSign = confObj.optString("defaultSalesSign", ""),
                        defaultManagerSignImg = confObj.optString("defaultManagerSignImg", ""),
                        defaultSalesSignImg = confObj.optString("defaultSalesSignImg", ""),
                        licensePrice = confObj.optString("licensePrice", "۵,۰۰۰,۰۰۰ تومان")
                    )
                }

                // Devices
                val devicesList = mutableListOf<ActivatedDeviceEntity>()
                val devArray = rootObj.optJSONArray("activatedDevices") ?: JSONArray()
                for (i in 0 until devArray.length()) {
                    val devObj = devArray.getJSONObject(i)
                    devicesList.add(
                        ActivatedDeviceEntity(
                            deviceId = devObj.optString("deviceId", ""),
                            licenseKey = devObj.optString("licenseKey", ""),
                            activationDate = devObj.optString("activationDate", ""),
                            isRevoked = devObj.optBoolean("isRevoked", false),
                            userName = devObj.optString("userName", "")
                        )
                    )
                }

                BackupRestoreResult(
                    success = true,
                    invoicesCount = invoicesList.size,
                    configRestored = restoredConfig != null,
                    devicesCount = devicesList.size,
                    invoices = invoicesList,
                    config = restoredConfig,
                    devices = devicesList
                )
            } else {
                // Legacy Single Invoice .qzb file
                val singleInvoice = deserializeInvoice(trimmed)
                BackupRestoreResult(
                    success = true,
                    invoicesCount = 1,
                    configRestored = false,
                    devicesCount = 0,
                    invoices = listOf(singleInvoice),
                    config = null,
                    devices = emptyList()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            BackupRestoreResult(
                success = false,
                invoicesCount = 0,
                configRestored = false,
                devicesCount = 0,
                invoices = emptyList(),
                config = null,
                devices = emptyList(),
                errorMessage = e.message ?: "خطا در پردازش فایل پشتیبان"
            )
        }
    }

    /**
     * Reads text content from a content Uri (SAF / File Picker).
     */
    fun readFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Reads text content from a file.
     */
    fun readFromFile(file: File): String? {
        return try {
            FileInputStream(file).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Lists all available backup files stored in the local backup folder.
     */
    fun listAvailableBackups(): List<BackupFileInfo> {
        val list = mutableListOf<BackupFileInfo>()
        try {
            val fullFolder = getFullBackupFolder()
            val singleFolder = getBackupFolder()
            val filesList = mutableListOf<File>()
            
            fullFolder.listFiles { _, name -> name.endsWith(".qzb", ignoreCase = true) || name.endsWith(".json", ignoreCase = true) }?.let {
                filesList.addAll(it)
            }
            singleFolder.listFiles { _, name -> name.endsWith(".qzb", ignoreCase = true) || name.endsWith(".json", ignoreCase = true) }?.let {
                filesList.addAll(it)
            }
            val sorted = filesList.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }

            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

            for (file in sorted) {
                val sizeBytes = file.length()
                val sizeFormatted = when {
                    sizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
                    sizeBytes >= 1024 -> String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0)
                    else -> "$sizeBytes B"
                }
                val lastModifiedFormatted = sdf.format(Date(file.lastModified()))

                // Quick inspect to see if it's a full package or single invoice
                var isFull = file.name.contains("بکاپ_کامل") || file.name.contains("full_backup")
                var count = 1
                try {
                    val previewStr = FileInputStream(file).use { input ->
                        val buf = ByteArray(minOf(file.length().toInt(), 2048))
                        input.read(buf)
                        String(buf, Charsets.UTF_8)
                    }
                    if (previewStr.contains("IRAN_QUARTZ_FULL_BACKUP") || previewStr.contains("\"invoices\":")) {
                        isFull = true
                        val countMatch = Regex(""""invoicesCount"\s*:\s*(\d+)""").find(previewStr)
                        count = countMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    }
                } catch (e: Exception) {
                    // Ignore inspection errors
                }

                list.add(
                    BackupFileInfo(
                        file = file,
                        name = file.name,
                        sizeFormatted = sizeFormatted,
                        lastModifiedFormatted = lastModifiedFormatted,
                        invoiceCount = count,
                        isFullBackup = isFull
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * Serializes InvoiceEntity to JSON string.
     */
    fun serializeInvoice(invoice: InvoiceEntity): String {
        val obj = JSONObject()
        obj.put("id", invoice.id)
        obj.put("invoiceNo", invoice.invoiceNo)
        obj.put("invoiceDate", invoice.invoiceDate)
        obj.put("buyerName", invoice.buyerName)
        obj.put("sellerName", invoice.sellerName)
        obj.put("sellerPhone", invoice.sellerPhone)
        obj.put("sellerAddress", invoice.sellerAddress)
        obj.put("stoneCode", invoice.stoneCode)
        obj.put("stoneType", invoice.stoneType)
        obj.put("managerSign", invoice.managerSign)
        obj.put("salesSign", invoice.salesSign)
        obj.put("itemsJson", invoice.itemsJson)
        obj.put("simpleItemsJson", invoice.simpleItemsJson)
        obj.put("percentageItemsJson", invoice.percentageItemsJson)
        obj.put("invoiceTitle", invoice.invoiceTitle)
        obj.put("invoiceSubtitle", invoice.invoiceSubtitle)
        obj.put("totalAmount", invoice.totalAmount)
        obj.put("createdAt", invoice.createdAt)
        obj.put("isDeleted", invoice.isDeleted)
        obj.put("managerSignImgBase64", invoice.managerSignImgBase64)
        obj.put("salesSignImgBase64", invoice.salesSignImgBase64)
        return obj.toString(4)
    }

    /**
     * Parses JSON string back to InvoiceEntity.
     */
    fun deserializeInvoice(json: String): InvoiceEntity {
        val obj = JSONObject(json)
        val entity = InvoiceEntity(
            id = obj.optInt("id", 0),
            invoiceNo = obj.optString("invoiceNo", ""),
            invoiceDate = obj.optString("invoiceDate", ""),
            buyerName = obj.optString("buyerName", ""),
            sellerName = obj.optString("sellerName", ""),
            sellerPhone = obj.optString("sellerPhone", ""),
            sellerAddress = obj.optString("sellerAddress", ""),
            stoneCode = obj.optString("stoneCode", ""),
            stoneType = obj.optString("stoneType", ""),
            managerSign = obj.optString("managerSign", ""),
            salesSign = obj.optString("salesSign", ""),
            itemsJson = obj.optString("itemsJson", "[]"),
            simpleItemsJson = obj.optString("simpleItemsJson", "[]"),
            percentageItemsJson = obj.optString("percentageItemsJson", "[]"),
            invoiceTitle = obj.optString("invoiceTitle", ""),
            invoiceSubtitle = obj.optString("invoiceSubtitle", ""),
            totalAmount = com.example.util.importer.TextNormalizer.parseNumber(obj.optString("totalAmount", "")) ?: obj.optDouble("totalAmount", 0.0),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            isDeleted = obj.optBoolean("isDeleted", false),
            managerSignImgBase64 = obj.optString("managerSignImgBase64", ""),
            salesSignImgBase64 = obj.optString("salesSignImgBase64", "")
        )
        val calcTotal = HtmlInvoiceGenerator.calculateInvoiceTotal(entity)
        return if (calcTotal > 0.0) entity.copy(totalAmount = calcTotal) else entity
    }

    /**
     * Saves an invoice backup to .qzb format.
     */
    fun saveInvoiceBackup(invoice: InvoiceEntity): File? {
        return try {
            val folder = getBackupFolder()
            val safeBuyer = invoice.buyerName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val safeNo = invoice.invoiceNo.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            
            // File named: فاکتور_[نام خریدار]_[شماره فاکتور].qzb
            val fileName = "فاکتور_${safeBuyer}_${safeNo}.qzb"
            val file = File(folder, fileName)
            
            val jsonStr = serializeInvoice(invoice)
            FileOutputStream(file).use { out ->
                out.write(jsonStr.toByteArray(Charsets.UTF_8))
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes a backup from the storage.
     */
    fun deleteInvoiceBackup(invoice: InvoiceEntity) {
        try {
            val folder = getBackupFolder()
            val safeBuyer = invoice.buyerName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val safeNo = invoice.invoiceNo.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val file = File(folder, "فاکتور_${safeBuyer}_${safeNo}.qzb")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Returns all potential candidate folders where backup factors might be stored.
     */
    fun getAllCandidateBackupFolders(): List<File> {
        val folders = mutableListOf<File>()
        val docDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val extRoot = Environment.getExternalStorageDirectory()

        val candidateNames = listOf(
            "IranQuartz/Backup faktors",
            "Iranquartz/backupfactors",
            "IranQuartz/backup factors",
            "IranQuartz/Backup factors",
            "Iranquartz/Backup faktors",
            "Iranquartz/backup factors",
            "IranQuartz/faktors",
            "Iranquartz/faktors",
            "IranQuartz",
            "Iranquartz",
            "Backup faktors",
            "backupfactors",
            "backup factors"
        )

        for (name in candidateNames) {
            folders.add(File(docDir, name))
            folders.add(File(downloadDir, name))
            folders.add(File(extRoot, "Documents/$name"))
            folders.add(File(extRoot, name))
        }

        // Add standard root folders
        folders.add(getBackupFolder())
        folders.add(getAppPublicRoot())

        return folders.distinctBy { it.absolutePath }
    }

    /**
     * Reads all invoice backup (.qzb and .json) files from all candidate external directories recursively.
     */
    fun loadAllBackups(): List<InvoiceEntity> {
        val list = mutableListOf<InvoiceEntity>()
        val seenIdsOrNames = mutableSetOf<String>()

        try {
            val candidateFolders = getAllCandidateBackupFolders()
            for (folder in candidateFolders) {
                if (!folder.exists() || !folder.isDirectory) continue
                val files = folder.listFiles { _, name ->
                    name.endsWith(".qzb", ignoreCase = true) || name.endsWith(".json", ignoreCase = true)
                } ?: emptyArray()

                for (file in files) {
                    try {
                        val bytes = FileInputStream(file).use { input -> input.readBytes() }
                        val json = String(bytes, Charsets.UTF_8)
                        val parseResult = parseBackupContent(json)
                        if (parseResult.success) {
                            for (inv in parseResult.invoices) {
                                val uniqueKey = "${inv.invoiceNo}_${inv.buyerName}_${inv.invoiceDate}"
                                if (uniqueKey.isNotBlank() && seenIdsOrNames.add(uniqueKey)) {
                                    list.add(inv)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * Recursively traverses a DocumentFile tree (SAF directory picker) and restores all .qzb / .json invoices.
     */
    fun restoreFromTreeUri(context: Context, treeUri: Uri): List<InvoiceEntity> {
        val list = mutableListOf<InvoiceEntity>()
        val seenKeys = mutableSetOf<String>()
        try {
            val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
            if (rootDoc != null && rootDoc.isDirectory) {
                traverseAndCollectInvoices(context, rootDoc, list, seenKeys)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun traverseAndCollectInvoices(
        context: Context,
        dir: androidx.documentfile.provider.DocumentFile,
        outList: MutableList<InvoiceEntity>,
        seenKeys: MutableSet<String>
    ) {
        val files = dir.listFiles()
        for (f in files) {
            if (f.isDirectory) {
                traverseAndCollectInvoices(context, f, outList, seenKeys)
            } else if (f.isFile) {
                val name = f.name ?: ""
                if (name.endsWith(".qzb", ignoreCase = true) || name.endsWith(".json", ignoreCase = true)) {
                    try {
                        context.contentResolver.openInputStream(f.uri)?.use { stream ->
                            val json = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                            val res = parseBackupContent(json)
                            if (res.success) {
                                for (inv in res.invoices) {
                                    val key = "${inv.invoiceNo}_${inv.buyerName}_${inv.invoiceDate}"
                                    if (key.isNotBlank() && seenKeys.add(key)) {
                                        outList.add(inv)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    /**
     * Reads invoices from a list of user-picked Document Uris.
     */
    fun restoreFromMultipleUris(context: Context, uris: List<Uri>): List<InvoiceEntity> {
        val list = mutableListOf<InvoiceEntity>()
        val seenKeys = mutableSetOf<String>()
        for (uri in uris) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val json = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                    val res = parseBackupContent(json)
                    if (res.success) {
                        for (inv in res.invoices) {
                            val key = "${inv.invoiceNo}_${inv.buyerName}_${inv.invoiceDate}"
                            if (key.isNotBlank() && seenKeys.add(key)) {
                                list.add(inv)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list
    }

    /**
     * Permanent backend backup of activated licenses on Admin's phone.
     */
    fun saveLicensesBackup(devices: List<ActivatedDeviceEntity>) {
        try {
            val folder = getLicenseFolder()
            val file = File(folder, "licenses.qzb")
            val array = JSONArray()
            for (dev in devices) {
                val obj = JSONObject()
                obj.put("deviceId", dev.deviceId)
                obj.put("licenseKey", dev.licenseKey)
                obj.put("activationDate", dev.activationDate)
                obj.put("isRevoked", dev.isRevoked)
                obj.put("userName", dev.userName)
                array.put(obj)
            }
            FileOutputStream(file).use { out ->
                out.write(array.toString(4).toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Restore license keys permanently.
     */
    fun loadLicensesBackup(): List<ActivatedDeviceEntity> {
        val list = mutableListOf<ActivatedDeviceEntity>()
        try {
            val folder = getLicenseFolder()
            val file = File(folder, "licenses.qzb")
            if (file.exists()) {
                val bytes = FileInputStream(file).use { input -> input.readBytes() }
                val jsonStr = String(bytes, Charsets.UTF_8)
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ActivatedDeviceEntity(
                            deviceId = obj.optString("deviceId", ""),
                            licenseKey = obj.optString("licenseKey", ""),
                            activationDate = obj.optString("activationDate", ""),
                            isRevoked = obj.optBoolean("isRevoked", false),
                            userName = obj.optString("userName", "")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
