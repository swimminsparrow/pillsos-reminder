package swimminsparrow.ricordamelo.core.utils;

import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder;

/**
 * Modern helper for checking and requesting battery optimization exclusion on Android 6.0+,
 * with OEM-specific auto-start and background protection guidance.
 */
public class BatteryOptimizationsHelper {

    /**
     * Formats the device manufacturer brand name for display.
     */
    public static String getDeviceBrand() {
        String manufacturer = Build.MANUFACTURER;
        if (manufacturer == null || manufacturer.trim().isEmpty()) {
            return "Device";
        }
        String clean = manufacturer.trim();
        if (clean.equalsIgnoreCase("oneplus")) {
            return "OnePlus";
        }
        if (clean.equalsIgnoreCase("asus")) {
            return "ASUS";
        }
        return clean.substring(0, 1).toUpperCase(Locale.ROOT) + clean.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * Detects if the device belongs to an OEM known for aggressive proprietary background management.
     */
    public static boolean isAggressiveOem() {
        String manufacturer = Build.MANUFACTURER;
        if (manufacturer == null) return false;
        String m = manufacturer.toLowerCase(Locale.ROOT);
        return m.contains("honor") || m.contains("huawei")
                || m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")
                || m.contains("samsung")
                || m.contains("oppo") || m.contains("realme")
                || m.contains("vivo") || m.contains("iqoo")
                || m.contains("oneplus")
                || m.contains("asus")
                || m.contains("transsion") || m.contains("infinix") || m.contains("tecno");
    }

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
     * Checks whether exact alarms can be scheduled on Android 12+ (API 31+).
     */
    public static boolean canScheduleExactAlarms(Context context) {
        if (context == null) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    /**
     * Standard Android battery optimization prompt (used during onboarding to avoid OEM friction).
     */
    public static void openStandardBatteryOptimizationSettings(Context context) {
        if (context == null) return;
        if (isIgnoringBatteryOptimizations(context)) {
            DialogBuilder.successDialog(context,
                    context.getString(R.string.app_name),
                    context.getString(R.string.battery_already_unrestricted)).show();
        } else {
            showPromptDialog(context);
        }
    }

    /**
     * Handles user-initiated battery optimization verification from Settings, Help or Alarms banner.
     */
    public static void openBatteryOptimizationSettings(Context context) {
        if (context == null) return;

        if (isAggressiveOem()) {
            showOemPromptDialog(context);
        } else if (isIgnoringBatteryOptimizations(context)) {
            DialogBuilder.successDialog(context,
                    context.getString(R.string.app_name),
                    context.getString(R.string.battery_already_unrestricted)).show();
        } else {
            showPromptDialog(context);
        }
    }

    private static void showOemPromptDialog(Context context) {
        String brand = getDeviceBrand();
        new DialogBuilder(context, DialogBuilder.Type.WARNING)
                .setTitleText(context.getString(R.string.doze_title))
                .setContentText(context.getString(R.string.battery_oem_warning_desc, brand))
                .setConfirmText(context.getString(R.string.open_device_settings, brand))
                .setCancelText(context.getString(R.string.dialog_cancel))
                .setConfirmClickListener(sDialog -> {
                    swimminsparrow.ricordamelo.core.data.SettingsDB.getInstance(context).setOemBatteryOptimizationChecked(true);
                    if (!isIgnoringBatteryOptimizations(context)) {
                        launchBatterySettings(context);
                    }
                    openOemBatterySettings(context);
                })
                .setCancelClickListener(sDialog -> {
                    swimminsparrow.ricordamelo.core.data.SettingsDB.getInstance(context).setOemBatteryOptimizationChecked(true);
                })
                .show();
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

    /**
     * Attempts to open the OEM-specific power/auto-start manager screen.
     */
    public static void openOemBatterySettings(Context context) {
        if (context == null) return;
        String m = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.ROOT) : "";

        List<Intent> intents = new ArrayList<>();

        if (m.contains("honor") || m.contains("huawei")) {
            intents.add(new Intent().setComponent(new ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")));
        } else if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) {
            intents.add(new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")));
            intents.add(new Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT));
            intents.add(new Intent().setComponent(new ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")));
        } else if (m.contains("samsung")) {
            intents.add(new Intent().setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.AppSleepListActivity")));
        } else if (m.contains("oppo") || m.contains("realme")) {
            intents.add(new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")));
        } else if (m.contains("vivo") || m.contains("iqoo")) {
            intents.add(new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")));
        } else if (m.contains("oneplus")) {
            intents.add(new Intent().setComponent(new ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")));
        } else if (m.contains("asus")) {
            intents.add(new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")));
        } else if (m.contains("transsion") || m.contains("infinix") || m.contains("tecno")) {
            intents.add(new Intent().setComponent(new ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.view.AutoStartListActivity")));
        }

        for (Intent intent : intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
        }

        // Universal fallback to Application Details Settings where Battery settings are accessible on all OEMs
        openAppDetailsSettings(context);
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
