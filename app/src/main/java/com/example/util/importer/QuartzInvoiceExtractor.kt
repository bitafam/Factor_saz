package com.example.util.importer

import com.example.data.model.ComposeInvoiceItem
import com.example.data.model.ComposeSimpleItem
import java.text.DecimalFormat
import kotlin.math.roundToInt

object QuartzInvoiceExtractor {

    private val moneyFormatter = DecimalFormat("#,###")

    /**
     * Extracts invoice data from a 2D matrix of cells (Excel / CSV).
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

        // 1. Scan for header row
        for (r in matrix.indices) {
            val row = matrix[r]
            val rowText = row.joinToString(" ") { TextNormalizer.cleanText(it) }

            if (rowText.contains("شرح") || (rowText.contains("عرض") && (rowText.contains("طول") || rowText.contains("فی")))) {
                tableHeaderRowIndex = r
                for (c in row.indices) {
                    val cell = TextNormalizer.cleanText(row[c])
                    if (cell.contains("شرح") || cell.contains("کالا") || cell.contains("توضیحات")) colDesc = c
                    else if (cell.contains("عرض") || cell.contains("cm") || cell.contains("سانت")) colWidth = c
                    else if (cell.contains("طول") || cell.contains("مترطول") || cell == "طول") colLength = c
                    else if (cell.contains("۶۰") || cell.contains("60") || cell.contains("فی") || cell.contains("واحد")) colP60 = c
                    else if (cell.contains("مبلغ کل") || cell == "مبلغ" || cell.contains("جمع کل") || cell.contains("قیمت کل")) colTotal = c
                    else if (cell.contains("تعداد") || cell.contains("مقدار")) colQty = c
                }
                break
            }
        }

        // 2. Extract metadata from all rows
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
                if (cell.contains("خریدار") || cell.contains("نام حقیقی :") || cell.contains("نام خریدار") || cell.contains("طرف حساب")) {
                    val candidate = if (cell.contains(":")) cell.substringAfter(":") else if (c + 1 < row.size) row[c + 1] else ""
                    val cleanB = TextNormalizer.cleanText(candidate).replace("مشخصات", "").trim()
                    if (cleanB.isNotBlank() && buyerName.isBlank() && !cleanB.contains("شماره")) {
                        buyerName = cleanB
                    }
                }

                // Stone Code & Type
                if (cell.contains("کد سنگ:") || cell.contains("کد طرح:")) {
                    stoneCode = cell.substringAfter(":").trim()
                }
                if (cell.contains("نوع سنگ:") || cell.contains("نوع متریال:")) {
                    stoneType = cell.substringAfter(":").trim()
                }

                // Total
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

        // 3. Process Table Rows (without dropping rows)
        val startRow = if (tableHeaderRowIndex >= 0) tableHeaderRowIndex + 1 else 0
        for (r in startRow until matrix.size) {
            val row = matrix[r]
            val rowText = row.joinToString(" ") { it.trim() }
            if (rowText.isBlank()) continue

            // Skip pure summary rows (grand totals, signatures)
            if (rowText.startsWith("جمع کل") || rowText.contains("مدیر فروش:") || rowText.contains("کارشناس فروش:")) {
                val num = TextNormalizer.parseNumber(rowText)
                if (num != null && num > 1000 && detectedTotal == null) {
                    detectedTotal = num
                }
                continue
            }

            // Extract cells based on detected columns or fallback heuristics
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

            // Remove leading row numbers from description
            val cleanDesc = cleanRowDescription(rawDesc.ifBlank { row.firstOrNull { it.isNotBlank() } ?: "" })

            // Check if we have width & length from column indices
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
            } else {
                // Try fallback line parsing across entire row cells
                val parsedRow = parseRowHeuristically(row)
                if (parsedRow is ComposeInvoiceItem) {
                    normalItems.add(parsedRow)
                } else if (parsedRow is ComposeSimpleItem) {
                    simpleItems.add(parsedRow)
                } else if (cleanDesc.isNotBlank()) {
                    // Fallback: don't lose the row! Add as simple item or normal item
                    val amount = totNum ?: p60Num ?: 0.0
                    simpleItems.add(
                        ComposeSimpleItem(
                            description = cleanDesc,
                            totalAmountStr = if (amount > 0) moneyFormatter.format(amount.toLong()) else "",
                            quantityStr = if (qtyStr.isNotBlank()) qtyStr else "1"
                        )
                    )
                }
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
            if (line.contains("خریدار") || line.contains("آقای ") || line.contains("خانم ") || line.contains("طرف حساب")) {
                val match = Regex("""(?:نام خریدار|خریدار|نام حقیقی|نام حقیقی/حقوقی|طرف حساب)\s*[:：]?\s*([^0-9\n]{2,30})""").find(line)
                if (match != null && buyerName.isBlank()) {
                    val name = match.groupValues[1].replace("شماره تماس", "").replace("شماره", "").trim()
                    if (name.isNotBlank() && name != "مشخصات") {
                        buyerName = name
                    }
                } else if (line.contains("آقای ") || line.contains("خانم ")) {
                    val idx = if (line.indexOf("آقای ") >= 0) line.indexOf("آقای ") else line.indexOf("خانم ")
                    val candidate = line.substring(idx).take(25).trim()
                    if (buyerName.isBlank() && !candidate.contains("فروش") && !candidate.contains("مدیر")) {
                        buyerName = candidate
                    }
                }
            }

            // 3. Invoice Number
            if (line.contains("شماره:") || line.contains("شماره فاکتور:") || line.contains("شماره سند:")) {
                val after = line.substringAfter(":").trim()
                if (after.isNotBlank() && invoiceNo.isBlank()) {
                    invoiceNo = after
                }
            }

            // 4. Stone Code & Type
            if (line.contains("کد سنگ:") || line.contains("کد طرح:")) {
                stoneCode = line.substringAfter(":").trim()
            }
            if (line.contains("نوع سنگ:") || line.contains("نوع متریال:")) {
                stoneType = line.substringAfter(":").trim()
            }

            // 5. Grand Total Line
            if (line.contains("جمع کل") || line.contains("مبلغ کل فاکتور") || line.startsWith("جمع:")) {
                val numbers = extractAllNumbersFromLine(engLine)
                val bigNum = numbers.firstOrNull { it > 100_000 }
                if (bigNum != null) {
                    detectedTotal = bigNum
                }
                continue // Do not parse summary line as table row
            }

            // Skip table header row
            if (line.contains("شرح") && (line.contains("عرض") || line.contains("طول") || line.contains("فی"))) {
                continue
            }
            if (line.startsWith("ردیف") && line.contains("شرح")) {
                continue
            }

            // 6. Parse Item Row (Never drop rows!)
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

    /**
     * Intelligently parses a single text line into either a ComposeInvoiceItem or ComposeSimpleItem without dropping it.
     */
    private fun parseLineIntelligently(line: String, engLine: String): Any? {
        // Strip leading row number (e.g. "1.", "1 ", "۲-", "(3)")
        val lineWithoutRowIndex = stripLeadingRowIndex(line)
        val engLineWithoutRowIndex = stripLeadingRowIndex(engLine)

        val numbers = extractAllNumbersFromLine(engLineWithoutRowIndex)

        // Separate prices (big numbers) from dimensions (small numbers)
        val bigPrices = numbers.filter { it >= 100_000 }
        val smallDimensions = numbers.filter { it < 100_000 }

        // Clean description text
        val cleanDesc = cleanRowDescription(lineWithoutRowIndex)
        if (cleanDesc.isBlank() && numbers.isEmpty()) return null

        // Check for Service / Simple Item keywords (e.g. سینک, کرایه, حمل, نصب, برش, فارسی, ابزار)
        if (isServiceKeyword(cleanDesc) || (smallDimensions.isEmpty() && bigPrices.isNotEmpty())) {
            val bigPrice = bigPrices.firstOrNull()
            val qtyMatch = Regex("""(\d+)\s*(?:عدد|مورد|شاخه|متر)""").find(engLine)
            val qty = qtyMatch?.groupValues?.get(1) ?: "1"

            return ComposeSimpleItem(
                description = cleanDesc.ifBlank { "خدمات / متفرقه" },
                totalAmountStr = if (bigPrice != null) moneyFormatter.format(bigPrice.toLong()) else "",
                quantityStr = qty
            )
        }

        // Dimensional Stone Item:
        // Try to identify width (in cm) and length (in m)
        var widthNum: Double? = null
        var lengthNum: Double? = null
        var unitPrice: Double? = null

        // If we have unit price and total price in big prices:
        // typically unit price is in range 1,000,000 to 500,000,000
        if (bigPrices.isNotEmpty()) {
            unitPrice = if (bigPrices.size >= 2) bigPrices.minOrNull() else bigPrices.firstOrNull()
        }

        // Identify dimensions from small numbers
        // Patterns:
        // Width could be:
        //   - Written as cm: 57, 60, 65, 70, 80, 90, 100, 120 (10 <= n <= 300)
        //   - Written as meter / decimal: .57, 0.57, .60, 0.60, .90, 0.90 (0.05 <= n < 3.0)
        // Length could be:
        //   - Written in meters: 3.4, 2.85, 0.9, 1.5, 4.2, 5.0 (0.1 <= n <= 50.0)

        for (n in smallDimensions) {
            // Check if n is a decimal width like .57 or 0.57
            if (widthNum == null && (n in 0.05..2.5 && n != 1.0 && n != 2.0 && n.toString().contains("."))) {
                // If length is already set, or if this looks like a width (.57, .60, .90)
                if (isDecimalWidth(n)) {
                    widthNum = n
                    continue
                }
            }

            // Check if n is an integer width in cm (e.g. 57, 60, 65, 90, 120)
            if (widthNum == null && n in 15.0..300.0 && n % 1.0 == 0.0) {
                widthNum = n
                continue
            }

            // Length (e.g. 3.4, 2.85, 0.9, 1.5)
            if (lengthNum == null && n in 0.1..50.0) {
                lengthNum = n
                continue
            }
        }

        // If we found at least one dimension or description, create ComposeInvoiceItem
        if (widthNum != null || lengthNum != null || cleanDesc.isNotBlank()) {
            val finalWidth = standardizeWidth(widthNum ?: 60.0)
            val finalLength = standardizeLength(lengthNum ?: 1.0)
            val p60Formatted = formatPrice(unitPrice)

            return ComposeInvoiceItem(
                description = cleanDesc.ifBlank { "ردیف سنگی" },
                width = finalWidth,
                length = finalLength,
                price60cm = p60Formatted
            )
        }

        return null
    }

