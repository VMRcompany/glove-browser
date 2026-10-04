package com.glove.browser

object Weather {
    const val CSS = """
      .wx {
        position: relative; width: min(360px, 92vw); height: 118px; margin: 0 0 18px;
        border-radius: 24px; overflow: hidden; text-decoration: none; color: inherit;
        transform: perspective(900px) rotateX(8deg); transform-style: preserve-3d;
        box-shadow: 0 18px 40px rgba(32,33,36,.18), 0 2px 0 rgba(255,255,255,.35) inset;
        background: linear-gradient(145deg, #6ec8ff 0%, #3a8dff 48%, #234230 100%);
        transition: transform 220ms ease, box-shadow 220ms ease;
        display: block;
      }
      .wx:hover { transform: perspective(900px) rotateX(2deg) translateY(-2px); }
      .wx canvas { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
      .wx .face {
        position: relative; z-index: 2; height: 100%; display: flex; align-items: center; gap: 14px;
        padding: 14px 18px; color: #fff; text-shadow: 0 1px 2px rgba(0,0,0,.25);
      }
      .wx .icon3d {
        width: 64px; height: 64px; flex: none; display: grid; place-items: center;
        font-size: 42px; filter: drop-shadow(0 8px 10px rgba(0,0,0,.28));
        animation: wxBob 3.2s ease-in-out infinite;
      }
      .wx .meta { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
      .wx .temp { font-size: 34px; font-weight: 600; letter-spacing: -1px; line-height: 1; }
      .wx .label { font-size: 14px; opacity: .92; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
      .wx .place { font-size: 12px; opacity: .78; }
      .wx.snow { background: linear-gradient(150deg, #d7e9ff 0%, #7fb2ff 45%, #3d5a80 100%); }
      .wx.snow .face { color: #10233f; text-shadow: none; }
      .wx.rain { background: linear-gradient(150deg, #6b8cae 0%, #355c7d 50%, #1b2838 100%); }
      .wx.sun { background: linear-gradient(145deg, #ffe29a 0%, #ffb347 40%, #ff7e5f 100%); }
      .wx.cloud { background: linear-gradient(145deg, #cfd9df 0%, #8ea0b0 55%, #5c6b7a 100%); }
      @keyframes wxBob { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-4px); } }
    """.trimIndent()

