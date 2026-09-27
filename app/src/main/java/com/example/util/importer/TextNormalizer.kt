package com.example.util.importer

object TextNormalizer {

    private val PERSIAN_ARABIC_DIGITS = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )

    private val REVERSED_PHRASES = listOf(
        "یف رع 60cm" to "فی عرض 60cm",
        "یف عرض هتخاس شده" to "فی عرض ساخته شده",
        "غلبم لک" to "مبلغ کل",
        "عمج لک" to "جمع کل",
        "دک سنگ" to "کد سنگ",
        "فیدر شرح لااک" to "ردیف شرح کالا",
        "شرح لااک" to "شرح کالا",
        "متوت، کاینداستون، زتراوک" to "توتم، کاینداستون، کوارتز",
        "صفحات تنیباک" to "صفحات کابینت",
        "دنیاک استون" to "کایند استون",
        "هحفص و دیوار رختسا" to "صفحه و دیوار استخر",
        "هحفص هقبط اول" to "صفحه طبقه اول",
        "هحفص فکمه" to "صفحه همکف",
        "هحفص خبطم" to "صفحه مطبخ",
        "هحفص" to "صفحه",
        "خبطم" to "مطبخ",
        "فکمه" to "همکف",
        "هقبط اول" to "طبقه اول",
        "هقبط" to "طبقه",
        "رختسا" to "استخر",
        "هتخاس" to "ساخته",
        "هیشاح" to "حاشیه",
        "تمسق هود" to "قسمت دوم",
        "تمسق" to "قسمت",
        "هود" to "دوم",
        "یعیبط" to "طبیعی",
        "ینیسح" to "حسینی",
        "خیرات" to "تاریخ",
        "نفلت" to "تلفن",
        "فیدر" to "ردیف",
        "لااک" to "کالا",
        "تنیباک" to "کابینت",
        "زتراوک" to "کوارتز",
        "متوت" to "توتم"
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
            if (normalized.count { it == ',' } == 1 && normalized.substringAfter(',').length <= 2) {
                normalized = normalized.replace(',', '.')
            } else {
                normalized = normalized.replace(",", "")
            }
        }

        return normalized.toDoubleOrNull()
    }

    /**
     * Normalizes reversed phrases in PDF text.
     */
    fun fixReversedPersian(line: String): String {
        var fixedLine = line
        for ((rev, orig) in REVERSED_PHRASES) {
            if (fixedLine.contains(rev)) {
                fixedLine = fixedLine.replace(rev, orig)
            }
        }
        return fixedLine
    }
}
