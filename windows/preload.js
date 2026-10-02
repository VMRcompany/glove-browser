const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("glove", {
  ready: () => ipcRenderer.send("ui-ready"),
  navigate: (text) => ipcRenderer.send("navigate", text),
  back: () => ipcRenderer.send("back"),
  forward: () => ipcRenderer.send("forward"),
  reload: () => ipcRenderer.send("reload"),
  stop: () => ipcRenderer.send("stop"),
  home: () => ipcRenderer.send("home"),
  newTab: () => ipcRenderer.send("new-tab"),
  closeTab: (id) => ipcRenderer.send("close-tab", id),
  selectTab: (id) => ipcRenderer.send("select-tab", id),
  menu: () => ipcRenderer.send("menu"),
  find: (text, again) => ipcRenderer.send("find", text, !!again),
  stopFind: () => ipcRenderer.send("stop-find"),
  chromeHeight: (height) => ipcRenderer.send("chrome-height", height),
  window: (action) => ipcRenderer.send("window", action),
  onState: (callback) => ipcRenderer.on("state", (_event, state) => callback(state))
});
