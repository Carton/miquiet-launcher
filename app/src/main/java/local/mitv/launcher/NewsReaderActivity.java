package local.mitv.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** An internal reader for the news links; no external browser dependency. */
public final class NewsReaderActivity extends Activity {
    private static Boolean supported;
    private static final java.util.Set<String> unreadable = new java.util.HashSet<>();
    private final Handler handler = new Handler();
    private WebView web;
    private TextView back, status, retry;
    private String address;
    private boolean loading, failed, readerDocument;
    private int textZoom = 125;
    private final Runnable timeout = () -> { if (loading) { web.stopLoading(); failure("加载超时，请重试或返回新闻摘要"); } };

    static boolean available(Activity activity) {
        if (supported == null) {
            try { WebView probe = new WebView(activity); probe.destroy(); supported = true; }
            catch (RuntimeException | LinkageError error) {
                supported = false;
                android.util.Log.w("QuietTVReader", "WebView unavailable", error);
            }
        }
        return supported;
    }

    static boolean canRead(Activity activity,String url) {
        String normalized = NewsReaderPolicy.address(url);
        return normalized != null && !unreadable.contains(normalized) && available(activity);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        address = NewsReaderPolicy.address(getIntent().getStringExtra("url"));
        if (address == null || !available(this)) { finish(); return; }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.BACKGROUND); setContentView(root);
        LinearLayout toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),0);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,Ui.dp(this,46)));
        back = button("‹ 返回", () -> finish()); toolbar.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,78),Ui.dp(this,34)));
        TextView title = Ui.text(this,getIntent().getStringExtra("title"),16,Color.WHITE);
        title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END); title.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);
        toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(button("字号－",() -> changeText(-15)),new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,34)));
        toolbar.addView(button("字号＋",() -> changeText(15)),new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,34)));
        retry = button("重试",this::load); retry.setVisibility(View.GONE);
        toolbar.addView(retry,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,34)));
        status = Ui.text(this,"正在打开央视新闻…",13,Ui.MUTED); status.setPadding(Ui.dp(this,14),0,0,0);
        root.addView(status,new LinearLayout.LayoutParams(-1,Ui.dp(this,26)));
        try {
            web = new WebView(this);
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
            settings.setAllowFileAccessFromFileURLs(false); settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setMediaPlaybackRequiresUserGesture(true); settings.setGeolocationEnabled(false);
            settings.setSaveFormData(false); settings.setSupportZoom(true); settings.setBuiltInZoomControls(true); settings.setDisplayZoomControls(false);
            settings.setUseWideViewPort(true); settings.setLoadWithOverviewMode(true); settings.setTextZoom(textZoom);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
            web.setBackgroundColor(Color.WHITE);
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view,String url) {
                    String allowed = NewsReaderPolicy.address(url);
                    if (allowed == null) { Toast.makeText(NewsReaderActivity.this,"这个链接无法在电视内打开",Toast.LENGTH_SHORT).show(); return true; }
                    readerDocument = false; web.getSettings().setJavaScriptEnabled(true);
                    if (!allowed.equals(url)) { view.loadUrl(allowed); return true; }
                    return false;
                }
                @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) {
                    loading = true; failed = false; status.setText("正在打开央视新闻…");
                    handler.removeCallbacks(timeout); handler.postDelayed(timeout,25000);
                }
                @Override public void onPageFinished(WebView view,String url) {
                    if (failed) return;
                    if (!readerDocument) {
                        // Current CCTV pages keep the article in an inline string and fill an empty div with scripts.
                        view.evaluateJavascript("(function(){if(typeof contentdate==='string')return contentdate;var a=document.getElementById('content_area');return a&&a.innerHTML.trim()?a.innerHTML:null;})()", result -> article(result));
                    } else {
                        loading = false; handler.removeCallbacks(timeout); unreadable.remove(address);
                        status.setText("央视网 · 方向键上下滚动，返回键退出"); web.requestFocus();
                        web.postDelayed(() -> android.util.Log.i("QuietTVReader","Reader ready " + address + " height=" + web.getContentHeight()),300);
                    }
                }
                @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error) {
                    if (request.isForMainFrame()) failure("新闻暂时无法打开，请重试或返回摘要");
                }
                @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse error) {
                    if (request.isForMainFrame() && error.getStatusCode() >= 400) failure("这篇新闻暂时无法打开，请返回摘要");
                }
                @Override public void onReceivedSslError(WebView view,SslErrorHandler ssl,SslError error) {
                    ssl.cancel();
                    if (error.getUrl() != null && error.getUrl().equals(view.getUrl())) failure("无法安全连接新闻页面，请返回摘要");
                }
            });
            web.setOnKeyListener((v,key,event) -> {
                if (key != KeyEvent.KEYCODE_DPAD_DOWN && key != KeyEvent.KEYCODE_DPAD_UP) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (key == KeyEvent.KEYCODE_DPAD_UP && web.getScrollY() <= 0) back.requestFocus();
                    else web.scrollBy(0,(key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1) * Math.max(80,web.getHeight()/3));
                }
                return true;
            });
            root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
            load();
        } catch (RuntimeException | LinkageError error) {
            supported = false; Toast.makeText(this,"电视内置阅读组件不可用",Toast.LENGTH_LONG).show(); finish();
        }
    }

    private TextView button(String text,Runnable action) {
        TextView button = Ui.text(this,text,14,Color.WHITE); button.setGravity(Gravity.CENTER);
        Ui.focusable(button,Color.TRANSPARENT,6); button.setOnClickListener(v -> action.run()); return button;
    }
    private void changeText(int delta) { textZoom = Math.max(80,Math.min(230,textZoom + delta)); web.getSettings().setTextZoom(textZoom); }
    private void load() {
        failed = false; readerDocument = false; web.getSettings().setJavaScriptEnabled(true);
        web.setVisibility(View.VISIBLE); retry.setVisibility(View.GONE); web.loadUrl(address);
    }
    private void article(String json) {
        if (isFinishing() || failed) return;
        try {
            Object content = new org.json.JSONTokener(json).nextValue();
            if (!(content instanceof String) || ((String)content).trim().length() < 20) {
                failure("这篇新闻暂不支持电视阅读，请返回摘要"); return;
            }
            String title = android.text.Html.escapeHtml(getIntent().getStringExtra("title"));
            String document = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{margin:0;background:#f3f4f5;color:#25282d;font:22px/1.85 sans-serif}article{max-width:880px;padding:24px;margin:auto}h1{font-size:26px;line-height:1.5;margin:0 0 14px}.source{font-size:15px;color:#70757c}p{margin:0 0 20px}img,video{max-width:100%;height:auto}iframe{display:none}a{color:#24725e}</style>"
                + "</head><body><article><h1>" + title + "</h1><p class='source'>来源：央视网</p>" + content + "</article></body></html>";
            readerDocument = true;
            web.getSettings().setJavaScriptEnabled(false);
            web.getSettings().setLoadWithOverviewMode(false);
            web.loadDataWithBaseURL(address,document,"text/html","UTF-8",null);
        } catch (org.json.JSONException error) { failure("这篇新闻暂时无法显示，请返回摘要"); }
    }
    private void failure(String message) {
        failed = true; loading = false; handler.removeCallbacks(timeout); status.setText(message);
        unreadable.add(address);
        web.setVisibility(View.INVISIBLE); retry.setVisibility(View.VISIBLE); back.requestFocus();
        android.util.Log.w("QuietTVReader",message);
    }
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if (web != null && web.hasFocus()) {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) { web.scrollBy(0,Math.max(80,web.getHeight()/3)); return true; }
            if (key == KeyEvent.KEYCODE_DPAD_UP) {
                if (web.getScrollY() <= 0) back.requestFocus(); else web.scrollBy(0,-Math.max(80,web.getHeight()/3));
                return true;
            }
        }
        return super.onKeyDown(key,event);
    }
    @Override public void onBackPressed() { finish(); }
    @Override protected void onPause() { if (web != null) web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) {
            web.stopLoading();
            if (web.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup)web.getParent()).removeView(web);
            web.destroy();
        }
        super.onDestroy();
    }
}
