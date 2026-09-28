package com.example.data.database

import com.example.data.model.*
import org.json.JSONArray
import org.json.JSONObject

object ItemJsonConverter {
    fun serializeInvoiceItems(items: List<ComposeInvoiceItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("description", item.description)
            obj.put("width", item.width)
            obj.put("length", item.length)
            obj.put("price60cm", item.price60cm)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeInvoiceItems(json: String?): List<ComposeInvoiceItem> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<ComposeInvoiceItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ComposeInvoiceItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        description = obj.optString("description", ""),
                        width = obj.optString("width", ""),
                        length = obj.optString("length", ""),
                        price60cm = obj.optString("price60cm", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun serializeSimpleItems(items: List<ComposeSimpleItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("description", item.description)
            obj.put("totalAmountStr", item.totalAmountStr)
            obj.put("quantityStr", item.quantityStr)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeSimpleItems(json: String?): List<ComposeSimpleItem> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<ComposeSimpleItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ComposeSimpleItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        description = obj.optString("description", ""),
                        totalAmountStr = obj.optString("totalAmountStr", ""),
                        quantityStr = obj.optString("quantityStr", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun serializePercentageItems(items: List<ComposePercentageItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("description", item.description)
            obj.put("percentageStr", item.percentageStr)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializePercentageItems(json: String?): List<ComposePercentageItem> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<ComposePercentageItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ComposePercentageItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        description = obj.optString("description", ""),
                        percentageStr = obj.optString("percentageStr", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun serializeAttachments(attachments: List<InvoiceAttachment>): String {
        val array = JSONArray()
        for (item in attachments) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("fileName", item.fileName)
            obj.put("cloudUrl", item.cloudUrl)
            obj.put("localUri", item.localUri)
            obj.put("uploadDate", item.uploadDate)
            obj.put("fileSizeKb", item.fileSizeKb)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeAttachments(json: String?): List<InvoiceAttachment> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<InvoiceAttachment>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    InvoiceAttachment(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        title = obj.optString("title", ""),
                        fileName = obj.optString("fileName", ""),
                        cloudUrl = obj.optString("cloudUrl", ""),
                        localUri = obj.optString("localUri", ""),
                        uploadDate = obj.optString("uploadDate", ""),
                        fileSizeKb = obj.optLong("fileSizeKb", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
