package local.mitv.launcher;

import android.content.Context;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.media.tv.TvTrackInfo;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageView;
import java.util.List;

final class PreviewController {
    private final TvView tv;
    private final TextView status;
    private final ImageView fallback;
    private final TvInputManager manager;
    private final Handler handler = new Handler();
    private String current;
    private boolean active;
    private int port = 3;
    private final TvInputManager.TvInputCallback inputs = new TvInputManager.TvInputCallback() {
        @Override public void onInputAdded(String inputId) { retry(); }
        @Override public void onInputRemoved(String inputId) { if (inputId.equals(current)) retry(); }
        @Override public void onInputStateChanged(String inputId, int state) { if (inputId.equals(current)) retry(); }
    };

    PreviewController(Context context, TvView tv, TextView status, ImageView fallback) {
        this.tv = tv; this.status = status;
        this.fallback = fallback;
        manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        tv.setCallback(new TvView.TvInputCallback() {
            @Override public void onVideoAvailable(String inputId) {
                fitVideo();
                status.setVisibility(View.GONE);
                fallback.setVisibility(View.GONE);
                Log.i("QuietTV", "Preview video available: " + inputId);
            }
            @Override public void onVideoUnavailable(String inputId, int reason) {
                message(reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING ? "正在连接信号源…" : "暂无视频信号 · 按确定进入信号源");
                Log.i("QuietTV", "Preview video unavailable: " + inputId + " reason=" + reason);
            }
            @Override public void onConnectionFailed(String inputId) {
                current = null;
                message("该输入暂不可预览 · 按确定进入信号源");
                Log.w("QuietTV", "Preview connection failed: " + inputId);
            }
            @Override public void onDisconnected(String inputId) { message("信号已断开"); current = null; }
            @Override public void onTracksChanged(String inputId, List<TvTrackInfo> tracks) { fitVideo(); }
            @Override public void onTrackSelected(String inputId, int type, String trackId) {
                if (type == TvTrackInfo.TYPE_VIDEO) fitVideo();
            }
        });
    }

    void resume() {
        active = true;
        if (manager != null) manager.registerCallback(inputs, handler);
        select(port);
    }

    void pause() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        if (manager != null) manager.unregisterCallback(inputs);
        tv.reset();
        current = null;
    }

    void select(int selected) {
        port = selected;
        if (!active || manager == null) return;
        try {
            String input = null;
            List<TvInputInfo> available = manager.getTvInputList();
            for (TvInputInfo info : available) {
                if (info.getId().contains(".Hdmi" + port + "InputService/")) { input = info.getId(); break; }
            }
            if (input == null) {
                tv.reset(); current = null;
                message("HDMI" + port + " 未连接 · 按确定打开信号源");
                return;
            }
            if (input.equals(current)) return;
            tv.reset();
            tv.setLayoutParams(new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
            current = input;
            message("正在连接 HDMI" + port + "…");
            tv.setStreamVolume(0f);
            tv.tune(input, TvContract.buildChannelUriForPassthroughInput(input));
            Log.i("QuietTV", "Preview tune: " + input);
        } catch (RuntimeException e) {
            current = null;
            message("系统未开放此输入的预览 · 按确定进入信号源");
            Log.w("QuietTV", "Preview rejected", e);
        }
    }

    private void retry() { if (active) { current = null; select(port); } }
    private void fitVideo() {
        List<TvTrackInfo> tracks = tv.getTracks(TvTrackInfo.TYPE_VIDEO);
        if (tracks == null || tracks.isEmpty()) return;
        String selected = tv.getSelectedTrack(TvTrackInfo.TYPE_VIDEO);
        TvTrackInfo video = tracks.get(0);
        for (TvTrackInfo track : tracks) if (track.getId().equals(selected)) video = track;
        if (video.getVideoWidth() <= 0 || video.getVideoHeight() <= 0) return;
        View bounds = (View)tv.getParent();
        int width = bounds.getWidth(), height = bounds.getHeight();
        if (width <= 0 || height <= 0) return;
        double pixelAspect = video.getVideoPixelAspectRatio();
        double ratio = video.getVideoWidth() * (pixelAspect > 0 ? pixelAspect : 1) / video.getVideoHeight();
        if (width / (double)height > ratio) width = Math.max(1,(int)Math.round(height * ratio));
        else height = Math.max(1,(int)Math.round(width / ratio));
        tv.setLayoutParams(new FrameLayout.LayoutParams(width,height,Gravity.CENTER));
    }
    private void message(String text) {
        status.setText(text); status.setVisibility(View.VISIBLE);
        fallback.setVisibility(View.VISIBLE);
    }
}
