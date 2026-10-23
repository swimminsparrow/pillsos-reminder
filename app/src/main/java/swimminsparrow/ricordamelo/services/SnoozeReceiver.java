package swimminsparrow.ricordamelo.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationManagerCompat;

public class SnoozeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra("ID", -1);
        String orario = intent.getStringExtra("Orario");

        // Stop audio, vibration, duration timer, and restore volume
        swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().stop(context);

        // Dismiss the current notification
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
        PendingIntent missedPending = swimminsparrow.ricordamelo.core.utils.PendingIntentHelper.getBroadcast(
                context, id, missedIntent, true);
        
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(missedPending);

            // Schedule a snooze for 10 minutes (600,000 ms)
            Intent snoozeIntent = new Intent(context, ReminderReceiver.class);
            snoozeIntent.putExtra("ID", id);
            if (orario != null) {
                snoozeIntent.putExtra("Orario", orario);
            }

            PendingIntent snoozePending = swimminsparrow.ricordamelo.core.utils.PendingIntentHelper.getBroadcast(
                    context, id, snoozeIntent, true);

        int snoozeMinutes = intent.getIntExtra("snooze_minutes", 15);
        long triggerTime = System.currentTimeMillis() + ((long) snoozeMinutes * 60 * 1000);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, snoozePending);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, snoozePending);
            }
        }
    }
}