    private fun parseRowHeuristically(row: List<String>): Any? {
        val nonBlank = row.map { TextNormalizer.cleanText(it) }.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return null

        val rowText = nonBlank.joinToString(" ")
        val engText = TextNormalizer.toEnglishDigits(rowText)
        return parseLineIntelligently(rowText, engText)
    }

    /**
     * Extracts all numbers (including decimals starting with '.', e.g. ".57" -> 0.57).
     */
    private fun extractAllNumbersFromLine(line: String): List<Double> {
        val list = mutableListOf<Double>()
        // Match numbers like: 115,000,000 or 586.500.000 or 3.4 or .57 or /57 or 0.57
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
        // Common decimal widths in quartz/stone: .57, .58, .60, .65, .70, .80, .90, .100, .120
        val inCm = (n * 100.0).roundToInt()
        return inCm in 20..250
    }

    /**
     * Standardizes width to integer centimeters string (e.g. 0.57 or .57 -> "57", 60 -> "60").
     */
    private fun standardizeWidth(w: Double): String {
        return if (w < 3.0 && w > 0.0) {
            // It was entered in meters (e.g. 0.57 or .57) -> convert to cm
            (w * 100.0).roundToInt().toString()
        } else {
            w.toInt().toString()
        }
    }

    /**
     * Standardizes length to meters string (e.g. 3.4 -> "3.4", 2.0 -> "2").
     */
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

    private fun cleanRowDescription(rawDesc: String): String {
        return rawDesc
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
            .replace("،", " ")
            .trim()
    }

    private fun isServiceKeyword(desc: String): Boolean {
        val keywords = listOf(
            "سینک", "کرایه", "حمل", "نصب", "کفتراش", "زیرکار", "روکار",
            "برش", "گاز", "سوراخ", "شیر", "جای سینک", "جای گاز",
            "ابزار", "فارسی", "پخ", "دوبل", "لبه", "تراش", "ساب", "بسته‌بندی"
        )
        return keywords.any { desc.contains(it) }
    }
}
