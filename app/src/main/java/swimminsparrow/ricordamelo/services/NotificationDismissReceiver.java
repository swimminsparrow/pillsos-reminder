package swimminsparrow.ricordamelo.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

public class NotificationDismissReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra("ID", -1);
        
        // Stop audio, vibration, duration timer, and restore volume
        swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().stop(context);

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        if (id != -1) {
            nm.cancel(id);
        } else {
            nm.cancel(swimminsparrow.ricordamelo.config.Constants.TEST_ALARM_NOTIFICATION_ID);
        }

        // Recover old volume settings if sound was overridden
        swimminsparrow.ricordamelo.core.utils.VolumeUtils.restoreVolume(context);

        // Cancel Missed Alarm alert
        Intent missedIntent = new Intent(context, MissedAlarmReceiver.class);
        android.app.PendingIntent missedPending = swimminsparrow.ricordamelo.core.utils.PendingIntentHelper
                .getBroadcast(
                        context, id, missedIntent, true);

        android.app.AlarmManager alarmManager = (android.app.AlarmManager) context
                .getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(missedPending);
        }
    }
}