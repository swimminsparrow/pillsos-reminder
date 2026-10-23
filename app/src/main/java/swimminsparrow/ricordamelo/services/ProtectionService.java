package swimminsparrow.ricordamelo.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.List;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.ntfy.NtfyManager;
import swimminsparrow.ricordamelo.core.protection.FallAlertController;
import swimminsparrow.ricordamelo.core.protection.FallDetector;
import swimminsparrow.ricordamelo.core.sos.SOSContact;
import swimminsparrow.ricordamelo.core.sos.SOSContactsManager;
import swimminsparrow.ricordamelo.core.sos.SOSManager;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;
import swimminsparrow.ricordamelo.ui.alarms.MainActivity;

/**
 * Unified background protection service that combines:
 * 1. SOS Network listening (Ntfy SSE stream - remoteMessaging)
 * 2. Fall Detection accelerometer monitoring (Sensor - health)
 *
 * Keeps exactly ONE persistent notification in the status bar/drawer.
 */
public class ProtectionService extends Service implements SensorEventListener {

    private static final String TAG = "ProtectionService";

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isSensorRegistered = false;

    private FallDetector fallDetector;

    /**
     * Starts or updates the ProtectionService.
     */
    public static void startOrUpdate(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, ProtectionService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent);
            } else {
                appContext.startService(intent);
            }
        } catch (Exception e) {
            SpockLogger.error(TAG, "Failed to start ProtectionService: " + e.getMessage());
        }
    }

    /**
     * Synchronizes service state: starts if at least one protection module is active,
     * stops if neither is enabled.
     */
    public static void sync(Context context) {
        if (context == null) return;
        boolean fallActive = SettingsDB.getInstance(context).getFallDetectionMode();
        boolean sosActive = SOSContactsManager.hasConfiguredContacts(context);

        if (fallActive || sosActive) {
            startOrUpdate(context);
        } else {
            try {
                context.stopService(new Intent(context, ProtectionService.class));
            } catch (Exception e) {
                SpockLogger.error(TAG, "Failed to stop ProtectionService: " + e.getMessage());
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // Start immediate foreground notification to avoid ForegroundServiceDidNotStartInTimeException
        boolean fallActive = SettingsDB.getInstance(this).getFallDetectionMode();
        boolean sosActive = SOSContactsManager.hasConfiguredContacts(this);
        updateForegroundNotification(fallActive, sosActive);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean fallActive = SettingsDB.getInstance(this).getFallDetectionMode();

        // Ensure connection request listener is active
        swimminsparrow.ricordamelo.core.sos.ConnectionRequestManager.startListening(getApplicationContext());

        // 1. SOS Network: Listen for all paired contacts
        List<SOSContact> contacts = SOSContactsManager.getContacts(getApplicationContext());
        for (SOSContact c : contacts) {
            if (c.getTopic() != null && !c.getTopic().isEmpty()) {
                NtfyManager ntfy = NtfyManager.getInstance(getApplicationContext(), c.getTopic());
                if (ntfy != null) ntfy.startReceiving();
            }
        }

        // 2. SOS Network: Listen on own userUUID
        String myUuid = SettingsDB.getInstance(getApplicationContext()).getUserUUID();
        if (myUuid != null && !myUuid.isEmpty() && !contacts.isEmpty()) {
            NtfyManager ntfy = NtfyManager.getInstance(getApplicationContext(), myUuid);
            if (ntfy != null) ntfy.startReceiving();
        }

        boolean sosActive = !contacts.isEmpty();

        // 4. If neither module is active, stop self
        if (!fallActive && !sosActive) {
            updateSensorState(false);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        // 5. Update Fall Detection sensor listener
        updateSensorState(fallActive);

        // 6. Update notification text and foreground service types
        updateForegroundNotification(fallActive, sosActive);

        return START_STICKY;
    }

    private void updateSensorState(boolean shouldBeActive) {
        if (sensorManager == null) {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        }
        if (sensorManager != null && accelerometer == null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        if (fallDetector == null) {
            fallDetector = new FallDetector(new FallDetector.Listener() {
                @Override
                public void onFallCandidateDetected() {
                    SpockLogger.info(TAG, "Possibile impatto rilevato, avvio verifica immobilità...");
                }

                @Override
                public void onFallConfirmed() {
                    SpockLogger.info(TAG, "Caduta confermata dal sensore! Avvio procedura di pre-allarme...");
                    handleFallDetected();
                }

                @Override
                public void onFallDiscarded(String reason) {
                    SpockLogger.info(TAG, "Allarme caduta scartato (falso positivo evitato): " + reason);
                }
            });
        }

        if (shouldBeActive && !isSensorRegistered && sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            isSensorRegistered = true;
            SpockLogger.info(TAG, "Fall detection sensor registered at SENSOR_DELAY_GAME (~50Hz)");
        } else if (!shouldBeActive && isSensorRegistered && sensorManager != null) {
            sensorManager.unregisterListener(this);
            isSensorRegistered = false;
            if (fallDetector != null) {
                fallDetector.reset();
            }
            SpockLogger.info(TAG, "Fall detection sensor unregistered");
        }
    }

    private void updateForegroundNotification(boolean fallActive, boolean sosActive) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        String contentText;
        if (fallActive && sosActive) {
            contentText = getString(R.string.protection_service_both);
        } else if (fallActive) {
            contentText = getString(R.string.protection_service_fall);
        } else if (sosActive) {
            contentText = getString(R.string.protection_service_sos);
        } else {
            contentText = getString(R.string.notification_subtitle);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.SERVICE_CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.icon)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                .setLocalOnly(true)
                .setAutoCancel(false)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            int serviceType = 0;
            if (sosActive) {
                serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING;
            }
            if (fallActive) {
                serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH;
            }
            if (serviceType == 0) {
                serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING;
            }
            try {
                startForeground(Constants.PROTECTION_NOTIFICATION_ID, builder.build(), serviceType);
            } catch (Exception e) {
                SpockLogger.error(TAG, "Failed startForeground with type " + serviceType + ": " + e.getMessage());
                // Fallback to REMOTE_MESSAGING (permitted on boot/background without while-in-use sensor requirements)
                try {
                    startForeground(Constants.PROTECTION_NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
                } catch (Exception ex) {
                    SpockLogger.error(TAG, "Fallback startForeground failed: " + ex.getMessage());
                }
            }
        } else {
            try {
                startForeground(Constants.PROTECTION_NOTIFICATION_ID, builder.build());
            } catch (Exception e) {
                SpockLogger.error(TAG, "startForeground failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && fallDetector != null) {
            double impactThreshold = SettingsDB.getInstance(this).getFallDetectionImpactThreshold();
            fallDetector.setImpactThreshold(impactThreshold);
            fallDetector.processSample(event.values[0], event.values[1], event.values[2], System.currentTimeMillis());
        }
    }

    private void handleFallDetected() {
        SpockLogger.info(TAG, "Caduta rilevata! Lancio countdown pre-allarme...");
        FallAlertController.getInstance().startAlert(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        updateSensorState(false);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
