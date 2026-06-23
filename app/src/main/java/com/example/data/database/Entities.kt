package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val invoiceNo: String,
    val invoiceDate: String,
    val buyerName: String,
    val sellerName: String,
    val sellerPhone: String,
    val sellerAddress: String,
    val stoneCode: String,
    val stoneType: String,
    val managerSign: String,
    val salesSign: String,
    val itemsJson: String,       // JSON list of normal InvoiceItems
    val simpleItemsJson: String, // JSON list of SimpleItems
    val percentageItemsJson: String = "[]", // JSON list of PercentageItems
    val invoiceTitle: String = "فاکتور فروش صنایع سنگ ایران کوارتز",
    val invoiceSubtitle: String = "مجری فروش اسلب کوارتز ساخت و نصب کانترتاپ کوارتز کاینداستون توتم گریفین",
    val totalAmount: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val managerSignImgBase64: String = "",
    val salesSignImgBase64: String = ""
)

@Entity(tableName = "app_config")
data class ConfigEntity(
    @PrimaryKey val id: String = "current_config",
    val isLicensed: Boolean = false,
    val licensePrice: String = "۵,۰۰۰,۰۰۰ تومان",
    val licenseKey: String = "",
    val deviceId: String = "",
    val defaultSellerName: String = "",
    val defaultSellerPhone: String = "",
    val defaultSellerAddress: String = "",
    val defaultInvoiceTitle: String = "",
    val defaultInvoiceSubtitle: String = "",
    val defaultManagerSign: String = "",
    val defaultSalesSign: String = "",
    val defaultManagerSignImg: String = "",
    val defaultSalesSignImg: String = ""
)

@Entity(tableName = "activated_devices")
data class ActivatedDeviceEntity(
    @PrimaryKey val deviceId: String,
    val licenseKey: String,
    val activationDate: String,
    val isRevoked: Boolean = false,
    val userName: String = ""
)

