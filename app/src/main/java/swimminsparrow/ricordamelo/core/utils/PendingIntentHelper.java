package swimminsparrow.ricordamelo.core.utils;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Utility to centralize PendingIntent creation with correct Android 12+ (API 31+) mutability flags.
 */
public final class PendingIntentHelper {

    private PendingIntentHelper() {
    }

    /**
     * Creates a broadcast PendingIntent with FLAG_UPDATE_CURRENT and correct mutability flags.
     */
    public static PendingIntent getBroadcast(Context context, int requestCode, Intent intent, boolean isMutable) {
        int flags = getFlags(isMutable);
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    /**
     * Creates an activity PendingIntent with FLAG_UPDATE_CURRENT and correct mutability flags.
     */
    public static PendingIntent getActivity(Context context, int requestCode, Intent intent, boolean isMutable) {
        int flags = getFlags(isMutable);
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }

    /**
     * Returns FLAG_UPDATE_CURRENT combined with FLAG_MUTABLE or FLAG_IMMUTABLE on API 31+.
     */
    public static int getFlags(boolean isMutable) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= isMutable ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
