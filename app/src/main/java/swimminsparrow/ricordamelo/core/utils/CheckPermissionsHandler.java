package swimminsparrow.ricordamelo.core.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;


/**
 * This Class contains all functions that verifies permissions.
 *
 * @author swimminsparrow
 * @version 1 01/11/18
 */

public class CheckPermissionsHandler {

    public static boolean isNotificationGranted(Context c) {
        // Check if we're on Android 13 (API 33) or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission((Activity) c,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Below Android 13, notification permission is granted by default
            // when the app is installed
            return true;
        }
    }

    public static boolean isCameraGranted(Context c) {
        return ContextCompat.checkSelfPermission((Activity) c,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
}
