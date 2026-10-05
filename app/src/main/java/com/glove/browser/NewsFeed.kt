package com.glove.browser

import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

object NewsFeed {
    private data class Story(val title: String, val url: String, val source: String, val time: Long)

    /** Dzen News / Яндекс.Новости feeds that power the Dzen лента. */
    private val feeds = listOf(
        "https://dzen.ru/news/rss",
        "https://news.yandex.ru/index.rss",
        "https://news.yandex.ru/politics.rss",
        "https://news.yandex.ru/society.rss",
        "https://news.yandex.ru/business.rss",
        "https://news.yandex.ru/world.rss",
        "https://news.yandex.ru/sports.rss",
        "https://news.yandex.ru/incident.rss",
        "https://news.yandex.ru/computers.rss",
        "https://news.yandex.ru/science.rss",
        "https://news.yandex.ru/culture.rss",
        "https://news.yandex.ru/auto.rss",
        "https://news.yandex.ru/ecology.rss",
        "https://news.yandex.ru/travels.rss",
        "https://news.yandex.ru/showbusiness.rss",
        "https://news.yandex.ru/gadgets.rss",
        "https://news.yandex.ru/games.rss",
        "https://news.yandex.ru/army.rss",
        "https://news.yandex.ru/energy.rss",
        "https://news.yandex.ru/finances.rss"
    )

    @Volatile private var cached: String? = null
    @Volatile private var cachedAt: Long = 0

    fun load(onReady: (String) -> Unit) {
        val fresh = cached
        if (fresh != null && System.currentTimeMillis() - cachedAt < 10 * 60 * 1000) {
            onReady(fresh)
            return
        }
        thread(name = "glove-news") {
            val stories = linkedMapOf<String, Story>()
            feeds.forEach { address ->
                rss(address).forEach { story ->
                    val key = story.url.substringBefore("?").lowercase()
                    val previous = stories[key]
                    if (previous == null || story.time > previous.time) stories[key] = story
                }
            }
            val ordered = stories.values.sortedByDescending { it.time }
            val html = render(ordered)
            cached = html
            cachedAt = System.currentTimeMillis()
            onReady(html)
        }
    }

    private fun render(stories: List<Story>): String {
        if (stories.isEmpty()) return "<p class=\"note\">Новости Дзена сейчас недоступны.</p>"
        return stories.joinToString("") { story ->
            """<a class="story" href="${esc(story.url)}"><b>${esc(story.title)}</b><span>${esc(story.source)}</span></a>"""
        }
    }

    private fun rss(url: String): List<Story> {
        val xml = get(url) ?: return emptyList()
        val item = Regex("(?s)<item\\b[^>]*>(.*?)</item>")
        val title = Regex("(?s)<title[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</title>")
        val link = Regex("(?s)<link[^>]*>(?:<!\\[CDATA\\[)?(https?://[^<\\]]+)(?:]]>)?</link>")
        val pub = Regex("(?s)<pubDate[^>]*>(.*?)</pubDate>")
        var index = 0
        return item.findAll(xml).mapNotNull { match ->
            val block = match.groupValues[1]
            val headline = clean(title.find(block)?.groupValues?.get(1).orEmpty())
            val href = link.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            if (headline.isBlank() || !href.startsWith("http")) return@mapNotNull null
            val stamp = parseDate(pub.find(block)?.groupValues?.get(1).orEmpty())
            val time = if (stamp > 0) stamp else System.currentTimeMillis() - index * 1000L
            index++
            Story(headline, href, "Дзен", time)
        }.toList()
    }

    private fun parseDate(value: String): Long {
        val raw = value.trim()
        if (raw.isEmpty()) return 0L
        val patterns = arrayOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.timeZone = TimeZone.getTimeZone("GMT")
                return format.parse(raw)?.time ?: continue
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    private fun get(url: String): String? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("User-Agent", "Mozilla/5.0 GloveBrowser/1.8.5")
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
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
