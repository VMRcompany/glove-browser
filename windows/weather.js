const https = require("https");

function css() {
  return `
    .wx {
      position: relative; width: min(360px, 92vw); height: 124px; margin: 0 0 18px;
      border-radius: 26px; overflow: hidden; text-decoration: none; color: inherit;
      transform: perspective(980px) rotateX(10deg) translateZ(0); transform-style: preserve-3d;
      box-shadow: 0 22px 44px rgba(32,33,36,.2), 0 1px 0 rgba(255,255,255,.4) inset, 0 -18px 30px rgba(0,0,0,.08) inset;
      background: linear-gradient(145deg, #6ec8ff 0%, #3a8dff 48%, #1f4f78 100%);
      transition: transform 240ms ease, box-shadow 240ms ease;
      display: block; isolation: isolate;
    }
    .wx:hover { transform: perspective(980px) rotateX(3deg) translateY(-3px) scale(1.01); box-shadow: 0 28px 52px rgba(32,33,36,.24); }
    .wx .shine {
      position: absolute; inset: -40% -20%; z-index: 1; pointer-events: none;
      background: linear-gradient(115deg, transparent 35%, rgba(255,255,255,.22) 48%, transparent 62%);
      transform: translateX(-30%) rotate(8deg); animation: wxShine 5.5s ease-in-out infinite;
    }
    .wx canvas { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; z-index: 0; }
    .wx .face {
      position: relative; z-index: 2; height: 100%; display: flex; align-items: center; gap: 14px;
      padding: 14px 18px; color: #fff; text-shadow: 0 1px 2px rgba(0,0,0,.25);
      transform: translateZ(24px);
    }
    .wx .icon3d {
      width: 68px; height: 68px; flex: none; display: grid; place-items: center;
      font-size: 44px; filter: drop-shadow(0 10px 12px rgba(0,0,0,.3));
      transform: translateZ(36px) rotateY(-12deg); animation: wxBob 3.2s ease-in-out infinite;
      background: radial-gradient(circle at 35% 30%, rgba(255,255,255,.28), transparent 62%);
      border-radius: 22px;
    }
    .wx .meta { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .wx .temp { font-size: 36px; font-weight: 600; letter-spacing: -1.2px; line-height: 1; }
    .wx .label { font-size: 14px; opacity: .94; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .wx .place { font-size: 12px; opacity: .8; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .wx.snow { background: linear-gradient(150deg, #eaf4ff 0%, #8eb8ff 42%, #3d5a80 100%); }
    .wx.snow .face { color: #10233f; text-shadow: none; }
    .wx.rain { background: linear-gradient(150deg, #7f9db8 0%, #355c7d 48%, #152230 100%); }
    .wx.sun { background: linear-gradient(145deg, #ffe29a 0%, #ffb347 42%, #ff7e5f 100%); }
    .wx.cloud { background: linear-gradient(145deg, #d7e0e6 0%, #8ea0b0 55%, #4f5f6e 100%); }
    @keyframes wxBob { 0%,100% { transform: translateZ(36px) rotateY(-12deg) translateY(0); } 50% { transform: translateZ(36px) rotateY(-8deg) translateY(-5px); } }
    @keyframes wxShine { 0%,60%,100% { transform: translateX(-35%) rotate(8deg); opacity: .35; } 75% { transform: translateX(35%) rotate(8deg); opacity: .7; } }
    html.night .wx, @media (prefers-color-scheme: dark) {
      .wx { box-shadow: 0 22px 44px rgba(0,0,0,.5); }
    }
  `;
}

function html() {
  return `<a class="wx cloud" id="wx" href="https://yandex.ru/pogoda/" title="Яндекс Погода" rel="noopener">
    <canvas id="wxFx" width="720" height="248"></canvas>
    <div class="shine"></div>
    <div class="face">
      <div class="icon3d" id="wxIcon">⛅</div>
      <div class="meta">
        <div class="temp" id="wxTemp">—°</div>
        <div class="label" id="wxLabel">Погода рядом</div>
        <div class="place" id="wxPlace">Определяем место…</div>
      </div>
    </div>
  </a>`;
}

