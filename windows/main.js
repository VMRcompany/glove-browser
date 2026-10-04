const { app, BrowserWindow, BrowserView, Menu, clipboard, dialog, shell, ipcMain, session, nativeTheme } = require("electron");
const extensions = require("./extensions");
const engines = require("./engines");
const vault = require("./vault");
const voice = require("./voice");
const permissions = require("./permissions");
const APP_VERSION = "1.8.0";

function themeFile() {
  return path.join(app.getPath("userData"), "theme.json");
}

function themeMode() {
  try {
    const mode = JSON.parse(fs.readFileSync(themeFile(), "utf8")).mode;
    return mode === "dark" ? "dark" : "system";
  } catch {
    return "system";
  }
}

function applyTheme(mode) {
  const next = mode === "dark" ? "dark" : "system";
  nativeTheme.themeSource = next;
  try {
    fs.mkdirSync(path.dirname(themeFile()), { recursive: true });
    fs.writeFileSync(themeFile(), JSON.stringify({ mode: next }));
  } catch { /* theme still applies for this run */ }
}
const { spawn } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const https = require("https");

const logoData = "data:image/png;base64," + fs.readFileSync(path.join(__dirname, "logo.png")).toString("base64");

const ENGINES = [
  ["yandex", "Яндекс", "yandex.ru", "https://yandex.ru/search/?text={q}"],
  ["google", "Google", "google.com", "https://www.google.com/search?q={q}"],
  ["bing", "Bing", "bing.com", "https://www.bing.com/search?q={q}"],
  ["duckduckgo", "DuckDuckGo", "duckduckgo.com", "https://duckduckgo.com/?q={q}"],
  ["yahoo", "Yahoo", "search.yahoo.com", "https://search.yahoo.com/search?p={q}"],
  ["mail", "Mail.ru", "go.mail.ru", "https://go.mail.ru/search?q={q}"],
  ["rambler", "Rambler", "rambler.ru", "https://nova.rambler.ru/search?query={q}"],
  ["brave", "Brave", "search.brave.com", "https://search.brave.com/search?q={q}"],
  ["ecosia", "Ecosia", "ecosia.org", "https://www.ecosia.org/search?q={q}"],
  ["startpage", "Startpage", "startpage.com", "https://www.startpage.com/sp/search?query={q}"],
  ["qwant", "Qwant", "qwant.com", "https://www.qwant.com/?q={q}"],
  ["wikipedia", "Википедия", "ru.wikipedia.org", "https://ru.wikipedia.org/w/index.php?search={q}"],
  ["baidu", "Baidu", "baidu.com", "https://www.baidu.com/s?wd={q}"],
  ["naver", "Naver", "search.naver.com", "https://search.naver.com/search.naver?query={q}"],
  ["seznam", "Seznam", "search.seznam.cz", "https://search.seznam.cz/?q={q}"],
  ["ask", "Ask", "ask.com", "https://www.ask.com/web?q={q}"],
  ["aol", "AOL", "search.aol.com", "https://search.aol.com/aol/search?q={q}"],
  ["kagi", "Kagi", "kagi.com", "https://kagi.com/search?q={q}"],
  ["you", "You.com", "you.com", "https://you.com/search?q={q}"],
  ["mojeek", "Mojeek", "mojeek.com", "https://www.mojeek.com/search?q={q}"],
  ["swisscows", "Swisscows", "swisscows.com", "https://swisscows.com/web?query={q}"],
  ["dogpile", "Dogpile", "dogpile.com", "https://www.dogpile.com/serp?q={q}"],
  ["metager", "MetaGer", "metager.org", "https://metager.org/meta/meta.ger3?eingabe={q}"],
  ["presearch", "Presearch", "presearch.com", "https://presearch.com/search?q={q}"]
].map(([id, name, domain, template]) => ({ id, name, domain, template }));

function iconUrl(id) {
  return engines.icon(id);
}

function engineFile() {
  return path.join(app.getPath("userData"), "engine.json");
}

function currentEngine() {
  try {
    const id = JSON.parse(fs.readFileSync(engineFile(), "utf8")).id;
    return ENGINES.find((item) => item.id === id) || ENGINES[0];
  } catch {
    return ENGINES[0];
  }
}

function setEngine(id) {
  fs.mkdirSync(app.getPath("userData"), { recursive: true });
  fs.writeFileSync(engineFile(), JSON.stringify({ id }));
}

function searchUrl(query) {
  return currentEngine().template.replace("{q}", encodeURIComponent(query));
}

app.userAgentFallback = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.215 Safari/537.36 GloveBrowser/1.8.0";

const windows = new Set();
let tabSeq = 1;

function libraryFile() {
  return path.join(app.getPath("userData"), "library.json");
}

function loadLibrary() {
  try {
    const data = JSON.parse(fs.readFileSync(libraryFile(), "utf8"));
    return {
      bookmarks: data.bookmarks || [],
      history: data.history || [],
      downloads: data.downloads || []
    };
  } catch {
    return { bookmarks: [], history: [], downloads: [] };
  }
}

function saveLibrary(data) {
  fs.mkdirSync(path.dirname(libraryFile()), { recursive: true });
  fs.writeFileSync(libraryFile(), JSON.stringify(data));
}

function addHistory(title, url) {
  if (!url || !/^https?:/i.test(url)) return;
  const data = loadLibrary();
  data.history = [{ title: title || url, url, time: Date.now() }, ...data.history.filter((item) => item.url !== url)].slice(0, 200);
  saveLibrary(data);
}

