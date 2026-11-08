package com.example.animetv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {

    // >>>>>>>>>>  CHANGE THIS TO YOUR SITE  <<<<<<<<<<
    private static final String START_URL = "https://hianime.ba/";

    private FrameLayout root;
    private WebView web;
    private CursorView cursor;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;

    private float cx, cy;
    private boolean cursorMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cursor = new CursorView(this);
        root.addView(cursor, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        setupWebView();

        root.post(() -> {
            cx = root.getWidth() / 2f;
            cy = root.getHeight() / 2f;
            cursor.setPos(cx, cy);
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);

        Toast.makeText(this, "D-pad = move cursor, OK = click, MENU = toggle cursor", Toast.LENGTH_LONG).show();
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(true); // lets us intercept and block popup ads
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));

        web.setFocusable(false);
        web.setFocusableInTouchMode(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // keep http/https inside the app, ignore intent:// and other schemes
                return !(url.startsWith("http://") || url.startsWith("https://"));
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                return false; // block popups
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                customCallback = callback;
                web.setVisibility(View.GONE);
                cursor.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                goImmersive();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                if (customCallback != null) customCallback.onCustomViewHidden();
                web.setVisibility(View.VISIBLE);
                cursor.setVisibility(cursorMode ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void goImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) goImmersive();
    }

    // ---------- Remote control handling ----------
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();

        if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            cursorMode = !cursorMode;
            cursor.setVisibility(cursorMode ? View.VISIBLE : View.GONE);
            Toast.makeText(this, cursorMode ? "Cursor ON" : "Cursor OFF", Toast.LENGTH_SHORT).show();
            return true;
        }

        // In fullscreen video, or cursor off: let the player/system handle keys
        if (customView != null || !cursorMode) return super.dispatchKeyEvent(e);

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (e.getAction() == KeyEvent.ACTION_DOWN) moveCursor(code, e.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) click();
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void moveCursor(int code, int repeat) {
        float step = 20f * (1f + Math.min(repeat, 12) * 0.35f);
        int w = root.getWidth(), h = root.getHeight();
        int edge = 40;

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:  cx -= step; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: cx += step; break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (cy <= edge) web.scrollBy(0, (int) -step * 3); else cy -= step;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (cy >= h - edge) web.scrollBy(0, (int) step * 3); else cy += step;
                break;
        }
        cx = Math.max(0, Math.min(w - 1, cx));
        cy = Math.max(0, Math.min(h - 1, cy));
        cursor.setPos(cx, cy);
    }

    private void click() {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cx, cy, 0);
        MotionEvent up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, cx, cy, 0);
        web.dispatchTouchEvent(down);
        web.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            web.getWebChromeClient().onHideCustomView();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }

    // ---------- Simple on-screen mouse pointer ----------
    static class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private float x, y;

        CursorView(Context c) {
            super(c);
            setFocusable(false);
            fill.setColor(Color.WHITE);
            stroke.setColor(Color.BLACK);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(3f);
            arrow.moveTo(0, 0);
            arrow.lineTo(0, 34);
            arrow.lineTo(9, 26);
            arrow.lineTo(16, 40);
            arrow.lineTo(22, 37);
            arrow.lineTo(15, 24);
            arrow.lineTo(26, 24);
            arrow.close();
        }

        void setPos(float nx, float ny) { x = nx; y = ny; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.translate(x, y);
            canvas.drawPath(arrow, fill);
            canvas.drawPath(arrow, stroke);
            canvas.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent e) { return false; }
    }
}
