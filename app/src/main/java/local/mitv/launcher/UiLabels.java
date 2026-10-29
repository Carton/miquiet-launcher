package local.mitv.launcher;

/** Android resource IDs for menus whose navigation policies remain platform independent. */
final class UiLabels {
    private UiLabels() {}
    static final int[] SETTINGS = {
        R.string.tv_settings,
        R.string.home_setup,
        R.string.network,
        R.string.bluetooth,
        R.string.picture,
        R.string.sound,
        R.string.files,
        R.string.casting,
        R.string.local_screensaver,
        R.string.home_takeover,
        R.string.sources_licenses,
        R.string.about
    };
    static final int[] SETTINGS_DETAILS = {
        R.string.open_the_tv_s_native_settings,
        R.string.apps_input_names_appearance_and_visible_content,
        R.string.wi_fi_and_network_connections,
        R.string.connect_remotes_headphones_and_other_devices,
        R.string.tv_picture_and_display_settings,
        R.string.tv_sound_and_audio_output_settings,
        R.string.browse_tv_storage_and_connected_devices,
        R.string.open_the_tv_s_casting_app,
        R.string.manage_photos_and_idle_slideshows,
        R.string.manage_home_takeover_and_restoration,
        R.string.view_data_sources_and_open_source_licenses,
        R.string.view_the_project_name_version_and_source
    };
    static final int[] BACKGROUNDS = {
        R.string.charcoal,
        R.string.navy,
        R.string.forest
    };
    static final int[] NEWS = {
        R.string.china,
        R.string.world,
        R.string.finance,
        R.string.science,
        R.string.gaming,
        R.string.news_ai
    };
    static int weather(DashboardPolicy.Weather condition) {
        switch (condition) {
            case CLEAR: return R.string.weather_clear;
            case FAIR: return R.string.fair;
            case PARTLY_CLOUDY: return R.string.cloudy;
            case OVERCAST: return R.string.overcast;
            case FOG: return R.string.fog;
            case DRIZZLE: return R.string.drizzle;
            case LIGHT_RAIN: return R.string.light_rain;
            case RAIN: return R.string.rain;
            case HEAVY_RAIN: return R.string.heavy_rain;
            case FREEZING_RAIN: return R.string.freezing_rain;
            case LIGHT_SNOW: return R.string.light_snow;
            case SNOW: return R.string.snow;
            case HEAVY_SNOW: return R.string.heavy_snow;
            case SNOW_GRAINS: return R.string.snow_grains;
            case SHOWERS: return R.string.showers;
            case SNOW_SHOWERS: return R.string.snow_showers;
            case THUNDERSTORM: return R.string.thunder;
            case HAIL: return R.string.hailstorm;
            case UNKNOWN: return R.string.unknown;
            default: return R.string.unknown;
        }
    }
    static String photoFailure(android.content.Context context, Exception error) {
        if (error instanceof PhotoFailure) {
            switch (((PhotoFailure) error).reason) {
                case LIMIT: return context.getString(R.string.up_to_24_photos_can_be_saved);
                case DIRECTORY: return context.getString(R.string.could_not_create_the_photo_folder);
                case TOO_LARGE: return context.getString(R.string.each_photo_must_be_no_larger_than);
                case INVALID_IMAGE: return context.getString(R.string.unsupported_format_or_image_dimensions_too_large);
                case UNSUPPORTED_IMAGE: return context.getString(R.string.photo_is_damaged_or_its_format_is);
                case SAVE_FAILED: return context.getString(R.string.could_not_save_photo);
                case OUTSIDE_ROOT: return context.getString(R.string.this_folder_is_outside_the_selected_storage);
                case UNREADABLE_FOLDER: return context.getString(R.string.cannot_read_this_folder_reconnect_the_storage);
            }
        }
        if (error instanceof java.io.EOFException) return context.getString(R.string.image_transfer_interrupted);
        return context.getString(R.string.unreadable_file_or_unsupported_format);
    }
}
