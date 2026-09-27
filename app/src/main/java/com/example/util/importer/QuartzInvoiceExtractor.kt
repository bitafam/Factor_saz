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

            if (rowText.contains("شرح") && (rowText.contains("عرض") || rowText.contains("طول") || rowText.contains("فی") || rowText.contains("مبلغ"))) {
                tableHeaderRowIndex = r
                for (c in row.indices) {
                    val cell = TextNormalizer.cleanText(row[c])
                    if (cell.contains("شرح")) colDesc = c
                    else if (cell.contains("عرض") && (cell.contains("cm") || cell == "عرض" || cell.contains("سانت"))) colWidth = c
                    else if (cell.contains("طول")) colLength = c
                    else if (cell.contains("۶۰") || cell.contains("60")) colP60 = c
                    else if (cell.contains("مبلغ کل") || cell == "مبلغ" || cell.contains("کل") || cell.contains("قیمت")) colTotal = c
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
            
            // Skip footer and signature lines
            if (rowText.contains("جمع کل") || rowText.contains("کارشناس فروش") || rowText.contains("مدیر فروش") || rowText.contains("امضا خریدار") || rowText.contains("مانده فاکتور")) {
                val num = TextNormalizer.parseNumber(rowText)
                if (num != null && num > 1000 && detectedTotal == null) {
                    detectedTotal = num
                }
                continue
            }
            if (rowText.contains("شرح کالا") && rowText.contains("عرض")) continue // redundant header

            // Extract row numbers and non-empty texts
            val numbersInRow = mutableListOf<Double>()
            for (cell in row) {
                val n = TextNormalizer.parseNumber(cell)
                if (n != null) numbersInRow.add(n)
            }

            val descCell = if (colDesc in row.indices && row[colDesc].isNotBlank()) {
                row[colDesc]
            } else {
                row.firstOrNull { it.isNotBlank() && TextNormalizer.parseNumber(it) == null } ?: ""
            }

            val wStr = if (colWidth in row.indices) row[colWidth].trim() else ""
            val lStr = if (colLength in row.indices) row[colLength].trim() else ""
            val p60Str = if (colP60 in row.indices) row[colP60].trim() else ""
            val totStr = if (colTotal in row.indices) row[colTotal].trim() else ""

            val wNum = TextNormalizer.parseNumber(wStr)
            val lNum = TextNormalizer.parseNumber(lStr)
            val p60Num = TextNormalizer.parseNumber(p60Str)
            val totNum = TextNormalizer.parseNumber(totStr)

            // Rule: If BOTH Width and Length are present and positive, it is a Dimensional Normal Item
            if (wNum != null && wNum > 0 && lNum != null && lNum > 0) {
                val cleanDesc = TextNormalizer.extractCleanTitle(descCell).ifBlank { "صفحه سنگ کوارتز" }
                val p60Formatted = if (p60Num != null && p60Num > 0) moneyFormatter.format(p60Num.toLong()) else ""
                normalItems.add(
                    ComposeInvoiceItem(
                        description = cleanDesc,
                        width = wNum.toInt().toString(),
                        length = if (lNum % 1.0 == 0.0) lNum.toInt().toString() else lNum.toString(),
                        price60cm = p60Formatted
                    )
                )
            } else {
                // Rule: Less than 2 dimensional columns or empty fields (like سینک کفتراش, حمل طبقات, کرایه, برش) -> Simple Item
                val cleanTitle = TextNormalizer.extractCleanTitle(if (descCell.isNotBlank()) descCell else rowText)
                if (cleanTitle.isNotBlank() && cleanTitle.length >= 2 && !isHeaderOrFooterText(cleanTitle)) {
                    // Extract total price from total column or the largest number in the row
                    val maxPrice = totNum ?: numbersInRow.filter { it >= 1000 }.maxOrNull() ?: p60Num ?: 0.0
                    val formattedAmount = if (maxPrice > 0) moneyFormatter.format(maxPrice.toLong()) else ""
                    val qty = TextNormalizer.extractQuantity(rowText)

                    simpleItems.add(
                        ComposeSimpleItem(
                            description = cleanTitle,
                            totalAmountStr = formattedAmount,
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

            // Stone Specs
            if (line.contains("کد سنگ:")) stoneCode = line.substringAfter(":").trim()
            if (line.contains("نوع سنگ:")) stoneType = line.substringAfter(":").trim()

            // Invoice No
            if (line.contains("شماره:") || line.contains("شماره فاکتور")) {
                val cleanNo = line.substringAfter(":").trim()
                if (cleanNo.isNotBlank() && invoiceNo.isBlank()) {
                    invoiceNo = cleanNo
                }
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
                val numbers = TextNormalizer.extractAllNumbers(engLine)
                val bigNum = numbers.firstOrNull { it > 100_000 }
                if (bigNum != null) {
                    detectedTotal = bigNum
                }
                continue
            }

            // Skip obvious header or meta rows
            if (isHeaderOrFooterText(line)) continue

            // Table Row Detection:
            // Case 1: Dimensional Normal Item (has width and length)
            val dimItem = parseDimensionalLine(line, engLine)
            if (dimItem != null) {
                normalItems.add(dimItem)
                continue
            }

            // Case 2: Simple Item (fewer than 2 dimensional numbers or empty fields, e.g. سینک کفتراش, حمل طبقات, سینک زیرکار, کرایه)
            val cleanTitle = TextNormalizer.extractCleanTitle(line)
            if (cleanTitle.isNotBlank() && cleanTitle.length >= 2 && !isHeaderOrFooterText(cleanTitle)) {
                val numbers = TextNormalizer.extractAllNumbers(engLine)
                val bigPrice = numbers.filter { it >= 1000 }.maxOrNull() ?: numbers.maxOrNull() ?: 0.0
                val formattedAmount = if (bigPrice > 0) moneyFormatter.format(bigPrice.toLong()) else ""
                val qty = TextNormalizer.extractQuantity(line)

                simpleItems.add(
                    ComposeSimpleItem(
                        description = cleanTitle,
                        totalAmountStr = formattedAmount,
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

    private fun isHeaderOrFooterText(text: String): Boolean {
        val t = text.trim()
        if (t.contains("ردیف") && t.contains("شرح")) return true
        if (t.contains("عرض") && t.contains("طول")) return true
        if (t.contains("مبلغ کل") && t.contains("فی")) return true
        if (t == "شرح" || t == "شرح کالا" || t == "ردیف") return true
        if (t.contains("تاریخ") && t.contains("شماره")) return true
        if (t.contains("فروشنده") || t.contains("خریدار") || t.contains("تلفن")) return true
        if (t.contains("صفحات کابینت") || t.contains("کانترتاپ")) return true
        if (t.contains("مدیر فروش") || t.contains("کارشناس فروش") || t.contains("امضا")) return true
        if (t.contains("جمع کل") || t.contains("مانده") || t.contains("بیعانه") || t.contains("تخفیف")) return true
        return false
    }

    private fun parseDimensionalLine(line: String, engLine: String): ComposeInvoiceItem? {
        val numbers = TextNormalizer.extractAllNumbers(engLine)
        if (numbers.size < 2) return null

        var widthNum: Double? = null
        var lengthNum: Double? = null
        var price60Num: Double? = null

        // In this invoice format, width is typically 15..250 (cm)
        // Length is typically 0.2..30.0 (m)
        // Base price 60 is typically >= 1,000,000 (Rials)
        for (n in numbers) {
            if (n >= 1_000_000 && price60Num == null) {
                price60Num = n
            } else if (n in 15.0..250.0 && widthNum == null && n % 1.0 == 0.0) {
                widthNum = n
            } else if (n in 0.2..30.0 && lengthNum == null) {
                lengthNum = n
            }
        }

        val cleanDesc = TextNormalizer.extractCleanTitle(line)

        if (widthNum != null && lengthNum != null && cleanDesc.isNotBlank()) {
            val p60Formatted = if (price60Num != null && price60Num > 0) moneyFormatter.format(price60Num.toLong()) else ""
            return ComposeInvoiceItem(
                description = cleanDesc,
                width = widthNum.toInt().toString(),
                length = if (lengthNum % 1.0 == 0.0) lengthNum.toInt().toString() else lengthNum.toString(),
                price60cm = p60Formatted
            )
        }
        return null
    }
}
