package com.example.util.cloud

import com.example.data.model.CloudFileItem
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.xml.parsers.DocumentBuilderFactory

/**
 * High-performance, lightweight AWS S3 (Signature V4) Client specifically configured
 * for ArvanCloud (صندوقچه ابری آروان کلود) Object Storage.
 */
class ArvanCloudS3Client(
    val endpoint: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val customDomain: String = "",
    val region: String = "us-east-1"
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val service = "s3"

    val isConfigured: Boolean
        get() = endpoint.isNotBlank() && bucket.isNotBlank() && accessKey.isNotBlank() && secretKey.isNotBlank()

    /**
     * Constructs public URL for a given object key.
     */
    fun getPublicUrl(key: String): String {
        val cleanKey = key.trim().removePrefix("/")
        val encodedKey = cleanKey.split("/").joinToString("/") { 
            URLEncoder.encode(it, "UTF-8").replace("+", "%20") 
        }
        if (customDomain.isNotBlank()) {
            val domain = customDomain.trim().removePrefix("https://").removePrefix("http://").removeSuffix("/")
            return "https://$domain/$encodedKey"
        }
        val cleanEndpoint = endpoint.trim().removePrefix("https://").removePrefix("http://").removeSuffix("/")
        val cleanBucket = bucket.trim()
        return "https://$cleanBucket.$cleanEndpoint/$encodedKey"
    }

    /**
     * Tests connection to ArvanCloud S3 bucket by querying bucket info / objects.
     */
    fun testConnection(): Result<String> {
        if (!isConfigured) {
            return Result.failure(Exception("اطلاعات اتصال به آروان کلود ناقص است. لطفاً نام صندوقچه و کلیدها را وارد کنید."))
        }
        return try {
            val cleanEndpoint = endpoint.trim().removePrefix("https://").removePrefix("http://").removeSuffix("/")
            val cleanBucket = bucket.trim()
            val host = "$cleanBucket.$cleanEndpoint"
            val url = "https://$host/?list-type=2&max-keys=5"

            val dateStamp = getFormattedDate("yyyyMMdd")
            val amzDate = getFormattedDate("yyyyMMdd'T'HHmmss'Z'")
            val payloadHash = sha256Hex(ByteArray(0))

            val queryMap = sortedMapOf(
                "list-type" to "2",
                "max-keys" to "5"
            )
            val canonicalQueryString = queryMap.entries.joinToString("&") {
                "${it.key}=${it.value}"
            }

            val headersToSign = sortedMapOf(
                "host" to host,
                "x-amz-content-sha256" to payloadHash,
                "x-amz-date" to amzDate
            )

            val canonicalHeaders = headersToSign.entries.joinToString("\n") { "${it.key}:${it.value}" } + "\n"
            val signedHeaders = headersToSign.keys.joinToString(";")

            val canonicalRequest = listOf(
                "GET",
                "/",
                canonicalQueryString,
                canonicalHeaders,
                signedHeaders,
                payloadHash
            ).joinToString("\n")

            val credentialScope = "$dateStamp/$region/$service/aws4_request"
            val stringToSign = listOf(
                "AWS4-HMAC-SHA256",
                amzDate,
                credentialScope,
                sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8))
            ).joinToString("\n")

            val signingKey = getSignatureKey(secretKey, dateStamp, region, service)
            val signature = hmacHex(signingKey, stringToSign)

            val authHeader = "AWS4-HMAC-SHA256 Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Host", host)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authHeader)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Result.success("اتصال به صندوقچه ابری «$cleanBucket» با موفقیت برقرار شد.")
            } else {
                val errorMsg = parseS3ErrorMessage(responseBody) ?: "کد خطا: ${response.code} (${response.message})"
                Result.failure(Exception("خطا در اتصال به آروان کلود: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("خطای شبکه یا اتصال به آروان کلود: ${e.message}"))
        }
    }

    /**
     * Uploads content (HTML, WebP, etc.) to ArvanCloud S3 bucket and returns public URL.
     */
    fun putObject(key: String, content: ByteArray, contentType: String, isPublic: Boolean = true): Result<String> {
        if (!isConfigured) {
            return Result.failure(Exception("اطلاعات اتصال به آروان کلود وارد نشده است."))
        }
        return try {
            val cleanEndpoint = endpoint.trim().removePrefix("https://").removePrefix("http://").removeSuffix("/")
            val cleanBucket = bucket.trim()
            val host = "$cleanBucket.$cleanEndpoint"
            val cleanKey = key.trim().removePrefix("/")
            val encodedPath = "/" + cleanKey.split("/").joinToString("/") { 
                URLEncoder.encode(it, "UTF-8").replace("+", "%20") 
            }

            val url = "https://$host$encodedPath"

            val dateStamp = getFormattedDate("yyyyMMdd")
            val amzDate = getFormattedDate("yyyyMMdd'T'HHmmss'Z'")
            val payloadHash = sha256Hex(content)

            val headersToSign = sortedMapOf(
                "content-type" to contentType,
                "host" to host,
                "x-amz-content-sha256" to payloadHash,
                "x-amz-date" to amzDate
            )

            if (isPublic) {
                headersToSign["x-amz-acl"] = "public-read"
            }

            val canonicalHeaders = headersToSign.entries.joinToString("\n") { "${it.key}:${it.value}" } + "\n"
            val signedHeaders = headersToSign.keys.joinToString(";")

            val canonicalRequest = listOf(
                "PUT",
                encodedPath,
                "", // empty query string
                canonicalHeaders,
                signedHeaders,
                payloadHash
            ).joinToString("\n")

            val credentialScope = "$dateStamp/$region/$service/aws4_request"
            val stringToSign = listOf(
                "AWS4-HMAC-SHA256",
                amzDate,
                credentialScope,
                sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8))
            ).joinToString("\n")

            val signingKey = getSignatureKey(secretKey, dateStamp, region, service)
            val signature = hmacHex(signingKey, stringToSign)

            val authHeader = "AWS4-HMAC-SHA256 Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

            val reqBuilder = Request.Builder()
                .url(url)
                .put(content.toRequestBody(contentType.toMediaTypeOrNull()))
                .header("Host", host)
                .header("Content-Type", contentType)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authHeader)

            if (isPublic) {
                reqBuilder.header("x-amz-acl", "public-read")
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            val respBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val publicUrl = getPublicUrl(cleanKey)
                Result.success(publicUrl)
            } else {
                val errorMsg = parseS3ErrorMessage(respBody) ?: "کد خطا: ${response.code} (${response.message})"
                Result.failure(Exception("خطا در بارگذاری فایل در صندوقچه: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("خطای بارگذاری در آروان کلود: ${e.message}"))
        }
    }

    /**
     * Deletes an object from ArvanCloud S3 bucket.
     */
    fun deleteObject(key: String): Result<Boolean> {
        if (!isConfigured) {
            return Result.failure(Exception("اطلاعات اتصال به آروان کلود وارد نشده است."))
        }
        return try {
            val cleanEndpoint = endpoint.trim().removePrefix("https://").removePrefix("http://").removeSuffix("/")
            val cleanBucket = bucket.trim()
            val host = "$cleanBucket.$cleanEndpoint"
            val cleanKey = key.trim().removePrefix("/")
            val encodedPath = "/" + cleanKey.split("/").joinToString("/") { 
                URLEncoder.encode(it, "UTF-8").replace("+", "%20") 
            }

            val url = "https://$host$encodedPath"

            val dateStamp = getFormattedDate("yyyyMMdd")
            val amzDate = getFormattedDate("yyyyMMdd'T'HHmmss'Z'")
            val payloadHash = sha256Hex(ByteArray(0))

            val headersToSign = sortedMapOf(
                "host" to host,
                "x-amz-content-sha256" to payloadHash,
                "x-amz-date" to amzDate
            )

            val canonicalHeaders = headersToSign.entries.joinToString("\n") { "${it.key}:${it.value}" } + "\n"
            val signedHeaders = headersToSign.keys.joinToString(";")

            val canonicalRequest = listOf(
                "DELETE",
                encodedPath,
                "",
                canonicalHeaders,
                signedHeaders,
                payloadHash
            ).joinToString("\n")

            val credentialScope = "$dateStamp/$region/$service/aws4_request"
            val stringToSign = listOf(
                "AWS4-HMAC-SHA256",
                amzDate,
                credentialScope,
                sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8))
            ).joinToString("\n")

            val signingKey = getSignatureKey(secretKey, dateStamp, region, service)
            val signature = hmacHex(signingKey, stringToSign)

            val authHeader = "AWS4-HMAC-SHA256 Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

            val request = Request.Builder()
                .url(url)
                .delete()
                .header("Host", host)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authHeader)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful || response.code == 204 || response.code == 404) {
                Result.success(true)
            } else {
                val respBody = response.body?.string() ?: ""
                val errorMsg = parseS3ErrorMessage(respBody) ?: "کد خطا: ${response.code}"
                Result.failure(Exception("خطا در حذف فایل: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("خطای حذف از آروان کلود: ${e.message}"))
        }
    }

    /**
     * Lists all objects in the bucket, optionally filtered by prefix.
     */
    fun listObjects(prefix: String = ""): Result<List<CloudFileItem>> {
        if (!isConfigured) {
            return Result.failure(Exception("اطلاعات اتصال به آروان کلود وارد نشده است."))
        }
        return try {
            val cleanEndpoint = endpoint.trim().removePrefix("https://").removePrefix("http://").removeSuffix("/")
            val cleanBucket = bucket.trim()
            val host = "$cleanBucket.$cleanEndpoint"

            val queryMap = sortedMapOf(
                "list-type" to "2",
                "max-keys" to "1000"
            )
            if (prefix.isNotBlank()) {
                queryMap["prefix"] = prefix.trim().removePrefix("/")
            }

            val canonicalQueryString = queryMap.entries.joinToString("&") {
                "${it.key}=${URLEncoder.encode(it.value, "UTF-8").replace("+", "%20")}"
            }

            val url = "https://$host/?$canonicalQueryString"

            val dateStamp = getFormattedDate("yyyyMMdd")
            val amzDate = getFormattedDate("yyyyMMdd'T'HHmmss'Z'")
            val payloadHash = sha256Hex(ByteArray(0))

            val headersToSign = sortedMapOf(
                "host" to host,
                "x-amz-content-sha256" to payloadHash,
                "x-amz-date" to amzDate
            )

            val canonicalHeaders = headersToSign.entries.joinToString("\n") { "${it.key}:${it.value}" } + "\n"
            val signedHeaders = headersToSign.keys.joinToString(";")

            val canonicalRequest = listOf(
                "GET",
                "/",
                canonicalQueryString,
                canonicalHeaders,
                signedHeaders,
                payloadHash
            ).joinToString("\n")

            val credentialScope = "$dateStamp/$region/$service/aws4_request"
            val stringToSign = listOf(
                "AWS4-HMAC-SHA256",
                amzDate,
                credentialScope,
                sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8))
            ).joinToString("\n")

            val signingKey = getSignatureKey(secretKey, dateStamp, region, service)
            val signature = hmacHex(signingKey, stringToSign)

            val authHeader = "AWS4-HMAC-SHA256 Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Host", host)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authHeader)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val items = parseListBucketResult(responseBody)
                Result.success(items)
            } else {
                val errorMsg = parseS3ErrorMessage(responseBody) ?: "کد خطا: ${response.code}"
                Result.failure(Exception("خطا در دریافت لیست فایل‌ها: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("خطای لیست فایل‌های آروان کلود: ${e.message}"))
        }
    }

    private fun parseListBucketResult(xmlContent: String): List<CloudFileItem> {
        val list = mutableListOf<CloudFileItem>()
        if (xmlContent.isBlank()) return list
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xmlContent.toByteArray(StandardCharsets.UTF_8)))
            doc.documentElement.normalize()

            val contentsNodes = doc.getElementsByTagName("Contents")
            for (i in 0 until contentsNodes.length) {
                val node = contentsNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val elem = node as Element
                    val key = elem.getElementsByTagName("Key").item(0)?.textContent ?: ""
                    val sizeStr = elem.getElementsByTagName("Size").item(0)?.textContent ?: "0"
                    val lastModified = elem.getElementsByTagName("LastModified").item(0)?.textContent ?: ""
                    val sizeBytes = sizeStr.toLongOrNull() ?: 0L

                    if (key.isNotBlank()) {
                        val parts = key.split("/")
                        val fileName = parts.last()
                        val folderName = if (parts.size > 1) parts.dropLast(1).joinToString("/") else ""
                        val isHtml = fileName.endsWith(".html", ignoreCase = true) || fileName.endsWith(".htm", ignoreCase = true)
                        val isWebp = fileName.endsWith(".webp", ignoreCase = true)

                        list.add(
                            CloudFileItem(
                                key = key,
                                fileName = fileName,
                                folderName = folderName,
                                sizeBytes = sizeBytes,
                                lastModified = lastModified,
                                url = getPublicUrl(key),
                                isHtmlInvoice = isHtml,
                                isWebpAttachment = isWebp
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun parseS3ErrorMessage(xml: String): String? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)))
            val messageNode = doc.getElementsByTagName("Message").item(0)
            val codeNode = doc.getElementsByTagName("Code").item(0)
            val code = codeNode?.textContent ?: ""
            val msg = messageNode?.textContent ?: ""
            if (code.isNotBlank() || msg.isNotBlank()) "$code: $msg" else null
        } catch (e: Exception) {
            null
        }
    }

    // --- AWS SigV4 Crypto Helpers ---
    private fun getFormattedDate(pattern: String): String {
        val sdf = SimpleDateFormat(pattern, Locale.US)
        sdf.timeZone = SimpleTimeZone(0, "UTC")
        return sdf.format(Date())
    }

    private fun sha256Hex(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(data)
        return bytesToHex(digest)
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hmacHex(key: ByteArray, data: String): String {
        return bytesToHex(hmacSha256(key, data))
    }

    private fun getSignatureKey(key: String, dateStamp: String, regionName: String, serviceName: String): ByteArray {
        val kSecret = ("AWS4$key").toByteArray(StandardCharsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp)
        val kRegion = hmacSha256(kDate, regionName)
        val kService = hmacSha256(kRegion, serviceName)
        return hmacSha256(kService, "aws4_request")
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            result.append(hexChars[i ushr 4])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }
}
