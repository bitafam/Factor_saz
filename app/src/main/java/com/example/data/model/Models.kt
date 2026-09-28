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
            val p60 = TextNormalizer.parseNumber(price60cm) ?: 0.0
            val rawW = TextNormalizer.parseNumber(width)
            val w = if (rawW != null && rawW > 0.0) rawW else 60.0
            if (p60 > 0.0) {
                ((p60 * w) / 60.0).roundToLong().toDouble()
            } else 0.0
        } catch (e: Exception) { 0.0 }

    val totalAmount: Double
        get() = try {
            val len = TextNormalizer.parseNumber(length) ?: 0.0
            val p60 = TextNormalizer.parseNumber(price60cm) ?: 0.0
            val rawW = TextNormalizer.parseNumber(width)
            val w = if (rawW != null && rawW > 0.0) rawW else 60.0
            if (len > 0.0 && p60 > 0.0) {
                // Exact accounting calculation: (price60 * width * length) / 60
                // Avoids intermediate rounding errors in stone billing
                ((p60 * w * len) / 60.0).roundToLong().toDouble()
            } else if (len > 0.0 && finalPrice > 0.0) {
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
            val rawQty = TextNormalizer.parseNumber(quantityStr)
            val qty = if (rawQty != null && rawQty > 0.0) rawQty else 1.0
            (amount * qty).roundToLong().toDouble()
        } catch (e: Exception) { 0.0 }
}

data class ComposePercentageItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val description: String = "", // e.g., "اجرت نصب"
    val percentageStr: String = "" // e.g., "10"
)
