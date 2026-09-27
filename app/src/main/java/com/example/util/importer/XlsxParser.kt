package com.example.util.importer

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

object XlsxParser {

    /**
     * Reads either .xlsx (Zip-based OpenXML) or .csv / .tsv / .txt text table.
     * Returns a 2D matrix of rows and cell values.
     */
    fun parseToMatrix(inputStream: InputStream, filenameHint: String = ""): List<List<String>> {
        val bytes = inputStream.readBytes()
        if (isZipFile(bytes)) {
            return parseXlsxZip(ByteArrayInputStream(bytes))
        } else {
            return parseDelimitedText(ByteArrayInputStream(bytes))
        }
    }

    private fun isZipFile(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte()) &&
                (bytes[3] == 0x04.toByte() || bytes[3] == 0x06.toByte() || bytes[3] == 0x08.toByte())
    }

    private fun parseXlsxZip(inputStream: InputStream): List<List<String>> {
        var sharedStrings: List<String> = emptyList()
        var sheetXmlBytes: ByteArray? = null

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (name == "xl/sharedstrings.xml") {
                    sharedStrings = parseSharedStrings(readAll(zis))
                } else if (name == "xl/worksheets/sheet1.xml" || (name.startsWith("xl/worksheets/sheet") && sheetXmlBytes == null)) {
                    sheetXmlBytes = readAll(zis)
                }
                entry = zis.nextEntry
            }
        }

        if (sheetXmlBytes != null) {
            return parseSheetXml(ByteArrayInputStream(sheetXmlBytes), sharedStrings)
        }
        return emptyList()
    }

    private fun readAll(isStream: InputStream): ByteArray {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var read: Int
        while (isStream.read(buffer).also { read = it } != -1) {
            baos.write(buffer, 0, read)
        }
        return baos.toByteArray()
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        var currentText = StringBuilder()
        var insideSi = false
        var insideT = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "si" -> {
                            insideSi = true
                            currentText.setLength(0)
                        }
                        "t" -> insideT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideSi && insideT) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "t" -> insideT = false
                        "si" -> {
                            insideSi = false
                            list.add(currentText.toString())
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return list
    }

    private fun parseSheetXml(inputStream: InputStream, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentRowCells = mutableMapOf<Int, String>()
        var currentCellRef = ""
        var currentCellType = ""
        var currentCellVal = StringBuilder()
        var insideV = false
        var insideT = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "row" -> {
                            currentRowCells.clear()
                        }
                        "c" -> {
                            currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            currentCellVal.setLength(0)
                        }
                        "v" -> insideV = true
                        "t" -> insideT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideV || insideT) {
                        currentCellVal.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "v" -> insideV = false
                        "t" -> insideT = false
                        "c" -> {
                            val colIndex = cellRefToColIndex(currentCellRef)
                            val raw = currentCellVal.toString().trim()
                            val resolvedText = when (currentCellType) {
                                "s" -> {
                                    val idx = raw.toIntOrNull()
                                    if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                                }
                                else -> raw
                            }
                            if (colIndex >= 0) {
                                currentRowCells[colIndex] = resolvedText
                            }
                        }
                        "row" -> {
                            if (currentRowCells.isNotEmpty()) {
                                val maxCol = currentRowCells.keys.maxOrNull() ?: 0
                                val rowList = ArrayList<String>(maxCol + 1)
                                for (i in 0..maxCol) {
                                    rowList.add(currentRowCells[i] ?: "")
                                }
                                rows.add(rowList)
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return rows
    }

    private fun cellRefToColIndex(ref: String): Int {
        if (ref.isBlank()) return -1
        var col = 0
        var foundLetters = false
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
                foundLetters = true
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
                foundLetters = true
            } else {
                break
            }
        }
        return if (foundLetters) col - 1 else -1
    }

    private fun parseDelimitedText(inputStream: InputStream): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        var line = reader.readLine()
        while (line != null) {
            if (line.isNotBlank()) {
                val delimiter = when {
                    line.contains("\t") -> "\t"
                    line.contains(";") -> ";"
                    else -> ","
                }
                val parts = line.split(delimiter).map { it.trim().trim('"', '\'') }
                rows.add(parts)
            }
            line = reader.readLine()
        }
        return rows
    }
}
