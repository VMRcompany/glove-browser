const { desktopCapturer, dialog, BrowserWindow } = require("electron");
const fs = require("fs");
const path = require("path");

const LABELS = {
  media: "камеру и микрофон",
  "display-capture": "трансляцию экрана",
  geolocation: "геолокацию",
  notifications: "уведомления",
  fileSystem: "файлы на компьютере",
  "clipboard-read": "буфер обмена",
  midi: "MIDI-устройства",
  midiSysex: "MIDI-устройства",
  "idle-detection": "сведения об активности",
  pointerLock: "управление указателем",
  fullscreen: "полный экран",
  mediaKeySystem: "защищённое видео",
  openExternal: "внешние приложения"
};

function fileFor(userData) {
  return path.join(userData, "site-permissions.json");
}

function read(userData) {
  try {
    return JSON.parse(fs.readFileSync(fileFor(userData), "utf8"));
  } catch {
    return {};
  }
}

function write(userData, data) {
  fs.mkdirSync(userData, { recursive: true });
  fs.writeFileSync(fileFor(userData), JSON.stringify(data));
}

function originOf(url) {
  try {
    return new URL(url).origin;
  } catch {
    return "";
  }
}

function remembered(userData, origin, kind) {
  const row = read(userData)[origin] || {};
  if (row[kind] === true) return true;
  if (row[kind] === false) return false;
  return null;
}

function remember(userData, origin, kind, allow) {
  if (!origin) return;
  const data = read(userData);
  data[origin] = data[origin] || {};
  data[origin][kind] = !!allow;
  write(userData, data);
}

function ask(win, origin, kind) {
  const host = String(origin || "Сайт").replace(/^https?:\/\//, "");
  const what = LABELS[kind] || "дополнительные возможности";
  return dialog.showMessageBox(win, {
    type: "question",
    buttons: ["Разрешить", "Запретить"],
    defaultId: 0,
    cancelId: 1,
    noLink: true,
    message: host + " запрашивает " + what,
    detail: "Разрешение запоминается для этого сайта."
  }).then((result) => result.response === 0);
}

async function decide(win, userData, origin, kind) {
  const known = remembered(userData, origin, kind);
  if (known !== null) return known;
  const allow = await ask(win, origin || "Сайт", kind);
  remember(userData, origin, kind, allow);
  return allow;
}

function wire(browserSession, userData) {
  browserSession.setPermissionCheckHandler((_contents, permission) => {
    return ["media", "display-capture", "geolocation", "notifications", "fullscreen", "pointerLock",
      "clipboard-sanitized-write", "clipboard-read", "fileSystem", "idle-detection", "midi", "mediaKeySystem"]
      .includes(permission);
  });
  browserSession.setPermissionRequestHandler(async (contents, permission, callback, details) => {
    const rawUrl = (details && (details.requestingUrl || details.securityOrigin)) || contents.getURL();
    const origin = originOf(rawUrl) || (String(rawUrl || "").startsWith("data:") ? "glove://home" : "");
    const win = BrowserWindow.fromWebContents(contents) || BrowserWindow.getFocusedWindow();
    if (!win) {
      callback(false);
      return;
    }
    // NTP is a data: page; still ask once under a stable home key so weather geolocation works.
    callback(await decide(win, userData, origin || "glove://home", permission));
  });
  browserSession.setDisplayMediaRequestHandler(async (_request, callback) => {
    const sources = await desktopCapturer.getSources({ types: ["screen", "window"], thumbnailSize: { width: 0, height: 0 } });
    if (!sources.length) {
      callback({});
      return;
    }
    const buttons = sources.map((source) => source.name).concat(["Отмена"]);
    const choice = await dialog.showMessageBox(BrowserWindow.getFocusedWindow() || undefined, {
      type: "question",
      buttons,
      defaultId: 0,
      cancelId: buttons.length - 1,
      noLink: true,
      message: "Что транслировать",
      detail: "Сайт получит картинку выбранного экрана или окна."
    });
    if (choice.response < 0 || choice.response >= sources.length) {
      callback({});
      return;
    }
    callback({ video: sources[choice.response] });
  });
}

module.exports = { wire, decide, originOf };
