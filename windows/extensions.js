const { app, session, dialog, BrowserWindow } = require("electron");
const { execFileSync } = require("child_process");
const fs = require("fs");
const https = require("https");
const os = require("os");
const path = require("path");
const crypto = require("crypto");

const PARTITION = "persist:glove";
const pendingFiles = new Set();

function gloveSession() {
  return session.fromPartition(PARTITION);
}

function storeFile() {
  return path.join(app.getPath("userData"), "extensions.json");
}

function extensionsRoot() {
  return path.join(app.getPath("userData"), "extensions");
}

function loadStore() {
  try {
    const data = JSON.parse(fs.readFileSync(storeFile(), "utf8"));
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

function saveStore(list) {
  fs.mkdirSync(path.dirname(storeFile()), { recursive: true });
  fs.writeFileSync(storeFile(), JSON.stringify(list, null, 2));
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8").replace(/^\uFEFF/, ""));
}

function manifestName(manifest, dir) {
  const raw = manifest.name || "Дополнение";
  const match = /^__MSG_([A-Za-z0-9_@]+)__$/.exec(raw);
  if (!match || !manifest.default_locale) return raw;
  try {
    const messages = readJson(path.join(dir, "_locales", manifest.default_locale, "messages.json"));
    return messages[match[1]]?.message || raw;
  } catch {
    return raw;
  }
}

function readManifest(dir) {
  const manifest = readJson(path.join(dir, "manifest.json"));
  if (manifest.manifest_version !== 2 && manifest.manifest_version !== 3) {
    throw new Error("Нужен manifest версии 2 или 3");
  }
  return manifest;
}

function findManifestDir(dir) {
  if (fs.existsSync(path.join(dir, "manifest.json"))) return dir;
  const kids = fs.readdirSync(dir, { withFileTypes: true }).filter((entry) => entry.isDirectory());
  for (const kid of kids) {
    const nested = path.join(dir, kid.name);
    if (fs.existsSync(path.join(nested, "manifest.json"))) return nested;
  }
  throw new Error("В выбранном месте нет manifest.json");
}

function copyDir(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    const from = path.join(src, entry.name);
    const to = path.join(dest, entry.name);
    if (entry.isDirectory()) copyDir(from, to);
    else if (entry.isFile()) fs.copyFileSync(from, to);
  }
}

function readVarint(buf, offset) {
  let value = 0n;
  let shift = 0n;
  let pos = offset;
  while (pos < buf.length && shift < 70n) {
    const byte = buf[pos++];
    value |= BigInt(byte & 0x7f) << shift;
    if ((byte & 0x80) === 0) return { value, offset: pos };
    shift += 7n;
  }
  throw new Error("Повреждённый заголовок .crx");
}

function protoFields(buf) {
  const fields = [];
  let offset = 0;
  while (offset < buf.length) {
    const tag = readVarint(buf, offset);
    offset = tag.offset;
    const field = Number(tag.value >> 3n);
    const wire = Number(tag.value & 7n);
    if (wire === 2) {
      const len = readVarint(buf, offset);
      offset = len.offset;
      const size = Number(len.value);
      if (!Number.isFinite(size) || size < 0 || offset + size > buf.length) break;
      fields.push({ field, data: buf.slice(offset, offset + size) });
      offset += size;
    } else if (wire === 0) {
      offset = readVarint(buf, offset).offset;
    } else if (wire === 5) offset += 4;
    else if (wire === 1) offset += 8;
    else break;
  }
  return fields;
}

function firstPublicKey(header) {
  const proof = protoFields(header).find((item) => item.field === 2 || item.field === 3);
  if (!proof) return null;
  const key = protoFields(proof.data).find((item) => item.field === 1);
  return key ? key.data : null;
}

function crxPayload(buf) {
  const zipAt = buf.indexOf(Buffer.from([0x50, 0x4b, 0x03, 0x04]));
  if (zipAt < 0) throw new Error("Это не файл .crx");
  let key = null;
  if (buf.slice(0, 4).toString("ascii") === "Cr24" && buf.length >= 16) {
    const version = buf.readUInt32LE(4);
    if (version === 2) {
      const keyLen = buf.readUInt32LE(8);
      if (keyLen > 0 && 16 + keyLen <= buf.length) key = buf.slice(16, 16 + keyLen);
    } else if (version === 3) {
      const headerSize = buf.readUInt32LE(8);
      if (headerSize > 0 && 12 + headerSize <= buf.length) key = firstPublicKey(buf.slice(12, 12 + headerSize));
    }
  }
  return { zip: buf.slice(zipAt), key };
}

function unzipJs(buffer, dest) {
  const root = path.resolve(dest);
  let offset = 0;
  while (offset + 30 <= buffer.length) {
    if (buffer.readUInt32LE(offset) !== 0x04034b50) break;
    const flags = buffer.readUInt16LE(offset + 6);
    const method = buffer.readUInt16LE(offset + 8);
    const compSize = buffer.readUInt32LE(offset + 18);
    const nameLen = buffer.readUInt16LE(offset + 26);
    const extraLen = buffer.readUInt16LE(offset + 28);
    if ((flags & 8) !== 0 && compSize === 0) throw new Error("Архив с описателем данных");
    const nameStart = offset + 30;
    const name = buffer.slice(nameStart, nameStart + nameLen).toString("utf8").replace(/\\/g, "/");
    const dataStart = nameStart + nameLen + extraLen;
    const data = buffer.slice(dataStart, dataStart + compSize);
    offset = dataStart + compSize;
    if (!name || name.endsWith("/")) continue;
    const outPath = path.resolve(dest, name);
    if (outPath !== root && !outPath.startsWith(root + path.sep)) continue;
    fs.mkdirSync(path.dirname(outPath), { recursive: true });
    const content = method === 0 ? data : method === 8 ? require("zlib").inflateRawSync(data) : null;
    if (!content) throw new Error("Неподдерживаемое сжатие архива");
    fs.writeFileSync(outPath, content);
  }
}

function extractZip(buffer, dest) {
  fs.mkdirSync(dest, { recursive: true });
  const zipPath = path.join(os.tmpdir(), "glove-" + Date.now() + "-" + crypto.randomBytes(4).toString("hex") + ".zip");
  fs.writeFileSync(zipPath, buffer);
  try {
    const listing = execFileSync("tar", ["-tf", zipPath], { windowsHide: true, encoding: "utf8" });
    const names = listing.split(/\r?\n/).filter(Boolean);
    if (names.some((name) => name.replace(/\\/g, "/").split("/").includes("..") || path.win32.isAbsolute(name))) {
      throw new Error("Архив содержит небезопасный путь");
    }
    execFileSync("tar", ["-xf", zipPath, "-C", dest], { windowsHide: true });
  } catch (error) {
    if (String(error && error.message || error).includes("небезопасный путь")) throw error;
    unzipJs(buffer, dest);
  } finally {
    fs.rmSync(zipPath, { force: true });
  }
}

function injectKey(dir, key) {
  if (!key || !key.length) return;
  const file = path.join(dir, "manifest.json");
  const manifest = readJson(file);
  if (!manifest.key) {
    manifest.key = key.toString("base64");
    fs.writeFileSync(file, JSON.stringify(manifest));
  }
}

async function loadOne(dir) {
  const manifest = readManifest(dir);
  const loaded = await gloveSession().loadExtension(dir, { allowFileAccess: true });
  const permissions = [].concat(manifest.permissions || [], manifest.optional_permissions || []);
  if (permissions.includes("proxy")) {
    loaded.proxy = true;
  }
  return loaded;
}

function postText(url, body) {
  return new Promise((resolve, reject) => {
    const target = new URL(url);
    const request = https.request({
      hostname: target.hostname,
      path: target.pathname + target.search,
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "Content-Length": Buffer.byteLength(body),
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.215 Safari/537.36"
      }
    }, (response) => {
      if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
        response.resume();
        downloadToBuffer(new URL(response.headers.location, url).href).then((buf) => resolve(buf.toString("utf8")), reject);
        return;
      }
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
      response.on("error", reject);
    });
    request.on("error", reject);
    request.write(body);
    request.end();
  });
}

