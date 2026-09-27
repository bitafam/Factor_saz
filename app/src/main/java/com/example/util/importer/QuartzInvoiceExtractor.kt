package com.example.util.importer

import com.example.data.model.ComposeInvoiceItem
import com.example.data.model.ComposeSimpleItem
import java.text.DecimalFormat
import kotlin.math.roundToInt

object QuartzInvoiceExtractor {

    private val moneyFormatter = DecimalFormat("#,###")

    /**
     * Extracts invoice data from a 2D matrix of cells (Excel / CSV).
     * Strictly extracts only: Description, Width (cm), Length (m), and Price for 60cm width.
     */
    fun extractFromMatrix(matrix: List<List<String>>, sourceName: String = "Excel"): ParsedInvoiceResult {
        var invoiceDate = ""
        var invoiceNo = ""
        var sellerName = ""
        var sellerPhone = ""
        var sellerAddress = ""
        var buyerName = ""
        var stoneCode = ""
        var stoneType = ""
        var invoiceTitle = ""
        var invoiceSubtitle = ""
        var managerSign = ""
        var salesSign = ""
        var detectedTotal: Double? = null

        val normalItems = mutableListOf<ComposeInvoiceItem>()
        val simpleItems = mutableListOf<ComposeSimpleItem>()
        val warnings = mutableListOf<String>()

        var tableHeaderRowIndex = -1
        var colDesc = -1
        var colWidth = -1
        var colLength = -1
        var colP60 = -1
        var colTotal = -1
        var colQty = -1

        // 1. Scan for header row and accurately map standard quartz columns
        for (r in matrix.indices) {
            val row = matrix[r]
            val rowClean = row.map { TextNormalizer.cleanText(it) }
            val rowText = rowClean.joinToString(" ")

            if (rowText.contains("شرح") || (rowText.contains("عرض") && (rowText.contains("طول") || rowText.contains("فی")))) {
                tableHeaderRowIndex = r
                for (c in rowClean.indices) {
                    val cell = rowClean[c]
                    if (cell.contains("شرح") || cell.contains("کالا") || cell.contains("توضیحات")) {
                        if (!cell.contains("فی") && !cell.contains("مبلغ")) colDesc = c
                    } else if (cell.contains("عرض") && !cell.contains("فی") && !cell.contains("ساخته") && !cell.contains("مبلغ")) {
                        colWidth = c
                    } else if (cell.contains("طول") && !cell.contains("فی") && !cell.contains("مبلغ")) {
                        colLength = c
                    } else if (cell.contains("۶۰") || cell.contains("60") || (cell.contains("فی") && !cell.contains("ساخته") && !cell.contains("کل"))) {
                        colP60 = c
                    } else if (cell.contains("مبلغ کل") || cell.contains("قیمت کل") || cell == "مبلغ") {
                        colTotal = c
                    } else if (cell.contains("تعداد") || cell.contains("مقدار")) {
                        colQty = c
                    }
                }
                break
            }
        }

        // 2. Extract metadata from all non-table rows
        for (r in matrix.indices) {
            val row = matrix[r]
            for (c in row.indices) {
                val cell = TextNormalizer.cleanText(row[c])
                val cellEng = TextNormalizer.toEnglishDigits(cell)

                // Date
                val dateMatch = Regex("""(1[34]\d{2}[/-]\d{1,2}[/-]\d{1,2})""").find(cellEng)
                if (dateMatch != null && invoiceDate.isBlank()) {
                    invoiceDate = dateMatch.groupValues[1]
                }

                // Invoice No
                if (cell.contains("شماره:") || cell.contains("شماره فاکتور") || cell.contains("شماره سند")) {
                    val candidate = if (cell.contains(":")) cell.substringAfter(":") else if (c + 1 < row.size) row[c + 1] else ""
                    val cleanNo = TextNormalizer.cleanText(candidate).trim()
                    if (cleanNo.isNotBlank() && invoiceNo.isBlank()) {
                        invoiceNo = cleanNo
                    }
                }

                // Buyer Name
                if (cell.contains("خریدار") || cell.contains("نام حقیقی :") || cell.contains("نام حقیقی / حقوقی") || cell.contains("نام خریدار") || cell.contains("طرف حساب")) {
                    val candidate = if (cell.contains(":")) cell.substringAfter(":") else if (c + 1 < row.size) row[c + 1] else ""
                    val cleanB = TextNormalizer.cleanText(candidate).replace("مشخصات", "").replace("شماره تماس", "").trim()
                    if (cleanB.isNotBlank() && buyerName.isBlank() && !cleanB.contains("شماره")) {
                        buyerName = cleanB
                    }
                }

                // Seller
                if (cell.contains("فروشنده") || cell.contains("کایند استون") || cell.contains("کانتر استون")) {
                    if (sellerName.isBlank() && (cell.contains("کایند") || cell.contains("استون") || cell.contains("توتم"))) {
                        sellerName = "کایند استون - توتم"
                    }
                }

                // Stone Code & Type
                if (cell.contains("کد سنگ:") || cell.contains("کد طرح:")) {
                    val after = cell.substringAfter(":").trim()
                    if (after.isNotBlank() && after != "-") stoneCode = after
                }
                if (cell.contains("نوع سنگ:") || cell.contains("نوع متریال:")) {
                    val after = cell.substringAfter(":").trim()
                    if (after.isNotBlank() && after != "-") stoneType = after
                }

                // Grand Total
                if (cell.contains("جمع کل") || cell == "جمع") {
                    val num = TextNormalizer.parseNumber(cell)
                    if (num != null && num > 1000) {
                        detectedTotal = num
                    } else if (c + 1 < row.size) {
                        val nextNum = TextNormalizer.parseNumber(row[c + 1])
                        if (nextNum != null && nextNum > 1000) detectedTotal = nextNum
                    }
                }
            }
        }

        // 3. Process Table Rows
        val startRow = if (tableHeaderRowIndex >= 0) tableHeaderRowIndex + 1 else 0
        for (r in startRow until matrix.size) {
            val row = matrix[r]
            val rowText = row.joinToString(" ") { it.trim() }
            if (rowText.isBlank()) continue

            // Skip summary rows
            if (rowText.startsWith("جمع کل") || rowText.contains("مدیر فروش:") || rowText.contains("کارشناس فروش:") || rowText == "جمع") {
                val num = TextNormalizer.parseNumber(rowText)
                if (num != null && num > 1000 && detectedTotal == null) {
                    detectedTotal = num
                }
                continue
            }

            // Extract cells based on detected standard columns
            val rawDesc = (if (colDesc in row.indices) row[colDesc] else "").trim()
            val wStr = (if (colWidth in row.indices) row[colWidth] else "").trim()
            val lStr = (if (colLength in row.indices) row[colLength] else "").trim()
            val p60Str = (if (colP60 in row.indices) row[colP60] else "").trim()
            val totStr = (if (colTotal in row.indices) row[colTotal] else "").trim()
            val qtyStr = (if (colQty in row.indices) row[colQty] else "").trim()

            val wNum = TextNormalizer.parseNumber(wStr)
            val lNum = TextNormalizer.parseNumber(lStr)
            val p60Num = TextNormalizer.parseNumber(p60Str)
            val totNum = TextNormalizer.parseNumber(totStr)

            val cleanDesc = TextNormalizer.cleanText(rawDesc.ifBlank {
                row.firstOrNull { it.isNotBlank() && TextNormalizer.parseNumber(it) == null } ?: ""
            })

            // If width and length are present, it's a standard dimensional quartz item
            if (wNum != null && lNum != null && (wNum > 0 || lNum > 0)) {
                val standardizedWidth = standardizeWidth(wNum)
                val standardizedLength = standardizeLength(lNum)
                val p60Formatted = formatPrice(p60Num ?: totNum)

                normalItems.add(
                    ComposeInvoiceItem(
                        description = cleanDesc.ifBlank { "ردیف سنگی ${normalItems.size + 1}" },
                        width = standardizedWidth,
                        length = standardizedLength,
                        price60cm = p60Formatted
                    )
                )
            } else if (cleanDesc.isNotBlank() && (totNum != null || isServiceKeyword(cleanDesc))) {
                // Service / Simple Item (e.g. sink, cut, transport, installation)
                val amount = totNum ?: p60Num ?: 0.0
                val qtyMatch = Regex("""(\d+)\s*(?:عدد|مورد|شاخه|متر)""").find(TextNormalizer.toEnglishDigits(cleanDesc))
                val qty = qtyMatch?.groupValues?.get(1) ?: qtyStr.ifBlank { "1" }

                simpleItems.add(
                    ComposeSimpleItem(
                        description = cleanDesc,
                        totalAmountStr = if (amount > 0) moneyFormatter.format(amount.toLong()) else "",
                        quantityStr = qty
                    )
                )
            }
        }

        return ParsedInvoiceResult(
            invoiceNo = invoiceNo,
            invoiceDate = invoiceDate,
            sellerName = sellerName,
            sellerPhone = sellerPhone,
            sellerAddress = sellerAddress,
            buyerName = buyerName,
            stoneCode = stoneCode,
            stoneType = stoneType,
            invoiceTitle = invoiceTitle,
            invoiceSubtitle = invoiceSubtitle,
            managerSign = managerSign,
            salesSign = salesSign,
            normalItems = normalItems,
            simpleItems = simpleItems,
            detectedGrandTotal = detectedTotal,
            sourceFormat = sourceName,
            warnings = warnings
        )
    }

