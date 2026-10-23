package swimminsparrow.ricordamelo.services;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.data.dao.FarmacoDao;
import swimminsparrow.ricordamelo.core.data.models.Farmaco;
import swimminsparrow.ricordamelo.core.notification.AppNotificationManager;
import swimminsparrow.ricordamelo.core.sos.SOSManager;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;

public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_ALARM_NOTIFICATION_SWIPED = "swimminsparrow.ricordamelo.ACTION_ALARM_NOTIFICATION_SWIPED";
    public static final String ACTION_SOS_NOTIFICATION_SWIPED = "swimminsparrow.ricordamelo.ACTION_SOS_NOTIFICATION_SWIPED";

    private Context context;

    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;

        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_ALARM_NOTIFICATION_SWIPED.equals(action)) {
                int id = intent.getIntExtra("ID", -1);
                String orario = intent.getStringExtra("Orario");
                if (swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().isRinging()) {
                    AppNotificationManager.getInstance().showAlarmNotification(context, id, orario);
                }
                return;
            } else if (ACTION_SOS_NOTIFICATION_SWIPED.equals(action)) {
                if (swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().isRinging()) {
                    String username = intent.getStringExtra("username");
                    String topic = intent.getStringExtra("topic");
                    String message = intent.getStringExtra("message");
                    AppNotificationManager.getInstance().showSosAlert(context, username, topic, message != null ? message : "");
                }
                return;
            }
        }

        int idRicevuto = intent != null ? intent.getIntExtra("ID", -1) : -1;
        String orario = intent != null ? intent.getStringExtra("Orario") : null;

        SpockLogger.info(this.getClass().getSimpleName(),
                "Alarm received {ID: " + idRicevuto + ", Orario: " + orario + "}");

        // Wake screen & CPU for full screen alarm
        try {
            android.os.PowerManager pm = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                android.os.PowerManager.WakeLock wakeLock = pm.newWakeLock(
                        android.os.PowerManager.FULL_WAKE_LOCK |
                                android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP |
                                android.os.PowerManager.ON_AFTER_RELEASE,
                        "Ricordamelo:AlarmWakeLock");
                wakeLock.acquire(5000);
            }
        } catch (Exception e) {
            SpockLogger.errorLogWithToast(context, ReminderReceiver.class, "WakeLock error", e);
        }

        // 1. Show notification via AppNotificationManager
        AppNotificationManager.getInstance().showAlarmNotification(context, idRicevuto, orario);

        // 1.5. TTS Readout
        if (swimminsparrow.ricordamelo.core.data.SettingsDB.getInstance(context).getTTSMode()) {
            swimminsparrow.ricordamelo.core.data.models.Farmaco farmaco = swimminsparrow.ricordamelo.core.data.dao.FarmacoDao
                    .getInstance(context).getById(idRicevuto);
            if (farmaco != null && !farmaco.getNome().isEmpty()) {
                String speakText = context.getString(swimminsparrow.ricordamelo.R.string.tts_reminder_text,
                        farmaco.getNome());
                swimminsparrow.ricordamelo.PillSOS.getTtsManager(context).speakRepeating(speakText, 15000L);
            }
        }

        // 1.8. Schedule Missed Alarm alert (active by default if SOS contacts are configured)
        if (swimminsparrow.ricordamelo.core.data.SettingsDB.getInstance(context).isSOSSendEnabled()) {
            Intent missedIntent = new Intent(context, MissedAlarmReceiver.class);
            missedIntent.putExtra("ID", idRicevuto);
            PendingIntent missedPending = swimminsparrow.ricordamelo.core.utils.PendingIntentHelper
                    .getBroadcast(context, idRicevuto, missedIntent, true);

            android.app.AlarmManager alarmManager = (android.app.AlarmManager) context
                    .getSystemService(Context.ALARM_SERVICE);
            long alarmDurationMs = swimminsparrow.ricordamelo.core.data.SettingsDB.getInstance(context)
                    .getAlarmDuration();
            long triggerTime = System.currentTimeMillis() + alarmDurationMs;
            if (alarmManager != null) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, missedPending);
            }
        }

        // 2. Reschedule all alarms for next occurrence in background thread
        final BroadcastReceiver.PendingResult pendingResult = goAsync();
        swimminsparrow.ricordamelo.core.utils.AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                ReminderScheduler.refreshAlarms(context.getApplicationContext());
            } finally {
                pendingResult.finish();
            }
        });
    }
}
