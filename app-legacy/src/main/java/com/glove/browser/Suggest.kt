package com.glove.browser

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Suggest {
    fun yandex(query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val address = "https://suggest.yandex.ru/suggest-ff.cgi?part=" +
            URLEncoder.encode(q, "UTF-8") + "&uil=ru&v=4&sn=5"
        val conn = (URL(address).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "GloveBrowser/1.7")
            setRequestProperty("Accept", "application/json,text/javascript,*/*")
        }
        val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val root = JSONArray(raw.substring(start, end + 1))
        val list = root.optJSONArray(1) ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until list.length()) {
            val item = list.opt(i)
            val text = when (item) {
                is String -> item
                is JSONArray -> item.optString(0)
                else -> ""
            }.trim()
            if (text.isNotEmpty() && text !in out) out += text
            if (out.size == 8) break
        }
        return out
    }
}
