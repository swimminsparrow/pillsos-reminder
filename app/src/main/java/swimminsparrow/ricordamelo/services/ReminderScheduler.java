package swimminsparrow.ricordamelo.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.data.dao.FarmacoDao;
import swimminsparrow.ricordamelo.core.data.models.Farmaco;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;

public class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";

    public static synchronized void refreshAlarms(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        int alarmCount = 0;
        GregorianCalendar now = (GregorianCalendar) GregorianCalendar.getInstance();
        List<Farmaco> farmaci = FarmacoDao.getInstance(context.getApplicationContext()).getAll();

        for (Farmaco farmaco : farmaci) {
            alarmCount = scheduleAlarmsForFarmaco(context, alarmManager, farmaco, now, alarmCount);
        }

        cleanPreviousRemainingAlarms(context, alarmManager, alarmCount);
    }

    private static int scheduleAlarmsForFarmaco(Context context, AlarmManager alarmManager,
                                                 Farmaco farmaco, GregorianCalendar now, int alarmCount) {
        GregorianCalendar whenToFinish = new GregorianCalendar();
        whenToFinish.setTimeInMillis(farmaco.getWhenToFinish());

        for (int j = 0; j < farmaco.getHourSize(); j++) {
            int hour    = Integer.parseInt(farmaco.getHour(j));
            int minutes = Integer.parseInt(farmaco.getMinute(j));

            if (farmaco.isEveryDayAlarm()) {
                alarmCount = scheduleDailyAlarm(context, alarmManager, farmaco, j, hour, minutes, now, whenToFinish, alarmCount);
            } else {
                alarmCount = scheduleWeeklyAlarms(context, alarmManager, farmaco, j, hour, minutes, now, whenToFinish, alarmCount);
            }
        }
        return alarmCount;
    }

    private static int scheduleDailyAlarm(Context context, AlarmManager alarmManager,
                                          Farmaco farmaco, int timeIndex,
                                          int hour, int minutes,
                                          GregorianCalendar now,
                                          GregorianCalendar whenToFinish, int alarmCount) {
        GregorianCalendar beginTime = (GregorianCalendar) now.clone();
        beginTime.set(Calendar.HOUR_OF_DAY, hour);
        beginTime.set(Calendar.MINUTE, minutes);
        beginTime.set(Calendar.SECOND, 0);
        beginTime.set(Calendar.MILLISECOND, 0);

        if (beginTime.compareTo(now) <= 0)
            beginTime.add(Calendar.DAY_OF_YEAR, 1);

        if (farmaco.isForever() || whenToFinish.after(beginTime)) {
            addAlarm(context, alarmManager, farmaco.getId(), farmaco.getNome(),
                    farmaco.getHour(timeIndex), farmaco.getMinute(timeIndex),
                    beginTime.getTimeInMillis(), alarmCount,
                    whenToFinish);
            alarmCount++;
        }
        return alarmCount;
    }

    private static int scheduleWeeklyAlarms(Context context, AlarmManager alarmManager,
                                           Farmaco farmaco, int timeIndex,
                                           int hour, int minutes,
                                           GregorianCalendar now,
                                           GregorianCalendar whenToFinish, int alarmCount) {
        for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
            if (farmaco.getDay(dayIndex) != 1) continue;

            GregorianCalendar beginTime = (GregorianCalendar) now.clone();
            beginTime.set(Calendar.HOUR_OF_DAY, hour);
            beginTime.set(Calendar.MINUTE, minutes);
            beginTime.set(Calendar.SECOND, 0);
            beginTime.set(Calendar.MILLISECOND, 0);

            int currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK) - 1;
            beginTime.add(Calendar.DAY_OF_YEAR, dayIndex - currentDayOfWeek);

            if (!beginTime.after(now))
                beginTime.add(Calendar.DAY_OF_YEAR, 7);

            if (farmaco.isForever() || whenToFinish.after(beginTime)) {
                addAlarm(context, alarmManager, farmaco.getId(), farmaco.getNome(),
                        farmaco.getHour(timeIndex), farmaco.getMinute(timeIndex),
                        beginTime.getTimeInMillis(), alarmCount,
                        whenToFinish);
                alarmCount++;
            }
        }
        return alarmCount;
    }

    private static void addAlarm(Context context, AlarmManager alarmManager,
                                 int farmacoId, String nome, String hour, String minute,
                                 long nextTime, int alarmId,
                                 GregorianCalendar whenToFinish) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("ID", farmacoId);
        intent.putExtra("Orario", hour + ":" + minute);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        PendingIntent pendingIntent = buildPendingIntent(context, intent, alarmId,
                PendingIntent.FLAG_UPDATE_CURRENT);

        alarmManager.cancel(pendingIntent);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent);
            }

            GregorianCalendar nextTimeCal = (GregorianCalendar) GregorianCalendar.getInstance();
            nextTimeCal.setTimeInMillis(nextTime);
            SpockLogger.info(TAG, "Alarm added: {"
                    + "farmacoId=" + farmacoId
                    + ", alarmId=" + alarmId
                    + ", name=" + nome
                    + ", time=" + hour + ":" + minute
                    + ", next=" + nextTimeCal.getTime()
                    + ", expires=" + whenToFinish.getTime() + "}");

        } catch (SecurityException e) {
            SpockLogger.info(TAG, "SecurityException adding exact alarm, falling back: " + e.getMessage());
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent);
                }
            } catch (Exception ex) {
                SpockLogger.info(TAG, "Failed to schedule fallback alarm: " + ex.getMessage());
            }
        }
    }

    private static void cleanPreviousRemainingAlarms(Context context, AlarmManager alarmManager, int alarmCount) {
        int previousCount = SettingsDB.getInstance(context.getApplicationContext()).getPreviousAlarmsCount();
        SettingsDB.getInstance(context.getApplicationContext()).setPreviousAlarmsCount(alarmCount);

        for (int i = alarmCount; i <= previousCount; i++) {
            Intent intent = new Intent(context, ReminderReceiver.class);
            PendingIntent pendingIntent = buildPendingIntent(context, intent, i,
                    PendingIntent.FLAG_CANCEL_CURRENT);
            try {
                alarmManager.cancel(pendingIntent);
                SpockLogger.info(TAG, "Cancelled stale alarm id: " + i);
            } catch (SecurityException e) {
                SpockLogger.info(TAG, "SecurityException cancelling alarm: " + e.getMessage());
            }
        }
    }

    private static PendingIntent buildPendingIntent(Context context, Intent intent, int id, int extraFlags) {
        return swimminsparrow.ricordamelo.core.utils.PendingIntentHelper.getBroadcast(context, id, intent, true);
    }
}
