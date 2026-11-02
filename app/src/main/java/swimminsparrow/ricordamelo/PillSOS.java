package swimminsparrow.ricordamelo;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import swimminsparrow.ricordamelo.core.notification.AppNotificationManager;
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

        AppNotificationManager.getInstance().createNotificationChannels(this);

        // Initialize TTS in background so it's ready when an alarm fires.
        // If device has no TTS engines (e.g. AOSP emulator / pure de-Googled ROM),
        // auto-disable TTS mode to prevent broken state.
        if (swimminsparrow.ricordamelo.core.data.SettingsDB.getInstance(this).getTTSMode()) {
            if (TTSManager.isTTSEngineInstalled(this)) {
                ttsManager = new TTSManager(this);
            } else {
                swimminsparrow.ricordamelo.core.data.SettingsDB.getInstance(this).setTTSMode(false);
            }
        }

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