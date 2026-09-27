package com.example.util.importer

import com.example.data.model.ComposeInvoiceItem
import com.example.data.model.ComposeSimpleItem
import java.text.DecimalFormat

object QuartzInvoiceExtractor {

    private val moneyFormatter = DecimalFormat("#,###")

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

        // 1. Scan for header row
        for (r in matrix.indices) {
            val row = matrix[r]
            val rowText = row.joinToString(" ") { TextNormalizer.cleanText(it) }

            if (rowText.contains("شرح") && (rowText.contains("عرض") || rowText.contains("طول") || rowText.contains("فی"))) {
                tableHeaderRowIndex = r
                for (c in row.indices) {
                    val cell = TextNormalizer.cleanText(row[c])
                    if (cell.contains("شرح")) colDesc = c
                    else if (cell.contains("عرض") && (cell.contains("cm") || cell == "عرض" || cell.contains("سانت"))) colWidth = c
                    else if (cell.contains("طول")) colLength = c
                    else if (cell.contains("۶۰") || cell.contains("60")) colP60 = c
                    else if (cell.contains("مبلغ کل") || cell == "مبلغ" || cell.contains("کل")) colTotal = c
                }
                break
            }
        }

        // 2. Extract metadata from non-table rows
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
                if (cell.contains("شماره:") || cell.contains("شماره فاکتور")) {
                    val after = cell.substringAfter(":")
                        .ifBlank { if (c + 1 < row.size) row[c + 1] else "" }
                    val cleanNo = TextNormalizer.cleanText(after)
                    if (cleanNo.isNotBlank() && invoiceNo.isBlank()) {
                        invoiceNo = cleanNo
                    }
                }

                // Buyer
                if (cell.contains("خریدار") || cell.contains("نام حقیقی :") || cell.contains("نام خریدار")) {
                    val candidate = if (cell.contains(":")) cell.substringAfter(":") else if (c + 1 < row.size) row[c + 1] else ""
                    val cleanB = TextNormalizer.cleanText(candidate).replace("مشخصات", "").trim()
                    if (cleanB.isNotBlank() && buyerName.isBlank() && !cleanB.contains("شماره")) {
                        buyerName = cleanB
                    }
                }

                // Seller
                if (cell.contains("فروشنده") || cell.contains("کایند استون") || cell.contains("کانتر استون")) {
                    if (cell.contains("کایند استون") || cell.contains("کانتر استون") || cell.contains("توتم")) {
                        if (sellerName.isBlank()) sellerName = cell
                    }
                }
                if (cell.contains("نام حقیقی/حقوقی :") && cell.contains("استون")) {
                    sellerName = cell.substringAfter(":").trim()
                }

                // Phone
                if (cell.contains("تلفن") || cell.contains("شماره تماس")) {
                    val candidate = if (cell.contains(":")) cell.substringAfter(":") else if (c + 1 < row.size) row[c + 1] else ""
                    val p = TextNormalizer.toEnglishDigits(candidate).trim()
                    if (p.isNotBlank() && p != "0" && sellerPhone.isBlank()) {
                        sellerPhone = p
                    }
                }

                // Titles
                if ((cell.contains("توتم") || cell.contains("کوارتز") || cell.contains("کانتر استون")) && invoiceTitle.isBlank()) {
                    invoiceTitle = cell
                }
                if (cell.contains("صفحات کابینت") && invoiceSubtitle.isBlank()) {
                    invoiceSubtitle = cell
                }

                // Stone Specs
                if (cell.contains("کد سنگ:")) stoneCode = cell.substringAfter(":").trim()
                if (cell.contains("نوع سنگ:")) stoneType = cell.substringAfter(":").trim()

