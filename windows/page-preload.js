const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("gloveVault", (origin, username, password) => {
  ipcRenderer.send("vault-offer", origin, username, password);
});

contextBridge.exposeInMainWorld("gloveFill", (origin) => ipcRenderer.sendSync("vault-fill", origin));

contextBridge.exposeInMainWorld("GloveWeather", {
  load: (lat, lon) => ipcRenderer.invoke("weather-load", lat, lon)
});