function parseStoreHtml(html, limit) {
  const items = [];
  const seen = new Set();
  const linkRe = /\/detail\/([^"'/]+)\/([a-p]{32})/g;
  let match;
  while ((match = linkRe.exec(html)) && items.length < limit) {
    if (seen.has(match[2])) continue;
    seen.add(match[2]);
    const around = html.slice(Math.max(0, match.index - 1200), match.index + 500);
    const img = around.match(/https:\/\/lh3\.googleusercontent\.com\/[^"'\\\s<>]+/);
    const titled = around.match(/"title"\s*:\s*"((?:\\.|[^"\\])*)"/);
    let name = titled
      ? titled[1].replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16))).replace(/\\"/g, '"')
      : decodeURIComponent(match[1]).replace(/-/g, " ");
    items.push({ id: match[2], name, icon: img ? img[0].replace(/\\u0026/g, "&") : "" });
  }
  return items;
}

async function enrichItem(item) {
  if (item.icon && item.name && !/^[A-Z][a-z]+(\s[A-Z][a-z]+)+$/.test(item.name) && item.name.length > 2) return item;
  try {
    const html = (await downloadToBuffer("https://chromewebstore.google.com/detail/" + item.id + "?hl=ru")).toString("utf8");
    const og = html.match(/property="og:image"\s+content="([^"]+)"/) || html.match(/https:\/\/lh3\.googleusercontent\.com\/[^"'\\\s<>]+/);
    const title = html.match(/<title>([^<]+)/);
    const name = title ? title[1].replace(/\s+[-–—].*$/, "").trim() : item.name;
    return { id: item.id, name: name || item.name, icon: item.icon || (og ? (og[1] || og[0]) : "") };
  } catch {
    return item;
  }
}

