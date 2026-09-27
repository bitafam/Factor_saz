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
     * Extracts only the clean product / service title, stripping all digits, prices, currencies, and punctuation.
     */
    fun extractCleanTitle(raw: String): String {
        val fixed = fixReversedPersian(raw)
        var text = cleanText(fixed)

        // Remove row index numbers at beginning like "1-", "2.", "۱-", "۱ "
        text = text.replace(Regex("""^[0-9۰-۹٠-٩]+[\s\.\-–:：]+"""), "")

        // Remove explicit quantities like "1 عدد", "۲ عدد", "1دستگاه", "1عدد"
        text = text.replace(Regex("""[0-9۰-۹٠-٩]+\s*(?:عدد|دستگاه|شاخه|متر|ساعت|نفر)"""), "")

        // Remove currency words and symbols
        text = text
            .replace("ریال", "")
            .replace("تومان", "")
            .replace("تومن", "")
            .replace("Rials", "")
            .replace("Rial", "")
            .replace("IRR", "")

        // Remove table header / label keywords if stuck to title
        text = text
            .replace(Regex("""(?:مبلغ کل|مبلغ|فی پایه|قیمت کل|قیمت|جمع کل|شرح کالا|شرح)\s*[:：]?"""), "")

        // Remove all numbers (both integer and decimals / comma-separated)
        text = text.replace(Regex("""[0-9۰-۹٠-٩]+(?:[\,\.٫/][0-9۰-۹٠-٩]+)*"""), "")

        // Remove punctuation and leftover brackets / dashes
        text = text
            .replace(Regex("""[,\/\\\:\：\*\-\–\—\_]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return text
    }

    /**
     * Extracts all numeric values from text (handling Persian digits, commas, and decimals).
     */
    fun extractAllNumbers(input: String): List<Double> {
        val eng = toEnglishDigits(input)
        val matches = Regex("""(\d+(?:[.,/]\d+)?)""").findAll(eng.replace(",", ""))
        val list = mutableListOf<Double>()
        for (m in matches) {
            val raw = m.groupValues[1].replace('/', '.')
            val d = raw.toDoubleOrNull()
            if (d != null) list.add(d)
        }
        return list
    }

    /**
     * Extracts quantity if specified (e.g. "2 عدد", "3 دستگاه"), defaulting to "1".
     */
    fun extractQuantity(input: String): String {
        val eng = toEnglishDigits(input)
        val match = Regex("""(\d+)\s*(?:عدد|دستگاه|شاخه|متر|نفر|ساعت)""").find(eng)
        return match?.groupValues?.get(1) ?: "1"
    }

    /**
     * Parse monetary or number string (e.g. "115,000,000", "586,500,000", "2.55", "3/4")
     */
    fun parseNumber(str: String): Double? {
        val eng = toEnglishDigits(str)
            .replace(",", "")
            .replace(" ", "")
            .replace("ریال", "")
            .replace("تومان", "")
            .trim()
        if (eng.isBlank()) return null
        
        // Handle slash as decimal separator in Persian (e.g. 3/4 -> 3.4)
        val normalized = if (eng.contains('/') && !eng.contains('.')) {
            eng.replace('/', '.')
        } else {
            eng
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
