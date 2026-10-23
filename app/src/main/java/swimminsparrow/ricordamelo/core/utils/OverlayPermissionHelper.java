package swimminsparrow.ricordamelo.core.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Utility helper for managing the SYSTEM_ALERT_WINDOW ("Display over other apps") permission.
 * On Android 10+ (and especially Android 14/15/16), apps with overlay permission are explicitly
 * exempted from Background Activity Launch (BAL) restrictions, allowing immediate full-screen
 * activity display even while other applications are in the foreground.
 */
public class OverlayPermissionHelper {

    private static final String TAG = "OverlayPermissionHelper";

    public static boolean canDrawOverlays(Context context) {
        if (context == null) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    public static void requestOverlayPermission(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                SpockLogger.error(TAG, "Failed to launch ACTION_MANAGE_OVERLAY_PERMISSION: " + e.getMessage());
            }
        }
    }
}
