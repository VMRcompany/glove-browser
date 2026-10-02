package com.glove.browser

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

object NewsFeed {
    private data class Story(val title: String, val url: String, val source: String)

    @Volatile private var cached: String? = null
    @Volatile private var cachedAt: Long = 0

    fun load(onReady: (String) -> Unit) {
        val fresh = cached
        if (fresh != null && System.currentTimeMillis() - cachedAt < 15 * 60 * 1000) {
            onReady(fresh)
            return
        }
        thread(name = "glove-news") {
            val stories = mutableListOf<Story>()
            stories += rss("https://news.google.com/rss?hl=ru&gl=RU&ceid=RU:ru", "Google Новости").take(4)
            val dzen = rss("https://dzen.ru/news/rss", "Дзен").ifEmpty {
                rss("https://news.yandex.ru/index.rss", "Дзен")
            }
            stories += dzen.take(4)
            stories += wikipedia().take(3)
            stories += rss("https://lenta.ru/rss/news", "Лента").take(3)
            val html = render(stories.distinctBy { it.title }.take(12))
            cached = html
            cachedAt = System.currentTimeMillis()
            onReady(html)
        }
    }

    private fun render(stories: List<Story>): String {
        if (stories.isEmpty()) return "<p class=\"note\">Новости сейчас недоступны.</p>"
        return stories.joinToString("") { story ->
            """<a class="story" href="${esc(story.url)}"><b>${esc(story.title)}</b><span>${esc(story.source)}</span></a>"""
        }
    }

    private fun rss(url: String, source: String): List<Story> {
        val xml = get(url) ?: return emptyList()
        val item = Regex("(?s)<item\\b[^>]*>(.*?)</item>")
        val title = Regex("(?s)<title[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</title>")
        val link = Regex("(?s)<link[^>]*>(?:<!\\[CDATA\\[)?(https?://[^<\\]]+)(?:]]>)?</link>")
        return item.findAll(xml).mapNotNull { match ->
            val block = match.groupValues[1]
            val headline = clean(title.find(block)?.groupValues?.get(1).orEmpty())
            val href = link.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            if (headline.isBlank() || !href.startsWith("http")) null else Story(headline, href, source)
        }.take(6).toList()
    }

    private fun wikipedia(): List<Story> {
        val day = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
        val raw = get("https://ru.wikipedia.org/api/rest_v1/feed/featured/$day") ?: return emptyList()
        val news = try {
            JSONObject(raw).optJSONArray("news")
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val out = mutableListOf<Story>()
        for (i in 0 until news.length()) {
            val item = news.optJSONObject(i) ?: continue
            val links = item.optJSONArray("links") ?: continue
            val link = links.optJSONObject(0) ?: continue
            val title = link.optString("title").ifBlank { item.optString("story") }
            val page = link.optJSONObject("content_urls")
                ?.optJSONObject("desktop")
                ?.optString("page")
                .orEmpty()
            if (title.isNotBlank() && page.startsWith("http")) out += Story(clean(title), page, "Википедия")
        }
        return out
    }

    private fun get(url: String): String? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("User-Agent", "GloveBrowser/1.3")
            setRequestProperty("Accept", "application/rss+xml, application/json, text/xml, */*")
        }
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (_: Exception) {
        null
    }

    private fun clean(value: String): String =
        value.replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun esc(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
