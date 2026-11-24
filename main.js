// OverBlaze desktop app: opens the OverBlaze website in its own window.
// The website's Play button leads to the game, so the app is always up to date.
const { app, BrowserWindow, shell, session, Menu } = require('electron');
const path = require('node:path');

const HOME = 'https://overblazeplay.web.app/';

function createWindow() {
  const win = new BrowserWindow({
    width: 1280, height: 800, minWidth: 800, minHeight: 500,
    title: 'OverBlaze', backgroundColor: '#030710', autoHideMenuBar: true,
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  Menu.setApplicationMenu(null);

  // Popups / new tabs open in the normal browser; the game itself stays in the app.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e, url) => { if (!/^https:\/\//.test(url)) e.preventDefault(); });

  // No internet / site unreachable: show a small offline page with a Retry button.
  win.webContents.on('did-fail-load', (_e, code, _desc, url, isMain) => {
    if (!isMain || code === -3) return; // -3 = aborted (normal during redirects)
    win.loadFile(path.join(__dirname, 'offline.html'), { query: { u: url || HOME } });
  });

  // F11 = fullscreen, Ctrl+R / F5 = reload, Alt+Left = back to the start page.
  win.webContents.on('before-input-event', (e, input) => {
    if (input.type !== 'keyDown') return;
    if (input.key === 'F11') { win.setFullScreen(!win.isFullScreen()); e.preventDefault(); }
    else if (input.key === 'F5' || (input.control && input.key.toLowerCase() === 'r')) { win.webContents.reload(); e.preventDefault(); }
    else if (input.alt && input.key === 'ArrowLeft') { win.loadURL(HOME); e.preventDefault(); }
  });

  win.loadURL(HOME);
}

app.whenReady().then(() => {
  // The game only needs mouse lock, fullscreen and sound.
  session.defaultSession.setPermissionRequestHandler((_wc, perm, cb) => cb(['pointerLock', 'fullscreen'].includes(perm)));
  createWindow();
});
app.on('window-all-closed', () => app.quit());