function script() {
  return `(function(){
    var card = document.getElementById("wx");
    var canvas = document.getElementById("wxFx");
    if (!card || !canvas) return;
    var ctx = canvas.getContext("2d");
    var mode = "cloud";
    var particles = [];
    var raf = 0;
    function resize() {
      var r = card.getBoundingClientRect();
      canvas.width = Math.max(320, Math.floor(r.width * 2));
      canvas.height = Math.max(124, Math.floor(r.height * 2));
    }
    function seed(kind) {
      particles = [];
      var count = kind === "sun" ? 22 : kind === "rain" ? 90 : kind === "snow" ? 70 : 28;
      for (var i = 0; i < count; i++) {
        particles.push({
          x: Math.random() * canvas.width,
          y: Math.random() * canvas.height,
          z: 0.35 + Math.random() * 1.8,
          s: 1 + Math.random() * 2.8,
          a: Math.random() * Math.PI * 2,
          w: 18 + Math.random() * 36
        });
      }
    }
    function frame() {
      var w = canvas.width, h = canvas.height, t = Date.now();
      ctx.clearRect(0, 0, w, h);
      if (mode === "sun") {
        var cx = w * 0.82, cy = h * 0.26;
        var g = ctx.createRadialGradient(cx, cy, 6, cx, cy, 110);
        g.addColorStop(0, "rgba(255,255,220,.98)");
        g.addColorStop(0.35, "rgba(255,210,90,.45)");
        g.addColorStop(1, "rgba(255,200,80,0)");
        ctx.fillStyle = g;
        ctx.beginPath(); ctx.arc(cx, cy, 110, 0, Math.PI * 2); ctx.fill();
        ctx.save(); ctx.translate(cx, cy); ctx.rotate(t / 2400);
        ctx.strokeStyle = "rgba(255,240,180,.6)"; ctx.lineWidth = 3.2;
        for (var r = 0; r < 14; r++) {
          ctx.rotate(Math.PI / 7);
          ctx.beginPath(); ctx.moveTo(30, 0); ctx.lineTo(62 + (r % 2) * 12, 0); ctx.stroke();
        }
        ctx.restore();
      }
      particles.forEach(function (p) {
        if (mode === "snow") {
          p.y += 0.55 * p.z; p.x += Math.sin((p.a += 0.018) + t / 900) * 0.55 * p.z;
          if (p.y > h + 8) { p.y = -8; p.x = Math.random() * w; }
          var rad = p.s * p.z * 1.15;
          var flake = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, rad);
          flake.addColorStop(0, "rgba(255,255,255," + (0.85 + p.z * 0.1) + ")");
          flake.addColorStop(1, "rgba(255,255,255,0)");
          ctx.fillStyle = flake;
          ctx.beginPath(); ctx.arc(p.x, p.y, rad, 0, Math.PI * 2); ctx.fill();
        } else if (mode === "rain") {
          p.y += 8.5 * p.z; p.x += 1.6 * p.z;
          if (p.y > h) { p.y = -12; p.x = Math.random() * w; }
          ctx.strokeStyle = "rgba(210,235,255," + (0.28 + p.z * 0.3) + ")";
          ctx.lineWidth = 1.1 * p.z;
          ctx.beginPath(); ctx.moveTo(p.x, p.y); ctx.lineTo(p.x - 4 * p.z, p.y + 14 * p.z); ctx.stroke();
        } else if (mode === "cloud") {
          p.x += 0.22 * p.z;
          if (p.x > w + 50) p.x = -50;
          ctx.fillStyle = "rgba(255,255,255," + (0.07 + p.z * 0.07) + ")";
          ctx.beginPath(); ctx.ellipse(p.x, (p.y % h), p.w * p.z * 0.45, 11 * p.z, 0, 0, Math.PI * 2); ctx.fill();
        } else if (mode === "sun") {
          p.a += 0.01;
          var px = w * 0.82 + Math.cos(p.a) * (20 + p.z * 18);
          var py = h * 0.26 + Math.sin(p.a * 1.3) * (12 + p.z * 10);
          ctx.fillStyle = "rgba(255,255,220," + (0.12 + p.z * 0.08) + ")";
          ctx.beginPath(); ctx.arc(px, py, 2.2 * p.z, 0, Math.PI * 2); ctx.fill();
        }
      });
      raf = requestAnimationFrame(frame);
    }
    function setMode(next) {
      mode = next || "cloud";
      card.className = "wx " + mode;
      seed(mode);
    }
    function condMode(c) {
      c = String(c || "").toLowerCase();
      if (/snow|снег|метел/.test(c)) return "snow";
      if (/rain|drizzle|shower|thunder|ливень|дожд|гроз|морось/.test(c)) return "rain";
      if (/clear|sunny|ясно|солнечно/.test(c)) return "sun";
      if (/partly|малообл/.test(c)) return "sun";
      return "cloud";
    }
    function condIcon(c) {
      c = String(c || "").toLowerCase();
      if (/thunder|гроз/.test(c)) return "⛈️";
      if (/snow|снег|метел/.test(c)) return "❄️";
      if (/rain|drizzle|shower|дожд|ливень|морось/.test(c)) return "🌧️";
      if (/fog|туман/.test(c)) return "🌫️";
      if (/clear|sunny|ясно|солнечно/.test(c)) return "☀️";
      if (/partly|малообл/.test(c)) return "⛅";
      return "☁️";
    }
    function paint(data) {
      if (!data) return;
      var temp = data.temp;
      var label = data.label || "Яндекс Погода";
      var place = data.place || "Яндекс Погода";
      var cond = data.condition || label;
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
          var ret = GloveWeather.load(lat, lon);
          if (ret && typeof ret.then === "function") ret.then(paint).catch(function(){ paint({ label: "Яндекс Погода", place: "Открыть прогноз" }); });
          return true;
        }
      } catch (e) {}
      return false;
    }
    function load(lat, lon) {
      if (typeof lat === "number" && typeof lon === "number") {
        card.href = "https://yandex.ru/pogoda/?lat=" + lat + "&lon=" + lon;
      } else {
        card.href = "https://yandex.ru/pogoda/";
      }
      if (!askNative(lat, lon)) {
        document.getElementById("wxPlace").textContent = "Открыть Яндекс Погоду";
      }
    }
    resize(); seed("cloud"); frame();
    window.addEventListener("resize", function(){ resize(); seed(mode); });
    if (!navigator.geolocation) {
      load(null, null);
      return;
    }
    navigator.geolocation.getCurrentPosition(function(pos){
      load(pos.coords.latitude, pos.coords.longitude);
    }, function(){
      document.getElementById("wxLabel").textContent = "Яндекс Погода";
      document.getElementById("wxPlace").textContent = "По IP или откройте прогноз";
      load(null, null);
    }, { enableHighAccuracy: false, timeout: 10000, maximumAge: 600000 });
  })();`;
}