    /**
     * Extracts invoice data from a list of text lines (PDF text extraction or pasted text).
     * Extracts only: Description, Width (cm), Length (m), and Price for 60cm width.
     */
    fun extractFromLines(lines: List<String>, sourceName: String = "PDF"): ParsedInvoiceResult {
        // Pre-process: fix reversed Persian lines if necessary
        val processedLines = lines.map { raw ->
            val fixed = TextNormalizer.fixReversedPersian(raw)
            TextNormalizer.cleanText(fixed)
        }

        var invoiceDate = ""
        var invoiceNo = ""
        var sellerName = ""
        var sellerPhone = ""
        var sellerAddress = ""
        var buyerName = ""
        var stoneCode = ""
        var stoneType = ""
        var invoiceTitle = ""
        var invoiceSubtitle = ""
        var managerSign = ""
        var salesSign = ""
        var detectedTotal: Double? = null

        val normalItems = mutableListOf<ComposeInvoiceItem>()
        val simpleItems = mutableListOf<ComposeSimpleItem>()
        val warnings = mutableListOf<String>()

        for (line in processedLines) {
            if (line.isBlank()) continue
            val engLine = TextNormalizer.toEnglishDigits(line)

            // 1. Date matching
            val dateMatch = Regex("""(1[34]\d{2}[/-]\d{1,2}[/-]\d{1,2})""").find(engLine)
            if (dateMatch != null && invoiceDate.isBlank()) {
                invoiceDate = dateMatch.groupValues[1]
            }

            // 2. Buyer Name
            if (line.contains("خریدار") || line.contains("نام حقیقی") || line.contains("طرف حساب") || line.contains("آقای ") || line.contains("اقای ") || line.contains("خانم ")) {
                val match = Regex("""(?:نام خریدار|خریدار|نام حقیقی\s*/\s*حقوقی|نام حقیقی|طرف حساب)\s*[:：]?\s*([^0-9\n]{2,30})""").find(line)
                if (match != null && buyerName.isBlank()) {
                    val candidate = match.groupValues[1].replace("شماره تماس", "").replace("شماره", "").replace("مشخصات", "").trim()
                    if (candidate.isNotBlank() && !candidate.contains("فروش") && !candidate.contains("استون")) {
                        buyerName = candidate
                    }
                } else if (line.contains("آقای ") || line.contains("اقای ") || line.contains("خانم ")) {
                    val idx = when {
                        line.indexOf("آقای ") >= 0 -> line.indexOf("آقای ")
                        line.indexOf("اقای ") >= 0 -> line.indexOf("اقای ")
                        else -> line.indexOf("خانم ")
                    }
                    val candidate = line.substring(idx).take(25).replace("شماره تماس", "").trim()
                    if (buyerName.isBlank() && !candidate.contains("فروش") && !candidate.contains("مدیر") && !candidate.contains("استون")) {
                        buyerName = candidate
                    }
                }
            }

            // 3. Invoice Number
            if (line.contains("شماره:") || line.contains("شماره فاکتور:") || line.contains("شماره سند:")) {
                val after = line.substringAfter(":").trim()
                if (after.isNotBlank() && after != "-" && invoiceNo.isBlank()) {
                    invoiceNo = after
                }
            }

            // 4. Stone Code & Type (Leave blank if not present)
            if (line.contains("کد سنگ:") || line.contains("کد طرح:")) {
                val after = line.substringAfter(":").trim()
                if (after.isNotBlank() && after != "-") {
                    stoneCode = after
                }
            }
            if (line.contains("نوع سنگ:") || line.contains("نوع متریال:")) {
                val after = line.substringAfter(":").trim()
                if (after.isNotBlank() && after != "-") {
                    stoneType = after
                }
            }

            // 5. Grand Total Line
            if (line.contains("جمع کل") || line.contains("مبلغ کل فاکتور") || line.startsWith("جمع:") || line.startsWith("عمج لک") || line == "جمع") {
                val numbers = extractAllNumbersFromLine(engLine)
                val bigNum = numbers.firstOrNull { it > 100_000 }
                if (bigNum != null) {
                    detectedTotal = bigNum
                }
                continue
            }

            // Skip pure non-item metadata/header lines
            if (isNonItemLine(line)) {
                val numbers = extractAllNumbersFromLine(engLine)
                val bigNum = numbers.firstOrNull { it > 100_000 }
                if (bigNum != null && detectedTotal == null) {
                    detectedTotal = bigNum
                }
                continue
            }

            // 6. Parse Item Row
            val item = parseLineIntelligently(line, engLine)
            if (item is ComposeInvoiceItem) {
                normalItems.add(item)
            } else if (item is ComposeSimpleItem) {
                simpleItems.add(item)
            }
        }

        return ParsedInvoiceResult(
            invoiceNo = invoiceNo,
            invoiceDate = invoiceDate,
            sellerName = sellerName,
            sellerPhone = sellerPhone,
            sellerAddress = sellerAddress,
            buyerName = buyerName,
            stoneCode = stoneCode,
            stoneType = stoneType,
            invoiceTitle = invoiceTitle,
            invoiceSubtitle = invoiceSubtitle,
            managerSign = managerSign,
            salesSign = salesSign,
            normalItems = normalItems,
            simpleItems = simpleItems,
            detectedGrandTotal = detectedTotal,
            sourceFormat = sourceName,
            warnings = warnings
        )
    }

