const { app, safeStorage } = require("electron");
const fs = require("fs");
const path = require("path");

function file() {
  return path.join(app.getPath("userData"), "vault.bin");
}

function neverFile() {
  return path.join(app.getPath("userData"), "vault-never.json");
}

function readNever() {
  try {
    const data = JSON.parse(fs.readFileSync(neverFile(), "utf8"));
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

function writeNever(list) {
  fs.mkdirSync(app.getPath("userData"), { recursive: true });
  fs.writeFileSync(neverFile(), JSON.stringify(list));
}

function readAll() {
  try {
    const raw = fs.readFileSync(file());
    const text = safeStorage.isEncryptionAvailable()
      ? safeStorage.decryptString(raw)
      : raw.toString("utf8");
    const data = JSON.parse(text);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

function writeAll(list) {
  fs.mkdirSync(app.getPath("userData"), { recursive: true });
  const text = JSON.stringify(list);
  const raw = safeStorage.isEncryptionAvailable() ? safeStorage.encryptString(text) : Buffer.from(text);
  fs.writeFileSync(file(), raw);
}

function originOf(url) {
  try {
    return new URL(url).origin;
  } catch {
    return "";
  }
}

function find(origin) {
  return readAll().filter((item) => item.origin === origin);
}

function save(origin, username, password) {
  if (!origin || !password) return;
  const list = readAll().filter((item) => !(item.origin === origin && item.username === username));
  list.push({ origin, username: username || "", password });
  writeAll(list);
  const never = readNever().filter((item) => item !== origin);
  writeNever(never);
}

function forget(origin, username) {
  writeAll(readAll().filter((item) => !(item.origin === origin && item.username === username)));
}

function never(origin) {
  const list = readNever();
  if (origin && !list.includes(origin)) {
    list.push(origin);
    writeNever(list);
  }
}

function blocked(origin) {
  return readNever().includes(origin);
}

function listPublic() {
  return readAll().map((item) => ({ origin: item.origin, username: item.username }));
}

function reveal(origin, username) {
  const found = readAll().find((item) => item.origin === origin && item.username === username);
  return found ? found.password : "";
}

const PAGE = `
(function () {
  if (window.__gloveVault) return;
  window.__gloveVault = true;
  function username(form, pass) {
    var inputs = Array.prototype.slice.call(form.querySelectorAll("input"));
    var before = inputs.filter(function (input) { return input.compareDocumentPosition(pass) & 4; });
    var named = before.filter(function (input) {
      var type = (input.type || "").toLowerCase();
      return type === "email" || type === "text" || type === "tel";
    });
    return (named.length ? named[named.length - 1] : null);
  }
  function report(form) {
    var pass = form.querySelector("input[type=password]");
    if (!pass || !pass.value) return;
    var user = username(form, pass);
    if (window.gloveVault) window.gloveVault(location.origin, user ? user.value : "", pass.value);
  }
  document.addEventListener("submit", function (event) {
    if (event.target && event.target.querySelector) report(event.target);
  }, true);
  document.addEventListener("change", function (event) {
    var input = event.target;
    if (!input || (input.type || "").toLowerCase() !== "password" || !input.form) return;
    report(input.form);
  }, true);
  function fill() {
    if (!window.gloveFill) return;
    var saved = window.gloveFill(location.origin);
    if (!saved || !saved.length) return;
    var entry = saved[0];
    var pass = document.querySelector("input[type=password]");
    if (!pass || !pass.form) return;
    var user = username(pass.form, pass);
    if (user && !user.value && entry.username) user.value = entry.username;
    if (!pass.value && entry.password) pass.value = entry.password;
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", fill);
  else fill();
})();
`;

module.exports = { originOf, find, save, forget, never, blocked, listPublic, reveal, PAGE };
