package swimminsparrow.ricordamelo.core.utils;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
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

/**
 * Centralized Alarm Ringer controller.
 * Manages sound playback with repeat delay, looping vibration, and auto-dismiss
 * timing according to user settings.
 * Ensures consistent behavior whether the alarm is displayed via Heads-up
 * Notification or FullAlarmActivity.
 */
public class AlarmRinger {
    private static final String TAG = "AlarmRinger";
    private static final long REPEAT_DELAY_MS = 3000L; // 3 seconds repeat delay between sound plays
    private static final long VIBRATION_DURATION_MS = 800L; // 800 ms vibration pulse
    private static final long VIBRATION_DELAY_MS = 4000L; // 4 seconds pause between vibrations

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

    private AlarmRinger() {
    }

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

        // If already ringing for this exact alarm, continue ongoing playback without
        // restart
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

        // 2. Override alarm stream volume to max (SOS always forces max volume;
        // max_volume mode overrides)
        if (isSosAlert || settingsDB.isMaxVolumeMode()) {
            VolumeUtils.overrideVolumeToMax(appContext, isSosAlert);
        }

        // Determine if sound and vibration should play according to setting and system
        // state
        AudioManager audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager != null ? audioManager.getRingerMode() : AudioManager.RINGER_MODE_NORMAL;

        boolean shouldPlaySound;
        boolean shouldVibrate;

        if (isSosAlert || settingsDB.isMaxVolumeMode()) {
            // Maximum volume mode or SOS emergency: always play looping sound and
            // continuous vibration
            shouldPlaySound = true;
            shouldVibrate = true;
        } else {
            // System mode ("Segui impostazioni di sistema"):
            // If system has ringer enabled -> loop custom sound at system volume
            // If system has vibration enabled -> loop vibration
            // If silent mode -> no sound, no vibration
            shouldPlaySound = (ringerMode == AudioManager.RINGER_MODE_NORMAL);
            shouldVibrate = (ringerMode != AudioManager.RINGER_MODE_SILENT);
        }

        // 3. Play sound with repeat delay (loops custom ringtone)
        if (shouldPlaySound) {
            startSound(appContext);
        }

