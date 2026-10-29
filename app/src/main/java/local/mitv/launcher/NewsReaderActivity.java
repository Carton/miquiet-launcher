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
    private String address, publisher, suppliedBody;
    private boolean loading, failed, readerDocument;
    private int textZoom = 125;
    private int fetchGeneration;
    private final java.util.concurrent.ExecutorService articles = java.util.concurrent.Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> pending;
    private final Runnable timeout = () -> { if (loading) { if (pending != null) pending.cancel(true); web.stopLoading(); failure(NewsReaderActivity.this.getString(R.string.loading_timed_out_retry_or_return_to)); } };

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
        publisher = getIntent().getStringExtra("publisher");
        if (publisher == null || publisher.isEmpty()) publisher = NewsReaderActivity.this.getString(R.string.cctv);
        suppliedBody = getIntent().getStringExtra("body");
        if (suppliedBody == null || suppliedBody.length() > 60_000) suppliedBody = "";
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.BACKGROUND); setContentView(root);
        LinearLayout toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),0);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,Ui.dp(this,46)));
        back = button(NewsReaderActivity.this.getString(R.string.reader_back), () -> finish()); toolbar.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,78),Ui.dp(this,34)));
        TextView title = Ui.text(this,getIntent().getStringExtra("title"),16,Color.WHITE);
        title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END); title.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);
        toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(button(NewsReaderActivity.this.getString(R.string.reader_text_smaller),() -> changeText(-15)),new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,34)));
        toolbar.addView(button(NewsReaderActivity.this.getString(R.string.reader_text_larger),() -> changeText(15)),new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,34)));
        retry = button(NewsReaderActivity.this.getString(R.string.retry),this::load); retry.setVisibility(View.GONE);
        toolbar.addView(retry,new LinearLayout.LayoutParams(Ui.dp(this,62),Ui.dp(this,34)));
        status = Ui.text(this,NewsReaderActivity.this.getString(R.string.opening_article),13,Ui.MUTED); status.setPadding(Ui.dp(this,14),0,0,0);
        root.addView(status,new LinearLayout.LayoutParams(-1,Ui.dp(this,26)));
        try {
            web = new WebView(this);
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(false); settings.setDomStorageEnabled(false);
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
                    if (allowed == null) { Toast.makeText(NewsReaderActivity.this,NewsReaderActivity.this.getString(R.string.this_link_cannot_be_opened_on_the),Toast.LENGTH_SHORT).show(); return true; }
                    // Keep this reader on its selected story. Other pages have different extraction rules.
                    Toast.makeText(NewsReaderActivity.this,NewsReaderActivity.this.getString(R.string.return_to_the_news_list_and_choose),Toast.LENGTH_SHORT).show(); return true;
                }
                @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) {
                    loading = true; failed = false; status.setText(getString(R.string.opening_publisher, publisher));
                    handler.removeCallbacks(timeout); handler.postDelayed(timeout,25000);
                }
                @Override public void onPageFinished(WebView view,String url) {
                    if (failed || !readerDocument) return;
                    loading = false; handler.removeCallbacks(timeout); unreadable.remove(address);
                    status.setText(publisher + (suppliedBody.isEmpty() ? " · " : NewsReaderActivity.this.getString(R.string.feed_content)) + NewsReaderActivity.this.getString(R.string.up_down_scroll_back_close)); web.requestFocus(); web.scrollTo(0,0);
                    web.postDelayed(() -> android.util.Log.i("QuietTVReader","Reader ready " + address + " height=" + web.getContentHeight()),300);
                }
                @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error) {
                    if (request.isForMainFrame()) failure(NewsReaderActivity.this.getString(R.string.could_not_open_the_article_retry_or));
                }
                @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse error) {
                    if (request.isForMainFrame() && error.getStatusCode() >= 400) failure(NewsReaderActivity.this.getString(R.string.this_article_is_unavailable_return_to_the));
                }
                @Override public void onReceivedSslError(WebView view,SslErrorHandler ssl,SslError error) {
                    ssl.cancel();
                    if (error.getUrl() != null && error.getUrl().equals(view.getUrl())) failure(NewsReaderActivity.this.getString(R.string.could_not_connect_securely_return_to_the));
                }
            });
            root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
            load();
        } catch (RuntimeException | LinkageError error) {
            supported = false; Toast.makeText(this,NewsReaderActivity.this.getString(R.string.the_tv_s_built_in_reader_is),Toast.LENGTH_LONG).show(); finish();
        }
    }

    private TextView button(String text,Runnable action) {
        TextView button = Ui.text(this,text,14,Color.WHITE); button.setGravity(Gravity.CENTER);
        Ui.focusable(button,Color.TRANSPARENT,6); button.setOnClickListener(v -> action.run()); return button;
    }
    private void changeText(int delta) { textZoom = Math.max(80,Math.min(230,textZoom + delta)); web.getSettings().setTextZoom(textZoom); }
    private void load() {
        int generation = ++fetchGeneration;
        if (pending != null) pending.cancel(true);
        failed = false; readerDocument = false;
        web.setVisibility(View.VISIBLE); retry.setVisibility(View.GONE);
        if (!suppliedBody.isEmpty()) showArticle(suppliedBody);
        else {
            web.getSettings().setJavaScriptEnabled(false);
            loading = true; status.setText(getString(R.string.opening_publisher, publisher));
            handler.removeCallbacks(timeout); handler.postDelayed(timeout,25000);
            // Static publishers use the same verified HTTPS client as feeds. This also supplies
            // the public roots missing from Android 6, without overriding WebView SSL failures.
            pending = articles.submit(() -> {
                try {
                    String html = DashboardRepository.request(getApplicationContext(),address);
                    String body = ArticleHtml.extract(html,new java.net.URL(address).getHost());
                    handler.post(() -> {
                        if (isFinishing() || failed || generation != fetchGeneration) return;
                        if (body == null) failure(NewsReaderActivity.this.getString(R.string.this_article_is_not_supported_in_tv));
                        else showArticle(body);
                    });
                } catch (Exception error) {
                    handler.post(() -> { if (!isFinishing() && generation == fetchGeneration) failure(NewsReaderActivity.this.getString(R.string.could_not_open_the_article_retry_or)); });
                }
            });
        }
    }
    private void showArticle(String content) {
        String title = android.text.Html.escapeHtml(getIntent().getStringExtra("title"));
        String document = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<style>body{margin:0;background:#f3f4f5;color:#25282d;font:22px/1.85 sans-serif}article{max-width:880px;padding:24px;margin:auto}h1{font-size:26px;line-height:1.5;margin:0 0 14px}.source{font-size:15px;color:#70757c}p{margin:0 0 20px}img,video{max-width:100%;height:auto}iframe,.article_info{display:none}</style>"
            + "</head><body><article><h1>" + title + "</h1><p class='source'>" + getString(R.string.article_source_label) + android.text.Html.escapeHtml(publisher)
            + (suppliedBody.isEmpty() ? "" : NewsReaderActivity.this.getString(R.string.original_feed_brief_may_be_an_excerpt)) + "</p>" + ArticleHtml.clean(content) + "</article></body></html>";
        readerDocument = true;
        web.getSettings().setJavaScriptEnabled(false);
        web.getSettings().setLoadWithOverviewMode(false);
        web.loadDataWithBaseURL(address,document,"text/html","UTF-8",null);
    }
    private void failure(String message) {
        failed = true; loading = false; handler.removeCallbacks(timeout); status.setText(message);
        unreadable.add(address);
        web.setVisibility(View.INVISIBLE); retry.setVisibility(View.VISIBLE); back.requestFocus();
        android.util.Log.w("QuietTVReader",message);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (web != null && web.hasFocus() && (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_UP)) {
            // Intercept before Chromium moves focus among HTML links instead of scrolling.
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (key == KeyEvent.KEYCODE_DPAD_UP && web.getScrollY() <= 0) back.requestFocus();
                else web.scrollBy(0,(key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1) * Math.max(80,web.getHeight()/3));
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed() { finish(); }
    @Override protected void onPause() { if (web != null) web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() {
        fetchGeneration++; if (pending != null) pending.cancel(true); articles.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        if (web != null) {
            web.stopLoading();
            if (web.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup)web.getParent()).removeView(web);
            web.destroy();
        }
        super.onDestroy();
    }
}