function get(url) {
  return new Promise((resolve, reject) => {
    const req = https.get(url, {
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 GloveBrowser/1.8.1",
        "Accept": "text/html,application/json;q=0.9,*/*;q=0.8",
        "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.8"
      },
      timeout: 12000
    }, (res) => {
      if (res.statusCode && res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        const next = res.headers.location.startsWith("http") ? res.headers.location : new URL(res.headers.location, url).href;
        res.resume();
        get(next).then(resolve, reject);
        return;
      }
      const chunks = [];
      res.on("data", (c) => chunks.push(c));
      res.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    });
    req.on("error", reject);
    req.on("timeout", () => {
      req.destroy();
      reject(new Error("timeout"));
    });
  });
}

function decode(value) {
  return String(value || "")
    .replace(/\\u([0-9a-fA-F]{4})/g, (_, h) => String.fromCharCode(parseInt(h, 16)))
    .replace(/&quot;/g, "\"")
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .trim();
}

const COND_RU = {
  clear: "Ясно",
  "partly-cloudy": "Малооблачно",
  cloudy: "Облачно",
  overcast: "Пасмурно",
  drizzle: "Морось",
  "light-rain": "Небольшой дождь",
  rain: "Дождь",
  "moderate-rain": "Дождь",
  "heavy-rain": "Сильный дождь",
  "continuous-heavy-rain": "Ливень",
  showers: "Ливень",
  "wet-snow": "Мокрый снег",
  "light-snow": "Небольшой снег",
  snow: "Снег",
  "snow-showers": "Снегопад",
  hail: "Град",
  thunderstorm: "Гроза",
  "thunderstorm-with-rain": "Гроза",
  "thunderstorm-with-hail": "Гроза с градом",
  fog: "Туман"
};

