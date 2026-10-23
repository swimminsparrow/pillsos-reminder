package swimminsparrow.ricordamelo.ui.rateme;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;

public class RateMeManager {

    private static final String PREF_NAME = "rateme_prefs";
    private static final String KEY_LAUNCH_COUNT = "launch_count";
    private static final String KEY_FIRST_INSTALL_TIME = "first_install_time";
    private static final String KEY_LAST_SNOOZE_TIME = "last_snooze_time";
    private static final String KEY_PERMANENTLY_DISMISSED = "permanently_dismissed";

    private static final int LAUNCH_TIMES_THRESHOLD = 5;
    private static final long SNOOZE_DURATION_MS = 5 * 24 * 60 * 60 * 1000L; // 5 days
    private static final long MIN_INSTALL_DELAY_MS = 24 * 60 * 60 * 1000L; // 1 day

    private static boolean sSessionChecked = false;

    public static void onAppLaunch(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int currentCount = prefs.getInt(KEY_LAUNCH_COUNT, 0);
        long firstInstallTime = prefs.getLong(KEY_FIRST_INSTALL_TIME, 0);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_LAUNCH_COUNT, currentCount + 1);
        if (firstInstallTime == 0) {
            editor.putLong(KEY_FIRST_INSTALL_TIME, System.currentTimeMillis());
        }
        editor.apply();
    }

    public static boolean shouldShow(@NonNull Context context) {
        if (Boolean.TRUE.equals(SettingsDB.getInstance(context).getRated())) {
            return false;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_PERMANENTLY_DISMISSED, false)) {
            return false;
        }

        long firstInstallTime = prefs.getLong(KEY_FIRST_INSTALL_TIME, 0);
        if (firstInstallTime > 0 && (System.currentTimeMillis() - firstInstallTime) < MIN_INSTALL_DELAY_MS) {
            return false;
        }

        int launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0);
        if (launchCount < LAUNCH_TIMES_THRESHOLD) {
            return false;
        }

        long lastSnooze = prefs.getLong(KEY_LAST_SNOOZE_TIME, 0);
        if (lastSnooze > 0 && (System.currentTimeMillis() - lastSnooze) < SNOOZE_DURATION_MS) {
            return false;
        }

        return true;
    }

    public static void showIfNeeded(@NonNull FragmentActivity activity) {
        if (sSessionChecked) {
            return;
        }
        sSessionChecked = true;

        onAppLaunch(activity);
        if (shouldShow(activity)) {
            show(activity);
        }
    }

    public static void show(@NonNull FragmentActivity activity) {
        RateMeDialogFragment.show(activity.getSupportFragmentManager());
    }

    public static void markAsRated(@NonNull Context context) {
        SettingsDB.getInstance(context).setRated(true);
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PERMANENTLY_DISMISSED, true)
                .apply();
    }

    public static void snooze(@NonNull Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SNOOZE_TIME, System.currentTimeMillis())
                .apply();
    }

    public static void dismissPermanently(@NonNull Context context) {
        SettingsDB.getInstance(context).setRated(true);
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PERMANENTLY_DISMISSED, true)
                .apply();
    }

    public static void openPlayStore(@NonNull Context context) {
        String packageName = context.getPackageName();
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ex) {
                SpockLogger.error(RateMeManager.class, "Failed to open Play Store link: " + ex.getMessage());
            }
        }
    }

    public static void sendFeedbackEmail(@NonNull Context context, int rating) {
        String appName = context.getString(R.string.app_name);
        String ratingText = rating > 0 ? " (" + rating + "/5 stelle)" : "";
        String subject = String.format(context.getString(R.string.rateme__email_subject), appName) + ratingText;

        String body = "\n\n" +
                "----------------------------------------\n" +
                "App: " + appName + "\n" +
                (rating > 0 ? "Valutazione: " + rating + " / 5 stelle\n" : "") +
                "Dispositivo: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n" +
                "Versione Android: " + android.os.Build.VERSION.RELEASE + " (SDK " + android.os.Build.VERSION.SDK_INT + ")\n" +
                "----------------------------------------\n";

        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:swimminsparrow@gmail.com"));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, body);

        try {
            Intent chooser = Intent.createChooser(emailIntent, context.getString(R.string.rateme__dialog_feedback_message));
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            try {
                emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(emailIntent);
            } catch (Exception ex) {
                SpockLogger.error(RateMeManager.class, "Failed to open email client: " + ex.getMessage());
            }
        }
    }
}
