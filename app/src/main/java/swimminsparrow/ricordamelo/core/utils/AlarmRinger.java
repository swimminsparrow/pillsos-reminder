package swimminsparrow.ricordamelo.core.utils;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.app.NotificationManagerCompat;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.services.NotificationDismissReceiver;

/**
 * Centralized Alarm Ringer controller.
 * Manages sound playback with repeat delay, looping vibration, and auto-dismiss timing according to user settings.
 * Ensures consistent behavior whether the alarm is displayed via Heads-up Notification or FullAlarmActivity.
 */
public class AlarmRinger {
    private static final String TAG = "AlarmRinger";
    private static final long REPEAT_DELAY_MS = 3000L; // 3 seconds repeat delay between sound plays
    private static final long VIBRATION_DURATION_MS = 1000L; // 1 second vibration
    private static final long VIBRATION_DELAY_MS = 2500L; // 2.5 seconds pause between vibrations

    private static AlarmRinger instance;

    public interface AlarmEventListener {
        void onAlarmAutoDismissed();
    }

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private Handler durationHandler;
    private Runnable durationRunnable;
    private Handler repeatHandler;
    private Runnable repeatRunnable;
    private PowerManager.WakeLock wakeLock;

    private int activeAlarmId = -1;
    private boolean isRinging = false;
    private AlarmEventListener eventListener;

    private AlarmRinger() {}

    public static synchronized AlarmRinger getInstance() {
        if (instance == null) {
            instance = new AlarmRinger();
        }
        return instance;
    }

    public synchronized void setAlarmEventListener(AlarmEventListener listener) {
        this.eventListener = listener;
    }

    public synchronized void start(Context context, int alarmId) {
        Context appContext = context.getApplicationContext();
        SpockLogger.info(TAG, "start() requested for alarmId=" + alarmId + ", isRinging=" + isRinging);

        // If already ringing for this exact alarm, continue ongoing playback without restart
        if (isRinging && activeAlarmId == alarmId) {
            SpockLogger.info(TAG, "Already ringing for alarmId=" + alarmId + ", continuing.");
            return;
        }

        // If ringing for a previous alarm, stop it first
        if (isRinging) {
            stop(appContext);
        }

        activeAlarmId = alarmId;
        isRinging = true;

        SettingsDB settingsDB = SettingsDB.getInstance(appContext);
        long alarmDurationMs = settingsDB.getAlarmDuration();

        // 1. Partial WakeLock to keep CPU awake while alarm rings
        try {
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Ricordamelo:AlarmRingerWakeLock");
                wakeLock.acquire(alarmDurationMs + 5000);
            }
        } catch (Exception e) {
            SpockLogger.info(TAG, "WakeLock acquire error: " + e.getMessage());
        }

        boolean isSosAlert = (alarmId == swimminsparrow.ricordamelo.core.notification.AppNotificationManager.ID_SOS_ALERT);

        // 2. Override alarm stream volume to max (SOS always forces max volume like fall alert emergency)
        if (isSosAlert) {
            VolumeUtils.overrideVolumeToMax(appContext, true);
        } else if (settingsDB.isMaxVolumeMode()) {
            VolumeUtils.overrideVolumeToMax(appContext);
        }

        // 3. Play sound with repeat delay (SOS always sounds, ignoring silent mode)
        if (isSosAlert || !settingsDB.getSilentMode()) {
            startSound(appContext);
        }

