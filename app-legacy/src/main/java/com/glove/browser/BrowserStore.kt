package com.glove.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LinkItem(val title: String, val url: String, val time: Long)

class BrowserStore(context: Context) {
    private val prefs = context.getSharedPreferences("glove", Context.MODE_PRIVATE)

    fun bookmarks(): MutableList<LinkItem> = read("bookmarks")
    fun history(): MutableList<LinkItem> = read("history")

    fun toggleBookmark(title: String, url: String): Boolean {
        val list = bookmarks()
        val existing = list.indexOfFirst { it.url == url }
        val added = if (existing >= 0) {
            list.removeAt(existing)
            false
        } else {
            list.add(0, LinkItem(title.ifBlank { url }, url, System.currentTimeMillis()))
            true
        }
        write("bookmarks", list)
        return added
    }

    fun isBookmarked(url: String) = bookmarks().any { it.url == url }

    fun removeBookmark(url: String) {
        write("bookmarks", bookmarks().filter { it.url != url })
    }

    fun addHistory(title: String, url: String) {
        if (url.startsWith("file:") || url.isBlank()) return
        val list = history().filter { it.url != url }.toMutableList()
        list.add(0, LinkItem(title.ifBlank { url }, url, System.currentTimeMillis()))
        write("history", list.take(200))
    }

    fun clearHistory() {
        prefs.edit().remove("history").apply()
    }

    fun clearBookmarks() {
        prefs.edit().remove("bookmarks").apply()
    }

    fun downloads(): MutableList<LinkItem> = read("downloads")

    fun addDownload(title: String, url: String) {
        val list = downloads()
        list.add(0, LinkItem(title.ifBlank { url }, url, System.currentTimeMillis()))
        write("downloads", list.take(100))
    }

    fun clearDownloads() {
        prefs.edit().remove("downloads").apply()
    }

    var desktopSite: Boolean
        get() = prefs.getBoolean("desktop", false)
        set(value) { prefs.edit().putBoolean("desktop", value).apply() }

    var introSeen: Boolean
        get() = prefs.getBoolean("intro", false)
        set(value) { prefs.edit().putBoolean("intro", value).apply() }

    var searchEngine: String
        get() = prefs.getString("engine", "yandex") ?: "yandex"
        set(value) { prefs.edit().putString("engine", value).apply() }

    var forceDark: Boolean
        get() = prefs.getBoolean("forceDark", false)
        set(value) { prefs.edit().putBoolean("forceDark", value).apply() }

    data class TabState(val url: String, val title: String, val home: Boolean)

    fun saveTabs(tabs: List<TabState>, activeIndex: Int) {
        val arr = JSONArray()
        tabs.forEach { tab ->
            arr.put(
                JSONObject()
                    .put("url", tab.url)
                    .put("title", tab.title)
                    .put("home", tab.home)
            )
        }
        prefs.edit()
            .putString("tabs", arr.toString())
            .putInt("tabIndex", activeIndex.coerceAtLeast(0))
            .commit()
    }

    fun loadTabs(): List<TabState> {
        val raw = prefs.getString("tabs", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<TabState>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += TabState(o.optString("url"), o.optString("title"), o.optBoolean("home", o.optString("url").isBlank()))
        }
        return out
    }

    fun loadTabIndex(): Int = prefs.getInt("tabIndex", 0)

    private fun read(key: String): MutableList<LinkItem> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<LinkItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += LinkItem(o.optString("title"), o.optString("url"), o.optLong("time"))
        }
        return out
    }

    private fun write(key: String, items: List<LinkItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("title", it.title).put("url", it.url).put("time", it.time))
        }
        prefs.edit().putString(key, arr.toString()).commit()
    }
}
