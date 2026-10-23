package swimminsparrow.ricordamelo;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.ui.alarms.MainActivity;
import swimminsparrow.ricordamelo.core.utils.TTSManager;

public class PillSOS extends Application {
    private static volatile TTSManager ttsManager;
    private static java.lang.ref.WeakReference<Activity> currentActivityRef;

    public static Activity getCurrentActivity() {
        return currentActivityRef != null ? currentActivityRef.get() : null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState) {}
            @Override
            public void onActivityStarted(Activity activity) {}
            @Override
            public void onActivityResumed(Activity activity) {
                currentActivityRef = new java.lang.ref.WeakReference<>(activity);
            }
            @Override
            public void onActivityPaused(Activity activity) {
                if (currentActivityRef != null && currentActivityRef.get() == activity) {
                    currentActivityRef = null;
                }
            }
            @Override
            public void onActivityStopped(Activity activity) {}
            @Override
            public void onActivitySaveInstanceState(Activity activity, android.os.Bundle outState) {}
            @Override
            public void onActivityDestroyed(Activity activity) {
                if (currentActivityRef != null && currentActivityRef.get() == activity) {
                    currentActivityRef = null;
                }
            }
        });

        // Prefer IPv4 stack to prevent public ntfy / ISP IPv6 rate-limit / 429 issues
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
        swimminsparrow.ricordamelo.core.utils.ForceIPv4SSLSocketFactory.installAsDefault();

        createNotificationChannels();

        // Initialize TTS in background so it's ready when an alarm fires
        if (swimminsparrow.ricordamelo.core.data.SettingsDB.getInstance(this).getTTSMode()) {
            ttsManager = new TTSManager(this);
        }

        // Start listening for connection requests
        swimminsparrow.ricordamelo.core.sos.ConnectionRequestManager.startListening(this);

        // Sync protection service so SOS network listening is active whenever app
        // initializes
        try {
            swimminsparrow.ricordamelo.services.ProtectionService.sync(this);
        } catch (Exception e) {
            android.util.Log.e("PillSOS", "Failed to sync ProtectionService on app init: " + e.getMessage());
        }
    }

    public static TTSManager getTtsManager(Context context) {
        if (ttsManager == null) {
            ttsManager = new TTSManager(context.getApplicationContext());
        }
        return ttsManager;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {

                // Clean up legacy channel so old badge settings are not retained.
                // Wrapped in try/catch: Android throws SecurityException if the channel
                // is still bound to a running foreground service.
                try {
                    manager.deleteNotificationChannel("pillsos_service_channel");
                } catch (Exception ignored) {}

                // Silent - for persistent foreground services (no badge on launcher icon)
                NotificationChannel serviceChannel = new NotificationChannel(
                        Constants.SERVICE_CHANNEL_ID,
                        getString(R.string.app_name),
                        NotificationManager.IMPORTANCE_LOW);
                serviceChannel.setShowBadge(false);
                serviceChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

                // Max priority - for alarms and SOS
                NotificationChannel existingAlarmChannel = manager.getNotificationChannel(Constants.ALARM_CHANNEL_ID);
                if (existingAlarmChannel != null && (existingAlarmChannel.getVibrationPattern() == null || existingAlarmChannel.getVibrationPattern().length <= 2)) {
                    try {
                        manager.deleteNotificationChannel(Constants.ALARM_CHANNEL_ID);
                    } catch (Exception ignored) {}
                }

                NotificationChannel alarmChannel = new NotificationChannel(
                        Constants.ALARM_CHANNEL_ID,
                        getString(R.string.app_name), // e.g. "Reminders & SOS"
                        NotificationManager.IMPORTANCE_MAX);
                alarmChannel.setShowBadge(true);
                alarmChannel.enableVibration(true);
                alarmChannel.setVibrationPattern(new long[] { 0, 400, 200, 400 });
                alarmChannel.setBypassDnd(true);
                alarmChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

                // SOS & Connection Requests - High priority with sound, vibration, and heads-up
                // banner
                NotificationChannel sosChannel = new NotificationChannel(
                        Constants.SOS_CHANNEL_ID,
                        getString(R.string.sos_channel_name),
                        NotificationManager.IMPORTANCE_HIGH);
                sosChannel.setShowBadge(true);
                sosChannel.enableVibration(true);
                sosChannel.setVibrationPattern(new long[] { 0, 400, 200, 400 });
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                sosChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes);
                sosChannel.setBypassDnd(true);
                sosChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

                // Fall Detection Pre-Alarm - Emergency full-screen channel.
                // Sound is intentionally set to null here because FallAlertController
                // manages audio directly via MediaPlayer to avoid double-play.
                // Vibration is kept so the notification itself still pulses the device
                // when the MediaPlayer isn't audible (e.g. MediaPlayer error).
                NotificationChannel fallChannel = new NotificationChannel(
                        Constants.FALL_CHANNEL_ID,
                        getString(R.string.fall_alert_title),
                        NotificationManager.IMPORTANCE_MAX);
                fallChannel.setShowBadge(true);
                fallChannel.enableVibration(false); // Vibration handled by FallAlertController
                fallChannel.setSound(null, null);   // Audio handled by FallAlertController MediaPlayer
                fallChannel.setBypassDnd(true);
                fallChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

                manager.createNotificationChannel(serviceChannel);
                manager.createNotificationChannel(alarmChannel);
                manager.createNotificationChannel(sosChannel);
                manager.createNotificationChannel(fallChannel);
            }
        }
    }

    public static void callRicordamelo(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("skip_splash", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);// close all the others?
        context.startActivity(intent);
        if (context instanceof Activity) {
            ((Activity) context).finish();
        }
    }

}