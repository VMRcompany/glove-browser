package com.glove.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

object SearchEngines {
    data class Engine(val id: String, val name: String, val domain: String, val template: String)

    val all = listOf(
        Engine("yandex", "Яндекс", "yandex.ru", "https://yandex.ru/search/?text={q}"),
        Engine("google", "Google", "google.com", "https://www.google.com/search?q={q}"),
        Engine("bing", "Bing", "bing.com", "https://www.bing.com/search?q={q}"),
        Engine("duckduckgo", "DuckDuckGo", "duckduckgo.com", "https://duckduckgo.com/?q={q}"),
        Engine("yahoo", "Yahoo", "search.yahoo.com", "https://search.yahoo.com/search?p={q}"),
        Engine("mail", "Mail.ru", "go.mail.ru", "https://go.mail.ru/search?q={q}"),
        Engine("rambler", "Rambler", "rambler.ru", "https://nova.rambler.ru/search?query={q}"),
        Engine("brave", "Brave", "search.brave.com", "https://search.brave.com/search?q={q}"),
        Engine("ecosia", "Ecosia", "ecosia.org", "https://www.ecosia.org/search?q={q}"),
        Engine("startpage", "Startpage", "startpage.com", "https://www.startpage.com/sp/search?query={q}"),
        Engine("qwant", "Qwant", "qwant.com", "https://www.qwant.com/?q={q}"),
        Engine("wikipedia", "Википедия", "ru.wikipedia.org", "https://ru.wikipedia.org/w/index.php?search={q}"),
        Engine("baidu", "Baidu", "baidu.com", "https://www.baidu.com/s?wd={q}"),
        Engine("naver", "Naver", "search.naver.com", "https://search.naver.com/search.naver?query={q}"),
        Engine("seznam", "Seznam", "search.seznam.cz", "https://search.seznam.cz/?q={q}"),
        Engine("ask", "Ask", "ask.com", "https://www.ask.com/web?q={q}"),
        Engine("aol", "AOL", "search.aol.com", "https://search.aol.com/aol/search?q={q}"),
        Engine("kagi", "Kagi", "kagi.com", "https://kagi.com/search?q={q}"),
        Engine("you", "You.com", "you.com", "https://you.com/search?q={q}"),
        Engine("mojeek", "Mojeek", "mojeek.com", "https://www.mojeek.com/search?q={q}"),
        Engine("swisscows", "Swisscows", "swisscows.com", "https://swisscows.com/web?query={q}"),
        Engine("dogpile", "Dogpile", "dogpile.com", "https://www.dogpile.com/serp?q={q}"),
        Engine("metager", "MetaGer", "metager.org", "https://metager.org/meta/meta.ger3?eingabe={q}"),
        Engine("presearch", "Presearch", "presearch.com", "https://presearch.com/search?q={q}")
    )

    fun get(id: String) = all.firstOrNull { it.id == id } ?: all.first()

    fun searchUrl(id: String, query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return get(id).template.replace("{q}", encoded)
    }

    fun iconUrl(domain: String) = "https://www.google.com/s2/favicons?domain=$domain&sz=64"

    private val icons = mutableMapOf<String, Bitmap>()

    fun cachedIcon(domain: String) = icons[domain]

    fun loadIcon(domain: String, onReady: (Bitmap) -> Unit) {
        icons[domain]?.let {
            onReady(it)
            return
        }
        thread(name = "engine-icon") {
            val bitmap = fetchIcon(domain) ?: return@thread
            synchronized(icons) { icons[domain] = bitmap }
            onReady(bitmap)
        }
    }

    fun boxHtml(currentId: String): String {
        val current = get(currentId)
        val choices = all.joinToString("") { engine ->
            val selected = if (engine.id == current.id) " selected" else ""
            """<a class="choice$selected" href="https://orion.glove/engine?id=${engine.id}"><img src="${iconUrl(engine.domain)}" alt=""><span>${escape(engine.name)}</span></a>"""
        }
        return """
            <div class="searchline">
              <button class="engine" type="button" title="${escape(current.name)}" onclick="document.getElementById('picker').classList.toggle('open')">
                <img src="${iconUrl(current.domain)}" alt="">
              </button>
              <form action="https://orion.glove/search" method="get">
                <input name="q" placeholder="Введите запрос или адрес" autofocus>
                <button class="lens" type="submit" title="Найти" aria-label="Найти">
                  <svg viewBox="0 0 24 24" width="22" height="22"><path fill="#5f6368" d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16a6.47 6.47 0 0 0 4.23-1.57l.27.28v.79l5 4.99L20.49 19zM9.5 14A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z"/></svg>
                </button>
              </form>
            </div>
            <div class="picker" id="picker">$choices</div>
        """.trimIndent()
    }

    private fun fetchIcon(domain: String): Bitmap? = try {
        val conn = (URL(iconUrl(domain)).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "GloveBrowser/1.4")
        }
        conn.inputStream.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }

    private fun escape(value: String) =
        value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
}
