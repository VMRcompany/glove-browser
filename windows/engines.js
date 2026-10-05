const fs = require("fs");
const path = require("path");

const cache = new Map();

const MARKS = {
  yandex: ["#FC3F1D", "Я"],
  google: ["#4285F4", "G"],
  bing: ["#008373", "B"],
  duckduckgo: ["#DE5833", "D"],
  yahoo: ["#6001D2", "Y"],
  mail: ["#005FF9", "M"],
  rambler: ["#315EFB", "R"],
  brave: ["#FB542B", "B"],
  ecosia: ["#008009", "E"],
  startpage: ["#6573FF", "S"],
  qwant: ["#5C97FF", "Q"],
  wikipedia: ["#202124", "W"],
  baidu: ["#2932E1", "B"],
  naver: ["#03C75A", "N"],
  seznam: ["#CC0000", "S"],
  ask: ["#D32011", "A"],
  aol: ["#202124", "A"],
  kagi: ["#1A1A1A", "K"],
  you: ["#202124", "Y"],
  mojeek: ["#1A4F8B", "M"],
  swisscows: ["#E30613", "S"],
  dogpile: ["#E85D04", "D"],
  metager: ["#2E7D32", "M"],
  presearch: ["#1A56DB", "P"]
};

function fallback(id) {
  const mark = MARKS[id] || ["#234230", "?"];
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64"><rect width="64" height="64" rx="16" fill="${mark[0]}"/><text x="32" y="43" text-anchor="middle" font-size="32" font-family="Segoe UI,Arial" fill="#fff">${mark[1]}</text></svg>`;
  return "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
}

function mimeOf(buf) {
  if (buf.length >= 8 && buf[0] === 0x89 && buf[1] === 0x50) return "image/png";
  if (buf.length >= 3 && buf[0] === 0xff && buf[1] === 0xd8) return "image/jpeg";
  if (buf.length >= 4 && buf[0] === 0x00 && buf[1] === 0x00 && buf[2] === 0x01 && buf[3] === 0x00) return "image/x-icon";
  if (buf.length >= 6 && buf.slice(0, 6).toString("ascii") === "GIF89a") return "image/gif";
  if (buf.length >= 4 && buf.slice(0, 4).toString("ascii") === "RIFF") return "image/webp";
  return "image/png";
}

function icon(id) {
  if (cache.has(id)) return cache.get(id);
  const file = path.join(__dirname, "engines", id + ".png");
  try {
    if (fs.existsSync(file)) {
      const buf = fs.readFileSync(file);
      if (buf.length > 64) {
        const uri = "data:" + mimeOf(buf) + ";base64," + buf.toString("base64");
        cache.set(id, uri);
        return uri;
      }
    }
  } catch { /* keep letter mark */ }
  const uri = fallback(id);
  cache.set(id, uri);
  return uri;
}

module.exports = { icon };
