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
    val totalAmount: Double,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_config")
data class ConfigEntity(
    @PrimaryKey val id: String = "current_config",
    val isLicensed: Boolean = false,
    val licensePrice: String = "۵,۰۰۰,۰۰۰ تومان",
    val licenseKey: String = "",
    val deviceId: String = ""
)