function addDownload(name, file) {
  const data = loadLibrary();
  data.downloads = [{ title: name, url: file, time: Date.now() }, ...data.downloads].slice(0, 100);
  saveLibrary(data);
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function page(title, body) {
  return `<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8"><title>${escapeHtml(title)}</title>
  <style>
    body { margin: 0; font-family: "Segoe UI", sans-serif; color: #202124; background: #fff; }
    .ntp { min-height: 100vh; display: flex; flex-direction: column; align-items: center; padding: 8vh 16px 24px; }
    .logo { font-size: 48px; font-weight: 500; letter-spacing: -1px; margin: 8px 0 20px; }
    .mark { width: 88px; height: 88px; border-radius: 22px; }
    .news { width: min(640px, 100%); margin-top: auto; padding-top: 36px; }
    .news h2 { font-size: 16px; font-weight: 500; }
    .story { display: block; text-decoration: none; color: #202124; padding: 12px 0; border-top: 1px solid #eceff1; }
    .story b { display: block; }
    .story span { color: #234230; font-size: 12px; }
    form input { width: min(560px, 86vw); height: 44px; border: 1px solid #dfe1e5; border-radius: 22px; padding: 0 18px; font-size: 16px; outline: none; }
    form input:focus { box-shadow: 0 1px 6px rgba(32,33,36,.28); }
    .serp { max-width: 720px; margin: 24px auto; padding: 0 16px; }
    .hit { display: block; text-decoration: none; margin: 0 0 22px; }
    .hit strong { display: block; color: #1a0dab; font-size: 20px; font-weight: 400; }
    .hit span { display: block; color: #188038; font-size: 13px; margin: 2px 0; word-break: break-all; }
    .hit em { display: block; color: #4d5156; font-style: normal; font-size: 14px; }
    .row { display: block; padding: 12px 0; border-bottom: 1px solid #dadce0; text-decoration: none; color: inherit; }
    .row b { display: block; }
    .row span { color: #188038; font-size: 13px; word-break: break-all; }
    .note { color: #5f6368; }
    @media (prefers-color-scheme: dark) {
      body { background: #202124; color: #e8eaed; }
      .story { color: #e8eaed; border-top-color: #3c4043; }
      form input, .searchline .engine, .picker { background: #303134; color: #e8eaed; border-color: #3c4043; }
      .choice { color: #e8eaed; }
      .choice.selected { background: #3c4043; }
      .row { border-bottom-color: #3c4043; }
      .note { color: #9aa0a6; }
    }
    .searchline { display: flex; align-items: center; gap: 8px; width: min(640px, 100%); }
    .searchline form { position: relative; flex: 1; width: auto; margin: 0; }
    .searchline input { padding: 0 116px 0 18px; }
    .suggest { width: min(640px, 100%); margin-top: 8px; background: #fff; border: 1px solid #dadce0; border-radius: 16px; overflow: hidden; }
    .suggest:empty { display: none; }
    .suggest a { display: block; padding: 10px 16px; color: #202124; text-decoration: none; }
    .searchline .engine, .searchline .lens { margin: 0; border: 0; cursor: pointer; }
    .searchline .engine { width: 46px; height: 46px; border-radius: 23px; border: 1px solid #dfe1e5; background: #fff; display: flex; align-items: center; justify-content: center; padding: 0; }
    .searchline .lens, .searchline .cam, .searchline .mic { position: absolute; top: 5px; width: 36px; height: 36px; padding: 0; background: transparent; display: flex; align-items: center; justify-content: center; }
    .searchline .lens { right: 6px; }
    .searchline .cam { right: 40px; }
    .searchline .mic { right: 74px; }
    .suggest { width: min(640px, 100%); margin-top: 8px; background: #fff; border: 1px solid #dadce0; border-radius: 16px; overflow: hidden; }
    .suggest:empty { display: none; }
    .suggest a { display: block; padding: 10px 16px; color: #202124; text-decoration: none; }
    .engine img, .choice img { width: 22px; height: 22px; }
    .picker { display: none; width: min(640px, 100%); margin-top: 10px; max-height: 280px; overflow: auto; background: #fff; border: 1px solid #dadce0; border-radius: 16px; box-shadow: 0 8px 24px rgba(32,33,36,.16); }
    .picker.open { display: grid; grid-template-columns: 1fr 1fr; }
    .choice { display: flex; align-items: center; gap: 8px; padding: 10px 12px; text-decoration: none; color: #202124; }
    .choice.selected { background: #f1f3f4; }
    button { margin-top: 16px; }
  </style></head><body>${body}</body></html>`;
}

function marketHtml(query, items) {
  const cards = (items || []).map((item) => `<article class="hit">
    ${item.icon ? `<img src="${escapeHtml(item.icon)}" alt="" width="48" height="48" style="border-radius:12px">` : ""}
    <strong>${escapeHtml(item.name)}</strong>
    <p>
      <a href="glove://install?id=${escapeHtml(item.id)}">Glove</a>
      <a href="https://chromewebstore.google.com/detail/${escapeHtml(item.id)}">Chrome</a>
      <a href="https://chromewebstore.google.com/detail/${escapeHtml(item.id)}">Яндекс</a>
      <a href="https://chromewebstore.google.com/detail/${escapeHtml(item.id)}">Edge</a>
    </p>
  </article>`).join("") || `<p class="note">${query ? "Ничего не нашлось." : "Введите название дополнения."}</p>`;
  return page("Дополнения", `<main class="serp">
    <h1>Дополнения</h1>
    <form action="https://orion.glove/market" method="get">
      <input name="q" value="${escapeHtml(query || "")}" placeholder="Найти бесплатное дополнение">
    </form>
    ${cards}
    <p class="note"><a href="https://glove-dop.mineholde.pro/">Маркет на сайте</a> · <a href="https://glove-dop.mineholde.pro/privacy.html">Конфиденциальность</a></p>
  </main>`);
}

function newerVersion(remote, local) {
  const left = String(remote || "").split(".").map((part) => parseInt(part, 10) || 0);
  const right = String(local || "").split(".").map((part) => parseInt(part, 10) || 0);
  const count = Math.max(left.length, right.length);
  for (let i = 0; i < count; i++) {
    const diff = (left[i] || 0) - (right[i] || 0);
    if (diff) return diff > 0;
  }
  return false;
}

let updateDismissed = false;
let updateReady = false;

function readVersion(url) {
  return new Promise((resolve) => {
    const request = https.get(url, { headers: { "User-Agent": "GloveBrowser/1.7" } }, (response) => {
      if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
        response.resume();
        readVersion(response.headers.location).then(resolve);
        return;
      }
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => {
        try { resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")).version || ""); }
        catch { resolve(""); }
      });
    });
    request.on("error", () => resolve(""));
  });
}

