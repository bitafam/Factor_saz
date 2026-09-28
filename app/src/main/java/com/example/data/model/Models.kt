package com.example.data.model

import com.example.util.importer.TextNormalizer
import kotlin.math.roundToLong

data class ComposeInvoiceItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val description: String = "",
    val width: String = "",
    val length: String = "",
    val price60cm: String = "",
) {
    val finalPrice: Double
        get() = try {
            val w = TextNormalizer.parseNumber(width) ?: 0.0
            val p60 = TextNormalizer.parseNumber(price60cm) ?: 0.0
            if (w > 0.0 && p60 > 0.0) {
                ((p60 * w) / 60.0).roundToLong().toDouble()
            } else 0.0
        } catch (e: Exception) { 0.0 }

    val totalAmount: Double
        get() = try {
            val len = TextNormalizer.parseNumber(length) ?: 0.0
            if (len > 0.0 && finalPrice > 0.0) {
                (finalPrice * len).roundToLong().toDouble()
            } else 0.0
        } catch (e: Exception) { 0.0 }
}

data class ComposeSimpleItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val description: String = "",
    val totalAmountStr: String = "",
    val quantityStr: String = "" // Optional count/quantity
) {
    val totalAmount: Double
        get() = try {
            val amount = TextNormalizer.parseNumber(totalAmountStr) ?: 0.0
            val qty = TextNormalizer.parseNumber(quantityStr) ?: 1.0
            (amount * qty).roundToLong().toDouble()
        } catch (e: Exception) { 0.0 }
}

data class ComposePercentageItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val description: String = "", // e.g., "اجرت نصب"
    val percentageStr: String = "" // e.g., "10"
)
