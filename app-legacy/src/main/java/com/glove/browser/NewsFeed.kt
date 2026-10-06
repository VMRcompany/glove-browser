package com.glove.browser

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

object NewsFeed {
    private data class Story(val title: String, val url: String, val source: String, val time: Long)

    private val dzenPages = listOf(
        "https://dzen.ru/news",
        "https://dzen.ru/news/rubric/politics",
        "https://dzen.ru/news/rubric/society",
        "https://dzen.ru/news/rubric/business",
        "https://dzen.ru/news/rubric/world",
        "https://dzen.ru/news/rubric/sports",
        "https://dzen.ru/news/rubric/incident",
        "https://dzen.ru/news/rubric/computers",
        "https://dzen.ru/news/rubric/science",
        "https://dzen.ru/news/rubric/culture",
        "https://dzen.ru/news/rubric/auto",
        "https://dzen.ru/news/rubric/army"
    )

    private val lentaFeeds = listOf(
        "https://lenta.ru/rss",
        "https://lenta.ru/rss/articles",
        "https://lenta.ru/rss/news/russia",
        "https://lenta.ru/rss/news/world",
        "https://lenta.ru/rss/news/science",
        "https://lenta.ru/rss/news/forces",
        "https://lenta.ru/rss/news/sport"
    )

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val DZEN_COOKIE = "zen_sso_checked=1; zen_vk_sso_checked=1"
    private const val BUDGET_MS = 7000L

    @Volatile private var cached: String? = null
    @Volatile private var cachedAt: Long = 0
    private val cookieJar = AtomicReference(DZEN_COOKIE)

    fun load(onReady: (String) -> Unit) {
        val fresh = cached
        if (fresh != null && System.currentTimeMillis() - cachedAt < 5 * 60 * 1000) {
            onReady(fresh)
            return
        }
        thread(name = "glove-news") {
            val dzen = ConcurrentHashMap<String, Story>()
            val lenta = ConcurrentHashMap<String, Story>()
            val pool = Executors.newFixedThreadPool(16)
            val rank = AtomicInteger(0)

            dzenPages.forEach { address ->
                pool.execute {
                    scrapeDzen(address).forEach { story ->
                        val key = story.url.substringBefore("?").lowercase()
                        dzen.putIfAbsent(key, story.copy(time = System.currentTimeMillis() - rank.getAndIncrement() * 1000L))
                    }
                }
            }
            lentaFeeds.forEach { address ->
                pool.execute {
                    rss(address, "Lenta.ru").forEach { story ->
                        val key = story.url.substringBefore("?").lowercase()
                        val previous = lenta[key]
                        if (previous == null || story.time > previous.time) lenta[key] = story
                    }
                }
            }

            pool.shutdown()
            pool.awaitTermination(BUDGET_MS, TimeUnit.MILLISECONDS)
            pool.shutdownNow()

            val dzenList = dzen.values.sortedByDescending { it.time }
            val lentaList = lenta.values.sortedByDescending { it.time }
            val html = render(interleave(dzenList, lentaList))
            cached = html
            cachedAt = System.currentTimeMillis()
            onReady(html)
        }
    }

    private fun interleave(left: List<Story>, right: List<Story>): List<Story> {
        val out = ArrayList<Story>(left.size + right.size)
        var i = 0
        var j = 0
        while (i < left.size || j < right.size) {
            if (i < left.size) out.add(left[i++])
            if (j < right.size) out.add(right[j++])
        }
        return out
    }

    private fun render(stories: List<Story>): String {
        if (stories.isEmpty()) return "<p class=\"note\">Новости сейчас недоступны.</p>"
        return stories.joinToString("") { story ->
            """<a class="story" href="${esc(story.url)}"><b>${esc(story.title)}</b><span>${esc(story.source)}</span></a>"""
        }
    }

