package com.example.util

import com.example.data.database.InvoiceEntity
import com.example.data.database.ItemJsonConverter
import java.text.DecimalFormat

object HtmlInvoiceGenerator {
    private val df = DecimalFormat("#,###")

    fun generateHtml(invoice: InvoiceEntity): String {
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
                    .invoice-box {
                        max-width: 900px;
                        margin: auto;
                        padding: 25px;
                        border: 1px solid #e0e0e0;
                        border-radius: 8px;
                        background: #ffffff;
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
                        }
                        .invoice-box {
                            border: none;
                            padding: 0;
                            max-width: 100%;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="invoice-box">
                    <table class="header-table">
                        <tr>
                            <td class="header-logo-section">
                                <div class="header-title">صنف سنگ کوارتز، توتم و دکتون</div>
                                <div class="header-subtitle">طراحی، ساخت و نصب صفحات کابینت و کانترتاپ</div>
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
                                <th style="width: 13%;">فی عرض 60cm</th>
                                <th style="width: 13%;">فی عرض ساخته شده</th>
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

            sb.append("""
                <tr>
                    <td>${itemIndex++}</td>
                    <td style="text-align: right;">${item.description.ifBlank { "محاسبه سنگ کوارتز" }}</td>
                    <td>${item.width}</td>
                    <td>${item.length}</td>
                    <td>${formatPrice(item.price60cm)}</td>
                    <td>${formatAmount(finalPriceCalculated)}</td>
                    <td>${formatAmount(totalAmountCalculated)}</td>
                </tr>
            """.trimIndent())
        }

        // 2. Simple items (installation, cutting, etc.)
        for (item in simpleItems) {
            val amount = item.totalAmount
            calculatedTotal += amount

            sb.append("""
                <tr class="special-row">
                    <td>${itemIndex++}</td>
                    <td style="text-align: right;">${item.description}</td>
                    <td>-</td>
                    <td>-</td>
                    <td>-</td>
                    <td>-</td>
                    <td>${formatPrice(item.totalAmountStr)}</td>
                </tr>
            """.trimIndent())
        }

        // 3. Grand total row
        sb.append("""
                <tr class="total-row">
                    <td colspan="6" style="text-align: left; padding: 12px;">جمع کل فاکتور:</td>
                    <td style="color: #004d40; font-size: 14px; font-weight: bold;">${df.format(calculatedTotal)} ریال</td>
                </tr>
            </tbody>
            </table>

            <table class="signatures-section">
                <tr>
                    <td class="signature-box">
                        <div class="signature-title">مهر و امضای ${invoice.managerSign.ifBlank { "مدیر فروش" }}</div>
                        <div class="signature-line">امضاء</div>
                    </td>
                    <td class="signature-box">
                        <div class="signature-title">مهر و امضای ${invoice.salesSign.ifBlank { "مسئول فروش" }}</div>
                        <div class="signature-line">امضاء</div>
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

    private fun formatPrice(priceStr: String): String {
        val d = priceStr.toDoubleOrNull() ?: return "0"
        return df.format(d)
    }

    private fun formatAmount(value: Double): String {
        return df.format(value)
    }
}
