package com.example.util.importer

import com.example.data.model.ComposeInvoiceItem
import com.example.data.model.ComposePercentageItem
import com.example.data.model.ComposeSimpleItem

data class ParsedInvoiceResult(
    val invoiceNo: String = "",
    val invoiceDate: String = "",
    val sellerName: String = "",
    val sellerPhone: String = "",
    val sellerAddress: String = "",
    val buyerName: String = "",
    val stoneCode: String = "",
    val stoneType: String = "",
    val invoiceTitle: String = "",
    val invoiceSubtitle: String = "",
    val managerSign: String = "",
    val salesSign: String = "",
    val normalItems: List<ComposeInvoiceItem> = emptyList(),
    val simpleItems: List<ComposeSimpleItem> = emptyList(),
    val percentageItems: List<ComposePercentageItem> = emptyList(),
    val detectedGrandTotal: Double? = null,
    val sourceFormat: String = "", // "Excel" or "PDF" or "Text"
    val warnings: List<String> = emptyList()
) {
    val totalItemsCount: Int
        get() = normalItems.size + simpleItems.size + percentageItems.size

    val calculatedGrandTotal: Double
        get() {
            val normalSum = normalItems.sumOf { it.totalAmount }
            val simpleSum = simpleItems.sumOf { it.totalAmount }
            val baseSum = normalSum + simpleSum
            val percentSum = percentageItems.sumOf { (baseSum * (it.percentageStr.toDoubleOrNull() ?: 0.0)) / 100.0 }
            return baseSum + percentSum
        }
}
