package com.example.util

import android.content.Context
import android.os.Environment
import com.example.data.database.InvoiceEntity
import com.example.data.database.ActivatedDeviceEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object InvoiceBackupManager {

    private const val ROOT_DIR_NAME = "IranQuartz"
    private const val BACKUP_DIR_NAME = "Backup faktors"
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
        return InvoiceEntity(
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
            totalAmount = obj.optDouble("totalAmount", 0.0),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            isDeleted = obj.optBoolean("isDeleted", false),
            managerSignImgBase64 = obj.optString("managerSignImgBase64", ""),
            salesSignImgBase64 = obj.optString("salesSignImgBase64", "")
        )
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
     * Reads all invoice backup (.qzb) files from external directory.
     */
    fun loadAllBackups(): List<InvoiceEntity> {
        val list = mutableListOf<InvoiceEntity>()
        try {
            val folder = getBackupFolder()
            val files = folder.listFiles { _, name -> name.endsWith(".qzb") } ?: emptyArray()
            for (file in files) {
                try {
                    val bytes = FileInputStream(file).use { input -> input.readBytes() }
                    val json = String(bytes, Charsets.UTF_8)
                    val invoice = deserializeInvoice(json)
                    list.add(invoice)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
