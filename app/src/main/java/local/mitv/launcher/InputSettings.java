package local.mitv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.LinearLayout;

final class InputSettings {
    private InputSettings() {}

    static void show(Activity activity, SharedPreferences prefs, Runnable changed) {
        String[] labels = new String[3];
        for (int port = 1; port <= 3; port++) {
            String name = InputNames.normalize(prefs.getString(InputNames.key(port), ""));
            labels[port - 1] = "HDMI" + port + "    " + (name.isEmpty() ? activity.getString(R.string.unnamed) : name);
        }
        new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.input_names))
            .setItems(labels, (dialog, index) -> edit(activity, prefs, index + 1, changed))
            .setNegativeButton(activity.getString(R.string.close), null).show();
    }

    static void edit(Activity activity, SharedPreferences prefs, int port, Runnable changed) {
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setTextSize(20);
        name.setHint(activity.getString(R.string.example_console_set_top_box));
        name.setText(prefs.getString(InputNames.key(port), ""));
        name.setSelection(name.length());
        LinearLayout content = new LinearLayout(activity);
        content.setPadding(Ui.dp(activity, 24), Ui.dp(activity, 8), Ui.dp(activity, 24), 0);
        content.addView(name, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("HDMI" + port + activity.getString(R.string.name))
            .setMessage(activity.getString(R.string.up_to_8_characters_leave_empty_to))
            .setView(content).setPositiveButton(activity.getString(R.string.save), null).setNegativeButton(activity.getString(R.string.cancel), null)
            .setNeutralButton(activity.getString(R.string.restore_default), (d, which) -> {
                prefs.edit().remove(InputNames.key(port)).apply();
                changed.run();
            }).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = InputNames.normalize(name.getText().toString());
            if (!InputNames.valid(value)) {
                name.setError(activity.getString(R.string.use_8_characters_or_fewer));
                name.requestFocus();
                return;
            }
            prefs.edit().putString(InputNames.key(port), value).apply();
            changed.run();
            dialog.dismiss();
        });
    }
}