    private fun isNonItemLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return true
        if (trimmed.contains("شرح") && (trimmed.contains("عرض") || trimmed.contains("طول") || trimmed.contains("فی") || trimmed.contains("ردیف"))) return true
        if (trimmed.startsWith("ردیف") && (trimmed.contains("شرح") || trimmed.contains("عرض"))) return true
        if (trimmed.contains("کارشناس فروش") || trimmed.contains("مدیر فروش") || trimmed.contains("مسئول فروش")) return true
        if (trimmed.contains("مشخصات خریدار") || trimmed.contains("مشخصات فروشنده") || trimmed.contains("مشخصات سنگ")) return true
        if (trimmed.contains("کرایه و حمل طبقات") || trimmed.contains("نشانی کامل") || trimmed.contains("فاکتور فروش صفحات")) return true
        if (trimmed.contains("کایند استون") && !trimmed.contains("عرض") && !trimmed.contains("سینک")) return true
        if (trimmed.contains("توتم کوارتز") && !trimmed.contains("عرض")) return true
        if (trimmed == "جمع" || trimmed == "عمج لک") return true
        return false
    }

    private fun parseLineIntelligently(line: String, engLine: String): Any? {
        val lineWithoutRowIndex = stripLeadingRowIndex(line)
        val engLineWithoutRowIndex = stripLeadingRowIndex(engLine)

        val numbers = extractAllNumbersFromLine(engLineWithoutRowIndex)
        if (numbers.isEmpty()) return null

        // Separate prices (big numbers >= 100_000) from dimensions (small numbers < 100_000)
        val bigPrices = numbers.filter { it >= 100_000 }
        val smallDimensions = numbers.filter { it < 100_000 }

        // Service / Simple Item (e.g. سینک, کرایه, هزینه کرایه, حمل, نصب, برش, فارسی, ابزار)
        if (isServiceKeyword(lineWithoutRowIndex)) {
            val bigPrice = bigPrices.firstOrNull()
            val qtyMatch = Regex("""(\d+)\s*(?:عدد|مورد|شاخه|متر)""").find(engLineWithoutRowIndex)
            val qty = qtyMatch?.groupValues?.get(1) ?: "1"

            val cleanDesc = lineWithoutRowIndex
                .replace(Regex("""\b(?:\d{1,3}(?:[,_]\d{3}){2,}|\d{6,})\b"""), "")
                .replace("ریال", "")
                .replace("تومان", "")
                .replace("-", "")
                .trim()

            return ComposeSimpleItem(
                description = cleanDesc.ifBlank { "خدمات / متفرقه" },
                totalAmountStr = if (bigPrice != null) moneyFormatter.format(bigPrice.toLong()) else "",
                quantityStr = qty
            )
        }

        // Stone Countertop Item (Extract ONLY: Description, Width cm, Length m, Price 60)
        var widthNum: Double? = null
        var lengthNum: Double? = null
        var price60Num: Double? = null

        // Price60 (فی عرض ۶۰) is ALWAYS the minimum among unit prices in standard unit range
        if (bigPrices.isNotEmpty()) {
            price60Num = if (bigPrices.size >= 2) bigPrices.minOrNull() else bigPrices.firstOrNull()
        }

        // Identify dimensions among small numbers:
        for (n in smallDimensions) {
            // Check decimal width (.57, 0.57, .60, .90)
            if (widthNum == null && isDecimalWidth(n)) {
                widthNum = n
                continue
            }

            // Check cm width (15 <= n <= 250)
            if (widthNum == null && n in 15.0..250.0) {
                widthNum = n
                continue
            }

            // Length (in meters, e.g. 0.1 <= n <= 30.0)
            if (lengthNum == null && n in 0.1..30.0 && n != 1.0) {
                lengthNum = n
                continue
            } else if (lengthNum == null && n == 1.0 && smallDimensions.size == 1) {
                lengthNum = n
                continue
            }
        }

        // Fallback for length if length was 1.0 or integer
        if (lengthNum == null && smallDimensions.any { it in 0.1..30.0 && it != widthNum }) {
            lengthNum = smallDimensions.first { it in 0.1..30.0 && it != widthNum }
        }

        val cleanDesc = cleanStoneDescription(lineWithoutRowIndex)

        // Only create a stone item if we actually have dimensions
        if (widthNum != null && lengthNum != null) {
            val finalWidth = standardizeWidth(widthNum)
            val finalLength = standardizeLength(lengthNum)
            val p60Formatted = formatPrice(price60Num)

            return ComposeInvoiceItem(
                description = cleanDesc.ifBlank { "ردیف سنگی" },
                width = finalWidth,
                length = finalLength,
                price60cm = p60Formatted
            )
        }

        return null
    }

    private fun extractAllNumbersFromLine(line: String): List<Double> {
        val list = mutableListOf<Double>()
        val regex = Regex("""(?:(?:\d{1,3}(?:[,_]\d{3})+|\d+)(?:[.,/]\d+)?|[.,/]\d+)""")
        val matches = regex.findAll(line)
        for (m in matches) {
            val raw = m.value
            val num = TextNormalizer.parseNumber(raw)
            if (num != null) {
                list.add(num)
            }
        }
        return list
    }

    private fun isDecimalWidth(n: Double): Boolean {
        if (n < 0.1 || n > 2.5 || n == 1.0 || n == 2.0) return false
        val inCm = (n * 100.0).roundToInt()
        return inCm in 20..250
    }

    private fun standardizeWidth(w: Double): String {
        return if (w < 3.0 && w > 0.0) {
            (w * 100.0).roundToInt().toString()
        } else {
            w.toInt().toString()
        }
    }

    private fun standardizeLength(l: Double): String {
        return if (l % 1.0 == 0.0) {
            l.toInt().toString()
        } else {
            l.toString()
        }
    }

    private fun formatPrice(price: Double?): String {
        return if (price != null && price > 0) {
            moneyFormatter.format(price.toLong())
        } else {
            ""
        }
    }

    private fun stripLeadingRowIndex(text: String): String {
        return text.replace(Regex("""^\s*(?:\d+|[۰-۹]+)[\s.\-–:)]+\s*"""), "").trim()
    }

    private fun cleanStoneDescription(text: String): String {
        return text
            .replace(Regex("""\b(?:\d{1,3}(?:[,_]\d{3})+|\d+(?:[.,/]\d+)?|[.,/]\d+)\b"""), "")
            .replace("ریال", "")
            .replace("تومان", "")
            .replace("مترطول", "")
            .replace("متر مربع", "")
            .replace("متر", "")
            .replace("سانت", "")
            .replace("cm", "", ignoreCase = true)
            .replace("m", "", ignoreCase = true)
            .replace("-", " ")
            .replace("–", " ")
            .replace(":", " ")
            .trim()
    }

    private fun isServiceKeyword(desc: String): Boolean {
        val keywords = listOf(
            "سینک", "کرایه", "هزینه کرایه", "حمل", "نصب", "کفتراش", "زیرکار", "روکار",
            "برش", "گاز", "سوراخ", "شیر", "جای سینک", "جای گاز",
            "ابزار", "فارسی", "پخ", "دوبل", "لبه", "تراش", "ساب", "بسته‌بندی"
        )
        return keywords.any { desc.contains(it) }
    }
}
