package swimminsparrow.ricordamelo.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.notification.AppNotificationManager;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;

public class NotificationDismissReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationDismissReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        int id = -1;
        if (intent != null) {
            id = intent.getIntExtra("ID", -1);
            if (id == -1 && intent.getData() != null) {
                try {
                    String lastSegment = intent.getData().getLastPathSegment();
                    if (lastSegment != null) {
                        id = Integer.parseInt(lastSegment);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        SpockLogger.info(TAG, "Dismiss received for ID: " + id);
        AppNotificationManager.getInstance().dismissAlarm(context, id);
    }
}
