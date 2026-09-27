package com.example.util.importer

object TextNormalizer {

    private val PERSIAN_ARABIC_DIGITS = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )

    fun toEnglishDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val mapped = PERSIAN_ARABIC_DIGITS[ch]
            sb.append(mapped ?: ch)
        }
        return sb.toString()
    }

    fun cleanText(input: String): String {
        return input
            .replace('\u200C', ' ') // Zero-width non-joiner
            .replace('\u200B', ' ') // Zero-width space
            .replace('\uFEFF', ' ') // Zero-width no-break space
            .replace('\u0640', ' ') // Tatweel
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace('ة', 'ه')
            .trim()
    }

    /**
     * Parse monetary or number string (e.g. "115,000,000", "586,500,000", "2.55", "3/4", ".57", "/57", "،57")
     */
    fun parseNumber(str: String): Double? {
        val raw = toEnglishDigits(str)
            .replace("ریال", "")
            .replace("تومان", "")
            .replace("متر", "")
            .replace("سانت", "")
            .replace("cm", "", ignoreCase = true)
            .replace("m", "", ignoreCase = true)
            .replace(" ", "")
            .trim()
        if (raw.isBlank()) return null

        // Replace Persian decimal separators ('/' or '،') with standard '.'
        var normalized = raw
            .replace('،', '.')
            .replace('/', '.')

        // If it starts with '.' (e.g. ".57" or ".5"), prepend '0' -> "0.57"
        if (normalized.startsWith(".")) {
            normalized = "0$normalized"
        }

        // If it has commas (e.g. 115,000,000), strip commas if used as thousands separators
        if (normalized.contains(",")) {
            // If comma is used like 3,4 and no other commas, treat as decimal, else remove
            if (normalized.count { it == ',' } == 1 && normalized.substringAfter(',').length <= 2) {
                normalized = normalized.replace(',', '.')
            } else {
                normalized = normalized.replace(",", "")
            }
        }

        return normalized.toDoubleOrNull()
    }

    /**
     * Checks whether text extracted from PDF appears to be visually reversed (common in RTL PDFs).
     */
    fun isVisuallyReversed(text: String): Boolean {
        val reversedIndicators = listOf(
            "یف رع", "غلبم لک", "فیدر", "هحفص", "رختسا", "هتخاس", "خیرات", "هقبط", "متوت", "زتراوک"
        )
        return reversedIndicators.any { text.contains(it) }
    }

    /**
     * If Persian word characters are reversed, fixes each Persian token while preserving English/digits.
     */
    fun fixReversedPersian(line: String): String {
        if (!isVisuallyReversed(line)) return line

        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (isPersianLetter(ch)) {
                val start = i
                while (i < line.length && (isPersianLetter(line[i]) || line[i] == '‌')) {
                    i++
                }
                val persianWord = line.substring(start, i)
                sb.append(persianWord.reversed())
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    private fun isPersianLetter(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x0600..0x06FF) || (code in 0xFB50..0xFDFF) || (code in 0xFE70..0xFEFF)
    }
}