    private fun scrapeDzen(url: String): List<Story> {
        var html = get(url, cookie = cookieJar.get(), timeoutMs = 5500) ?: return emptyList()
        if (needsSso(html)) {
            unlockDzen(html)
            html = get(url, cookie = cookieJar.get(), timeoutMs = 5500) ?: return emptyList()
            if (needsSso(html)) return emptyList()
        }
        val link = Regex("""(?is)<a[^>]+href="(https://dzen\.ru/news/story/[^"]+)"[^>]*>(.*?)</a>""")
        val out = LinkedHashMap<String, Story>()
        link.findAll(html).forEach { match ->
            val href = match.groupValues[1].replace("&amp;", "&").substringBefore("?").trim()
            val headline = clean(match.groupValues[2])
            if (headline.length < 12) return@forEach
            if (headline.contains("Насколько вы") || headline.contains("Расскажите") || headline.startsWith("Спасибо")) return@forEach
            out.putIfAbsent(href.lowercase(), Story(headline, href, "Дзен", 0L))
        }
        return out.values.toList()
    }

    private fun needsSso(html: String): Boolean =
        html.length < 8000 && (html.contains("element2.value") || html.contains("sso.dzen.ru/install"))

    private fun unlockDzen(challenge: String) {
        val host = Regex("""host":"([^"]+)""").find(challenge)?.groupValues?.get(1)
            ?.replace("\\u002F", "/")?.replace("\\/", "/") ?: return
        val retpath = Regex("""retpath":"([^"]+)""").find(challenge)?.groupValues?.get(1)
            ?.replace("\\u002F", "/")?.replace("\\/", "/") ?: "https://dzen.ru/news"
        val container = Regex("""element2\.value = '([^']+)'""").find(challenge)?.groupValues?.get(1) ?: return
        val body =
            "retpath=" + URLEncoder.encode(retpath, "UTF-8") +
                "&container=" + URLEncoder.encode(container, "UTF-8") +
                "&dzen=1"
        get(host, method = "POST", body = body, cookie = cookieJar.get(), timeoutMs = 5000, referer = "https://dzen.ru/news")
    }

    private fun rss(url: String, source: String): List<Story> {
        val xml = get(url, timeoutMs = 5500) ?: return emptyList()
        val item = Regex("(?s)<item\\b[^>]*>(.*?)</item>")
        val title = Regex("(?s)<title[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</title>")
        val link = Regex("(?s)<link[^>]*>(?:<!\\[CDATA\\[)?(https?://[^<\\]]+)(?:]]>)?</link>")
        val guid = Regex("(?s)<guid[^>]*>(?:<!\\[CDATA\\[)?(https?://[^<\\]]+)(?:]]>)?</guid>")
        val pub = Regex("(?s)<pubDate[^>]*>(.*?)</pubDate>")
        var index = 0
        return item.findAll(xml).mapNotNull { match ->
            val block = match.groupValues[1]
            val headline = clean(title.find(block)?.groupValues?.get(1).orEmpty())
            val href = link.find(block)?.groupValues?.get(1)?.trim()
                ?: guid.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            if (headline.isBlank() || !href.startsWith("http")) return@mapNotNull null
            val stamp = parseDate(pub.find(block)?.groupValues?.get(1).orEmpty())
            val time = if (stamp > 0) stamp else System.currentTimeMillis() - index * 1000L
            index++
            Story(headline, href, source, time)
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

    private fun get(
        url: String,
        method: String = "GET",
        body: String? = null,
        cookie: String? = null,
        timeoutMs: Int = 5500,
        referer: String? = null
    ): String? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/rss+xml,*/*;q=0.8")
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9")
            if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
            if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        val setCookie = conn.headerFields["Set-Cookie"].orEmpty()
            .mapNotNull { it.substringBefore(';').takeIf { part -> part.contains('=') } }
        if (setCookie.isNotEmpty()) {
            val merged = LinkedHashMap<String, String>()
            (cookieJar.get() + "; " + setCookie.joinToString("; ")).split(';')
                .map { it.trim() }
                .filter { it.contains('=') }
                .forEach { part -> merged[part.substringBefore('=')] = part }
            cookieJar.set(merged.values.joinToString("; "))
        }
        val stream = if (conn.responseCode in 200..399) conn.inputStream else conn.errorStream
        stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    } catch (_: Exception) {
        null
    }

    private fun clean(value: String): String =
        value.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun esc(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
