package swimminsparrow.ricordamelo.core.protection;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.notification.AppNotificationManager;
import swimminsparrow.ricordamelo.core.sos.SOSManager;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;
import swimminsparrow.ricordamelo.ui.sos.FallAlertActivity;

/**
 * Controller for managing the Fall Detection Pre-Alarm countdown (Grace Period).
 * Plays an alert ringtone and vibration, presents a 30-second countdown notification
 * and full-screen UI, and triggers the emergency SOS only if the user does not cancel in time.
 */
public class FallAlertController {

    private static final String TAG = "FallAlertController";
    public static final int COUNTDOWN_SECONDS = 30;

    public interface AlertListener {
        void onTick(int secondsRemaining);
        void onAlertCancelled();
        void onAlertConfirmed();
    }

    private static FallAlertController instance;

    private boolean isAlerting = false;
    private int secondsRemaining = COUNTDOWN_SECONDS;
    private CountDownTimer countDownTimer;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private AlertListener listener;
    private Context appContext;

    private FallAlertController() {}

    public static synchronized FallAlertController getInstance() {
        if (instance == null) {
            instance = new FallAlertController();
        }
        return instance;
    }

    public synchronized boolean isAlerting() {
        return isAlerting;
    }

    public synchronized int getSecondsRemaining() {
        return secondsRemaining;
    }

    public synchronized void setAlertListener(AlertListener listener) {
        this.listener = listener;
    }