function installProtocol(url, win) {
  try {
    const id = new URL(url).searchParams.get("id");
    if (!id) return;
    extensions.installFromStore("https://chromewebstore.google.com/detail/" + id)
      .then((item) => dialog.showMessageBox(win, { message: "Дополнение установлено", detail: item.name }))
      .catch((error) => dialog.showMessageBox(win, { type: "error", message: "Не удалось установить", detail: String(error.message || error) }));
  } catch { /* ignore a broken link */ }
}

async function checkUpdate() {
  const remote = await readVersion("https://glove.mineholde.pro/version.json")
    || await readVersion("https://raw.githubusercontent.com/VMRcompany/glove-browser/main/docs/version.json");
  updateReady = !updateDismissed && newerVersion(remote, APP_VERSION);
  for (const state of windows) {
    if (!state.win.isDestroyed()) state.win.webContents.send("update", { show: updateReady });
  }
}

function homeHtml(shortcuts) {
  const engine = currentEngine();
  const tiles = (shortcuts || []).slice(0, 8).map((item) =>
    `<a class="row" href="${escapeHtml(item.url)}"><b>${escapeHtml(item.title)}</b><span>${escapeHtml(item.url)}</span></a>`
  ).join("");
  const choices = ENGINES.map((item) => {
    const selected = item.id === engine.id ? " selected" : "";
    return `<a class="choice${selected}" href="https://orion.glove/engine?id=${item.id}"><img src="${iconUrl(item.id)}" alt=""><span>${escapeHtml(item.name)}</span></a>`;
  }).join("");
  return page("Glove Browser", `<main class="ntp"><div class="hero" style="display:flex;flex-direction:column;align-items:center">
    <img class="mark" src="${logoData}" alt="">
    <div class="logo">Glove</div>
    <div class="searchline">
      <button class="engine" type="button" title="${escapeHtml(engine.name)}" onclick="document.getElementById('picker').classList.toggle('open')"><img src="${iconUrl(engine.id)}" alt=""></button>
      <form action="https://orion.glove/search" method="get">
        <input name="q" placeholder="Введите запрос или адрес" autofocus>
        <a class="mic" href="https://orion.glove/voice" title="Голосовой ввод" aria-label="Голосовой ввод"><svg viewBox="0 0 24 24" width="20" height="20"><path fill="#5f6368" d="M12 14a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v5a3 3 0 0 0 3 3zm5-3a1 1 0 0 0-2 0 3 3 0 0 1-6 0 1 1 0 0 0-2 0 5 5 0 0 0 4 4.9V18H9a1 1 0 0 0 0 2h6a1 1 0 0 0 0-2h-2v-2.1A5 5 0 0 0 17 11z"/></svg></a>
        <a class="cam" href="https://orion.glove/image" title="Поиск по изображению" aria-label="Поиск по изображению"><svg viewBox="0 0 24 24" width="20" height="20"><path fill="#5f6368" d="M9 3 7.17 5H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-3.17L15 3H9zm3 15a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z"/></svg></a>
        <button class="lens" type="submit" title="Найти" aria-label="Найти"><svg viewBox="0 0 24 24" width="22" height="22"><path fill="#5f6368" d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16a6.47 6.47 0 0 0 4.23-1.57l.27.28v.79l5 4.99L20.49 19zm-6 0A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z"/></svg></button>
      </form>
    </div>
    <div class="picker" id="picker">${choices}</div>
    <div class="suggest" id="suggest"></div>
    <div style="width:min(560px,86vw);margin-top:28px">${tiles}</div></div>
    <section class="news"><h2>Новости</h2><div id="news"><p class="note">Собираем новости…</p></div></section></main>
    <script>function gloveNews(html){ var n=document.getElementById("news"); if(n) n.innerHTML=html; }
      (function(){ var input=document.querySelector(".searchline input"); var box=document.getElementById("suggest"); if(!input||!box) return; var timer; input.addEventListener("input", function(){ clearTimeout(timer); var q=input.value.trim(); if(!q){ box.innerHTML=""; return; } timer=setTimeout(function(){ fetch("https://suggest.yandex.ru/suggest-ff.cgi?part="+encodeURIComponent(q)+"&uil=ru&v=4&sn=5").then(function(r){return r.text();}).then(function(text){ var data=JSON.parse(text.slice(text.indexOf("["))); var list=data[1]||[]; box.innerHTML=list.slice(0,8).map(function(item){ var label=typeof item==="string"?item:item[0]; return '<a href="https://orion.glove/search?q='+encodeURIComponent(label)+'">'+String(label).replace(/[&<>]/g,function(ch){return {"&":"&amp;","<":"&lt;",">":"&gt;"}[ch];})+"</a>"; }).join(""); }).catch(function(){}); }, 160); }); })();
    </script>`);
}

function resultsHtml(query, hits, note) {
  const items = hits.map((hit) => `<a class="hit" href="${escapeHtml(hit.url)}"><strong>${escapeHtml(hit.title)}</strong><span>${escapeHtml(hit.url)}</span><em>${escapeHtml(hit.snippet || "")}</em></a>`).join("");
  const extra = note ? `<p class="note">${escapeHtml(note)}</p>` : "";
  const empty = !hits.length && !note ? `<p class="note">Ничего не нашлось.</p>` : items;
  return page(query, `<main class="serp"><form action="https://orion.glove/search" method="get"><input name="q" value="${escapeHtml(query)}"></form>${extra}<section>${empty}</section></main>`);
}