                // Total
                if (cell.contains("جمع") || cell.contains("جمع کل")) {
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
            if (rowText.contains("جمع") || rowText.contains("کارشناس") || rowText.contains("مدیر فروش")) {
                val num = TextNormalizer.parseNumber(rowText)
                if (num != null && num > 1000 && detectedTotal == null) {
                    detectedTotal = num
                }
                continue
            }

            // Extract cells based on detected columns or fallback heuristics
            val desc = (if (colDesc in row.indices) row[colDesc] else "").trim()
            val wStr = (if (colWidth in row.indices) row[colWidth] else "").trim()
            val lStr = (if (colLength in row.indices) row[colLength] else "").trim()
            val p60Str = (if (colP60 in row.indices) row[colP60] else "").trim()
            val totStr = (if (colTotal in row.indices) row[colTotal] else "").trim()

            val wNum = TextNormalizer.parseNumber(wStr)
            val lNum = TextNormalizer.parseNumber(lStr)
            val p60Num = TextNormalizer.parseNumber(p60Str)
            val totNum = TextNormalizer.parseNumber(totStr)

            // If we have valid width and length, it's a dimensional item
            if (wNum != null && wNum > 0 && lNum != null && lNum > 0) {
                val p60Formatted = if (p60Num != null && p60Num > 0) moneyFormatter.format(p60Num.toLong()) else ""
                normalItems.add(
                    ComposeInvoiceItem(
                        description = TextNormalizer.cleanText(desc),
                        width = wNum.toInt().toString(),
                        length = if (lNum % 1.0 == 0.0) lNum.toInt().toString() else lNum.toString(),
                        price60cm = p60Formatted
                    )
                )
            } else if (desc.isNotBlank()) {
                // Service item (e.g. سینک زیرکار, سینک کفتراش, کرایه)
                val amount = totNum ?: p60Num ?: 0.0
                if (amount > 0 || isServiceKeyword(desc)) {
                    val qtyMatch = Regex("""(\d+)\s*عدد""").find(TextNormalizer.toEnglishDigits(desc))
                    val qty = qtyMatch?.groupValues?.get(1) ?: "1"
                    simpleItems.add(
                        ComposeSimpleItem(
                            description = TextNormalizer.cleanText(desc),
                            totalAmountStr = if (amount > 0) moneyFormatter.format(amount.toLong()) else "",
                            quantityStr = qty
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
     * Extracts invoice data from a list of text lines (e.g. from PDF text extraction or pasted text).
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
            val engLine = TextNormalizer.toEnglishDigits(line)

            // Date matching
            val dateMatch = Regex("""(1[34]\d{2}[/-]\d{1,2}[/-]\d{1,2})""").find(engLine)
            if (dateMatch != null && invoiceDate.isBlank()) {
                invoiceDate = dateMatch.groupValues[1]
            }

            // Buyer Name
            if (line.contains("خریدار") || line.contains("آقای ") || line.contains("خانم ")) {
                val match = Regex("""(?:نام خریدار|خریدار|نام حقیقی|نام حقیقی/حقوقی)\s*[:：]?\s*([^0-9\n]{2,30})""").find(line)
                if (match != null && buyerName.isBlank()) {
                    val name = match.groupValues[1].replace("شماره تماس", "").trim()
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

            // Seller
            if (line.contains("کایند استون") || line.contains("کانتر استون") || line.contains("توتم")) {
                if (sellerName.isBlank() && (line.contains("فروشنده") || line.contains("استون"))) {
                    sellerName = "کایند استون - توتم"
                }
                if (invoiceTitle.isBlank()) {
                    invoiceTitle = line.take(40)
                }
            }
            if (line.contains("صفحات کابینت") && invoiceSubtitle.isBlank()) {
                invoiceSubtitle = "فاکتور فروش صفحات کابینت کانترتاپ"
            }

            // Signatures
            if (line.contains("مدیر فروش")) {
                val after = line.substringAfter("مدیر فروش").replace(":", "").replace("کارشناس فروش", "").trim()
                if (after.isNotBlank() && managerSign.isBlank()) managerSign = after
            }
            if (line.contains("کارشناس فروش") || line.contains("مسئول فروش")) {
                val after = line.substringAfter("فروش").replace(":", "").trim()
                if (after.isNotBlank() && salesSign.isBlank() && !after.contains("مدیر")) salesSign = after
            }

            // Grand Total
            if (line.contains("جمع") || line.contains("جمع کل")) {
                val numbers = extractNumbersFromLine(engLine)
                val bigNum = numbers.firstOrNull { it > 100_000 }
                if (bigNum != null) {
                    detectedTotal = bigNum
                }
            }

            // Table Row Detection
            // Case 1: Standard Quartz Dimension Line
            // Line format typically has: [Item Description] [Width] [Length] [Price60] [PriceMade] [Total]
            // Or reverse order: [Total] [PriceMade] [Price60] [Length] [Width] [Item Description]
            val dimItem = parseDimensionalLine(line, engLine)
            if (dimItem != null) {
                normalItems.add(dimItem)
                continue
            }

            // Case 2: Service / Simple Item Line (e.g. "سینک کفتراش", "سینک زیرکار", "کرایه")
            val serviceItem = parseServiceLine(line, engLine)
            if (serviceItem != null) {
                simpleItems.add(serviceItem)
                continue
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

    private fun isServiceKeyword(desc: String): Boolean {
        return desc.contains("سینک") || desc.contains("کرایه") || desc.contains("حمل") ||
                desc.contains("نصب") || desc.contains("کفتراش") || desc.contains("زیرکار") ||
                desc.contains("برش") || desc.contains("سوراخ")
    }

    private fun parseDimensionalLine(line: String, engLine: String): ComposeInvoiceItem? {
        val keywords = listOf("جزیره", "صفحه", "دیوارکوب", "بین کابینتی", "دیوار کوب", "مطبخ", "استخر", "کابینت", "قرنیز")
        val hasKeyword = keywords.any { line.contains(it) }
        if (!hasKeyword) return null

        // Extract tokens and numbers
        val numbers = extractNumbersFromLine(engLine)
        if (numbers.size < 2) return null

        // In this invoice format, width is typically 15..200 (cm)
        // Length is typically 0.5..20.0 (m)
        // Base price 60 is typically >= 10,000,000 (Rials)
        var widthNum: Double? = null
        var lengthNum: Double? = null
        var price60Num: Double? = null

        // Find width (typically integer between 15 and 250)
        // Find length (typically float <= 30.0)
        // Find big prices (>= 1,000,000)
        for (n in numbers) {
            if (n >= 1_000_000 && price60Num == null) {
                price60Num = n
            } else if (n in 15.0..250.0 && widthNum == null && n % 1.0 == 0.0) {
                widthNum = n
            } else if (n in 0.2..30.0 && lengthNum == null) {
                lengthNum = n
            }
        }

        // Clean description by removing isolated numeric tokens from line
        val desc = line.replace(Regex("""\b\d+(?:[.,/]\d+)?\b"""), "")
            .replace(",", "")
            .replace("ریال", "")
            .replace("-", "")
            .trim()

        if (widthNum != null && lengthNum != null && desc.isNotBlank()) {
            val p60Formatted = if (price60Num != null && price60Num > 0) moneyFormatter.format(price60Num.toLong()) else "115,000,000"
            return ComposeInvoiceItem(
                description = TextNormalizer.cleanText(desc),
                width = widthNum.toInt().toString(),
                length = if (lengthNum % 1.0 == 0.0) lengthNum.toInt().toString() else lengthNum.toString(),
                price60cm = p60Formatted
            )
        }
        return null
    }

    private fun parseServiceLine(line: String, engLine: String): ComposeSimpleItem? {
        if (!isServiceKeyword(line)) return null

        val numbers = extractNumbersFromLine(engLine)
        val bigPrice = numbers.firstOrNull { it >= 100_000 }

        val qtyMatch = Regex("""(\d+)\s*عدد""").find(engLine)
        val qty = qtyMatch?.groupValues?.get(1) ?: "1"

        val cleanDesc = line.replace(Regex("""\b\d{4,}\b"""), "")
            .replace(",", "")
            .replace("ریال", "")
            .replace("-", "")
            .trim()

        return ComposeSimpleItem(
            description = TextNormalizer.cleanText(cleanDesc),
            totalAmountStr = if (bigPrice != null) moneyFormatter.format(bigPrice.toLong()) else "",
            quantityStr = qty
        )
    }

    private fun extractNumbersFromLine(line: String): List<Double> {
        val matches = Regex("""(\d+(?:[.,/]\d+)?)""").findAll(line)
        val list = mutableListOf<Double>()
        for (m in matches) {
            val raw = m.groupValues[1].replace('/', '.')
            val d = raw.toDoubleOrNull()
            if (d != null) list.add(d)
        }
        return list
    }
}
