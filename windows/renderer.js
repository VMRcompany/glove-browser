const tabsEl = document.getElementById("tabs");
const address = document.getElementById("address");
const findbar = document.getElementById("findbar");
const findQuery = document.getElementById("findQuery");
const reload = document.getElementById("reload");

const params = new URLSearchParams(location.search);
if (params.get("incognito") === "1") document.body.classList.add("incognito");

function chromeHeight() {
  const height = 88 + (findbar.hidden ? 0 : 40);
  window.glove.chromeHeight(height);
}

function render(state) {
  if (!state || !state.tabs) return;
  tabsEl.innerHTML = "";
  state.tabs.forEach((tab) => {
    const button = document.createElement("button");
    button.className = "tab" + (tab.active ? " active" : "");
    button.type = "button";
    const title = document.createElement("span");
    title.textContent = tab.title || "Новая вкладка";
    const close = document.createElement("span");
    close.className = "x";
    close.textContent = "×";
    close.addEventListener("click", (event) => {
      event.stopPropagation();
      window.glove.closeTab(tab.id);
    });
    button.append(title, close);
    button.addEventListener("click", () => window.glove.selectTab(tab.id));
    tabsEl.append(button);
  });
  document.getElementById("back").disabled = !state.canBack;
  document.getElementById("forward").disabled = !state.canForward;
  reload.textContent = state.loading ? "×" : "↻";
  reload.title = state.loading ? "Остановить" : "Обновить";
  if (!address.matches(":focus")) {
    address.value = state.home ? "" : (state.display || "");
  }
}

window.glove.onState(render);

document.getElementById("add").onclick = () => window.glove.newTab();
document.getElementById("back").onclick = () => window.glove.back();
document.getElementById("forward").onclick = () => window.glove.forward();
document.getElementById("reload").onclick = () => {
  if (reload.title === "Остановить") window.glove.stop();
  else window.glove.reload();
};
document.getElementById("home").onclick = () => window.glove.home();
document.getElementById("menu").onclick = () => window.glove.menu();
document.getElementById("min").onclick = () => window.glove.window("minimize");
document.getElementById("max").onclick = () => window.glove.window("maximize");
document.getElementById("close").onclick = () => window.glove.window("close");

document.getElementById("omnibox").addEventListener("submit", (event) => {
  event.preventDefault();
  window.glove.navigate(address.value);
  address.blur();
});

address.addEventListener("focus", () => address.select());

document.addEventListener("keydown", (event) => {
  if (event.ctrlKey && event.key.toLowerCase() === "f") {
    event.preventDefault();
    findbar.hidden = false;
    chromeHeight();
    findQuery.focus();
    findQuery.select();
  }
  if (event.ctrlKey && event.key.toLowerCase() === "t") {
    event.preventDefault();
    window.glove.newTab();
  }
  if (event.ctrlKey && event.key.toLowerCase() === "l") {
    event.preventDefault();
    address.focus();
  }
  if (event.key === "Escape" && !findbar.hidden) {
    findbar.hidden = true;
    chromeHeight();
    window.glove.stopFind();
  }
});

findQuery.addEventListener("input", () => window.glove.find(findQuery.value, false));
document.getElementById("findNext").onclick = () => window.glove.find(findQuery.value, true);
document.getElementById("findClose").onclick = () => {
  findbar.hidden = true;
  chromeHeight();
  window.glove.stopFind();
};

window.addEventListener("glove-find", () => {
  findbar.hidden = false;
  chromeHeight();
  findQuery.focus();
  findQuery.select();
});

window.glove.ready();
chromeHeight();
