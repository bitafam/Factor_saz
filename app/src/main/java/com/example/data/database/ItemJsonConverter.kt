package com.example.data.database

import com.example.data.model.ComposeInvoiceItem
import com.example.data.model.ComposeSimpleItem
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
                        totalAmountStr = obj.optString("totalAmountStr", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
