package com.example.data.model

data class ComposeInvoiceItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val description: String = "",
    val width: String = "",
    val length: String = "",
    val price60cm: String = "",
) {
    val finalPrice: Double
        get() = try {
            val w = width.toDoubleOrNull() ?: 0.0
            val p60 = price60cm.toDoubleOrNull() ?: 0.0
            if (w > 0 && p60 > 0) {
                (p60 * w) / 60.0
            } else 0.0
        } catch (e: Exception) { 0.0 }

    val totalAmount: Double
        get() = try {
            val len = length.toDoubleOrNull() ?: 0.0
            finalPrice * len
        } catch (e: Exception) { 0.0 }
}

data class ComposeSimpleItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val description: String = "",
    val totalAmountStr: String = ""
) {
    val totalAmount: Double
        get() = totalAmountStr.toDoubleOrNull() ?: 0.0
}
