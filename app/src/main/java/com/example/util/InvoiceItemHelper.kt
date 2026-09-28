package com.example.util

/**
 * Utility for formatting invoice items, connecting descriptions with dimensions,
 * and maintaining standard stone trade nomenclature.
 */
object InvoiceItemHelper {
    // Regex matching "به عرض ..." or "(به عرض ...)" at the end of the text
    private val WIDTH_SUFFIX_REGEX = Regex("""\s*(?:به\s*عرض|\(به\s*عرض)\s*[^)]*\)?$""", RegexOption.IGNORE_CASE)

    /**
     * Removes any existing "به عرض [مقدار]" suffix from the description.
     */
    fun stripWidthSuffix(description: String): String {
        return description.replace(WIDTH_SUFFIX_REGEX, "").trim()
    }

    /**
     * Attaches or updates "به عرض [عرض]" to the stone item description.
     * Example: "صفحه" with width "60" -> "صفحه به عرض 60"
     * If width is empty, returns the clean base description.
     */
    fun formatDescriptionWithWidth(baseDesc: String, widthStr: String): String {
        val cleanBase = stripWidthSuffix(baseDesc)
        val cleanWidth = widthStr.trim()
        return when {
            cleanBase.isBlank() -> ""
            cleanWidth.isNotBlank() -> "$cleanBase به عرض $cleanWidth"
            else -> cleanBase
        }
    }
}
