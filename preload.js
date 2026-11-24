const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('overblaze', { play: () => ipcRenderer.invoke('ob:play') });