    const val HTML = """
      <a class="wx cloud" id="wx" href="https://yandex.ru/pogoda/" title="Яндекс Погода">
        <canvas id="wxFx" width="720" height="236"></canvas>
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

    const val SCRIPT = """
      (function(){
        var card = document.getElementById("wx");
        var canvas = document.getElementById("wxFx");
        if (!card || !canvas) return;
        var ctx = canvas.getContext("2d");
        var mode = "cloud";
        var particles = [];
        function resize() {
          var r = card.getBoundingClientRect();
          canvas.width = Math.max(320, Math.floor(r.width * 2));
          canvas.height = Math.max(118, Math.floor(r.height * 2));
        }
        function seed(kind) {
          particles = [];
          var count = kind === "sun" ? 18 : kind === "rain" ? 70 : kind === "snow" ? 55 : 24;
          for (var i = 0; i < count; i++) {
            particles.push({ x: Math.random()*canvas.width, y: Math.random()*canvas.height, z: 0.4+Math.random()*1.6, s: 1+Math.random()*2.5, a: Math.random()*Math.PI*2 });
          }
        }
        function frame() {
          var w = canvas.width, h = canvas.height;
          ctx.clearRect(0,0,w,h);
          if (mode === "sun") {
            var cx = w*0.82, cy = h*0.28;
            var g = ctx.createRadialGradient(cx,cy,8,cx,cy,90);
            g.addColorStop(0,"rgba(255,255,220,.95)"); g.addColorStop(1,"rgba(255,200,80,0)");
            ctx.fillStyle = g; ctx.beginPath(); ctx.arc(cx,cy,90,0,Math.PI*2); ctx.fill();
            ctx.save(); ctx.translate(cx,cy); ctx.rotate(Date.now()/2200);
            ctx.strokeStyle = "rgba(255,240,180,.55)"; ctx.lineWidth = 3;
            for (var r = 0; r < 12; r++) { ctx.rotate(Math.PI/6); ctx.beginPath(); ctx.moveTo(28,0); ctx.lineTo(58,0); ctx.stroke(); }
            ctx.restore();
          }
          particles.forEach(function(p){
            if (mode === "snow") {
              p.y += 0.7*p.z; p.x += Math.sin(p.a += 0.02)*0.4;
              if (p.y > h) { p.y = -4; p.x = Math.random()*w; }
              ctx.fillStyle = "rgba(255,255,255,"+(0.45+p.z*0.25)+")";
              ctx.beginPath(); ctx.arc(p.x,p.y,p.s*p.z,0,Math.PI*2); ctx.fill();
            } else if (mode === "rain") {
              p.y += 7*p.z; p.x += 1.2;
              if (p.y > h) { p.y = -10; p.x = Math.random()*w; }
              ctx.strokeStyle = "rgba(210,230,255,"+(0.35+p.z*0.25)+")";
              ctx.lineWidth = 1.2*p.z;
              ctx.beginPath(); ctx.moveTo(p.x,p.y); ctx.lineTo(p.x-3,p.y+12*p.z); ctx.stroke();
            } else if (mode === "cloud") {
              p.x += 0.25*p.z; if (p.x > w+40) p.x = -40;
              ctx.fillStyle = "rgba(255,255,255,"+(0.08+p.z*0.08)+")";
              ctx.beginPath(); ctx.ellipse(p.x, p.y%h, 28*p.z, 12*p.z, 0, 0, Math.PI*2); ctx.fill();
            }
          });
          requestAnimationFrame(frame);
        }
        function setMode(next) { mode = next; card.className = "wx "+next; seed(next); }
        function codeMode(code) {
          if (code === 0) return "sun";
          if ((code >= 71 && code <= 77) || code === 85 || code === 86) return "snow";
          if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95) return "rain";
          return "cloud";
        }
        function codeLabel(code) {
          if (code === 0) return "Ясно";
          if (code === 1 || code === 2) return "Малооблачно";
          if (code === 3) return "Облачно";
          if (code >= 45 && code <= 48) return "Туман";
          if (code >= 51 && code <= 67) return "Дождь";
          if (code >= 71 && code <= 77) return "Снег";
          if (code >= 80 && code <= 82) return "Ливень";
          if (code === 85 || code === 86) return "Снегопад";
          if (code >= 95) return "Гроза";
          return "Погода";
        }
        function codeIcon(code) {
          if (code === 0) return "☀️";
          if (code <= 3) return "⛅";
          if (code <= 48) return "🌫️";
          if (code <= 67 || (code >= 80 && code <= 82)) return "🌧️";
          if (code <= 86) return "❄️";
          if (code >= 95) return "⛈️";
          return "🌤️";
        }
        function paint(temp, code) {
          document.getElementById("wxTemp").textContent = (Math.round(temp) > 0 ? "+" : "") + Math.round(temp) + "°";
          document.getElementById("wxLabel").textContent = codeLabel(code);
          document.getElementById("wxPlace").textContent = "Рядом с вами";
          document.getElementById("wxIcon").textContent = codeIcon(code);
          setMode(codeMode(code));
        }
        function load(lat, lon) {
          card.href = "https://yandex.ru/pogoda/?lat=" + lat + "&lon=" + lon;
          fetch("https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+"&current=temperature_2m,weather_code&timezone=auto")
            .then(function(r){ return r.json(); })
            .then(function(data){ var cur = data.current || {}; paint(cur.temperature_2m, cur.weather_code || 1); })
            .catch(function(){ document.getElementById("wxPlace").textContent = "Открыть Яндекс Погоду"; });
        }
        resize(); seed("cloud"); frame();
        if (!navigator.geolocation) {
          document.getElementById("wxPlace").textContent = "Открыть Яндекс Погоду";
          return;
        }
        navigator.geolocation.getCurrentPosition(function(pos){
          load(pos.coords.latitude, pos.coords.longitude);
        }, function(){
          document.getElementById("wxPlace").textContent = "Разрешите геолокацию";
          document.getElementById("wxLabel").textContent = "Яндекс Погода";
        }, { enableHighAccuracy: false, timeout: 10000, maximumAge: 600000 });
      })();
    """.trimIndent()
}