        // 4. Start continuous vibration looping
        if (shouldVibrate) {
            try {
                vibrator = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    long[] pattern = swimminsparrow.ricordamelo.core.notification.AppNotificationManager.VIBRATION_PATTERN;
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
            SpockLogger.info(TAG,
                    "Alarm duration reached (" + alarmDurationMs + " ms). Auto-stopping alarm " + alarmId);

            // Stop ringing, vibrating, release wakelock, and restore volume
            stop(appContext);

            // Cancel the active alarm notification from status bar / drawer so it does not
            // linger after timeout
            swimminsparrow.ricordamelo.core.notification.AppNotificationManager.getInstance()
                    .cancelNotificationOnly(appContext, alarmId);

            // Cancel the AlarmManager fallback to prevent duplicate triggers
            try {
                Intent missedIntent = new Intent(appContext,
                        swimminsparrow.ricordamelo.services.MissedAlarmReceiver.class);
                android.app.PendingIntent missedPending = swimminsparrow.ricordamelo.core.utils.PendingIntentHelper
                        .getBroadcast(appContext, alarmId, missedIntent, true);
                android.app.AlarmManager am = (android.app.AlarmManager) appContext
                        .getSystemService(Context.ALARM_SERVICE);
                if (am != null) {
                    am.cancel(missedPending);
                }
            } catch (Exception ignored) {
            }

            // Send MissedAlarm broadcast so SOS is dispatched for the ignored alarm
            if (alarmId != -1 && alarmId != Constants.TEST_ALARM_NOTIFICATION_ID &&
                    alarmId != swimminsparrow.ricordamelo.core.notification.AppNotificationManager.ID_SOS_ALERT) {
                Intent missedBroadcast = new Intent(appContext,
                        swimminsparrow.ricordamelo.services.MissedAlarmReceiver.class);
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

    /**
     * Ensures the continuous looping vibration remains actively vibrating.
     * Prevents system notification services from killing the vibration loop during
     * heads-up banner transitions.
     */
    public synchronized void ensureVibrating(Context context) {
        if (!isRinging)
            return;
        Context appContext = context.getApplicationContext();
        SettingsDB settingsDB = SettingsDB.getInstance(appContext);
        boolean isSosAlert = (activeAlarmId == swimminsparrow.ricordamelo.core.notification.AppNotificationManager.ID_SOS_ALERT);
        AudioManager audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager != null ? audioManager.getRingerMode() : AudioManager.RINGER_MODE_NORMAL;

        boolean shouldVibrate;
        if (isSosAlert || settingsDB.isMaxVolumeMode()) {
            shouldVibrate = true;
        } else {
            shouldVibrate = (ringerMode != AudioManager.RINGER_MODE_SILENT);
        }

        if (shouldVibrate) {
            try {
                if (vibrator == null) {
                    vibrator = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
                }
                if (vibrator != null && vibrator.hasVibrator()) {
                    long[] pattern = swimminsparrow.ricordamelo.core.notification.AppNotificationManager.VIBRATION_PATTERN;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                    } else {
                        vibrator.vibrate(pattern, 0);
                    }
                    SpockLogger.info(TAG, "ensureVibrating: Vibrator running waveform pattern successfully");
                }
            } catch (Exception e) {
                SpockLogger.info(TAG, "Error in ensureVibrating: " + e.getMessage());
            }
        }
    }

    private synchronized void startSound(Context appContext) {
        if (!isRinging)
            return;
        SettingsDB settingsDB = SettingsDB.getInstance(appContext);
        boolean isSosAlert = (activeAlarmId == swimminsparrow.ricordamelo.core.notification.AppNotificationManager.ID_SOS_ALERT);
        boolean isMaxVolume = isSosAlert || settingsDB.isMaxVolumeMode();

        releaseMediaPlayer();

        int usage = isMaxVolume ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_NOTIFICATION;

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK);
            mediaPlayer.setDataSource(appContext, settingsDB.getSoundUri());
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mediaPlayer.setLooping(false);
            mediaPlayer.setOnCompletionListener(mp -> scheduleNextPlay(appContext));
            mediaPlayer.prepare();
            mediaPlayer.start();
            SpockLogger.info(TAG,
                    "MediaPlayer started playing with sound: " + settingsDB.getSoundUri() + " (usage=" + usage + ")");
        } catch (Exception e) {
            SpockLogger.info(TAG,
                    "Error starting MediaPlayer with custom uri: " + e.getMessage() + ", falling back to default");
            try {
                releaseMediaPlayer();
                mediaPlayer = MediaPlayer.create(appContext, R.raw.notifica);
                if (mediaPlayer != null) {
                    mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(usage)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build());
                    mediaPlayer.setLooping(false);
                    mediaPlayer.setOnCompletionListener(mp -> scheduleNextPlay(appContext));
                    mediaPlayer.start();
                    SpockLogger.info(TAG, "Fallback MediaPlayer started playing successfully (usage=" + usage + ")");
                }
            } catch (Exception ex) {
                SpockLogger.info(TAG, "Fallback MediaPlayer also failed: " + ex.getMessage());
            }
        }
    }

    private synchronized void scheduleNextPlay(Context appContext) {
        if (!isRinging)
            return;
        if (repeatHandler == null) {
            repeatHandler = new Handler(Looper.getMainLooper());
        }
        if (repeatRunnable != null) {
            repeatHandler.removeCallbacks(repeatRunnable);
        }
        repeatRunnable = () -> {
            synchronized (AlarmRinger.this) {
                if (!isRinging)
                    return;
                try {
                    if (mediaPlayer != null) {
                        mediaPlayer.seekTo(0);
                        mediaPlayer.start();
                    } else {
                        startSound(appContext);
                    }
                } catch (Exception e) {
                    SpockLogger.info(TAG,
                            "Error re-starting MediaPlayer with seekTo: " + e.getMessage() + ", re-initializing");
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
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
    }

    public synchronized void stop(Context context) {
        SpockLogger.info(TAG, "stop() called, activeAlarmId=" + activeAlarmId);

        if (activeAlarmId == swimminsparrow.ricordamelo.core.notification.AppNotificationManager.ID_SOS_ALERT) {
            swimminsparrow.ricordamelo.core.notification.AppNotificationManager.getInstance().clearActiveSos();
        }

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
            } catch (Exception ignored) {
            }
            vibrator = null;
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {
            }
            wakeLock = null;
        }

        if (context != null) {
            VolumeUtils.restoreVolume(context.getApplicationContext());
            try {
                swimminsparrow.ricordamelo.PillSOS.getTtsManager(context.getApplicationContext()).stopRepeating();
            } catch (Exception ignored) {
            }
        }

        if (eventListener != null) {
            AlarmEventListener listener = eventListener;
            new Handler(Looper.getMainLooper()).post(listener::onAlarmAutoDismissed);
        }
    }

    public synchronized boolean isRinging() {
        return isRinging;
    }

    public synchronized int getActiveAlarmId() {
        return activeAlarmId;
    }
}
