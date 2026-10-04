package com.glove.browser

import android.content.Context
import org.json.JSONObject

object SiteAccess {
    private const val FILE = "site-access"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(context: Context, origin: String, kind: String): Boolean? {
        val raw = prefs(context).getString(origin, null) ?: return null
        val json = JSONObject(raw)
        if (!json.has(kind)) return null
        return json.getBoolean(kind)
    }

    fun put(context: Context, origin: String, kind: String, allow: Boolean) {
        val json = prefs(context).getString(origin, null)?.let { JSONObject(it) } ?: JSONObject()
        json.put(kind, allow)
        prefs(context).edit().putString(origin, json.toString()).apply()
    }

    fun all(context: Context): List<Triple<String, String, Boolean>> {
        val rows = mutableListOf<Triple<String, String, Boolean>>()
        prefs(context).all.forEach { (origin, value) ->
            val json = JSONObject(value?.toString() ?: return@forEach)
            json.keys().forEach { kind ->
                rows.add(Triple(origin, kind, json.getBoolean(kind)))
            }
        }
        return rows.sortedBy { it.first }
    }

    fun clear(context: Context, origin: String, kind: String) {
        val json = prefs(context).getString(origin, null)?.let { JSONObject(it) } ?: return
        json.remove(kind)
        if (json.length() == 0) prefs(context).edit().remove(origin).apply()
        else prefs(context).edit().putString(origin, json.toString()).apply()
    }

    fun label(kind: String) = when (kind) {
        "camera" -> "камера"
        "microphone" -> "микрофон"
        "location" -> "геолокация"
        "screen" -> "трансляция экрана"
        "files" -> "файлы"
        "notifications" -> "уведомления"
        else -> kind
    }
}
