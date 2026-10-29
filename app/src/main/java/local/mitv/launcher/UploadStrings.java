package local.mitv.launcher;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.EnumMap;

/** Snapshot Android-localized upload text before the foreground receiver starts. */
final class UploadStrings implements PhotoUploadServer.Labels {
    private final EnumMap<PhotoUploadServer.Message, String> messages = new EnumMap<>(PhotoUploadServer.Message.class);
    UploadStrings(Context context) throws IOException {
        try (InputStream input = context.getResources().openRawResource(R.raw.upload_page);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096]; int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
            messages.put(PhotoUploadServer.Message.PAGE, new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        }
        messages.put(PhotoUploadServer.Message.INTERRUPTED, context.getString(R.string.image_transfer_interrupted));
        messages.put(PhotoUploadServer.Message.PHOTO_NOT_SAVED_PLEASE_TRY, context.getString(R.string.photo_not_saved_please_try_again));
        messages.put(PhotoUploadServer.Message.REQUEST_HEADERS_TOO_LARGE, context.getString(R.string.request_headers_too_large));
        messages.put(PhotoUploadServer.Message.INVALID_REQUEST, context.getString(R.string.invalid_request));
        messages.put(PhotoUploadServer.Message.DUPLICATE_REQUEST_HEADER, context.getString(R.string.duplicate_request_header));
        messages.put(PhotoUploadServer.Message.PAGE_NOT_FOUND, context.getString(R.string.page_not_found));
        messages.put(PhotoUploadServer.Message.CHOOSE_A_PHOTO_TO_UPLOAD, context.getString(R.string.choose_a_photo_to_upload));
        messages.put(PhotoUploadServer.Message.USE_THE_PHOTO_PICKER_ON, context.getString(R.string.use_the_photo_picker_on_this_page));
        messages.put(PhotoUploadServer.Message.EACH_PHOTO_MUST_BE_NON, context.getString(R.string.each_photo_must_be_non_empty_and));
        messages.put(PhotoUploadServer.Message.TOO_MANY_INCORRECT_CODES_CLOSE, context.getString(R.string.too_many_incorrect_codes_close_and_reopen));
        messages.put(PhotoUploadServer.Message.INCORRECT_CODE, context.getString(R.string.incorrect_code_check_the_six_digits_shown));
        messages.put(PhotoUploadServer.Message.SAVED_TO_TV_SCREENSAVER, context.getString(R.string.saved_to_tv_screensaver));
        messages.put(PhotoUploadServer.Message.COULD_NOT_SAVE_THIS_PHOTO, context.getString(R.string.could_not_save_this_photo));
    }
    @Override public String text(PhotoUploadServer.Message message) { return messages.get(message); }
}