    public synchronized void startAlert(Context context) {
        if (isAlerting) {
            SpockLogger.info(TAG, "Fall alert already in progress, ignoring duplicate start");
            return;
        }

        this.appContext = context.getApplicationContext();
        isAlerting = true;
        secondsRemaining = COUNTDOWN_SECONDS;
        SpockLogger.info(TAG, "Starting Fall Detection Pre-Alarm countdown: " + COUNTDOWN_SECONDS + "s");

        // 1. Acquire WakeLock
        try {
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                        "pillsos:FallAlertWakeLock"
                );
                wakeLock.acquire((COUNTDOWN_SECONDS + 15) * 1000L);
            }
        } catch (Exception e) {
            SpockLogger.error(TAG, "Failed to acquire wake lock: " + e.getMessage());
        }

        // 2. Start Alert Sound & Looping Vibration
        startSoundAndVibration(appContext);

        // 2.5. Speak fall detection voice warning (1 single time).
        // Intentionally bypasses the user's getTTSMode() setting — this is an emergency
        // announcement that must play regardless of TTS preference.
        try {
            String speakText = appContext.getString(R.string.fall_detected_tts_announcement);
            swimminsparrow.ricordamelo.PillSOS.getTtsManager(appContext).speak(speakText);
        } catch (Exception e) {
            SpockLogger.error(TAG, "Error announcing fall detection via TTS: " + e.getMessage());
        }

        // 3. Post Notification & Full Screen Intent via AppNotificationManager
        PendingIntent fullScreenPending = AppNotificationManager.getInstance()
                .showFallPreAlarmNotification(appContext, COUNTDOWN_SECONDS);


        // 4. Trigger FallAlertActivity launch across all device states.
        //
        // Android Background Activity Launch (BAL) strategy:
        //   1. Foreground: Direct startActivity() from currentActivity (exempt from BAL).
        //   2. Overlay Permission: If SYSTEM_ALERT_WINDOW is granted, the app is 100% exempt from BAL,
        //      allowing immediate full-screen takeover directly over other open apps.
        //   3. Locked / Screen Off: Direct startActivity() is attempted, and the notification's
        //      setFullScreenIntent (with creator MODE_BACKGROUND_ACTIVITY_START_ALLOWED) wakes the display
        //      and surfaces FallAlertActivity over the lock screen.
        //   4. Background without Overlay Permission (unlocked): Notification displays as a high-priority
        //      Heads-Up banner so the user can immediately tap "Sto bene" or "Invia SOS".

        Intent fullScreenIntent = new Intent(appContext, FallAlertActivity.class);
        fullScreenIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        android.app.Activity currentActivity = swimminsparrow.ricordamelo.PillSOS.getCurrentActivity();
        if (currentActivity != null && !currentActivity.isFinishing()) {
            try {
                currentActivity.startActivity(fullScreenIntent);
                SpockLogger.info(TAG, "Started FallAlertActivity from foreground activity: "
                        + currentActivity.getClass().getSimpleName());
            } catch (Exception e) {
                SpockLogger.error(TAG, "Could not start FallAlertActivity from currentActivity: " + e.getMessage());
            }
        } else if (swimminsparrow.ricordamelo.core.utils.OverlayPermissionHelper.canDrawOverlays(appContext)) {
            try {
                appContext.startActivity(fullScreenIntent);
                SpockLogger.info(TAG, "Started FallAlertActivity over other apps via SYSTEM_ALERT_WINDOW overlay permission");
            } catch (Exception e) {
                SpockLogger.error(TAG, "Could not start FallAlertActivity via overlay permission: " + e.getMessage());
            }
        } else {
            android.app.KeyguardManager km = (android.app.KeyguardManager) appContext.getSystemService(Context.KEYGUARD_SERVICE);
            android.os.PowerManager pm = (android.os.PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            boolean isLocked = km != null && km.isKeyguardLocked();
            boolean isInteractive = pm != null && pm.isInteractive();

            if (!isInteractive || isLocked) {
                try {
                    appContext.startActivity(fullScreenIntent);
                    SpockLogger.info(TAG, "Device is locked/sleeping — started FallAlertActivity directly");
                } catch (Exception e) {
                    SpockLogger.info(TAG, "Direct launch when locked failed; notification fullScreenIntent will handle it: " + e.getMessage());
                }
            } else {
                SpockLogger.info(TAG, "App in background without overlay permission — notification will alert the user");
            }
        }


        // 5. Start 30-Second Countdown Timer on Main Thread (updates Activity UI without notification spam)
        new Handler(Looper.getMainLooper()).post(() -> {
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            countDownTimer = new CountDownTimer(COUNTDOWN_SECONDS * 1000L, 1000L) {
                @Override
                public void onTick(long millisUntilFinished) {
                    secondsRemaining = (int) Math.round(millisUntilFinished / 1000.0);
                    synchronized (FallAlertController.this) {
                        if (listener != null) {
                            listener.onTick(secondsRemaining);
                        }
                    }
                }

                @Override
                public void onFinish() {
                    secondsRemaining = 0;
                    synchronized (FallAlertController.this) {
                        if (listener != null) {
                            listener.onTick(0);
                        }
                    }
                    confirmAndSendSOS(appContext);
                }
            };
            countDownTimer.start();
        });
    }

    public synchronized void cancelAlert(Context context) {
        if (!isAlerting) return;
        SpockLogger.info(TAG, "Fall alert CANCELLED by user (Sto bene)");

        stopTimerAndMedia();
        isAlerting = false;

        Context appContext = context.getApplicationContext();

        // Dismiss Notification
        AppNotificationManager.getInstance().cancelFallAlertNotification(appContext);

        if (listener != null) {
            listener.onAlertCancelled();
        }

        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(appContext, R.string.fall_alert_cancelled_msg, Toast.LENGTH_LONG).show()
        );
    }

    public synchronized void confirmAndSendSOS(Context context) {
        if (!isAlerting) return;
        SpockLogger.info(TAG, "Fall alert CONFIRMED -> Sending emergency SOS!");

        stopTimerAndMedia();
        isAlerting = false;

        Context appContext = context.getApplicationContext();

        if (listener != null) {
            listener.onAlertConfirmed();
        }

        boolean hasContacts = swimminsparrow.ricordamelo.core.sos.SOSContactsManager.hasConfiguredContacts(appContext);
        if (hasContacts) {
            SpockLogger.info(TAG, "Sending emergency SOS to configured contacts...");
            // Dispatch emergency message
            String msg = appContext.getString(R.string.fall_detected_sms_format, SettingsDB.getInstance(appContext).getPersonalName());
            SOSManager.sendSOSMessage(appContext, msg);

            // Show final notification confirming that SOS was sent
            AppNotificationManager.getInstance().showFallAlertConfirmedNotification(appContext);
        } else {
            SpockLogger.info(TAG, "Fall alert confirmed but NO SOS contacts are configured. Showing emergency dial notification.");
            AppNotificationManager.getInstance().showFallAlertNoContactsNotification(appContext);
        }
    }

    private void stopTimerAndMedia() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }

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

        try {
            swimminsparrow.ricordamelo.PillSOS.getTtsManager(appContext).stop();
        } catch (Exception ignored) {}

        // Restore pre-override volume levels
        if (appContext != null) {
            try {
                swimminsparrow.ricordamelo.core.utils.VolumeUtils.restoreVolume(appContext);
            } catch (Exception e) {
                SpockLogger.error(TAG, "Error restoring volume: " + e.getMessage());
            }
        }
    }

    private void startSoundAndVibration(Context context) {
        // 1. Override volume to max for the emergency fall alert (even if silent mode)
        try {
            swimminsparrow.ricordamelo.core.utils.VolumeUtils.overrideVolumeToMax(context, true);
        } catch (Exception e) {
            SpockLogger.error(TAG, "Error overriding volume to max: " + e.getMessage());
        }

        // 2. Sound
        try {
            Uri soundUri = SettingsDB.getInstance(context).getSoundUri();
            if (soundUri == null) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            }
            if (soundUri == null) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(context, soundUri);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            SpockLogger.error(TAG, "Error playing fall alert sound: " + e.getMessage());
        }

        // Vibration
        try {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = new long[]{0, 500, 250, 500, 750};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception e) {
            SpockLogger.error(TAG, "Error starting vibrator: " + e.getMessage());
        }
    }
}
