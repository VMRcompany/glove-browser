(function () {
  var cfg = {
    yandex: "https://yandex.ru/search/?text=",
    yandexHttp: "http://www.yandex.ru/yandsearch?text=",
    readers: ["https://r.jina.ai/", "https://api.allorigins.win/raw?url="],
    proxy: true
  };
  var q = document.getElementById("q");
  var view = document.getElementById("view");
  var panel = document.getElementById("panel");
  var menu = document.getElementById("menu");
  var proxyState = document.getElementById("proxyState");
  var body = document.body;
  var historyStack = [];

  function isTouch() {
    try {
      return ("ontouchstart" in window) || (navigator.maxTouchPoints > 0);
    } catch (e) {
      return false;
    }
  }

  function applyMode(mode) {
    body.className = "mode-" + mode;
    try { localStorage.setItem("glove-json-mode", mode); } catch (e) {}
  }

  function detectMode() {
    var saved = null;
    try { saved = localStorage.getItem("glove-json-mode"); } catch (e) {}
    if (saved === "touch" || saved === "keypad") return saved;
    return isTouch() ? "touch" : "keypad";
  }

  applyMode(detectMode());

  function looksUrl(text) {
    var t = String(text || "").replace(/^\s+|\s+$/g, "");
    if (!t) return false;
    if (/^https?:\/\//i.test(t)) return true;
    if (t.indexOf(" ") >= 0) return false;
    return t.indexOf(".") > 0 || /^localhost/i.test(t);
  }

  function normalize(text) {
    var t = String(text || "").replace(/^\s+|\s+$/g, "");
    if (/^https?:\/\//i.test(t)) return t;
    return "http://" + t;
  }

  function searchUrl(text) {
    return cfg.yandex + encodeURIComponent(text);
  }

  function proxyUrl(url) {
    // jina reader accepts full URL after prefix and modern TLS on server side
    return cfg.readers[0] + url;
  }

  function setPanelHtml(html, baseUrl) {
    panel.hidden = false;
    panel.innerHTML = html || "<p>Пусто</p>";
    var links = panel.getElementsByTagName("a");
    for (var i = 0; i < links.length; i++) {
      (function (a) {
        var href = a.getAttribute("href") || "";
        a.onclick = function (ev) {
          if (ev && ev.preventDefault) ev.preventDefault();
          openTarget(absUrl(href, baseUrl));
          return false;
        };
      })(links[i]);
    }
  }

  function absUrl(href, base) {
    if (!href) return base || "";
    if (/^https?:\/\//i.test(href)) return href;
    if (href.charAt(0) === "#") return (base || "").split("#")[0] + href;
    try {
      var b = document.createElement("a");
      b.href = base || location.href;
      if (href.charAt(0) === "/") {
        return b.protocol + "//" + b.host + href;
      }
      var path = b.pathname || "/";
      path = path.replace(/[^\/]+$/, "");
      return b.protocol + "//" + b.host + path + href;
    } catch (e) {
      return href;
    }
  }

  function simplify(html) {
    var s = String(html || "");
    s = s.replace(/<script[\s\S]*?<\/script>/gi, "");
    s = s.replace(/<style[\s\S]*?<\/style>/gi, "");
    s = s.replace(/<noscript[\s\S]*?<\/noscript>/gi, "");
    s = s.replace(/on[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, "");
    if (s.length > 200000) s = s.slice(0, 200000) + "\n<p>…обрезано для старого телефона…</p>";
    return s;
  }

  function xhrGet(url, ok, fail) {
    var x = new XMLHttpRequest();
    x.open("GET", url, true);
    x.onreadystatechange = function () {
      if (x.readyState !== 4) return;
      if (x.status >= 200 && x.status < 400) ok(x.responseText || "");
      else if (fail) fail(x.status);
    };
    try { x.send(null); } catch (e) { if (fail) fail(0); }
  }

  function openInIframe(url) {
    panel.hidden = true;
    try {
      view.src = url;
    } catch (e) {
      location.href = url;
    }
  }

  function openViaProxy(url) {
    setPanelHtml("<p>Загрузка через прокси…</p>", url);
    var reader = proxyUrl(url);
    xhrGet(reader, function (text) {
      historyStack.push(url);
      if (body.className.indexOf("keypad") >= 0) {
        setPanelHtml(simplify(text), url);
      } else {
        // touch: try iframe to reader first, fallback panel
        openInIframe(reader);
        setPanelHtml(simplify(text), url);
      }
    }, function () {
      // fallback allorigins
      var alt = cfg.readers[1] + encodeURIComponent(url);
      xhrGet(alt, function (text) {
        historyStack.push(url);
        setPanelHtml(simplify(text), url);
        if (body.className.indexOf("touch") >= 0) openInIframe(alt);
      }, function () {
        setPanelHtml("<p>Не удалось открыть. Попробуйте Яндекс или другой адрес.</p><p><a href='" + url + "'>" + url + "</a></p>", url);
      });
    });
  }

  function openTarget(raw) {
    var text = String(raw || "").replace(/^\s+|\s+$/g, "");
    if (!text) return;
    var url = looksUrl(text) ? normalize(text) : searchUrl(text);
    q.value = looksUrl(text) ? url : text;
    if (cfg.proxy) openViaProxy(url);
    else openInIframe(url);
  }

  document.getElementById("go").onsubmit = function (e) {
    if (e && e.preventDefault) e.preventDefault();
    openTarget(q.value);
    return false;
  };

  document.getElementById("skL").onclick = function () {
    menu.hidden = !menu.hidden;
  };
  document.getElementById("skC").onclick = function () {
    openTarget(q.value || "https://yandex.ru/");
  };
  document.getElementById("skR").onclick = function () {
    if (historyStack.length > 1) {
      historyStack.pop();
      openTarget(historyStack.pop());
    } else if (history.length > 1) history.back();
  };

  menu.onclick = function (ev) {
    var t = ev.target || ev.srcElement;
    var act = t && t.getAttribute ? t.getAttribute("data-act") : "";
    if (!act && t && t.parentNode) act = t.parentNode.getAttribute("data-act");
    if (act === "yandex") openTarget("https://yandex.ru/");
    if (act === "home") openTarget("https://yandex.ru/");
    if (act === "proxy") {
      cfg.proxy = !cfg.proxy;
      proxyState.innerHTML = cfg.proxy ? "вкл" : "выкл";
    }
    if (act === "touch") {
      applyMode(body.className.indexOf("touch") >= 0 ? "keypad" : "touch");
    }
    if (act === "close") menu.hidden = true;
    if (act && act !== "close") menu.hidden = true;
  };

  document.onkeydown = function (ev) {
    ev = ev || window.event;
    var code = ev.keyCode || ev.which;
    if (code === 13) { openTarget(q.value); return false; }
    if (code === 8 || code === 27) { document.getElementById("skR").onclick(); return false; }
    if (code === 37) { document.getElementById("skL").onclick(); return false; }
    if (code === 39) { document.getElementById("skC").onclick(); return false; }
  };

  // boot: yandex home without weather widgets of main app
  openTarget("https://yandex.ru/");
})();