function parseYandex(html, lat, lon) {
  const text = String(html || "");
  let temp = null;
  let condition = "";
  let label = "";
  let place = "";

  const fact = text.match(/"fact"\s*:\s*\{([\s\S]*?)\}/);
  if (fact) {
    const block = fact[1];
    const t = block.match(/"temp"\s*:\s*(-?\d+(?:\.\d+)?)/);
    const c = block.match(/"condition"\s*:\s*"([a-z-]+)"/i);
    if (t) temp = Number(t[1]);
    if (c) {
      condition = c[1];
      label = COND_RU[condition] || condition;
    }
  }
  if (temp == null) {
    const tv = text.match(/temp__value[^>]*>\s*([+\-]?\d+)/i) || text.match(/"temperature"\s*:\s*(-?\d+)/);
    if (tv) temp = Number(tv[1]);
  }
  if (!label) {
    const lc = text.match(/link__condition[^>]*>\s*([^<]+)/i) || text.match(/fact__condition[^>]*>\s*([^<]+)/i);
    if (lc) {
      label = decode(lc[1]);
      condition = label;
    }
  }
  const loc =
    text.match(/"locality"\s*:\s*\{[^{}]*"name"\s*:\s*"([^"]+)"/) ||
    text.match(/"province"\s*:\s*\{[^{}]*"name"\s*:\s*"([^"]+)"/) ||
    text.match(/property="og:title"\s+content="([^"]+)"/i);
  if (loc) {
    place = decode(loc[1]).replace(/\s*[—–-]\s*Яндекс\.Погода.*/i, "").trim();
  }
  if (!place) place = "Яндекс Погода";
  if (!label) label = "Яндекс Погода";

  const href =
    typeof lat === "number" && typeof lon === "number"
      ? "https://yandex.ru/pogoda/?lat=" + lat + "&lon=" + lon
      : "https://yandex.ru/pogoda/";

  if (temp == null && !condition) {
    return { temp: null, label: "Яндекс Погода", place: "Открыть прогноз", condition: "cloud", href };
  }
  return { temp, label, place, condition: condition || label, href };
}

async function fetchWeather(lat, lon) {
  const hasCoords = typeof lat === "number" && typeof lon === "number" && !Number.isNaN(lat) && !Number.isNaN(lon);
  const url = hasCoords
    ? "https://yandex.ru/pogoda/?lat=" + encodeURIComponent(lat) + "&lon=" + encodeURIComponent(lon)
    : "https://yandex.ru/pogoda/";
  const htmlBody = await get(url);
  return parseYandex(htmlBody, hasCoords ? lat : null, hasCoords ? lon : null);
}

module.exports = { css, html, script, fetchWeather, parseYandex };
