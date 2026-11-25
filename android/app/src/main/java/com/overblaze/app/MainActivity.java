package com.overblaze.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OverBlaze for phones and tablets. The game runs full screen inside the app
 * (it has its own touch controls). Play looks up the current game server
 * address on the website and opens it here.
 */
public class MainActivity extends Activity {
    private static final String LINK_URL = "https://overblazeplay.web.app/link.js";
    private static final String START = "file:///android_asset/start.html";
    private WebView web;
    private long lastBack;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(0xFF030710);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        CookieManager.getInstance().setAcceptCookie(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                String u = req.getUrl().toString();
                return !(u.startsWith("https://") || u.startsWith("file:"));
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame() && !req.getUrl().toString().startsWith("file:")) v.loadUrl(START);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            // links the game opens in a new window go to the phone's browser; the game stays here
            @Override
            public boolean onCreateWindow(WebView v, boolean dialog, boolean user, Message msg) {
                WebView tmp = new WebView(MainActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView t, WebResourceRequest req) {
                        openExternal(req.getUrl());
                        t.destroy();
                        return true;
                    }
                });
                ((WebView.WebViewTransport) msg.obj).setWebView(tmp);
                msg.sendToTarget();
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "OverBlazeAndroid");
        setContentView(web);
        hideBars();

        if (isPlayIntent(getIntent())) play();
        else web.loadUrl(START);
    }

    private static boolean isPlayIntent(Intent i) {
        return i != null && i.getData() != null && "overblaze".equals(i.getData().getScheme());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (isPlayIntent(i)) play();
    }

    private void openExternal(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
    }

    // ---------- Play: find the game server, check it answers, open it ----------
    private class Bridge {
        @JavascriptInterface
        public void play() { MainActivity.this.play(); }
    }

    private void play() {
        new Thread(() -> {
            String reason;
            try {
                String js = get(LINK_URL + "?t=" + System.currentTimeMillis(), 15000);
                Matcher m = Pattern.compile("\"(https://[^\"]+)\"").matcher(js);
                if (m.find()) {
                    String url = m.group(1).replaceAll("/+$", "");
                    get(url + "/logo.svg?t=" + System.currentTimeMillis(), 8000);
                    runOnUiThread(() -> web.loadUrl(url));
                    return;
                }
                reason = "offline";
            } catch (java.net.UnknownHostException e) {
                reason = "network";
            } catch (java.io.IOException e) {
                reason = "offline";
            } catch (Exception e) {
                reason = "network";
            }
            final String r = reason;
            runOnUiThread(() -> {
                if (!START.equals(web.getUrl())) web.loadUrl(START);
                else web.evaluateJavascript("window.__obResult&&__obResult({ok:false,reason:'" + r + "'})", null);
            });
        }).start();
    }

    private static String get(String url, int timeout) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }

    // ---------- full screen, back button ----------
    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideBars();
    }

    @SuppressWarnings("deprecation")
    private void hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    // In the game: press Back twice to return to the start screen. On the start screen: leave the app.
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (START.equals(web.getUrl())) { super.onBackPressed(); return; }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) { web.loadUrl(START); return; }
        lastBack = now;
        Toast.makeText(this, "Press Back again to leave the game", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() { super.onPause(); web.onPause(); }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); hideBars(); }
}