function listHtml(kind, items) {
  const titles = { bookmarks: "Закладки", history: "История", downloads: "Загрузки", settings: "Настройки" };
  if (kind === "settings") {
    return page("Настройки", `<main class="serp"><h1>Настройки</h1>
      <p>Glove Browser ${APP_VERSION}<br>Поиск: Яндекс<br>Windows 7 и новее<br>Дополнения ставятся из меню «Дополнения».</p>
      <p><a href="https://orion.glove/clear-history">Очистить историю</a></p>
      <p><a href="https://orion.glove/clear-bookmarks">Очистить закладки</a></p></main>`);
  }
  const rows = items.length
    ? items.map((item) => `<a class="row" href="${escapeHtml(item.url)}"><b>${escapeHtml(item.title)}</b><span>${escapeHtml(item.url)}</span></a>`).join("")
    : `<p class="note">Пока пусто.</p>`;
  return page(titles[kind], `<main class="serp"><h1>${titles[kind]}</h1>${rows}</main>`);
}

function fetchText(url) {
  return new Promise((resolve, reject) => {
    const request = https.get(url, { headers: { "User-Agent": "GloveBrowser/1.0", Accept: "text/html" } }, (response) => {
      if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
        fetchText(response.headers.location).then(resolve, reject);
        response.resume();
        return;
      }
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    });
    request.setTimeout(15000, () => request.destroy(new Error("timeout")));
    request.on("error", reject);
  });
}

function clean(html) {
  return html.replace(/<[^>]+>/g, " ").replace(/&amp;/g, "&").replace(/&quot;/g, "\"").replace(/&#x27;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/\s+/g, " ").trim();
}

function unwrap(href) {
  try {
    const value = href.replace(/&amp;/g, "&");
    const found = new URL(value, "https://html.duckduckgo.com").searchParams.get("uddg");
    return found || value;
  } catch {
    return href;
  }
}

async function search(query) {
  const html = await fetchText("https://html.duckduckgo.com/html/?q=" + encodeURIComponent(query));
  const links = [...html.matchAll(/<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)<\/a>/gis)];
  const snippets = [...html.matchAll(/<(?:a|td)[^>]*class="result__snippet"[^>]*>(.*?)<\/(?:a|td)>/gis)].map((match) => clean(match[1]));
  return links.slice(0, 12).map((match, index) => {
    const url = unwrap(match[1]);
    return { title: clean(match[2]) || url, url, snippet: snippets[index] || "" };
  }).filter((hit) => /^https?:/i.test(hit.url));
}

function looksLikeUrl(text) {
  const lower = text.toLowerCase();
  if (lower.startsWith("http://") || lower.startsWith("https://")) return true;
  return !text.includes(" ") && (text.includes(".") || lower.startsWith("localhost"));
}

function normalize(text) {
  const lower = text.toLowerCase();
  return lower.startsWith("http://") || lower.startsWith("https://") ? text : "https://" + text;
}

function introFile() {
  return path.join(app.getPath("userData"), "intro.json");
}

function introDone() {
  try { return JSON.parse(fs.readFileSync(introFile(), "utf8")).done === true; }
  catch { return false; }
}

function markIntro() {
  fs.mkdirSync(app.getPath("userData"), { recursive: true });
  fs.writeFileSync(introFile(), JSON.stringify({ done: true }));
}

function openDefaultApps() {
  const release = os.release();
  if (release.startsWith("6.")) {
    spawn("control", ["/name", "Microsoft.DefaultPrograms"], { detached: true, stdio: "ignore" }).unref();
  } else {
    shell.openExternal("ms-settings:defaultapps");
  }
}

function introHtml() {
  return page("Glove Browser", `<main class="ntp" style="padding-top:12vh">
    <img class="mark" src="${logoData}" alt="">
    <div class="logo">Glove</div>
    <p class="note" style="max-width:520px;text-align:center">Строка сверху открывает адрес или поиск. Кнопка слева выбирает поисковую систему, лупа справа начинает поиск. Меню открывает закладки, историю и загрузки.</p>
    <p><a href="https://orion.glove/make-default">Сделать браузером по умолчанию</a></p>
    <p><a href="https://orion.glove/intro-done">Начать</a></p>
  </main>`);
}

let newsCache = { at: 0, html: "" };

