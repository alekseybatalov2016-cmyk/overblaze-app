// OverBlaze desktop app. The game runs in the app's own window (never in a browser).
// Start screen is built in; Play looks up the current game server address and opens it here.
const { app, BrowserWindow, shell, session, Menu, ipcMain, net } = require('electron');
const path = require('node:path');

const LINK_URL = 'https://overblazeplay.web.app/link.js'; // kept up to date by the game host
let win = null;
let autoPlay = process.argv.some((a) => a.startsWith('overblaze://'));

// Only one copy of the app; overblaze:// links from the website go to the running copy.
if (!app.requestSingleInstanceLock()) app.quit();
app.on('second-instance', (_e, argv) => {
  if (!win) return;
  if (win.isMinimized()) win.restore();
  win.focus();
  if (argv.some((a) => a.startsWith('overblaze://'))) play().then((r) => r.ok || showStart());
});
if (process.defaultApp) app.setAsDefaultProtocolClient('overblaze', process.execPath, [path.resolve(process.argv[1] || '.')]);
else app.setAsDefaultProtocolClient('overblaze');

async function gameUrl() {
  const r = await net.fetch(`${LINK_URL}?t=${Date.now()}`, { cache: 'no-store' });
  const m = (await r.text()).match(/"(https:\/\/[^"]+)"/);
  return m ? m[1].replace(/\/+$/, '') : '';
}
async function play() {
  try {
    const url = await gameUrl();
    if (!url) return { ok: false, reason: 'offline' };
    const ping = await net.fetch(`${url}/logo.svg?t=${Date.now()}`, { cache: 'no-store', signal: AbortSignal.timeout(8000) });
    if (!ping.ok) return { ok: false, reason: 'offline' };
    await win.loadURL(url);
    return { ok: true };
  } catch {
    return { ok: false, reason: 'network' };
  }
}
function showStart() { return win.loadFile(path.join(__dirname, 'start.html')); }

function createWindow() {
  win = new BrowserWindow({
    width: 1280, height: 800, minWidth: 800, minHeight: 500, show: false,
    title: 'OverBlaze', backgroundColor: '#030710', autoHideMenuBar: true,
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true, preload: path.join(__dirname, 'preload.js') },
  });
  Menu.setApplicationMenu(null);
  win.maximize();
  win.show();

  // Links that try to open a new window go to the normal browser; the game stays in the app.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e, url) => { if (!/^(https:|file:)/.test(url)) e.preventDefault(); });
  win.webContents.on('did-fail-load', (_e, code, _d, _u, isMain) => { if (isMain && code !== -3) showStart(); });
  win.webContents.on('render-process-gone', () => showStart());

  // F11 = fullscreen, F5 / Ctrl+R = reload, Alt+Left = back to the start screen.
  win.webContents.on('before-input-event', (e, input) => {
    if (input.type !== 'keyDown') return;
    if (input.key === 'F11') { win.setFullScreen(!win.isFullScreen()); e.preventDefault(); }
    else if (input.key === 'F5' || (input.control && input.key.toLowerCase() === 'r')) { win.webContents.reload(); e.preventDefault(); }
    else if (input.alt && input.key === 'ArrowLeft') { showStart(); e.preventDefault(); }
  });

  if (autoPlay) { autoPlay = false; play().then((r) => r.ok || showStart()); }
  else showStart();
}

ipcMain.handle('ob:play', () => play());

app.whenReady().then(() => {
  // The game only needs mouse lock, fullscreen and sound.
  session.defaultSession.setPermissionRequestHandler((_wc, perm, cb) => cb(['pointerLock', 'fullscreen'].includes(perm)));
  createWindow();
});
app.on('window-all-closed', () => app.quit());
