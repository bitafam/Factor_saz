package com.example.util

import com.example.data.database.InvoiceEntity
import com.example.data.database.ItemJsonConverter
import com.example.data.model.*
import java.text.DecimalFormat

object HtmlInvoiceGenerator {
    private val df = DecimalFormat("#,###")

    fun generateHtml(invoice: InvoiceEntity, includeAttachments: Boolean = true): String {
        val normalItems = ItemJsonConverter.deserializeInvoiceItems(invoice.itemsJson)
        val simpleItems = ItemJsonConverter.deserializeSimpleItems(invoice.simpleItemsJson)

        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html>
            <html dir="rtl" lang="fa">
            <head>
                <meta charset="UTF-8">
                <title>فاکتور فروش سنگ کوارتز</title>
                <style>
                    body {
                        font-family: 'Tahoma', 'Segoe UI', Arial, sans-serif;
                        padding: 10px;
                        background: #fff;
                        color: #1a1a1a;
                        margin: 0;
                        font-size: 13px;
                    }
                    @page {
                        size: A4 portrait;
                        margin: 16mm 14mm;
                    }
                    .invoice-box {
                        max-width: 900px;
                        margin: 15px auto;
                        padding: 35px;
                        border: 5px double #00796b;
                        border-radius: 12px;
                        background: #ffffff;
                        box-shadow: 0 4px 15px rgba(0,0,0,0.05);
                    }
                    .header-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 20px;
                    }
                    .header-logo-section {
                        text-align: right;
                        vertical-align: middle;
                    }
                    .header-title {
                        font-size: 20px;
                        font-weight: bold;
                        color: #00796b;
                        margin: 0 0 5px 0;
                    }
                    .header-subtitle {
                        font-size: 13px;
                        color: #555;
                        margin: 0;
                    }
                    .header-meta {
                        text-align: left;
                        vertical-align: middle;
                        font-size: 13px;
                        line-height: 1.8;
                    }
                    .meta-badge {
                        background: #e0f2f1;
                        color: #004d40;
                        padding: 2px 8px;
                        border-radius: 4px;
                        font-weight: bold;
                    }
                    .section-divider {
                        border-top: 2px solid #00796b;
                        margin: 15px 0;
                        padding: 0;
                    }
                    .info-grid {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 15px;
                    }
                    .info-cell {
                        width: 50%;
                        vertical-align: top;
                        padding: 6px;
                    }
                    .info-card {
                        background: #fafafa;
                        border: 1px solid #eaeaea;
                        border-radius: 6px;
                        padding: 10px 12px;
                        min-height: 80px;
                    }
                    .info-title {
                        font-weight: bold;
                        color: #00796b;
                        margin-bottom: 6px;
                        border-bottom: 1px solid #e0e0e0;
                        padding-bottom: 4px;
                        font-size: 12px;
                    }
                    .info-row {
                        margin-bottom: 4px;
                        font-size: 12px;
                    }
                    .stone-badge-container {
                        display: flex;
                        gap: 15px;
                        margin-top: 5px;
                    }
                    .stone-badge {
                        background: #efebe9;
                        border: 1px solid #d7ccc8;
                        border-radius: 4px;
                        padding: 4px 8px;
                        font-size: 12px;
                        color: #4e342e;
                    }
                    .invoice-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 15px 0;
                    }
                    .invoice-table th {
                        background: #00796b;
                        color: white;
                        font-weight: bold;
                        padding: 10px 8px;
                        text-align: center;
                        border: 1px solid #004d40;
                        font-size: 12px;
                    }
                    .invoice-table td {
                        padding: 10px 8px;
                        border: 1px solid #e0e0e0;
                        text-align: center;
                        font-size: 12px;
                    }
                    .special-row {
                        background: #f9fbe7;
                    }
                    .total-row {
                        background: #e0f2f1;
                        font-weight: bold;
                        font-size: 13px;
                    }
                    .signatures-section {
                        width: 100%;
                        border-collapse: collapse;
                        margin-top: 35px;
                    }
                    .signature-box {
                        width: 50%;
                        text-align: center;
                        vertical-align: top;
                        padding: 15px;
                    }
                    .signature-title {
                        font-weight: bold;
                        color: #00796b;
                        margin-bottom: 40px;
                        font-size: 13px;
                    }
                    .signature-line {
                        border-top: 1px dotted #999;
                        width: 160px;
                        margin: auto;
                        padding-top: 5px;
                        color: #666;
                        font-size: 12px;
                    }
                    .print-btn-container {
                        text-align: center;
                        margin-top: 25px;
                    }
                    .print-btn {
                        background-color: #00796b;
                        color: white;
                        border: none;
                        padding: 10px 30px;
                        font-size: 14px;
                        font-weight: bold;
                        border-radius: 4px;
                        cursor: pointer;
                        box-shadow: 0 2px 5px rgba(0,0,0,0.2);
                        transition: background 0.2s;
                    }
                    .print-btn:hover {
                        background-color: #004d40;
                    }
                    @media print {
                        .print-btn-container {
                            display: none;
                        }
                        body {
                            padding: 0;
                            margin: 0;
                            background-color: #fff;
                        }
                        .invoice-box {
                            border: 5px double #00796b !important;
                            padding: 30px !important;
                            max-width: 100%;
                            box-shadow: none !important;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="invoice-box">
                    <table class="header-table">
                        <tr>
                            <td class="header-logo-section">
                                <div class="header-title">${invoice.invoiceTitle}</div>
                                <div class="header-subtitle">${invoice.invoiceSubtitle}</div>
                            </td>
                            <td class="header-meta">
                                <div>شماره فاکتور: <span class="meta-badge">${invoice.invoiceNo}</span></div>
                                <div style="margin-top: 6px;">تاریخ فاکتور: <strong>${invoice.invoiceDate}</strong></div>
                            </td>
                        </tr>
                    </table>

                    <div class="section-divider"></div>

                    <table class="info-grid">
                        <tr>
                            <td class="info-cell">
                                <div class="info-card">
                                    <div class="info-title"> مشخصات فروشنده</div>
                                    <div class="info-row"><strong>نام فروشگاه/کارخانه:</strong> ${invoice.sellerName}</div>
                                    <div class="info-row"><strong>تلفن تماس:</strong> ${invoice.sellerPhone}</div>
                                    <div class="info-row"><strong>آدرس:</strong> ${invoice.sellerAddress}</div>
                                </div>
                            </td>
                            <td class="info-cell">
                                <div class="info-card">
                                    <div class="info-title"> مشخصات خریدار</div>
                                    <div class="info-row"><strong>نام خریدار:</strong> ${invoice.buyerName}</div>
                                    <div class="info-title" style="margin-top: 10px; border-bottom: none; padding-bottom: 0;"> مشخصات سنگ سفارش داده شده</div>
                                    <div class="stone-badge-container">
                                        <div class="stone-badge"><strong>کد سنگ:</strong> ${invoice.stoneCode.ifBlank { "ثبت نشده" }}</div>
                                        <div class="stone-badge"><strong>نوع سنگ:</strong> ${invoice.stoneType.ifBlank { "ثبت نشده" }}</div>
                                    </div>
                                </div>
                            </td>
                        </tr>
                    </table>

                    <table class="invoice-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">شرح کالا / خدمات</th>
                                <th style="width: 11%;">عرض (cm)</th>
                                <th style="width: 11%;">طول (m)</th>
                                <th style="width: 14%;">فی عرض 60cm (ریال)</th>
                                <th style="width: 14%;">فی عرض ساخته شده (ریال)</th>
                                <th style="width: 12%;">مبلغ کل (ریال)</th>
                            </tr>
                        </thead>
                        <tbody>
        """.trimIndent())

        var itemIndex = 1
        var calculatedTotal = 0.0

        // 1. Regular/Quartz items
        for (item in normalItems) {
            val finalPriceCalculated = item.finalPrice
            val totalAmountCalculated = item.totalAmount
            calculatedTotal += totalAmountCalculated

            val displayWidth = item.width.trim().ifBlank { "60" }
            val displayLength = item.length.trim().ifBlank { "-" }

            sb.append("""
                <tr>
                    <td>${itemIndex++}</td>
                    <td style="text-align: right;">${item.description.ifBlank { "محاسبه سنگ کوارتز" }}</td>
                    <td>${displayWidth}</td>
                    <td>${displayLength}</td>
                    <td>${formatPrice(item.price60cm)} ریال</td>
                    <td>${formatAmount(finalPriceCalculated)} ریال</td>
                    <td>${formatAmount(totalAmountCalculated)} ریال</td>
                </tr>
            """.trimIndent())
        }

        // 2. Simple items (installation, cutting, etc.)
        for (item in simpleItems) {
            val amount = item.totalAmount
            calculatedTotal += amount
            val descEx = if (item.quantityStr.isNotBlank()) {
                "${item.description.ifBlank { "خدمات جانبی" }} (تعداد: ${item.quantityStr})"
            } else {
                item.description.ifBlank { "خدمات جانبی" }
            }

            sb.append("""
                <tr class="special-row">
                    <td>${itemIndex++}</td>
                    <td style="text-align: right;">${descEx}</td>
                    <td>-</td>
                    <td>-</td>
                    <td>-</td>
                    <td>-</td>
                    <td>${formatAmount(item.totalAmount)} ریال</td>
                </tr>
            """.trimIndent())
        }

        // 3. Percentage items (calculated cumulative)
        val baseTotal = calculatedTotal
        val percentageItems = ItemJsonConverter.deserializePercentageItems(invoice.percentageItemsJson)
        for (item in percentageItems) {
            val pct = com.example.util.importer.TextNormalizer.parseNumber(item.percentageStr) ?: 0.0
            val percentAmount = ((baseTotal * pct) / 100.0).let { kotlin.math.round(it) }
            calculatedTotal += percentAmount

            sb.append("""
                <tr style="background: #fffde7;">
                    <td>${itemIndex++}</td>
                    <td style="text-align: right;">${item.description.ifBlank { "درصد محاسباتی" }} (${item.percentageStr} درصد)</td>
                    <td>-</td>
                    <td>-</td>
                    <td>-</td>
                    <td>-</td>
                    <td>${formatAmount(percentAmount)} ریال</td>
                </tr>
            """.trimIndent())
        }

        val finalGrandTotal = if (calculatedTotal > 0.0) calculatedTotal else invoice.totalAmount

        // 4. Grand total row
        sb.append("""
                <tr class="total-row">
                    <td colspan="6" style="text-align: left; padding: 12px;">جمع کل فاکتور:</td>
                    <td style="color: #004d40; font-size: 14px; font-weight: bold;">${df.format(finalGrandTotal)} ریال</td>
                </tr>
            </tbody>
            </table>
        """.trimIndent())

        // 5. Attachments Section (if any)
        if (includeAttachments) {
            val attachments = ItemJsonConverter.deserializeAttachments(invoice.attachmentsJson)
            if (attachments.isNotEmpty() || invoice.cloudHtmlUrl.isNotBlank()) {
                sb.append("""
                    <div class="attachments-container" style="margin-top: 20px; padding: 14px; background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 8px;">
                        <div style="font-weight: bold; color: #00796b; font-size: 13px; margin-bottom: 8px; display: flex; align-items: center; justify-content: space-between;">
                            <span>📎 پیوست‌ها و اسناد ضمیمه فاکتور (رسیدها، نقشه‌ها و تصاویر):</span>
                            ${if (invoice.cloudHtmlUrl.isNotBlank()) """<span style="font-size: 11px; background: #e0f2f1; color: #004d40; padding: 3px 8px; border-radius: 4px;">ثبت شده در صندوقچه ابری آروان کلود</span>""" else ""}
                        </div>
                        ${if (attachments.isNotEmpty()) """
                            <div style="display: flex; flex-direction: column; gap: 8px; margin-top: 8px;">
                                ${attachments.mapIndexed { idx, att ->
                                    """
                                    <div style="display: flex; align-items: center; justify-content: space-between; background: #ffffff; padding: 8px 12px; border-radius: 6px; border: 1px solid #e2e8f0;">
                                        <div style="display: flex; align-items: center; gap: 8px;">
                                            <span style="font-weight: bold; color: #00796b; font-size: 12px;">${idx + 1}.</span>
                                            <span style="font-size: 12px; font-weight: bold; color: #1e293b;">${att.title.ifBlank { "ضمیمه ${idx + 1}" }}</span>
                                            <span style="font-size: 11px; color: #64748b;">(${att.fileName})</span>
                                        </div>
                                        <div>
                                            ${if (att.cloudUrl.isNotBlank()) """
                                                <a href="${att.cloudUrl}" target="_blank" style="display: inline-block; background: #00796b; color: #ffffff; text-decoration: none; padding: 4px 12px; border-radius: 4px; font-size: 11px; font-weight: bold;">مشاهده آنلاین تصویر</a>
                                            """ else """
                                                <span style="font-size: 11px; color: #94a3b8;">در صف بارگذاری</span>
                                            """}
                                        </div>
                                    </div>
                                    """
                                }.joinToString("\n")}
                            </div>
                        """ else """
                            <div style="font-size: 11px; color: #64748b;">ضمیمه‌ای برای این فاکتور ثبت نشده است.</div>
                        """}
                        ${if (invoice.cloudHtmlUrl.isNotBlank()) """
                            <div style="margin-top: 10px; padding-top: 8px; border-top: 1px dashed #cbd5e1; font-size: 11px; color: #334155;">
                                <strong>لینک دسترسی آنلاین به فاکتور:</strong> <a href="${invoice.cloudHtmlUrl}" target="_blank" style="color: #0284c7; text-decoration: underline; word-break: break-all;">${invoice.cloudHtmlUrl}</a>
                            </div>
                        """ else ""}
                    </div>
                """.trimIndent())
            }
        }

        sb.append("""
            <table class="signatures-section">
                <tr>
                    <td class="signature-box">
                        <div class="signature-title">مهر و امضای ${invoice.managerSign.ifBlank { "مدیر فروش" }}</div>
                        ${if (invoice.managerSignImgBase64.isNotBlank()) """
                            <img src="${invoice.managerSignImgBase64}" style="max-height: 80px; max-width: 160px; margin-top: 8px; object-fit: contain; display: inline-block;" />
                        """.trimIndent() else """
                            <div class="signature-line">امضاء</div>
                        """.trimIndent()}
                    </td>
                    <td class="signature-box">
                        <div class="signature-title">مهر و امضای ${invoice.salesSign.ifBlank { "مسئول فروش" }}</div>
                        ${if (invoice.salesSignImgBase64.isNotBlank()) """
                            <img src="${invoice.salesSignImgBase64}" style="max-height: 80px; max-width: 160px; margin-top: 8px; object-fit: contain; display: inline-block;" />
                        """.trimIndent() else """
                            <div class="signature-line">امضاء</div>
                        """.trimIndent()}
                    </td>
                </tr>
            </table>

            <div class="print-btn-container">
                <button class="print-btn" onclick="window.print()"> چاپ فاکتور / ذخیره به PDF </button>
            </div>
            </div>
            </body>
            </html>
        """.trimIndent())

        return sb.toString()
    }

    fun calculateInvoiceTotal(invoice: InvoiceEntity): Double {
        val normalItems = ItemJsonConverter.deserializeInvoiceItems(invoice.itemsJson)
        val simpleItems = ItemJsonConverter.deserializeSimpleItems(invoice.simpleItemsJson)
        val percentageItems = ItemJsonConverter.deserializePercentageItems(invoice.percentageItemsJson)

        val normalSum = normalItems.sumOf { it.totalAmount }
        val simpleSum = simpleItems.sumOf { it.totalAmount }
        val baseSum = normalSum + simpleSum
        val percentSum = percentageItems.sumOf {
            val pct = com.example.util.importer.TextNormalizer.parseNumber(it.percentageStr) ?: 0.0
            ((baseSum * pct) / 100.0).let { kotlin.math.round(it) }
        }
        val calculated = (baseSum + percentSum).let { kotlin.math.round(it) }
        return if (calculated > 0.0) calculated else invoice.totalAmount
    }

    fun generateSummaryHtml(startDate: String, endDate: String, invoices: List<InvoiceEntity>, includeViewColumn: Boolean = true): String {
        val df = DecimalFormat("#,###")
        val grandTotal = invoices.sumOf { inv ->
            val calc = calculateInvoiceTotal(inv)
            if (calc > 0.0) calc else inv.totalAmount
        }
        
        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html>
            <html lang="fa" dir="rtl">
            <head>
                <meta charset="UTF-8">
                <title>خلاصه کارکرد و فاکتورهای فروش</title>
                <style>
                    @font-face {
                        font-family: 'Vazir';
                        src: local('Vazir'), local('Tahoma');
                    }
                    body {
                        font-family: 'Tahoma', 'Vazir', sans-serif;
                        margin: 0;
                        padding: 10px;
                        background-color: #f5f5f5;
                        color: #1a1a1a;
                        -webkit-print-color-adjust: exact;
                        font-size: 11px;
                    }
                    @page {
                        size: A4 portrait;
                        margin: 16mm 14mm;
                    }
                    .report-container {
                        max-width: 800px;
                        margin: 15px auto;
                        background: #fff;
                        padding: 35px;
                        border-radius: 12px;
                        border: 5px double #004d40;
                        box-shadow: 0 4px 15px rgba(0,0,0,0.05);
                    }
                    .header-section {
                        text-align: center;
                        border-bottom: 2px solid #004d40;
                        padding-bottom: 10px;
                        margin-bottom: 20px;
                    }
                    .header-title {
                        font-size: 18px;
                        font-weight: bold;
                        color: #004d40;
                        margin: 0 0 6px 0;
                    }
                    .header-subtitle {
                        font-size: 12px;
                        color: #555;
                        margin: 0;
                    }
                    .info-grid {
                        display: flex;
                        justify-content: space-between;
                        font-size: 11px;
                        color: #444;
                        margin-bottom: 15px;
                        padding: 8px 12px;
                        background-color: #f9f9f9;
                        border: 1px solid #eee;
                        border-radius: 4px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 25px;
                        font-size: 11px;
                        text-align: center;
                    }
                    th {
                        background-color: #004d40;
                        color: white;
                        font-weight: bold;
                        padding: 8px;
                        border: 1px solid #ccc;
                    }
                    td {
                        padding: 8px;
                        border: 1px solid #ccc;
                    }
                    tr:nth-child(even) {
                        background-color: #fafafa;
                    }
                    .total-box {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        background: #e0f2f1;
                        border: 2px solid #004d40;
                        padding: 12px 16px;
                        border-radius: 6px;
                        font-size: 13px;
                        font-weight: bold;
                        color: #004d40;
                    }
                    .print-btn-container {
                        text-align: center;
                        margin-top: 20px;
                    }
                    .print-btn {
                        background-color: #004d40;
                        color: white;
                        border: none;
                        padding: 8px 20px;
                        font-size: 12px;
                        font-weight: bold;
                        border-radius: 4px;
                        cursor: pointer;
                    }
                    @media print {
                        body {
                            background-color: #fff;
                            padding: 0;
                            margin: 0;
                        }
                        .report-container {
                            border: 5px double #004d40 !important;
                            margin: 0;
                            padding: 30px !important;
                            max-width: 100%;
                            box-shadow: none !important;
                        }
                        .print-btn-container {
                            display: none;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="report-container">
                    <div class="header-section">
                        <h1 class="header-title">خلاصه کارکرد و فاکتورهای فروش</h1>
                        <h2 class="header-subtitle">گزارش دوره‌ای تراکنش‌ها و اسناد صادره صنایع سنگ</h2>
                    </div>
                    
                    <div class="info-grid">
                        <div><strong>بازه زمانی گزارش:</strong> از تاریخ ${startDate} تا ${endDate}</div>
                        <div><strong>تاریخ صدور گزارش:</strong> ${getTodayJalaliDate()}</div>
                    </div>
                    
                    <table>
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 20%;">نام خریدار</th>
                                <th style="width: 13%;">شماره فاکتور/تماس</th>
                                <th style="width: 14%;">کد سنگ / مشخصات</th>
                                <th style="width: 12%;">تاریخ صدور</th>
                                <th style="width: 18%;">مبلغ کل (ریال)</th>
                                ${if (includeViewColumn) """<th style="width: 18%;">لینک آنلاین فاکتور</th>""" else ""}
                            </tr>
                        </thead>
                        <tbody>
        """.trimIndent())
        
        invoices.forEachIndexed { idx, inv ->
            val rowTotal = calculateInvoiceTotal(inv).let { if (it > 0.0) it else inv.totalAmount }
            val codeDesc = buildString {
                if (inv.stoneCode.isNotBlank()) append(inv.stoneCode)
                if (inv.stoneType.isNotBlank()) {
                    if (isNotEmpty()) append(" - ")
                    append(inv.stoneType)
                }
                if (isEmpty()) append("-")
            }
            sb.append("""
                <tr>
                    <td>${idx + 1}</td>
                    <td style="text-align: right; font-weight: bold;">${inv.buyerName.ifBlank { "نامشخص" }}</td>
                    <td>${inv.invoiceNo}</td>
                    <td>${codeDesc}</td>
                    <td>${inv.invoiceDate}</td>
                    <td style="font-weight: bold; color: #004d40;">${df.format(rowTotal)} ریال</td>
                    ${if (includeViewColumn) """
                        <td>
                            ${if (inv.cloudHtmlUrl.isNotBlank()) """
                                <a href="${inv.cloudHtmlUrl}" target="_blank" style="display: inline-block; background: #00796b; color: #ffffff; text-decoration: none; padding: 3px 8px; border-radius: 4px; font-size: 11px; font-weight: bold;">مشاهده فاکتور</a>
                            """ else """
                                <span style="color: #94a3b8; font-size: 11px;">آفلاین</span>
                            """}
                        </td>
                    """ else ""}
                </tr>
            """.trimIndent())
        }
        
        sb.append("""
                        </tbody>
                    </table>
                    
                    <div class="total-box">
                        <div>تعداد کل فاکتورهای دوره: ${invoices.size} فقره</div>
                        <div>جمع کل درآمد: ${df.format(grandTotal)} ریال</div>
                    </div>
                    
                    <div class="print-btn-container">
                        <button class="print-btn" onclick="window.print()">چاپ و ذخیره گزارش خلاصه (A4)</button>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent())
        
        return sb.toString()
    }

    private fun getTodayJalaliDate(): String {
        return JalaliCalendar.getTodayJalali()
    }

    private fun formatPrice(priceStr: String): String {
        val num = com.example.util.importer.TextNormalizer.parseNumber(priceStr) ?: return "0"
        return df.format(num)
    }

    private fun formatAmount(value: Double): String {
        return df.format(value)
    }
}
