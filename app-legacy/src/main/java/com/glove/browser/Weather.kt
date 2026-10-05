package com.glove.browser

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object Weather {
    private val COND_RU = mapOf(
        "clear" to "Ясно",
        "partly-cloudy" to "Малооблачно",
        "cloudy" to "Облачно",
        "overcast" to "Пасмурно",
        "drizzle" to "Морось",
        "light-rain" to "Небольшой дождь",
        "rain" to "Дождь",
        "moderate-rain" to "Дождь",
        "heavy-rain" to "Сильный дождь",
        "continuous-heavy-rain" to "Ливень",
        "showers" to "Ливень",
        "wet-snow" to "Мокрый снег",
        "light-snow" to "Небольшой снег",
        "snow" to "Снег",
        "snow-showers" to "Снегопад",
        "hail" to "Град",
        "thunderstorm" to "Гроза",
        "thunderstorm-with-rain" to "Гроза",
        "thunderstorm-with-hail" to "Гроза с градом",
        "fog" to "Туман"
    )

    val CSS = """
      .wx {
        position: relative; width: min(380px, 94vw); height: 132px; margin: 0 0 18px;
        border-radius: 28px; overflow: hidden; text-decoration: none; color: inherit;
        transform: perspective(1100px) rotateX(11deg) translateZ(0); transform-style: preserve-3d;
        box-shadow: 0 26px 48px rgba(32,33,36,.22), 0 1px 0 rgba(255,255,255,.45) inset, 0 -20px 34px rgba(0,0,0,.1) inset;
        background: linear-gradient(145deg, #6ec8ff 0%, #3a8dff 48%, #1f4f78 100%);
        transition: transform 260ms cubic-bezier(.2,.8,.2,1), box-shadow 260ms ease;
        display: block; isolation: isolate;
      }
      .wx:hover { transform: perspective(1100px) rotateX(4deg) translateY(-4px) scale(1.015); }
      .wx .shine {
        position: absolute; inset: -45% -25%; z-index: 1; pointer-events: none;
        background: linear-gradient(115deg, transparent 34%, rgba(255,255,255,.28) 48%, transparent 62%);
        animation: wxShine 6s ease-in-out infinite;
      }
      .wx canvas { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; z-index: 0; }
      .wx .face {
        position: relative; z-index: 2; height: 100%; display: flex; align-items: center; gap: 14px;
        padding: 14px 18px; color: #fff; text-shadow: 0 2px 8px rgba(0,0,0,.28);
      }
      .wx .icon3d {
        width: 72px; height: 72px; flex: none; display: grid; place-items: center;
        font-size: 46px; filter: drop-shadow(0 14px 16px rgba(0,0,0,.35));
        animation: wxBob 3.4s ease-in-out infinite;
        background: radial-gradient(circle at 32% 28%, rgba(255,255,255,.42), rgba(255,255,255,.08) 48%, transparent 70%);
        border-radius: 24px; border: 1px solid rgba(255,255,255,.18);
      }
      .wx .meta { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
      .wx .temp { font-size: 40px; font-weight: 650; letter-spacing: -1.4px; line-height: 1; }
      .wx .label { font-size: 14px; opacity: .95; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
      .wx .place { font-size: 12px; opacity: .82; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
      .wx.snow { background: linear-gradient(155deg, #f4f9ff 0%, #9ec4ff 40%, #3b587f 100%); }
      .wx.snow .face { color: #10233f; text-shadow: 0 1px 0 rgba(255,255,255,.35); }
      .wx.rain { background: linear-gradient(155deg, #8eabc4 0%, #2f5a7d 46%, #0f1c2a 100%); }
      .wx.sun { background: linear-gradient(145deg, #ffe7a8 0%, #ffb347 40%, #ff7043 100%); }
      .wx.cloud { background: linear-gradient(145deg, #e3ebf1 0%, #8fa3b4 52%, #455868 100%); }
      .wx.thunder { background: linear-gradient(155deg, #6b7c93 0%, #2a3344 48%, #12151c 100%); }
      @keyframes wxBob { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-6px); } }
      @keyframes wxShine { 0%,55%,100% { transform: translateX(-40%) rotate(8deg); opacity: .25; } 72% { transform: translateX(40%) rotate(8deg); opacity: .75; } }
    """.trimIndent()

    val HTML = """
      <a class="wx cloud" id="wx" href="https://yandex.ru/pogoda/" title="Яндекс Погода">
        <canvas id="wxFx" width="760" height="264"></canvas>
        <div class="shine"></div>
        <div class="face">
          <div class="icon3d" id="wxIcon">⛅</div>
          <div class="meta">
            <div class="temp" id="wxTemp">—°</div>
            <div class="label" id="wxLabel">Погода рядом</div>
            <div class="place" id="wxPlace">Определяем место…</div>
          </div>
        </div>
      </a>
    """.trimIndent()

    val SCRIPT = """
      (function(){
        var card = document.getElementById("wx");
        var canvas = document.getElementById("wxFx");
        if (!card || !canvas) return;
        var ctx = canvas.getContext("2d");
        var mode = "cloud";
        var particles = [];
        var bolts = [];
        var t0 = Date.now();
        function resize() {
          var r = card.getBoundingClientRect();
          canvas.width = Math.max(340, Math.floor(r.width * 2));
          canvas.height = Math.max(132, Math.floor(r.height * 2));
        }
        function flake(x, y, r, a) {
          ctx.save(); ctx.translate(x, y); ctx.rotate(a);
          ctx.strokeStyle = "rgba(255,255,255,.92)"; ctx.lineWidth = Math.max(0.8, r * 0.18); ctx.lineCap = "round";
          for (var i = 0; i < 6; i++) {
            ctx.rotate(Math.PI / 3);
            ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(0, -r); ctx.stroke();
            ctx.beginPath(); ctx.moveTo(0, -r * 0.45); ctx.lineTo(r * 0.22, -r * 0.7);
            ctx.moveTo(0, -r * 0.45); ctx.lineTo(-r * 0.22, -r * 0.7); ctx.stroke();
          }
          ctx.restore();
        }
        function seed(kind) {
          particles = []; bolts = [];
          var count = kind === "sun" ? 28 : kind === "rain" || kind === "thunder" ? 120 : kind === "snow" ? 85 : 34;
          for (var i = 0; i < count; i++) {
            particles.push({
              x: Math.random() * canvas.width, y: Math.random() * canvas.height,
              z: 0.35 + Math.random() * 2.1, s: 1 + Math.random() * 3.2,
              a: Math.random() * Math.PI * 2, w: 22 + Math.random() * 48,
              v: 0.6 + Math.random() * 1.4, splash: 0
            });
          }
        }
        function drawSun(w, h, t) {
          var cx = w * 0.82, cy = h * 0.28, pulse = 1 + Math.sin(t / 900) * 0.04;
          var g = ctx.createRadialGradient(cx, cy, 4, cx, cy, 130 * pulse);
          g.addColorStop(0, "rgba(255,255,230,1)"); g.addColorStop(0.22, "rgba(255,220,110,.7)");
          g.addColorStop(0.55, "rgba(255,170,60,.22)"); g.addColorStop(1, "rgba(255,150,40,0)");
          ctx.fillStyle = g; ctx.beginPath(); ctx.arc(cx, cy, 130 * pulse, 0, Math.PI * 2); ctx.fill();
          ctx.save(); ctx.translate(cx, cy); ctx.rotate(t / 2800);
          for (var r = 0; r < 16; r++) {
            ctx.rotate(Math.PI / 8);
            var len = 38 + (r % 2) * 18 + Math.sin(t / 500 + r) * 4;
            var rg = ctx.createLinearGradient(24, 0, len + 24, 0);
            rg.addColorStop(0, "rgba(255,245,180,.0)"); rg.addColorStop(0.35, "rgba(255,235,150,.75)"); rg.addColorStop(1, "rgba(255,200,80,0)");
            ctx.strokeStyle = rg; ctx.lineWidth = 3.4; ctx.beginPath(); ctx.moveTo(26, 0); ctx.lineTo(26 + len, 0); ctx.stroke();
          }
          ctx.restore();
          ctx.fillStyle = "rgba(255,250,220,.95)"; ctx.beginPath(); ctx.arc(cx, cy, 18 * pulse, 0, Math.PI * 2); ctx.fill();
        }
        function drawCloudBlob(x, y, s, a) {
          ctx.fillStyle = "rgba(255,255,255," + a + ")";
          ctx.beginPath();
          ctx.ellipse(x, y, 28 * s, 14 * s, 0, 0, Math.PI * 2);
          ctx.ellipse(x - 18 * s, y + 4 * s, 18 * s, 11 * s, 0, 0, Math.PI * 2);
          ctx.ellipse(x + 20 * s, y + 3 * s, 20 * s, 12 * s, 0, 0, Math.PI * 2);
          ctx.ellipse(x + 2 * s, y - 8 * s, 16 * s, 12 * s, 0, 0, Math.PI * 2);
          ctx.fill();
        }
        function maybeBolt(w, h) {
          if (Math.random() > 0.012) return;
          var x = w * (0.2 + Math.random() * 0.55), path = [{ x: x, y: 0 }], y = 0;
          while (y < h * 0.75) { y += 18 + Math.random() * 28; path.push({ x: path[path.length - 1].x + (Math.random() - 0.5) * 40, y: y }); }
          bolts.push({ path: path, life: 1, width: 2 + Math.random() * 2.5 });
        }
        function frame() {
          var w = canvas.width, h = canvas.height, t = Date.now() - t0;
          ctx.clearRect(0, 0, w, h);
          if (mode === "sun") drawSun(w, h, t);
          if (mode === "thunder") {
            var sky = ctx.createLinearGradient(0, 0, 0, h);
            sky.addColorStop(0, "rgba(90,110,140,.18)"); sky.addColorStop(1, "rgba(10,14,22,.35)");
            ctx.fillStyle = sky; ctx.fillRect(0, 0, w, h); maybeBolt(w, h);
          }
          particles.forEach(function (p) {
            if (mode === "snow") {
              p.y += 0.45 * p.z * p.v; p.x += Math.sin((p.a += 0.015) + t / 800) * 0.7 * p.z;
              if (p.y > h + 10) { p.y = -10; p.x = Math.random() * w; }
              flake(p.x, p.y, p.s * p.z * 1.4, p.a);
            } else if (mode === "rain" || mode === "thunder") {
              p.y += (mode === "thunder" ? 11 : 9.2) * p.z; p.x += 2.1 * p.z;
              if (p.y > h) { p.splash = 1; p.y = -12; p.x = Math.random() * w; }
              ctx.strokeStyle = "rgba(190,220,255," + (0.25 + p.z * 0.35) + ")";
              ctx.lineWidth = Math.max(1, 1.2 * p.z);
              ctx.beginPath(); ctx.moveTo(p.x, p.y); ctx.lineTo(p.x - 5 * p.z, p.y + 16 * p.z); ctx.stroke();
              if (p.splash > 0) {
                p.splash *= 0.86;
                ctx.strokeStyle = "rgba(210,235,255," + (p.splash * 0.45) + ")";
                ctx.beginPath(); ctx.arc(p.x, h - 4, 6 * (1 - p.splash) + 2, Math.PI, 0); ctx.stroke();
              }
            } else if (mode === "cloud") {
              p.x += 0.18 * p.z * p.v; if (p.x > w + 80) p.x = -80;
              drawCloudBlob(p.x, (p.y % h), 0.45 + p.z * 0.35, 0.08 + p.z * 0.07);
            } else if (mode === "sun") {
              p.a += 0.012;
              var px = w * 0.82 + Math.cos(p.a) * (18 + p.z * 22);
              var py = h * 0.28 + Math.sin(p.a * 1.35) * (10 + p.z * 14);
              ctx.fillStyle = "rgba(255,255,210," + (0.1 + p.z * 0.1) + ")";
              ctx.beginPath(); ctx.arc(px, py, 1.8 * p.z, 0, Math.PI * 2); ctx.fill();
            }
          });
          bolts = bolts.filter(function (b) {
            b.life -= 0.08; if (b.life <= 0) return false;
            ctx.strokeStyle = "rgba(210,230,255," + b.life + ")";
            ctx.shadowColor = "rgba(180,210,255,.9)"; ctx.shadowBlur = 18;
            ctx.lineWidth = b.width * b.life * 2; ctx.beginPath();
            b.path.forEach(function (pt, i) { if (i === 0) ctx.moveTo(pt.x, pt.y); else ctx.lineTo(pt.x, pt.y); });
            ctx.stroke(); ctx.shadowBlur = 0; return true;
          });
          requestAnimationFrame(frame);
        }
        function setMode(next) { mode = next || "cloud"; card.className = "wx " + mode; seed(mode); }
        function condMode(c) {
          c = String(c || "").toLowerCase();
          if (/thunder|гроз/.test(c)) return "thunder";
          if (/snow|снег|метел|град|hail/.test(c)) return "snow";
          if (/rain|drizzle|shower|ливень|дожд|морось/.test(c)) return "rain";
          if (/clear|sunny|ясно|солнечно|partly|малообл/.test(c)) return "sun";
          return "cloud";
        }
        function condIcon(c) {
          c = String(c || "").toLowerCase();
          if (/thunder|гроз/.test(c)) return "⛈️";
          if (/snow|снег|метел/.test(c)) return "❄️";
          if (/hail|град/.test(c)) return "🌨️";
          if (/rain|drizzle|shower|дожд|ливень|морось/.test(c)) return "🌧️";
          if (/fog|туман/.test(c)) return "🌫️";
          if (/clear|sunny|ясно|солнечно/.test(c)) return "☀️";
          if (/partly|малообл/.test(c)) return "⛅";
          return "☁️";
        }
        function paint(data) {
          if (!data) return;
          var temp = data.temp, label = data.label || "Яндекс Погода", place = data.place || "Яндекс Погода", cond = data.condition || label;
          if (typeof temp === "number" && !isNaN(temp)) {
            document.getElementById("wxTemp").textContent = (temp > 0 ? "+" : "") + Math.round(temp) + "°";
          }
          document.getElementById("wxLabel").textContent = label;
          document.getElementById("wxPlace").textContent = place;
          document.getElementById("wxIcon").textContent = condIcon(cond);
          setMode(condMode(cond));
          if (data.href) card.href = data.href;
        }
        window.gloveWeatherApply = paint;
        function askNative(lat, lon) {
          try {
            if (window.GloveWeather && typeof GloveWeather.load === "function") {
              if (lat == null || lon == null) GloveWeather.load("", "");
              else GloveWeather.load(String(lat), String(lon));
              return true;
            }
          } catch (e) {}
          return false;
        }
        function load(lat, lon) {
          if (typeof lat === "number" && typeof lon === "number") card.href = "https://yandex.ru/pogoda/?lat=" + lat + "&lon=" + lon;
          else card.href = "https://yandex.ru/pogoda/";
          if (!askNative(lat, lon)) document.getElementById("wxPlace").textContent = "Открыть Яндекс Погоду";
        }
        resize(); seed("cloud"); frame();
        if (!navigator.geolocation) { load(null, null); return; }
        navigator.geolocation.getCurrentPosition(function(pos){
          load(pos.coords.latitude, pos.coords.longitude);
        }, function(){
          document.getElementById("wxLabel").textContent = "Яндекс Погода";
          document.getElementById("wxPlace").textContent = "По IP или откройте прогноз";
          load(null, null);
        }, { enableHighAccuracy: false, timeout: 10000, maximumAge: 600000 });
      })();
    """.trimIndent()

    fun fetchJson(lat: Double?, lon: Double?): String {
        val hasCoords = lat != null && lon != null
        val url = if (hasCoords) {
            "https://yandex.ru/pogoda/?lat=$lat&lon=$lon"
        } else {
            "https://yandex.ru/pogoda/"
        }
        return parse(get(url).orEmpty(), if (hasCoords) lat else null, if (hasCoords) lon else null).toString()
    }

    fun loadAsync(lat: Double?, lon: Double?, onReady: (String) -> Unit) {
        thread(name = "glove-weather") {
            onReady(fetchJson(lat, lon))
        }
    }

    private fun parse(html: String, lat: Double?, lon: Double?): JSONObject {
        var temp: Number? = null
        var condition = ""
        var label = ""
        var place = ""

        val value = Regex("""AppFactTemperature_value__[A-Za-z0-9_-]+"[^>]*>\s*([+\-]?\d+)""").find(html)
        val sign = Regex("""AppFactTemperature_sign__[A-Za-z0-9_-]+"[^>]*>\s*([+\-])""").find(html)
        if (value != null) {
            var n = value.groupValues[1].toDoubleOrNull()
            if (n != null && sign?.groupValues?.get(1) == "-" && n > 0) n = -n
            temp = n
        }

        Regex("""погода сейчас:\s*([^.<]+)""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let {
            label = decode(it)
            condition = label
        }

        if (temp == null) {
            Regex(""""fact"\s*:\s*\{([\s\S]*?)\}""").find(html)?.groupValues?.get(1)?.let { block ->
                Regex(""""temp"\s*:\s*(-?\d+(?:\.\d+)?)""").find(block)?.groupValues?.get(1)?.toDoubleOrNull()?.let { temp = it }
                Regex(""""condition"\s*:\s*"([a-z-]+)"""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.let {
                    condition = it
                    label = COND_RU[it] ?: it
                }
            }
        }
        if (temp == null) {
            val tv = Regex("""temp__value[^>]*>\s*([+\-]?\d+)""", RegexOption.IGNORE_CASE).find(html)
                ?: Regex(""""temperature"\s*:\s*(-?\d+)""").find(html)
            tv?.groupValues?.get(1)?.toDoubleOrNull()?.let { temp = it }
        }
        if (label.isBlank()) {
            val lc = Regex("""link__condition[^>]*>\s*([^<]+)""", RegexOption.IGNORE_CASE).find(html)
                ?: Regex("""fact__condition[^>]*>\s*([^<]+)""", RegexOption.IGNORE_CASE).find(html)
            lc?.groupValues?.get(1)?.let {
                label = decode(it)
                condition = label
            }
        }

        val loc = Regex("""Погода в ([^—<\n]{2,60})""").find(html)
            ?: Regex("""property="og:title"\s+content="Погода в ([^"—]+)""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex(""""locality"\s*:\s*\{[^{}]*"name"\s*:\s*"([^"]+)"""").find(html)
        if (loc != null) {
            place = decode(loc.groupValues[1]).replace(Regex("""\s*[—–-]\s*Прогноз.*$""", RegexOption.IGNORE_CASE), "").trim()
        }
        if (place.isBlank()) place = "Яндекс Погода"
        if (label.isBlank()) label = "Яндекс Погода"

        val href = if (lat != null && lon != null) {
            "https://yandex.ru/pogoda/?lat=$lat&lon=$lon"
        } else {
            "https://yandex.ru/pogoda/"
        }

        val out = JSONObject()
            .put("label", label)
            .put("place", place)
            .put("condition", condition.ifBlank { label })
            .put("href", href)
        if (temp != null) out.put("temp", temp) else out.put("temp", JSONObject.NULL)
        return out
    }

    private fun get(url: String): String? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 GloveBrowser/1.8.4")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
        }
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (_: Exception) {
        null
    }

    private fun decode(value: String): String =
        value.replace(Regex("""\\u([0-9a-fA-F]{4})""")) {
            it.groupValues[1].toInt(16).toChar().toString()
        }
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("\u00a0", " ")
            .trim()
}
