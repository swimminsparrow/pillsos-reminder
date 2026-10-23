package swimminsparrow.ricordamelo.core.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder;

/**
 * Modern helper for checking and requesting battery optimization exclusion on Android 6.0+.
 * Automatic startup popups and obsolete OEM reflection hacks have been removed.
 */
public class BatteryOptimizationsHelper {

    /**
     * Checks whether the application is already exempt from battery optimizations.
     */
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (context == null) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        return true;
    }

    /**
     * Handles user-initiated battery optimization verification from Settings or Help.
     */
    public static void openBatteryOptimizationSettings(Context context) {
        if (context == null) return;

        if (isIgnoringBatteryOptimizations(context)) {
            DialogBuilder.successDialog(context,
                    context.getString(R.string.app_name),
                    context.getString(R.string.battery_already_unrestricted)).show();
        } else {
            showPromptDialog(context);
        }
    }

    private static void showPromptDialog(Context context) {
        new DialogBuilder(context, DialogBuilder.Type.WARNING)
                .setTitleText(context.getString(R.string.doze_title))
                .setContentText(context.getString(R.string.battery_prompt_dialog_desc))
                .setConfirmText(context.getString(R.string.configure))
                .setCancelText(context.getString(R.string.dialog_cancel))
                .setConfirmClickListener(sDialog -> launchBatterySettings(context))
                .show();
    }

    private static void launchBatterySettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (Exception e) {
                SpockLogger.error(BatteryOptimizationsHelper.class, "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS failed, trying fallback: " + e.getMessage());
            }

            try {
                Intent fallbackIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallbackIntent);
                return;
            } catch (Exception e) {
                SpockLogger.error(BatteryOptimizationsHelper.class, "ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS failed: " + e.getMessage());
            }
        }

        // Universal fallback to Application Details Settings where Battery settings are accessible on all OEMs
        openAppDetailsSettings(context);
    }

    private static void openAppDetailsSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            SpockLogger.error(BatteryOptimizationsHelper.class, "Failed to open app details: " + e.getMessage());
        }
    }
}