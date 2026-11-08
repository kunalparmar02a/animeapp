package com.example.animetv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.util.ArrayList;

public class MainActivity extends Activity {

    // Default site. You can also change it inside the app: hold OK > Change site.
    private static final String DEFAULT_URL = "https://hianime.ba/";

    private static final int REQ_VOICE = 101;
    private static final int DIALOG_THEME = android.R.style.Theme_Material_Dialog_Alert;

    private static final String[] BLOCKED_HOSTS = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
            "adsterra.com", "juicyads.com", "trafficjunky.com", "clickadu.com",
            "hilltopads.net", "onclickads.net"
    };

    private static final String ERROR_HTML =
            "<html><body style='background:#111;color:#eee;font-family:sans-serif;padding:60px;font-size:28px'>"
                    + "<h1>Can't open the site</h1>"
                    + "<p>Check the internet connection, or the site may be down.</p>"
                    + "<p>Hold the OK button (or press MENU) and choose <b>Change site</b> or <b>Reload</b>.</p>"
                    + "</body></html>";

    // Opens the TV keyboard dialog whenever a text box on the page gets focus
    private static final String FOCUS_JS =
            "(function(){ if(window.__tvHook) return; window.__tvHook=true;"
                    + "document.addEventListener('focusin',function(e){"
                    + " var t=e.target; if(!t||!t.tagName) return;"
                    + " if(t.tagName==='INPUT'||t.tagName==='TEXTAREA'){"
                    + "  var ty=(t.type||'text').toLowerCase();"
                    + "  if(['text','search','email','url','tel',''].indexOf(ty)>=0 && window.TVBridge){"
                    + "   window.TVBridge.onTextField(t.value||''); }"
                    + " } },true); })();";

    private FrameLayout root;
    private WebView web;
    private CursorView cursor;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private AlertDialog inputDialog;
    private SharedPreferences prefs;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideCursorTask = new Runnable() {
        @Override public void run() {
            if (cursorMode && customView == null) cursor.setVisibility(View.INVISIBLE);
        }
    };

    private float cx, cy;
    private boolean cursorMode = true;
    private boolean pressHandled = false;
    private long lastBack = 0;
    private int zoom = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = getSharedPreferences("animetv", MODE_PRIVATE);
        zoom = prefs.getInt("zoom", 100);

        root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cursor = new CursorView(this);
        root.addView(cursor, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        setupWebView();

        root.post(new Runnable() {
            @Override public void run() {
                cx = root.getWidth() / 2f;
                cy = root.getHeight() / 2f;
                cursor.setPos(cx, cy);
                showCursor();
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(homeUrl());

        Toast.makeText(this, "Hold OK or press MENU for the menu (search, voice, change site)",
                Toast.LENGTH_LONG).show();
    }

    private String homeUrl() {
        return prefs.getString("home_url", DEFAULT_URL);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setTextZoom(zoom);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));

        web.setFocusable(false);
        web.setFocusableInTouchMode(false);
        web.addJavascriptInterface(new TVBridge(), "TVBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return !(url.startsWith("http://") || url.startsWith("https://"));
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (host != null) {
                    for (String b : BLOCKED_HOSTS) {
                        if (host.contains(b)) {
                            return new WebResourceResponse("text/plain", "utf-8",
                                    new ByteArrayInputStream(new byte[0]));
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(FOCUS_JS, null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    view.loadDataWithBaseURL(null, ERROR_HTML, "text/html", "UTF-8", null);
                }
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
                hideCustomView();
            }
        });
    }

    /** Called from JavaScript when a text box on the page is focused. */
    public class TVBridge {
        @JavascriptInterface
        public void onTextField(final String value) {
            runOnUiThread(new Runnable() {
                @Override public void run() { showSearchDialog(value); }
            });
        }
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        if (customCallback != null) customCallback.onCustomViewHidden();
        customCallback = null;
        web.setVisibility(View.VISIBLE);
        if (cursorMode) showCursor(); else cursor.setVisibility(View.GONE);
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

    // ---------- Cursor visibility (auto-hides after 5 seconds) ----------
    private void showCursor() {
        if (!cursorMode || customView != null) return;
        cursor.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideCursorTask);
        handler.postDelayed(hideCursorTask, 5000);
    }

    private void toggleCursor() {
        cursorMode = !cursorMode;
        if (cursorMode) {
            web.setFocusable(false);
            web.setFocusableInTouchMode(false);
            showCursor();
        } else {
            cursor.setVisibility(View.GONE);
            web.setFocusable(true);
            web.setFocusableInTouchMode(true);
            web.requestFocus();
        }
        Toast.makeText(this, cursorMode ? "Cursor ON" : "Cursor OFF (normal D-pad navigation)",
                Toast.LENGTH_SHORT).show();
    }

    // ---------- Remote control ----------
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        int action = e.getAction();

        if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS) {
            if (action == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && customView == null) showMenu();
            return true;
        }
        if (code == KeyEvent.KEYCODE_SEARCH) {
            if (action == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) showSearchDialog("");
            return true;
        }

        // In fullscreen video, or cursor off: let the player/system handle keys
        if (customView != null || !cursorMode) return super.dispatchKeyEvent(e);

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (action == KeyEvent.ACTION_DOWN) moveCursor(code, e.getRepeatCount());
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (e.getRepeatCount() == 0) {
                        pressHandled = false;
                    } else if (!pressHandled) {
                        pressHandled = true;   // held for about half a second
                        showMenu();
                    }
                } else if (action == KeyEvent.ACTION_UP) {
                    if (!pressHandled) click();
                    pressHandled = false;
                }
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
                if (cy <= edge) web.scrollBy(0, -(int) (step * 3)); else cy -= step;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (cy >= h - edge) web.scrollBy(0, (int) (step * 3)); else cy += step;
                break;
        }
        cx = Math.max(0, Math.min(w - 1, cx));
        cy = Math.max(0, Math.min(h - 1, cy));
        cursor.setPos(cx, cy);
        showCursor();
    }

    private void click() {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cx, cy, 0);
        MotionEvent up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, cx, cy, 0);
        web.dispatchTouchEvent(down);
        web.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        showCursor();
    }

    // ---------- Menu ----------
    private void showMenu() {
        final String[] items = {
                "Search (keyboard)",
                "Voice search",
                "Home",
                "Back",
                "Reload",
                "Change site",
                "Text size  +",
                "Text size  -",
                cursorMode ? "Cursor: turn OFF" : "Cursor: turn ON",
                "Exit app"
        };
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Anime TV")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: showSearchDialog(""); break;
                        case 1: startVoice(); break;
                        case 2: web.loadUrl(homeUrl()); break;
                        case 3:
                            if (web.canGoBack()) web.goBack();
                            else Toast.makeText(this, "No previous page", Toast.LENGTH_SHORT).show();
                            break;
                        case 4: web.reload(); break;
                        case 5: showChangeSite(); break;
                        case 6: changeZoom(10); break;
                        case 7: changeZoom(-10); break;
                        case 8: toggleCursor(); break;
                        case 9: finish(); break;
                    }
                })
                .show();
    }

    private void changeZoom(int delta) {
        zoom = Math.max(50, Math.min(250, zoom + delta));
        web.getSettings().setTextZoom(zoom);
        prefs.edit().putInt("zoom", zoom).apply();
        Toast.makeText(this, "Text size " + zoom + "%", Toast.LENGTH_SHORT).show();
    }

    private FrameLayout holder(View inner) {
        FrameLayout h = new FrameLayout(this);
        h.setPadding(dp(24), dp(8), dp(24), 0);
        h.addView(inner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return h;
    }

    // ---------- Search with TV keyboard ----------
    private void showSearchDialog(String prefill) {
        if (inputDialog != null && inputDialog.isShowing()) return;

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Type anime name");
        input.setText(prefill);
        input.setSelection(input.getText().length());
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);

        inputDialog = new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Search")
                .setView(holder(input))
                .setPositiveButton("Search", (d, w) -> doSearch(input.getText().toString()))
                .setNeutralButton("Voice", (d, w) -> startVoice())
                .setNegativeButton("Cancel", null)
                .create();

        input.setOnEditorActionListener((v, actionId, ev) -> {
            doSearch(input.getText().toString());
            inputDialog.dismiss();
            return true;
        });
        // after closing, un-focus the page's text box so it can trigger the keyboard again
        inputDialog.setOnDismissListener(d -> web.evaluateJavascript(
                "if(document.activeElement&&document.activeElement.blur)document.activeElement.blur();", null));

        inputDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        inputDialog.show();
    }

    private void doSearch(String raw) {
        final String q = raw == null ? "" : raw.trim();
        if (q.isEmpty()) return;

        String js = "(function(q){ try{"
                + "var el=document.activeElement;"
                + "if(!(el&&(el.tagName==='INPUT'||el.tagName==='TEXTAREA'))){"
                + " el=document.querySelector('input[type=search],input[name=keyword],input[name=q],"
                + "input[name=s],input[name=search],input[placeholder*=earch],input[type=text]'); }"
                + "if(!el) return 'none';"
                + "var proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;"
                + "Object.getOwnPropertyDescriptor(proto,'value').set.call(el,q);"
                + "el.dispatchEvent(new Event('input',{bubbles:true}));"
                + "el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "var f=el.form;"
                + "if(f){ if(f.requestSubmit){f.requestSubmit();}else{f.submit();} }"
                + "else{ ['keydown','keypress','keyup'].forEach(function(t){"
                + " el.dispatchEvent(new KeyboardEvent(t,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));}); }"
                + "return 'ok'; }catch(e){ return 'none'; } })(" + JSONObject.quote(q) + ");";

        web.evaluateJavascript(js, result -> {
            if (result == null || result.contains("none")) {
                try {
                    String base = web.getUrl();
                    if (base == null || !base.startsWith("http")) base = homeUrl();
                    Uri u = Uri.parse(base);
                    String origin = u.getScheme() + "://" + u.getHost();
                    web.loadUrl(origin + "/search?keyword=" + URLEncoder.encode(q, "UTF-8"));
                } catch (Exception ex) {
                    Toast.makeText(this, "Could not find the search box", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // ---------- Voice search ----------
    private void startVoice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the anime name");
        try {
            startActivityForResult(i, REQ_VOICE);
        } catch (Exception ex) {
            Toast.makeText(this,
                    "Voice search is not available on this TV. Use the mic key on the keyboard instead.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) {
                Toast.makeText(this, "Searching: " + r.get(0), Toast.LENGTH_SHORT).show();
                doSearch(r.get(0));
            }
        }
    }

    // ---------- Change site (saved on the TV, no rebuild needed) ----------
    private void showChangeSite() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(homeUrl());
        input.setSelection(input.getText().length());

        AlertDialog dlg = new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Change site")
                .setView(holder(input))
                .setPositiveButton("Save & open", (d, w) -> {
                    String u = input.getText().toString().trim();
                    if (u.isEmpty()) return;
                    if (!u.startsWith("http")) u = "https://" + u;
                    prefs.edit().putString("home_url", u).apply();
                    web.loadUrl(u);
                })
                .setNegativeButton("Cancel", null)
                .create();
        dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
    }

    // ---------- Back / lifecycle ----------
    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            long now = SystemClock.uptimeMillis();
            if (now - lastBack < 2000) {
                super.onBackPressed();
            } else {
                lastBack = now;
                Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        web.destroy();
        super.onDestroy();
    }

    // ---------- On-screen mouse pointer ----------
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