function plain(value) {
  return String(value || "").replace(/<[^>]+>/g, " ").replace(/&amp;/g, "&").replace(/&quot;/g, "\"").replace(/&#39;/g, "'").replace(/\s+/g, " ").trim();
}

async function rssStories(url, source) {
  try {
    const xml = await fetchText(url);
    return [...xml.matchAll(/<item\b[^>]*>([\s\S]*?)<\/item>/gi)].slice(0, 4).map((match) => {
      const block = match[1];
      const title = plain((block.match(/<title[^>]*>(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/title>/i) || [])[1]);
      const href = ((block.match(/<link[^>]*>(?:<!\[CDATA\[)?(https?:\/\/[^<\]]+)/i) || [])[1] || "").trim();
      return title && href ? { title, url: href, source } : null;
    }).filter(Boolean);
  } catch {
    return [];
  }
}

async function wikiStories() {
  const day = new Date();
  const stamp = day.getUTCFullYear() + "/" + String(day.getUTCMonth() + 1).padStart(2, "0") + "/" + String(day.getUTCDate()).padStart(2, "0");
  try {
    const news = JSON.parse(await fetchText("https://ru.wikipedia.org/api/rest_v1/feed/featured/" + stamp)).news || [];
    return news.slice(0, 3).map((item) => {
      const link = (item.links || [])[0];
      const page = link && link.content_urls && link.content_urls.desktop && link.content_urls.desktop.page;
      return page ? { title: link.title || item.story, url: page, source: "Википедия" } : null;
    }).filter(Boolean);
  } catch {
    return [];
  }
}

async function loadNews() {
  if (newsCache.html && Date.now() - newsCache.at < 15 * 60 * 1000) return newsCache.html;
  const dzen = await rssStories("https://dzen.ru/news/rss", "Дзен");
  const [google, wiki, lenta, fallback] = await Promise.all([
    rssStories("https://news.google.com/rss?hl=ru&gl=RU&ceid=RU:ru", "Google Новости"),
    wikiStories(),
    rssStories("https://lenta.ru/rss/news", "Лента"),
    dzen.length ? Promise.resolve([]) : rssStories("https://news.yandex.ru/index.rss", "Дзен")
  ]);
  const stories = [...google, ...dzen, ...fallback, ...wiki, ...lenta]
    .filter((item, index, all) => all.findIndex((other) => other.title === item.title) === index)
    .slice(0, 12);
  const html = stories.length
    ? stories.map((story) => `<a class="story" href="${escapeHtml(story.url)}"><b>${escapeHtml(story.title)}</b><span>${escapeHtml(story.source)}</span></a>`).join("")
    : `<p class="note">Новости сейчас недоступны.</p>`;
  newsCache = { at: Date.now(), html };
  return html;
}

function createWindow(incognito) {
  const partition = incognito ? `incognito-${Date.now()}` : "persist:glove";
  const browserSession = session.fromPartition(partition);
  const win = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 720,
    minHeight: 480,
    frame: false,
    icon: path.join(__dirname, "logo.ico"),
    backgroundColor: incognito ? "#202124" : "#dee1e6",
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  const state = {
    win,
    incognito,
    session: browserSession,
    tabs: [],
    activeId: 0,
    chromeHeight: 88,
    ready: false,
    closed: [],
    covered: false
  };
  windows.add(state);
  if (!incognito) permissions.wire(browserSession, app.getPath("userData"));

  browserSession.on("will-download", (_event, item) => {
    const file = path.join(app.getPath("downloads"), item.getFilename());
    item.setSavePath(file);
    item.once("done", (_done, itemState) => {
      if (itemState !== "completed") return;
      if (!incognito) addDownload(item.getFilename(), file);
      if (!incognito && extensions.isCrxDownload(item.getFilename(), item.getMimeType(), item.getURL())) {
        extensions.installDownloaded(win, file);
      }
    });
  });

  function activeTab() {
    return state.tabs.find((tab) => tab.id === state.activeId) || null;
  }

  function layout() {
    if (state.covered) return;
    const tab = activeTab();
    if (!tab) return;
    const [width, height] = win.getContentSize();
    const top = state.chromeHeight;
    tab.view.setBounds({ x: 0, y: top, width, height: Math.max(0, height - top) });
  }

  function publish() {
    const tab = activeTab();
    const contents = tab && tab.view.webContents;
    const url = contents && !contents.isDestroyed() ? contents.getURL() : "";
    const home = !url || url.startsWith("data:");
    win.webContents.send("state", {
      tabs: state.tabs.map((item) => ({
        id: item.id,
        title: item.title,
        active: item.id === state.activeId
      })),
      canBack: !!(contents && contents.canGoBack()),
      canForward: !!(contents && contents.canGoForward()),
      loading: !!(contents && contents.isLoading()),
      home,
      display: home ? "" : url,
      engineId: currentEngine().id,
      engines: ENGINES.map((item) => ({ id: item.id, name: item.name, icon: iconUrl(item.id) })),
      picker: !!state.covered
    });
    if (!state.covered) layout();
  }

  function showHtml(tab, html) {
    tab.view.webContents.loadURL("data:text/html;charset=utf-8," + encodeURIComponent(html));
  }

  async function openAddress(tab, raw) {
    const text = String(raw || "").trim();
    if (!text) return showHome(tab);
    if (text.startsWith("https://orion.glove")) return handleOrion(tab, text);
    if (looksLikeUrl(text)) tab.view.webContents.loadURL(normalize(text));
    else await runSearch(tab, text);
  }

  function showHome(tab) {
    const shortcuts = incognito ? [] : loadLibrary().bookmarks;
    tab.title = "Новая вкладка";
    const contents = tab.view.webContents;
    contents.once("did-finish-load", () => {
      loadNews().then((body) => {
        if (contents.isDestroyed()) return;
        contents.executeJavaScript("typeof gloveNews==='function'&&gloveNews(" + JSON.stringify(body) + ")").catch(() => {});
      });
    });
    showHtml(tab, homeHtml(shortcuts));
    publish();
  }

  async function runSearch(tab, query) {
    tab.title = query;
    tab.view.webContents.loadURL(searchUrl(query));
    publish();
  }

  function handleOrion(tab, url) {
    let parsed;
    try { parsed = new URL(url); } catch { return; }
    if (parsed.pathname === "/voice") {
      win.webContents.send("voice-open");
      return;
    }
    if (parsed.pathname === "/image") {
      dialog.showOpenDialog(win, {
        title: "Изображение",
        properties: ["openFile"],
        filters: [{ name: "Изображения", extensions: ["png", "jpg", "jpeg", "webp", "gif"] }]
      }).then(async (result) => {
        if (result.canceled || !result.filePaths[0]) return;
        const page = await extensions.searchImage(result.filePaths[0]);
        shell.openExternal(page);
      }).catch(() => shell.openExternal("https://yandex.ru/images/"));
      return;
    }
    if (parsed.pathname === "/market") {
      const query = parsed.searchParams.get("q") || "";
      extensions.searchStore(query).then((items) => {
        if (tab.view.webContents.isDestroyed()) return;
        showHtml(tab, marketHtml(query, items));
      }).catch(() => showHtml(tab, marketHtml(query, [])));
      return;
    }
    if (parsed.pathname.startsWith("/search")) {
      const query = parsed.searchParams.get("q") || "";
      if (!query) showHome(tab);
      else if (looksLikeUrl(query)) tab.view.webContents.loadURL(normalize(query));
      else runSearch(tab, query);
      return;
    }
    if (parsed.pathname === "/engine") {
      setEngine(parsed.searchParams.get("id") || "yandex");
      showHome(tab);
      publish();
      return;
    }
    if (parsed.pathname === "/bookmarks") return showLibrary(tab, "bookmarks");
    if (parsed.pathname === "/history") return showLibrary(tab, "history");
    if (parsed.pathname === "/downloads") return showLibrary(tab, "downloads");
    if (parsed.pathname === "/settings") return showLibrary(tab, "settings");
    if (parsed.pathname === "/clear-history") {
      const data = loadLibrary();
      data.history = [];
      saveLibrary(data);
      showLibrary(tab, "history");
      return;
    }
    if (parsed.pathname === "/clear-bookmarks") {
      const data = loadLibrary();
      data.bookmarks = [];
      saveLibrary(data);
      showLibrary(tab, "bookmarks");
      return;
    }
    if (parsed.pathname === "/intro-done") {
      markIntro();
      showHome(tab);
      return;
    }
    if (parsed.pathname === "/make-default") {
      markIntro();
      openDefaultApps();
      showHome(tab);
      return;
    }
    showHome(tab);
  }

  function showIntro(tab) {
    tab.title = "Glove Browser";
    showHtml(tab, introHtml());
    publish();
  }

  function showLibrary(tab, kind) {
    const data = loadLibrary();
    tab.title = { bookmarks: "Закладки", history: "История", downloads: "Загрузки", settings: "Настройки" }[kind];
    showHtml(tab, listHtml(kind, data[kind] || []));
    publish();
  }

  function attach(tab) {
    const contents = tab.view.webContents;
    contents.setWindowOpenHandler(({ url }) => {
      createTab(url);
      return { action: "deny" };
    });
    contents.on("will-navigate", (event, url) => {
      if (url.startsWith("glove://")) {
        event.preventDefault();
        installProtocol(url, win);
        return;
      }
      if (url.startsWith("https://orion.glove")) {
        event.preventDefault();
        handleOrion(tab, url);
      }
    });
    contents.on("page-title-updated", (_event, title) => {
      if (title && !contents.getURL().startsWith("data:")) tab.title = title;
      publish();
    });
    contents.on("did-navigate", (_event, url) => {
      if (!incognito && /^https?:/i.test(url)) addHistory(contents.getTitle(), url);
      publish();
    });
    contents.on("did-navigate-in-page", publish);
    contents.on("did-start-loading", publish);
    contents.on("did-finish-load", () => {
      const url = contents.getURL();
      if (!incognito && /^https?:/i.test(url)) contents.executeJavaScript(vault.PAGE).catch(() => {});
    });
    contents.on("did-stop-loading", publish);
    contents.on("page-favicon-updated", publish);
  }

  function createTab(url) {
    const view = new BrowserView({
      webPreferences: {
        session: browserSession,
        preload: path.join(__dirname, "page-preload.js"),
        contextIsolation: true,
        nodeIntegration: false,
        sandbox: true
      }
    });
    const tab = { id: tabSeq++, title: "Новая вкладка", view };
    state.tabs.forEach((item) => win.removeBrowserView(item.view));
    state.tabs.push(tab);
    state.activeId = tab.id;
    win.addBrowserView(view);
    attach(tab);
    if (!url && !incognito && state.tabs.length === 1 && !introDone()) showIntro(tab);
    else if (!url) showHome(tab);
    else openAddress(tab, url);
    publish();
    return tab;
  }

  function selectTab(id) {
    const tab = state.tabs.find((item) => item.id === id);
    if (!tab) return;
    state.tabs.forEach((item) => win.removeBrowserView(item.view));
    state.activeId = id;
    win.addBrowserView(tab.view);
    publish();
  }

  function closeTab(id) {
    const at = state.tabs.findIndex((item) => item.id === id);
    if (at < 0) return;
    const [tab] = state.tabs.splice(at, 1);
    const pageUrl = tab.view.webContents.isDestroyed() ? "" : tab.view.webContents.getURL();
    if (!incognito && /^https?:/i.test(pageUrl)) {
      state.closed.unshift(pageUrl);
      state.closed = state.closed.slice(0, 8);
    }
    win.removeBrowserView(tab.view);
    if (!tab.view.webContents.isDestroyed()) tab.view.webContents.destroy();
    if (!state.tabs.length) {
      win.close();
      return;
    }
    if (state.activeId === id) selectTab(state.tabs[Math.max(0, at - 1)].id);
    else publish();
  }

  function showPasswords() {
    const rows = vault.listPublic();
    if (!rows.length) {
      dialog.showMessageBox(win, { message: "Пароли", detail: "Сохранённых паролей пока нет." });
      return;
    }
    const buttons = rows.map((item) => item.origin.replace(/^https?:\/\//, "") + (item.username ? " · " + item.username : "")).concat(["Закрыть"]);
    dialog.showMessageBox(win, {
      type: "none",
      buttons,
      cancelId: buttons.length - 1,
      noLink: true,
      message: "Сохранённые пароли"
    }).then(async (choice) => {
      const item = rows[choice.response];
      if (!item) return;
      const next = await dialog.showMessageBox(win, {
        type: "none",
        buttons: ["Показать", "Удалить", "Назад"],
        cancelId: 2,
        noLink: true,
        message: item.origin,
        detail: item.username || "Без имени"
      });
      if (next.response === 0) {
        dialog.showMessageBox(win, { message: item.origin, detail: vault.reveal(item.origin, item.username) });
      } else if (next.response === 1) {
        vault.forget(item.origin, item.username);
      }
    });
  }

  function popupMenu() {
    const tab = activeTab();
    const url = tab && !tab.view.webContents.isDestroyed() ? tab.view.webContents.getURL() : "";
    const pageUrl = url.startsWith("http") ? url : "";
    const bookmarked = pageUrl && loadLibrary().bookmarks.some((item) => item.url === pageUrl);
    const menu = Menu.buildFromTemplate([
      { label: "Новая вкладка", accelerator: "CmdOrCtrl+T", click: () => createTab() },
      { label: "Новое окно", click: () => createWindow(false) },
      { label: "Новое окно инкогнито", accelerator: "CmdOrCtrl+Shift+N", click: () => createWindow(true) },
      { type: "separator" },
      { label: "Закладки", click: () => tab && showLibrary(tab, "bookmarks") },
      { label: "История", enabled: !incognito, click: () => tab && showLibrary(tab, "history") },
      { label: "Загрузки", click: () => tab && showLibrary(tab, "downloads") },
      { label: "Пароли", enabled: !incognito, click: () => showPasswords() },
      { type: "separator" },
      { label: "Дублировать вкладку", enabled: !!pageUrl, click: () => createTab(pageUrl) },
      { label: "Открыть закрытую вкладку", enabled: state.closed.length > 0, click: () => createTab(state.closed.shift()) },
      { label: bookmarked ? "Удалить закладку" : "Добавить в закладки", enabled: !!pageUrl, click: () => toggleBookmark(tab, pageUrl) },
      { label: "Копировать ссылку", enabled: !!pageUrl, click: () => clipboard.writeText(pageUrl) },
      { label: "Найти на странице", accelerator: "CmdOrCtrl+F", click: () => {
        if (!win.isDestroyed()) {
          win.webContents.executeJavaScript("window.dispatchEvent(new Event('glove-find'))");
        }
      } },
      { label: "Масштаб +", accelerator: "CmdOrCtrl+numadd", click: () => zoom(0.1) },
      { label: "Масштаб −", accelerator: "CmdOrCtrl+numsub", click: () => zoom(-0.1) },
      { type: "separator" },
      {
        label: "Тёмная тема",
        submenu: [
          { label: "Как в системе", type: "radio", checked: themeMode() !== "dark", click: () => applyTheme("system") },
          { label: "Всегда включена", type: "radio", checked: themeMode() === "dark", click: () => applyTheme("dark") }
        ]
      },
      { label: "Маркет дополнений", click: () => tab && openAddress(tab, "https://orion.glove/market") },
      { label: "Настройки", click: () => tab && showLibrary(tab, "settings") },
      extensions.menuEntry(win, pageUrl),
      { label: "О программе", click: () => dialog.showMessageBox(win, { message: "Glove Browser " + APP_VERSION, detail: "Поиск: Яндекс" }) }
    ]);
    menu.popup({ window: win });
  }

  function toggleBookmark(tab, url) {
    const data = loadLibrary();
    const exists = data.bookmarks.findIndex((item) => item.url === url);
    if (exists >= 0) data.bookmarks.splice(exists, 1);
    else data.bookmarks.unshift({ title: tab.title || url, url, time: Date.now() });
    saveLibrary(data);
  }

  function zoom(delta) {
    const tab = activeTab();
    if (!tab) return;
    const next = Math.min(3, Math.max(0.3, tab.view.webContents.getZoomFactor() + delta));
    tab.view.webContents.setZoomFactor(next);
  }

  const api = { createTab, selectTab, closeTab, openAddress, showHome, popupMenu, activeTab, publish, layout, state };

  win.on("resize", layout);
  win.on("closed", () => {
    windows.delete(state);
  });

  ipcMain.on("ui-ready", (event) => {
    if (event.sender !== win.webContents || state.ready) return;
    state.ready = true;
    createTab();
    event.sender.send("update", { show: updateReady });
  });
  ipcMain.on("suggest", (event, text) => {
    if (event.sender !== win.webContents) return;
    const q = String(text || "").trim();
    if (!q) {
      event.sender.send("suggest", []);
      return;
    }
    const url = "https://suggest.yandex.ru/suggest-ff.cgi?part=" + encodeURIComponent(q) + "&uil=ru&v=4&sn=5";
    https.get(url, { headers: { "User-Agent": "GloveBrowser/1.7" } }, (response) => {
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => {
        let list = [];
        try {
          const raw = Buffer.concat(chunks).toString("utf8");
          const data = JSON.parse(raw.slice(raw.indexOf("[")));
          list = (data[1] || []).map((item) => (typeof item === "string" ? item : item[0])).filter(Boolean).slice(0, 8);
        } catch { list = []; }
        if (!event.sender.isDestroyed()) event.sender.send("suggest", list);
      });
    }).on("error", () => {
      if (!event.sender.isDestroyed()) event.sender.send("suggest", []);
    });
  });
  ipcMain.on("image-search", (event) => {
    if (event.sender !== win.webContents) return;
    const tab = activeTab();
    if (tab) handleOrion(tab, "https://orion.glove/image");
  });
  ipcMain.on("voice-start", (event) => {
    if (event.sender !== win.webContents) return;
    let spoken = "";
    voice.start((text) => {
      spoken = spoken ? spoken.replace(/[.!?…]$/, "") + " " + text : text;
      if (!event.sender.isDestroyed()) event.sender.send("voice-text", spoken);
    });
  });
  ipcMain.on("voice-stop", (event) => {
    if (event.sender !== win.webContents) return;
    voice.stop();
  });
  ipcMain.on("vault-offer", (event, origin, username, password) => {
    const fromPage = state.tabs.some((tab) => tab.view.webContents === event.sender);
    if (!fromPage || incognito || !origin || !password || vault.blocked(origin)) return;
    if (vault.find(origin).some((item) => item.username === username && item.password === password)) return;
    const host = String(origin).replace(/^https?:\/\//, "");
    dialog.showMessageBox(win, {
      type: "question",
      buttons: ["Сохранить", "Не сейчас", "Никогда"],
      defaultId: 0,
      cancelId: 1,
      noLink: true,
      message: "Сохранить пароль?",
      detail: host + (username ? "\n" + username : "")
    }).then((result) => {
      if (result.response === 0) vault.save(origin, username, password);
      if (result.response === 2) vault.never(origin);
    });
  });
  ipcMain.on("vault-fill", (event, origin) => {
    const fromPage = state.tabs.some((tab) => tab.view.webContents === event.sender);
    event.returnValue = fromPage && !incognito && !vault.blocked(origin) ? vault.find(origin) : [];
  });
  ipcMain.handle("extensions-list", (event) => {
    if (event.sender !== win.webContents) return [];
    return extensions.shelf();
  });
  ipcMain.on("extension-open", (event, id) => {
    if (event.sender !== win.webContents) return;
    try { extensions.openPopup(win, id); } catch (error) {
      dialog.showMessageBox(win, { type: "error", message: "Не удалось открыть дополнение", detail: String(error.message || error) });
    }
  });
  ipcMain.on("extension-remove", (event, id) => {
    if (event.sender !== win.webContents) return;
    extensions.removeById(id);
    event.sender.send("extensions-changed");
  });
  ipcMain.on("update-dismiss", (event) => {
    if (event.sender !== win.webContents) return;
    updateDismissed = true;
    updateReady = false;
    event.sender.send("update", { show: false });
  });
  ipcMain.on("navigate", (event, text) => {
    if (event.sender !== win.webContents) return;
    const tab = activeTab();
    if (tab) openAddress(tab, text);
  });
  ipcMain.on("back", (event) => {
    if (event.sender !== win.webContents) return;
    const tab = activeTab();
    if (tab && tab.view.webContents.canGoBack()) tab.view.webContents.goBack();
  });
  ipcMain.on("forward", (event) => {
    if (event.sender !== win.webContents) return;
    const tab = activeTab();
    if (tab && tab.view.webContents.canGoForward()) tab.view.webContents.goForward();
  });
  ipcMain.on("reload", (event) => {
    if (event.sender !== win.webContents) return;
    const tab = activeTab();
    if (!tab) return;
    const url = tab.view.webContents.getURL();
    if (url.startsWith("data:")) showHome(tab);
    else tab.view.webContents.reload();
  });
  ipcMain.on("stop", (event) => {
    if (event.sender !== win.webContents) return;
    const tab = activeTab();
    if (tab) tab.view.webContents.stop();
  });
  ipcMain.on("home", (event) => {
    if (event.sender !== win.webContents) return;
    const tab = activeTab();
    if (tab) showHome(tab);
  });
  ipcMain.on("new-tab", (event) => {
    if (event.sender !== win.webContents) return;
    createTab();
  });
  ipcMain.on("close-tab", (event, id) => {
    if (event.sender !== win.webContents) return;
    closeTab(id);
  });
  ipcMain.on("select-tab", (event, id) => {
    if (event.sender !== win.webContents) return;
    selectTab(id);
  });
  ipcMain.on("menu", (event) => {
    if (event.sender !== win.webContents) return;
    popupMenu();
  });
  ipcMain.on("toggle-picker", (event) => {
    if (event.sender !== win.webContents) return;
    state.covered = !state.covered;
    if (state.covered) {
      state.tabs.forEach((item) => win.removeBrowserView(item.view));
    } else {
      const tab = activeTab();
      if (tab) win.addBrowserView(tab.view);
    }
    publish();
  });
  ipcMain.on("choose-engine", (event, id) => {
    if (event.sender !== win.webContents) return;
    setEngine(id);
    state.covered = false;
    const tab = activeTab();
    if (tab) {
      win.addBrowserView(tab.view);
      const url = tab.view.webContents.isDestroyed() ? "" : tab.view.webContents.getURL();
      if (!url || url.startsWith("data:")) showHome(tab);
    }
    publish();
  });
  ipcMain.on("find", (event, text, again) => {
    if (event.sender !== win.webContents) return;
    const tab = activeTab();
    if (tab && text) tab.view.webContents.findInPage(text, { findNext: !!again });
  });
  ipcMain.on("stop-find", (event) => {
    if (event.sender !== win.webContents) return;
    const tab = activeTab();
    if (tab) tab.view.webContents.stopFindInPage("clearSelection");
  });
  ipcMain.on("chrome-height", (event, height) => {
    if (event.sender !== win.webContents) return;
    state.chromeHeight = Number(height) || 88;
    layout();
  });
  ipcMain.on("window", (event, action) => {
    if (event.sender !== win.webContents) return;
    if (action === "minimize") win.minimize();
    else if (action === "maximize") win.isMaximized() ? win.unmaximize() : win.maximize();
    else if (action === "close") win.close();
  });

  state.api = api;
  win.loadFile(path.join(__dirname, "index.html"), { query: { incognito: incognito ? "1" : "0" } });
  return state;
}

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on("second-instance", (_event, argv) => {
    const link = (argv || []).find((arg) => String(arg).startsWith("glove://"));
    const first = [...windows][0];
    if (link && first) installProtocol(link, first.win);
    if (first) {
      if (first.win.isMinimized()) first.win.restore();
      first.win.focus();
    }
  });
  app.whenReady().then(async () => {
    app.setAsDefaultProtocolClient("glove");
    applyTheme(themeMode());
    try { await extensions.reloadEnabled(); } catch { /* keep the window usable */ }
    createWindow(false);
    checkUpdate();
  });
}

app.on("window-all-closed", () => app.quit());
