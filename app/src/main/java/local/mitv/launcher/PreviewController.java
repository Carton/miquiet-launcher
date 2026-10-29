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
    private final Context context;
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
        this.context = context;
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
                message(reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING ? context.getString(R.string.connecting_to_input) : context.getString(R.string.no_video_signal_press_ok_to_open));
                Log.i("QuietTV", "Preview video unavailable: " + inputId + " reason=" + reason);
            }
            @Override public void onConnectionFailed(String inputId) {
                current = null;
                message(context.getString(R.string.preview_unavailable_press_ok_to_open_input));
                Log.w("QuietTV", "Preview connection failed: " + inputId);
            }
            @Override public void onDisconnected(String inputId) { message(context.getString(R.string.input_disconnected)); current = null; }
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
                message("HDMI" + port + context.getString(R.string.disconnected_press_ok_to_open_input));
                return;
            }
            if (input.equals(current)) return;
            tv.reset();
            tv.setLayoutParams(new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
            current = input;
            message(context.getString(R.string.connecting_to_hdmi) + port + "…");
            tv.setStreamVolume(0f);
            tv.tune(input, TvContract.buildChannelUriForPassthroughInput(input));
            Log.i("QuietTV", "Preview tune: " + input);
        } catch (RuntimeException e) {
            current = null;
            message(context.getString(R.string.preview_not_supported_press_ok_to_open));
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