async function searchStore(query) {
  const q = String(query || "").trim();
  if (!q) return [];
  try {
    const html = (await downloadToBuffer("https://chromewebstore.google.com/search/" + encodeURIComponent(q) + "?hl=ru")).toString("utf8");
    const items = parseStoreHtml(html, 24);
    const batch = [];
    for (let i = 0; i < items.length; i += 6) batch.push(items.slice(i, i + 6));
    const ready = [];
    for (const group of batch) ready.push(...await Promise.all(group.map(enrichItem)));
    return ready;
  } catch {
    return [];
  }
}

function iconData(file) {
  const ext = path.extname(file).toLowerCase();
  const mime = { ".svg": "image/svg+xml", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".webp": "image/webp", ".gif": "image/gif" }[ext] || "image/png";
  return "data:" + mime + ";base64," + fs.readFileSync(file).toString("base64");
}

function iconFile(dir, manifest) {
  const sources = [];
  const pushIcon = (icon) => {
    if (!icon) return;
    if (typeof icon === "string") sources.push(icon);
    else if (typeof icon === "object") {
      const size = Object.keys(icon).map(Number).filter((n) => !Number.isNaN(n)).sort((a, b) => b - a)[0];
      if (size) sources.push(icon[String(size)] || icon[size]);
    }
  };
  pushIcon(manifest.icons);
  pushIcon((manifest.action || {}).default_icon);
  pushIcon((manifest.browser_action || {}).default_icon);
  for (const rel of sources) {
    const file = path.resolve(dir, String(rel).replace(/^\//, ""));
    if (file.startsWith(path.resolve(dir)) && fs.existsSync(file)) return file;
  }
  return "";
}

function popupPath(manifest) {
  const action = manifest.action || manifest.browser_action || {};
  return action.default_popup ? String(action.default_popup).replace(/^\//, "") : "";
}

function hostLabel(manifest) {
  const perms = [].concat(manifest.host_permissions || [], manifest.permissions || []);
  if (perms.some((item) => item === "<all_urls>" || item === "*://*/*")) return "Разрешено на всех сайтах";
  const host = perms.find((item) => typeof item === "string" && item.includes("://"));
  return host ? "Сайты: " + host : "Работает в браузере";
}

function shelf() {
  return loadStore().filter((item) => item.enabled && item.path).map((item) => {
    let icon = "";
    let popup = "";
    let hosts = "Работает в браузере";
    try {
      const manifest = readManifest(item.path);
      const file = iconFile(item.path, manifest);
      if (file) icon = iconData(file);
      popup = popupPath(manifest);
      hosts = hostLabel(manifest);
    } catch { /* keep the row usable */ }
    return { id: item.id, extensionId: item.extensionId, name: item.name, icon, popup, hosts };
  });
}

function openPopup(parent, id) {
  const row = loadStore().find((item) => item.id === id);
  if (!row || !row.extensionId) return;
  const manifest = readManifest(row.path);
  const popup = popupPath(manifest);
  if (!popup) {
    dialog.showMessageBox(parent, { message: row.name, detail: "У этого дополнения нет своего окна. Оно работает прямо на страницах." });
    return;
  }
  const popupWin = new BrowserWindow({
    parent: parent || undefined,
    width: 400,
    height: 560,
    title: row.name,
    autoHideMenuBar: true,
    webPreferences: {
      session: gloveSession(),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  popupWin.loadURL("chrome-extension://" + row.extensionId + "/" + popup);
}

function remember(loaded, dest, manifest) {
  const list = loadStore();
  const previous = list.find((item) => item.extensionId === loaded.id);
  if (previous && previous.path && previous.path !== dest && previous.path.startsWith(extensionsRoot())) {
    fs.rmSync(previous.path, { recursive: true, force: true });
  }
  const next = list.filter((item) => item.extensionId !== loaded.id && item.path !== dest);
  const row = {
    id: previous?.id || path.basename(dest),
    extensionId: loaded.id,
    name: loaded.name || manifestName(manifest, dest),
    path: dest,
    enabled: true,
    version: loaded.version || manifest.version || ""
  };
  next.push(row);
  saveStore(next);
  return row;
}

async function installDirectory(source, key) {
  const manifestDir = findManifestDir(source);
  const manifest = readManifest(manifestDir);
  const dest = path.join(extensionsRoot(), crypto.randomBytes(8).toString("hex"));
  copyDir(manifestDir, dest);
  if (key) injectKey(dest, key);
  try {
    const loaded = await loadOne(dest);
    return remember(loaded, dest, readManifest(dest));
  } catch (error) {
    fs.rmSync(dest, { recursive: true, force: true });
    throw error;
  }
}

async function installCrxFile(file) {
  const { zip, key } = crxPayload(fs.readFileSync(file));
  const staging = path.join(os.tmpdir(), "glove-crx-" + Date.now() + "-" + crypto.randomBytes(3).toString("hex"));
  extractZip(zip, staging);
  try {
    return await installDirectory(staging, key);
  } finally {
    fs.rmSync(staging, { recursive: true, force: true });
  }
}

async function installFromDialog(win, kind) {
  const result = await dialog.showOpenDialog(win, {
    title: kind === "dir" ? "Папка дополнения" : "Файл дополнения",
    properties: kind === "dir" ? ["openDirectory"] : ["openFile"],
    filters: kind === "dir" ? undefined : [{ name: "Дополнение", extensions: ["crx"] }]
  });
  if (result.canceled || !result.filePaths[0]) return null;
  if (kind === "dir") return installDirectory(result.filePaths[0], null);
  return installCrxFile(result.filePaths[0]);
}

async function reloadEnabled() {
  const list = loadStore();
  let changed = false;
  for (const item of list) {
    if (!item.enabled || !item.path || !fs.existsSync(path.join(item.path, "manifest.json"))) continue;
    try {
      const loaded = await loadOne(item.path);
      if (item.extensionId !== loaded.id || item.name !== loaded.name) changed = true;
      item.extensionId = loaded.id;
      item.name = loaded.name || item.name;
      item.error = "";
    } catch (error) {
      item.enabled = false;
      item.error = String(error && error.message || error);
      changed = true;
    }
  }
  if (changed) saveStore(list);
}

async function setEnabled(item, enabled) {
  const list = loadStore();
  const found = list.find((row) => row.id === item.id);
  if (!found) return;
  if (enabled) {
    const loaded = await loadOne(found.path);
    found.extensionId = loaded.id;
    found.name = loaded.name || found.name;
    found.enabled = true;
    found.error = "";
  } else if (found.extensionId) {
    try { gloveSession().removeExtension(found.extensionId); } catch { /* already gone */ }
    found.enabled = false;
  } else {
    found.enabled = false;
  }
  saveStore(list);
}

function removeById(id) {
  const item = loadStore().find((row) => row.id === id);
  if (item) removeStored(item);
}

function removeStored(item) {
  if (item.extensionId) {
    try { gloveSession().removeExtension(item.extensionId); } catch { /* already gone */ }
  }
  if (item.path && path.resolve(item.path).startsWith(path.resolve(extensionsRoot()) + path.sep)) {
    fs.rmSync(item.path, { recursive: true, force: true });
  }
  saveStore(loadStore().filter((row) => row.id !== item.id));
}

function report(win, message, detail, error) {
  if (!win || win.isDestroyed()) return;
  dialog.showMessageBox(win, {
    type: error ? "error" : "info",
    message,
    detail: detail ? String(detail) : ""
  });
}

function menuTemplate(win) {
  const list = loadStore();
  const rows = list.length
    ? list.map((item) => ({
        label: item.enabled ? item.name : item.name + " (выкл.)",
        submenu: [
          {
            label: item.enabled ? "Выключить" : "Включить",
            click: () => {
              setEnabled(item, !item.enabled).catch((error) => {
                report(win, "Не удалось изменить дополнение", error.message || error, true);
              });
            }
          },
          { label: "Удалить", click: () => removeStored(item) }
        ]
      }))
    : [{ label: "Нет установленных дополнений", enabled: false }];
  return [
    {
      label: "Установить из папки…",
      click: () => {
        installFromDialog(win, "dir")
          .then((item) => { if (item) report(win, "Дополнение установлено", item.name); })
          .catch((error) => report(win, "Не удалось установить", error.message || error, true));
      }
    },
    {
      label: "Установить файл .crx…",
      click: () => {
        installFromDialog(win, "crx")
          .then((item) => { if (item) report(win, "Дополнение установлено", item.name); })
          .catch((error) => report(win, "Не удалось установить", error.message || error, true));
      }
    },
    { type: "separator" },
    ...rows
  ];
}

function storeExtensionId(url) {
  try {
    const parsed = new URL(url);
    if (parsed.hostname !== "chromewebstore.google.com" && parsed.hostname !== "chrome.google.com") return null;
    return parsed.pathname.split("/").find((part) => /^[a-p]{32}$/.test(part)) || null;
  } catch {
    return null;
  }
}

function downloadToBuffer(url, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 5) {
      reject(new Error("Слишком много переадресаций"));
      return;
    }
    const request = https.get(url, {
      headers: { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.215 Safari/537.36" }
    }, (response) => {
      if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
        response.resume();
        downloadToBuffer(new URL(response.headers.location, url).href, redirects + 1).then(resolve, reject);
        return;
      }
      if (response.statusCode !== 200) {
        response.resume();
        reject(new Error("Сервер ответил " + response.statusCode));
        return;
      }
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => resolve(Buffer.concat(chunks)));
      response.on("error", reject);
    });
    request.on("error", reject);
  });
}

async function installFromStore(pageUrl) {
  const id = storeExtensionId(pageUrl);
  if (!id) throw new Error("Откройте страницу конкретного дополнения");
  const url = "https://clients2.google.com/service/update2/crx?response=redirect&os=win&arch=x64&os_arch=x86_64&nacl_arch=x86-64&prod=chromiumcrx&prodchannel=&prodversion=108.0.5359.215&acceptformat=crx2,crx3&x=id%3D" + id + "%26uc";
  const buf = await downloadToBuffer(url);
  if (buf.length < 16 || buf.slice(0, 4).toString("ascii") !== "Cr24") {
    throw new Error("Магазин не отдал файл дополнения. Кнопка на странице здесь не срабатывает — сохраните .crx и установите его из меню.");
  }
  const file = path.join(os.tmpdir(), id + ".crx");
  fs.writeFileSync(file, buf);
  try {
    return await installCrxFile(file);
  } finally {
    fs.rmSync(file, { force: true });
  }
}

function looksLikeCrx(file) {
  try {
    const fd = fs.openSync(file, "r");
    const buf = Buffer.alloc(4);
    fs.readSync(fd, buf, 0, 4, 0);
    fs.closeSync(fd);
    return buf.toString("ascii") === "Cr24" || buf.readUInt32LE(0) === 0x04034b50;
  } catch {
    return false;
  }
}

function isCrxDownload(filename, mime, url) {
  return /\.crx$/i.test(filename || "")
    || mime === "application/x-chrome-extension"
    || /update2\/crx/i.test(url || "");
}

async function installDownloaded(win, file) {
  const key = path.resolve(file);
  if (pendingFiles.has(key) || !looksLikeCrx(file)) return;
  pendingFiles.add(key);
  try {
    const item = await installCrxFile(file);
    report(win, "Дополнение установлено", item.name);
  } catch (error) {
    report(win, "Не удалось установить дополнение", error.message || error, true);
  } finally {
    pendingFiles.delete(key);
  }
}

function menuEntry(win, pageUrl) {
  return {
    label: "Дополнения",
    submenu: [
      ...menuTemplate(win),
      { type: "separator" },
      {
        label: "Установить со страницы магазина",
        enabled: !!storeExtensionId(pageUrl),
        click: () => {
          installFromStore(pageUrl)
            .then((item) => dialog.showMessageBox(win, { message: "Дополнение установлено", detail: item.name }))
            .catch((error) => dialog.showMessageBox(win, { type: "error", message: "Не удалось скачать дополнение", detail: String(error.message || error) }));
        }
      }
    ]
  };
}

module.exports = {
  reloadEnabled,
  menuTemplate,
  menuEntry,
  installFromStore,
  storeExtensionId,
  isCrxDownload,
  installDownloaded,
  searchStore,
  searchImage,
  shelf,
  openPopup,
  removeStored,
  removeById
};

function searchImage(file) {
  return new Promise((resolve) => {
    const boundary = "----GloveBoundary" + Date.now();
    const bytes = fs.readFileSync(file);
    const head = Buffer.from("--" + boundary + "\r\nContent-Disposition: form-data; name=\"upfile\"; filename=\"photo.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n");
    const tail = Buffer.from("\r\n--" + boundary + "--\r\n");
    const body = Buffer.concat([head, bytes, tail]);
    const request = https.request({
      hostname: "yandex.ru",
      path: "/images/search?rpt=imageview&format=json",
      method: "POST",
      headers: {
        "Content-Type": "multipart/form-data; boundary=" + boundary,
        "Content-Length": body.length,
        "User-Agent": "Mozilla/5.0"
      }
    }, (response) => {
      const location = response.headers.location;
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => {
        if (location) {
          resolve(location.startsWith("http") ? location : "https://yandex.ru" + location);
          return;
        }
        const text = Buffer.concat(chunks).toString("utf8");
        const found = text.match(/https:\\?\/\\?\/yandex\.ru\\?\/images\\?\/search\?[^"\s\\]+/) || text.match(/https:\/\/yandex\.ru\/images\/search\?[^"\s]+/);
        resolve(found ? found[0].replace(/\\\//g, "/").replace(/\\u0026/g, "&") : "https://yandex.ru/images/");
      });
    });
    request.on("error", () => resolve("https://yandex.ru/images/"));
    request.write(body);
    request.end();
  });
}
