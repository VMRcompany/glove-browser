(function () {
  var cfg = {
    yandex: "https://yandex.ru/search/touch/?text=",
    yandexLite: "https://yandex.ru/search/?text=",
    yandexHttp: "http://m.yandex.ru/search?text=",
    readers: [
      "https://r.jina.ai/",
      "https://api.allorigins.win/raw?url=",
      "https://corsproxy.io/?",
      "https://api.codetabs.com/v1/proxy?quest="
    ],
    proxy: true
  };
  var q = document.getElementById("q");
  var view = document.getElementById("view");
  var panel = document.getElementById("panel");
  var menu = document.getElementById("menu");
  var proxyState = document.getElementById("proxyState");
  var body = document.body;
  var historyStack = [];
  var readerIndex = 0;

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
    var ua = String(navigator.userAgent || "").toLowerCase();
    if (/nokia|series40|s40|asha|symbian|feature/.test(ua)) return "keypad";
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

  function buildProxy(url, index) {
    var i = index || 0;
    var prefix = cfg.readers[i] || cfg.readers[0];
    if (prefix.indexOf("allorigins") >= 0 || prefix.indexOf("codetabs") >= 0 || prefix.indexOf("corsproxy") >= 0) {
      return prefix + encodeURIComponent(url);
    }
    return prefix + url;
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
    s = s.replace(/<iframe[\s\S]*?<\/iframe>/gi, "");
    s = s.replace(/on[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, "");
    s = s.replace(/<img[^>]*>/gi, "");
    if (s.length > 180000) s = s.slice(0, 180000) + "\n<p>…обрезано для старого телефона…</p>";
    return s;
  }

  function xhrGet(url, ok, fail) {
    var x = new XMLHttpRequest();
    x.open("GET", url, true);
    x.timeout = 20000;
    x.onreadystatechange = function () {
      if (x.readyState !== 4) return;
      if (x.status >= 200 && x.status < 400) ok(x.responseText || "");
      else if (fail) fail(x.status);
    };
    x.ontimeout = function () { if (fail) fail(0); };
    try { x.send(null); } catch (e) { if (fail) fail(0); }
  }

  function openInIframe(url) {
    try { view.src = url; } catch (e) { location.href = url; }
  }

  function tryReaders(url, index) {
    if (index >= cfg.readers.length) {
      // last resort: navigate browser itself (Opera Mini handles TLS on servers)
      setPanelHtml(
        "<p>Прокси недоступен. Открываю напрямую / через браузер телефона.</p>" +
        "<p><a href='" + url + "'>" + url + "</a></p>" +
        "<p><a href='" + cfg.yandexHttp + encodeURIComponent(url) + "'>Искать в Яндексе</a></p>",
        url
      );
      try { location.href = url; } catch (e) {}
      return;
    }
    var reader = buildProxy(url, index);
    setPanelHtml("<p>Загрузка через прокси " + (index + 1) + "/" + cfg.readers.length + "…</p>", url);
    xhrGet(reader, function (text) {
      historyStack.push(url);
      readerIndex = index;
      var clean = simplify(text);
      setPanelHtml(clean, url);
      if (body.className.indexOf("touch") >= 0) openInIframe(reader);
    }, function () {
      tryReaders(url, index + 1);
    });
  }

  function openViaProxy(url) {
    panel.hidden = false;
    tryReaders(url, readerIndex);
  }

  function openTarget(raw) {
    var text = String(raw || "").replace(/^\s+|\s+$/g, "");
    if (!text) return;
    var url = looksUrl(text) ? normalize(text) : searchUrl(text);
    q.value = looksUrl(text) ? url : text;
    if (cfg.proxy) openViaProxy(url);
    else {
      panel.hidden = true;
      openInIframe(url);
    }
  }

  document.getElementById("go").onsubmit = function (e) {
    if (e && e.preventDefault) e.preventDefault();
    openTarget(q.value);
    return false;
  };

  document.getElementById("skL").onclick = function () {
    menu.hidden = !menu.hidden;
  };
  var btnMenu = document.getElementById("btnMenu");
  if (btnMenu) btnMenu.onclick = function () { menu.hidden = !menu.hidden; };
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

  openTarget("https://yandex.ru/");
})();
