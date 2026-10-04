package com.glove.browser

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Orion {
    data class Hit(val title: String, val url: String, val snippet: String)

    fun homeHtml(shortcuts: List<Pair<String, String>> = emptyList(), logo: String = "", engineId: String = "yandex", dark: Boolean = false): String {
        val tiles = shortcuts.joinToString("") { (title, url) ->
            """<a class="tile" href="${escape(url)}"><b>${escape(title.take(1).uppercase())}</b><span>${escape(title)}</span></a>"""
        }
        val mark = if (logo.isBlank()) "" else """<img class="mark" src="$logo" alt="" />"""
        return page(
            title = "Glove Browser",
            body = """
                <main class="ntp">
                  <div class="hero">
                    $mark
                    <div class="logo">Glove</div>
                    ${SearchEngines.boxHtml(engineId)}
                    <div class="tiles">$tiles</div>
                  </div>
                  <section class="news">
                    <h2>Новости</h2>
                    <div id="news"><p class="note">Собираем новости…</p></div>
                  </section>
                </main>
                <script>
                  function gloveNews(html) {
                    var node = document.getElementById("news");
                    if (node) node.innerHTML = html;
                  }
                </script>
            """.trimIndent(),
            dark = dark
        )
    }

    fun resultsHtml(query: String, hits: List<Hit>, note: String?): String {
        val items = if (hits.isEmpty() && note == null) {
            "<p class=\"note\">Ничего не нашлось.</p>"
        } else {
            hits.joinToString("\n") { hit ->
                """
                <a class="hit" href="${escape(hit.url)}">
                  <strong>${escape(hit.title)}</strong>
                  <span>${escape(hit.url)}</span>
                  <em>${escape(hit.snippet)}</em>
                </a>
                """.trimIndent()
            }
        }
        val extra = if (note.isNullOrBlank()) "" else "<p class=\"note\">${escape(note)}</p>"
        return page(
            title = query,
            body = """
                <main class="serp">
                  <form action="https://orion.glove/search" method="get">
                    <input name="q" value="${escape(query)}" />
                  </form>
                  $extra
                  <section>$items</section>
                </main>
            """.trimIndent(),
        )
    }

    fun search(query: String): List<Hit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val html = get("https://html.duckduckgo.com/html/?q=$encoded")
        val link = Regex(
            """<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val snippet = Regex(
            """<(?:a|td)[^>]*class="result__snippet"[^>]*>(.*?)</(?:a|td)>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val links = link.findAll(html).toList()
        val snippets = snippet.findAll(html).map { clean(it.groupValues[1]) }.toList()
        return links.take(12).mapIndexed { index, match ->
            val href = unwrap(match.groupValues[1])
            Hit(
                title = clean(match.groupValues[2]).ifBlank { href },
                url = href,
                snippet = snippets.getOrElse(index) { "" }
            )
        }.filter { it.url.startsWith("http") }
    }

    private fun unwrap(href: String): String {
        val raw = href.replace("&amp;", "&")
        val uddg = Uri.parse(raw).getQueryParameter("uddg")
        return uddg ?: raw
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", "GloveBrowser/1.0")
            setRequestProperty("Accept", "text/html")
        }
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { return it.readText() }
    }

    private fun clean(html: String): String =
        html.replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun escape(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun page(title: String, body: String, dark: Boolean = false): String = """
        <!DOCTYPE html>
        <html lang="ru" class="${if (dark) "night" else ""}">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>${escape(title)}</title>
          <style>
            * { box-sizing: border-box; }
            body { margin: 0; background: #fff; color: #202124; font-family: Roboto, "Segoe UI", sans-serif; }
            .ntp { min-height: 100vh; display: flex; flex-direction: column; align-items: center; padding: 8vh 16px 24px; }
            .hero { width: min(584px, 100%); display: flex; flex-direction: column; align-items: center; }
            .mark { width: 88px; height: 88px; border-radius: 22px; margin-bottom: 12px; }
            .logo { font-size: 40px; font-weight: 500; letter-spacing: -1px; margin-bottom: 20px; }
            form { width: min(584px, 100%); }
            input {
              width: 100%; height: 46px; border-radius: 24px; border: 1px solid #dfe1e5;
              padding: 0 20px; font-size: 16px; outline: none; color: #202124;
              box-shadow: 0 1px 6px rgba(32,33,36,.12);
            }
            input:focus { box-shadow: 0 1px 6px rgba(32,33,36,.28); border-color: transparent; }
            .searchline { display: flex; align-items: center; gap: 8px; width: min(640px, 100%); }
            .searchline form { position: relative; flex: 1; width: auto; margin: 0; }
            .searchline input { padding: 0 46px 0 18px; }
            .searchline .engine, .searchline .lens { margin: 0; border: 0; cursor: pointer; }
            .searchline .engine {
              width: 46px; height: 46px; flex: none; border-radius: 23px; border: 1px solid #dfe1e5;
              background: #fff; display: flex; align-items: center; justify-content: center; padding: 0;
            }
            .searchline .lens {
              position: absolute; right: 6px; top: 5px; width: 36px; height: 36px; padding: 0;
              background: transparent; display: flex; align-items: center; justify-content: center;
            }
            .engine img, .choice img { width: 22px; height: 22px; }
            .picker {
              display: none; width: min(640px, 100%); margin-top: 10px; max-height: 280px; overflow: auto;
              background: #fff; border: 1px solid #dadce0; border-radius: 16px;
              box-shadow: 0 8px 24px rgba(32,33,36,.16);
            }
            .picker.open { display: grid; grid-template-columns: 1fr 1fr; }
            .choice { display: flex; align-items: center; gap: 8px; padding: 10px 12px; text-decoration: none; color: #202124; }
            .choice.selected { background: #f1f3f4; }
            .tiles { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; margin-top: 28px; max-width: 584px; }
            .tile { width: 92px; text-align: center; text-decoration: none; color: #202124; font-size: 12px; }
            .tile b {
              width: 48px; height: 48px; margin: 0 auto 6px; border-radius: 24px; background: #f1f3f4;
              display: flex; align-items: center; justify-content: center; font-size: 18px; font-weight: 500;
            }
            .tile span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .serp { max-width: 680px; margin: 0 auto; padding: 16px 16px 40px; }
            .serp form { margin-bottom: 18px; }
            .hit { display: flex; flex-direction: column; gap: 2px; text-decoration: none; margin: 0 0 18px; }
            .hit strong { color: #1a0dab; font-size: 18px; font-weight: 400; }
            .hit span { color: #006621; font-size: 13px; word-break: break-all; }
            .hit em { color: #4d5156; font-style: normal; font-size: 14px; line-height: 1.35; }
            .note { color: #4d5156; }
            .news { width: min(640px, 100%); margin-top: auto; padding-top: 36px; }
            .news h2 { margin: 0 0 4px; font-size: 16px; font-weight: 500; }
            .story { display: block; text-decoration: none; color: #202124; padding: 12px 0; border-top: 1px solid #eceff1; }
            .story b { display: block; font-weight: 500; line-height: 1.35; }
            .story span { display: block; margin-top: 3px; color: #234230; font-size: 12px; }
            html.night, html.night body { background: #202124; color: #e8eaed; }
            html.night input, html.night .searchline .engine, html.night .picker { background: #303134; color: #e8eaed; border-color: #3c4043; }
            html.night .choice, html.night .tile, html.night .story { color: #e8eaed; }
            html.night .choice.selected, html.night .tile b { background: #3c4043; }
            html.night .story { border-top-color: #3c4043; }
          </style>
        </head>
        <body>$body</body>
        </html>
    """.trimIndent()
}
