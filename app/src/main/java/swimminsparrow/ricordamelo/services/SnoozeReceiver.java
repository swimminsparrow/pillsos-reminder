package swimminsparrow.ricordamelo.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import swimminsparrow.ricordamelo.core.notification.AppNotificationManager;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;

public class SnoozeReceiver extends BroadcastReceiver {
    private static final String TAG = "SnoozeReceiver";

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
                } catch (Exception ignored) {}
            }
        }
        String orario = intent != null ? intent.getStringExtra("Orario") : null;
        int snoozeMinutes = intent != null ? intent.getIntExtra("snooze_minutes", 15) : 15;
        SpockLogger.info(TAG, "Snooze received for ID: " + id + ", mins: " + snoozeMinutes);
        AppNotificationManager.getInstance().snoozeAlarm(context, id, orario, snoozeMinutes);
    }
}