        // 4. Start continuous vibration looping (SOS always vibrates)
        if (isSosAlert || settingsDB.getVibrationMode()) {
            try {
                vibrator = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    long[] pattern = new long[]{0, VIBRATION_DURATION_MS, VIBRATION_DELAY_MS};
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                    } else {
                        vibrator.vibrate(pattern, 0);
                    }
                    SpockLogger.info(TAG, "Vibrator started looping successfully");
                }
            } catch (Exception e) {
                SpockLogger.info(TAG, "Error starting Vibrator: " + e.getMessage());
            }
        }

        // 5. Start Duration Timeout using Durata Dell'allarme setting
        durationHandler = new Handler(Looper.getMainLooper());
        durationRunnable = () -> {
            SpockLogger.info(TAG, "Alarm duration reached (" + alarmDurationMs + " ms). Auto-stopping alarm " + alarmId);

            // Stop ringing, vibrating, release wakelock, and restore volume
            stop(appContext);

            // Keep notification showing until explicitly dismissed via turn-off button

            // Cancel the AlarmManager fallback to prevent duplicate triggers
            try {
                Intent missedIntent = new Intent(appContext, swimminsparrow.ricordamelo.services.MissedAlarmReceiver.class);
                android.app.PendingIntent missedPending = swimminsparrow.ricordamelo.core.utils.PendingIntentHelper
                        .getBroadcast(appContext, alarmId, missedIntent, true);
                android.app.AlarmManager am = (android.app.AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
                if (am != null) {
                    am.cancel(missedPending);
                }
            } catch (Exception ignored) {}

            // Send MissedAlarm broadcast so SOS is dispatched for the ignored alarm
            if (alarmId != -1 && alarmId != Constants.TEST_ALARM_NOTIFICATION_ID &&
                    alarmId != swimminsparrow.ricordamelo.core.notification.AppNotificationManager.ID_SOS_ALERT) {
                Intent missedBroadcast = new Intent(appContext, swimminsparrow.ricordamelo.services.MissedAlarmReceiver.class);
                missedBroadcast.putExtra("ID", alarmId);
                appContext.sendBroadcast(missedBroadcast);
            }

            // Notify attached listener (e.g. FullAlarmActivity) to finish UI
            if (eventListener != null) {
                eventListener.onAlarmAutoDismissed();
            }
        };
        durationHandler.postDelayed(durationRunnable, alarmDurationMs);
    }

    private synchronized void startSound(Context appContext) {
        if (!isRinging) return;
        SettingsDB settingsDB = SettingsDB.getInstance(appContext);
        boolean isSosAlert = (activeAlarmId == swimminsparrow.ricordamelo.core.notification.AppNotificationManager.ID_SOS_ALERT);
        if (!isSosAlert && settingsDB.getSilentMode()) return;

        releaseMediaPlayer();

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK);
            mediaPlayer.setDataSource(appContext, settingsDB.getSoundUri());
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mediaPlayer.setLooping(false);
            mediaPlayer.setOnCompletionListener(mp -> scheduleNextPlay(appContext));
            mediaPlayer.prepare();
            mediaPlayer.start();
            SpockLogger.info(TAG, "MediaPlayer started playing with sound: " + settingsDB.getSoundUri());
        } catch (Exception e) {
            SpockLogger.info(TAG, "Error starting MediaPlayer with custom uri: " + e.getMessage() + ", falling back to default");
            try {
                releaseMediaPlayer();
                mediaPlayer = MediaPlayer.create(appContext, R.raw.notifica);
                if (mediaPlayer != null) {
                    mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build());
                    mediaPlayer.setLooping(false);
                    mediaPlayer.setOnCompletionListener(mp -> scheduleNextPlay(appContext));
                    mediaPlayer.start();
                    SpockLogger.info(TAG, "Fallback MediaPlayer started playing successfully");
                }
            } catch (Exception ex) {
                SpockLogger.info(TAG, "Fallback MediaPlayer also failed: " + ex.getMessage());
            }
        }
    }

    private synchronized void scheduleNextPlay(Context appContext) {
        if (!isRinging) return;
        if (repeatHandler == null) {
            repeatHandler = new Handler(Looper.getMainLooper());
        }
        if (repeatRunnable != null) {
            repeatHandler.removeCallbacks(repeatRunnable);
        }
        repeatRunnable = () -> {
            synchronized (AlarmRinger.this) {
                if (!isRinging) return;
                try {
                    if (mediaPlayer != null) {
                        mediaPlayer.seekTo(0);
                        mediaPlayer.start();
                    } else {
                        startSound(appContext);
                    }
                } catch (Exception e) {
                    SpockLogger.info(TAG, "Error re-starting MediaPlayer with seekTo: " + e.getMessage() + ", re-initializing");
                    startSound(appContext);
                }
            }
        };
        repeatHandler.postDelayed(repeatRunnable, REPEAT_DELAY_MS);
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    public synchronized void stop(Context context) {
        SpockLogger.info(TAG, "stop() called, activeAlarmId=" + activeAlarmId);

        isRinging = false;
        activeAlarmId = -1;

        if (repeatHandler != null && repeatRunnable != null) {
            repeatHandler.removeCallbacks(repeatRunnable);
            repeatRunnable = null;
            repeatHandler = null;
        }

        if (durationHandler != null && durationRunnable != null) {
            durationHandler.removeCallbacks(durationRunnable);
            durationHandler = null;
            durationRunnable = null;
        }

        releaseMediaPlayer();

        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {}
            vibrator = null;
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
            wakeLock = null;
        }

        if (context != null) {
            VolumeUtils.restoreVolume(context.getApplicationContext());
            try {
                swimminsparrow.ricordamelo.PillSOS.getTtsManager(context.getApplicationContext()).stopRepeating();
            } catch (Exception ignored) {}
        }
    }

    public synchronized boolean isRinging() {
        return isRinging;
    }

    public synchronized int getActiveAlarmId() {
        return activeAlarmId;
    }
}
