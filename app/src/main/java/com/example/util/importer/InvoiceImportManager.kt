package com.example.util.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object InvoiceImportManager {

    /**
     * Parses invoice from an Android document/file URI (.xlsx, .xls, .csv, .pdf, .txt)
     */
    fun parseFromUri(context: Context, uri: Uri): ParsedInvoiceResult {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri)?.lowercase() ?: ""
        val fileName = getFileName(context, uri).lowercase()

        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                if (fileName.endsWith(".pdf") || mimeType.contains("pdf")) {
                    val lines = PdfParser.extractLines(context, inputStream)
                    QuartzInvoiceExtractor.extractFromLines(lines, sourceName = "فایل PDF")
                } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls") || fileName.endsWith(".csv") ||
                    mimeType.contains("sheet") || mimeType.contains("excel") || mimeType.contains("csv")
                ) {
                    val matrix = XlsxParser.parseToMatrix(inputStream, fileName)
                    QuartzInvoiceExtractor.extractFromMatrix(matrix, sourceName = "فایل اکسل")
                } else {
                    val bytes = inputStream.readBytes()
                    val matrix = XlsxParser.parseToMatrix(bytes.inputStream(), fileName)
                    if (matrix.isNotEmpty() && matrix.size > 2) {
                        QuartzInvoiceExtractor.extractFromMatrix(matrix, sourceName = "فایل اکسل / جدول")
                    } else {
                        val lines = bytes.toString(Charsets.UTF_8).split("\n").filter { it.isNotBlank() }
                        QuartzInvoiceExtractor.extractFromLines(lines, sourceName = "فایل متنی")
                    }
                }
            } ?: ParsedInvoiceResult(warnings = listOf("امکان باز کردن فایل انتخابی وجود ندارد"))
        } catch (e: Exception) {
            e.printStackTrace()
            ParsedInvoiceResult(warnings = listOf("خطا در خواندن فایل: ${e.message}"))
        }
    }

    /**
     * Parses invoice from pasted text (e.g. copied from PDF viewer or copied from Excel spreadsheet table)
     */
    fun parseFromText(rawText: String): ParsedInvoiceResult {
        if (rawText.isBlank()) {
            return ParsedInvoiceResult(warnings = listOf("متنی برای استخراج وارد نشده است"))
        }
        val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val hasTabs = lines.any { it.contains("\t") }
        return if (hasTabs) {
            val matrix = lines.map { it.split("\t").map { cell -> cell.trim() } }
            QuartzInvoiceExtractor.extractFromMatrix(matrix, sourceName = "جدول کپی شده از اکسل")
        } else {
            QuartzInvoiceExtractor.extractFromLines(lines, sourceName = "متن کپی شده فاکتور")
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = ""
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = it.getString(index) ?: ""
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return if (name.isNotBlank()) name else uri.lastPathSegment ?: "unknown"
    }
}
