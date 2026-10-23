package swimminsparrow.ricordamelo.core.notification;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import android.view.View;
import android.widget.RemoteViews;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.core.data.dao.FarmacoDao;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.data.models.Farmaco;
import swimminsparrow.ricordamelo.core.utils.AlarmRinger;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;
import swimminsparrow.ricordamelo.core.utils.VolumeUtils;
import swimminsparrow.ricordamelo.services.FallAlertReceiver;
import swimminsparrow.ricordamelo.services.NotificationDismissReceiver;
import swimminsparrow.ricordamelo.services.ReminderReceiver;
import swimminsparrow.ricordamelo.services.SnoozeReceiver;
import swimminsparrow.ricordamelo.ui.alarms.FullAlarmActivity;
import swimminsparrow.ricordamelo.ui.alarms.MainActivity;
import swimminsparrow.ricordamelo.ui.sos.FallAlertActivity;

/**
 * Centralized manager for application notifications.
 * Encapsulates notification construction, channel selection, ID allocation,
 * and runtime permission checks.
 */
public class AppNotificationManager {

    private static final String TAG = "AppNotificationManager";

    // Unique Notification IDs to prevent collisions
    public static final int ID_SOS_ALERT = 1001;
    public static final int ID_IM_OK = 1002;
    public static final int ID_PUBLISH_RESULT = 1005;
    public static final int ID_OUTBOX_DELIVERED = 1006;

    private static volatile AppNotificationManager instance;

    public static AppNotificationManager getInstance() {
        if (instance == null) {
            synchronized (AppNotificationManager.class) {
                if (instance == null) {
                    instance = new AppNotificationManager();
                }
            }
        }
        return instance;
    }

    private AppNotificationManager() {
    }

    /**
     * Checks if notification permission is granted.
     */
    public boolean hasNotificationPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * Safely dispatches a notification checking for permissions and handling exceptions.
     */
    public void safeNotify(@NonNull Context context, int notificationId, @NonNull Notification notification) {
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "Notification permission not granted, skipping notification id: " + notificationId);
            return;
        }
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification);
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException while notifying id: " + notificationId, se);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error notifying id: " + notificationId, e);
        }
    }

    /**
     * Cancels an active notification by ID.
     */
    public void cancel(@NonNull Context context, int notificationId) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId);
        } catch (Exception e) {
            Log.e(TAG, "Error canceling notification id: " + notificationId, e);
        }
    }

    /**
     * High-priority SOS alert notification with full-screen intent and dismiss action.
     */
    public void showSosAlert(@NonNull Context context, @Nullable String username, @Nullable String topic, @NonNull String message) {
        String channelId = Constants.ALARM_CHANNEL_ID;

        String title = (username != null && !username.trim().isEmpty())
                ? username.trim()
                : context.getString(R.string.sos_detected_title);

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("username", username);
        if (topic != null) {
            intent.putExtra("topic", topic);
        }
        intent.putExtra("open_sos", true);
        intent.putExtra("is_sos_alert", true);
        intent.putExtra("skip_splash", true);
        android.os.Bundle optionsBundle = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                    android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            );
            optionsBundle = options.toBundle();
        }

        PendingIntent tapPendingIntent = PendingIntent.getActivity(
                context, ID_SOS_ALERT, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE, optionsBundle
        );

        // Dismiss action ("Spegni")
        Intent dismissIntent = new Intent(context, NotificationDismissReceiver.class);
        dismissIntent.putExtra("ID", ID_SOS_ALERT);
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
                context, ID_SOS_ALERT, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Intercept swipe dismiss to ensure SOS notification remains active if user attempts to swipe it away
        Intent swipeIntent = new Intent(context, ReminderReceiver.class);
        swipeIntent.setAction(ReminderReceiver.ACTION_SOS_NOTIFICATION_SWIPED);
        swipeIntent.putExtra("username", username);
        swipeIntent.putExtra("topic", topic);
        swipeIntent.putExtra("message", message);
        PendingIntent swipePendingIntent = PendingIntent.getBroadcast(
                context, ID_SOS_ALERT * 10 + 4, swipeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build Custom RemoteViews for Alarm Pop-up Banner & Drawer (matching pill alarm style)
        RemoteViews customView = new RemoteViews(context.getPackageName(), R.layout.notification_alarm_popup);
        customView.setTextViewText(R.id.notification_alarm_title, title);
        customView.setTextViewText(R.id.notification_alarm_time, message);
        customView.setTextViewText(R.id.btn_notification_dismiss, context.getString(R.string.alarm_dismiss));
        // Hide snooze button since SOS must be explicitly stopped/dismissed
        customView.setViewVisibility(R.id.btn_notification_snooze_container, View.GONE);
        customView.setViewVisibility(R.id.btn_notification_snooze, View.GONE);

        // Wire dismiss button directly on the pop-up banner
        customView.setOnClickPendingIntent(R.id.btn_notification_dismiss_container, dismissPendingIntent);
        customView.setOnClickPendingIntent(R.id.btn_notification_dismiss, dismissPendingIntent);

        // Start repeating alarm sound, vibration, volume control, and duration auto-dismiss timing
        swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().start(context, ID_SOS_ALERT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(message)
                .setCustomContentView(customView)
                .setCustomBigContentView(customView)
                .setCustomHeadsUpContentView(customView)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(tapPendingIntent)
                .setFullScreenIntent(tapPendingIntent, true)
                .setDeleteIntent(swipePendingIntent)
                .setVibrate(new long[] { 0, 400, 200, 400 })
                .setAutoCancel(false)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        safeNotify(context, ID_SOS_ALERT, notification);
    }

    /**
     * "I'm OK" status message notification.
     */
    public void showImOk(@NonNull Context context, @Nullable String username, @Nullable String topic, @NonNull String message) {
        String channelId = Constants.SOS_CHANNEL_ID;

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("username", username);
        if (topic != null) {
            intent.putExtra("topic", topic);
        }
        intent.putExtra("open_sos", true);
        intent.putExtra("skip_splash", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        boolean isSilent = SettingsDB.getInstance(context).getSilentMode();
        Uri soundUri = isSilent ? null : SettingsDB.getInstance(context).getSoundUri();

        boolean isVibro = SettingsDB.getInstance(context).getVibrationMode();
        long[] vibratePattern = isVibro ? new long[]{0, 300, 200, 300} : null;

        String title = (username != null && !username.trim().isEmpty())
                ? username.trim()
                : context.getString(R.string.im_ok);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setSound(soundUri)
                .setVibrate(vibratePattern)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(false);

        safeNotify(context, ID_IM_OK, builder.build());
    }

    /**
     * Feedback notification for SOS message delivery status.
     */
    public void showPublishResult(@NonNull Context context, boolean success, @NonNull String message) {
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        Intent tapIntent = new Intent(context, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tapPendingIntent = PendingIntent.getActivity(context, 0, tapIntent, pendingFlags);

        String title = success ? context.getString(R.string.sos_sent_title) : context.getString(R.string.sos_send_error_title);
        String body = success ? context.getString(R.string.sos_send_success_body) : context.getString(R.string.sos_send_error_body);

        boolean isSilent = SettingsDB.getInstance(context).getSilentMode();
        Uri soundUri = isSilent ? null : SettingsDB.getInstance(context).getSoundUri();

        boolean isVibro = SettingsDB.getInstance(context).getVibrationMode();
        long[] vibratePattern = isVibro ? new long[]{0, 250} : null;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.SOS_CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body + "\n\"" + message + "\""))
                .setPriority(success ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_HIGH)
                .setCategory(success ? NotificationCompat.CATEGORY_STATUS : NotificationCompat.CATEGORY_ERROR)
                .setSound(soundUri)
                .setVibrate(vibratePattern)
                .setContentIntent(tapPendingIntent)
                .setAutoCancel(true);

        safeNotify(context, ID_PUBLISH_RESULT, builder.build());
    }

    /**
     * Notification indicating a message could not be sent immediately and was queued offline.
     */
    public void showQueuedOffline(@NonNull Context context, @Nullable String message) {
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        Intent tapIntent = new Intent(context, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tapPendingIntent = PendingIntent.getActivity(context, 0, tapIntent, pendingFlags);

        String title = context.getString(R.string.sos_queued_offline_title);
        String body = context.getString(R.string.sos_queued_offline_body);

        boolean isSilent = SettingsDB.getInstance(context).getSilentMode();
        Uri soundUri = isSilent ? null : SettingsDB.getInstance(context).getSoundUri();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.SOS_CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body + (message != null && !message.isEmpty() ? "\n\"" + message + "\"" : "")))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setSound(soundUri)
                .setContentIntent(tapPendingIntent)
                .setAutoCancel(true);

        safeNotify(context, ID_PUBLISH_RESULT, builder.build());
    }

    /**
     * Notification when outbox offline messages are successfully flushed and delivered.
     */
    public void showOutboxDelivered(@NonNull Context context) {
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        Intent tapIntent = new Intent(context, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tapPendingIntent = PendingIntent.getActivity(context, 0, tapIntent, pendingFlags);

        String title = context.getString(R.string.sos_sent_from_outbox_title);
        String body = context.getString(R.string.sos_sent_from_outbox_body);

        boolean isSilent = SettingsDB.getInstance(context).getSilentMode();
        Uri soundUri = isSilent ? null : SettingsDB.getInstance(context).getSoundUri();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.SOS_CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setSound(soundUri)
                .setContentIntent(tapPendingIntent)
                .setAutoCancel(true);

        safeNotify(context, ID_OUTBOX_DELIVERED, builder.build());
    }

    /**
     * Shows the emergency Fall Pre-Alarm notification with live countdown and action buttons,
     * and returns the full-screen PendingIntent for immediate screen wake up.
     */
    @Nullable
    public PendingIntent showFallPreAlarmNotification(@NonNull Context context, int countdownSeconds) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        // Full Screen Intent
        // NOTE: We use FLAG_ACTIVITY_CLEAR_TASK (not FLAG_ACTIVITY_REORDER_TO_FRONT).
        // REORDER_TO_FRONT explicitly brings an existing background task to front, which is
        // what Android 15's balDontBringExistingBackgroundTaskStackToFg blocks.
        // On Android 14+ (targetSdk 34+), the PendingIntent creator must explicitly grant
        // background activity start privileges via setPendingIntentCreatorBackgroundActivityStartMode.
        Intent fullScreenIntent = new Intent(context, FallAlertActivity.class);
        fullScreenIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);

        android.os.Bundle optionsBundle = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                    android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            );
            optionsBundle = options.toBundle();
        }

        PendingIntent fullScreenPending = PendingIntent.getActivity(
                context, 20021, fullScreenIntent, flags, optionsBundle);

        // Cancel Intent (Sto bene)
        Intent cancelIntent = new Intent(context, FallAlertReceiver.class);
        cancelIntent.setAction(Constants.ACTION_CANCEL_FALL_ALERT);
        PendingIntent cancelPending = PendingIntent.getBroadcast(context, 20022, cancelIntent, flags);

        // Send SOS Now Intent
        Intent sendNowIntent = new Intent(context, FallAlertReceiver.class);
        sendNowIntent.setAction(Constants.ACTION_TRIGGER_SOS_NOW);
        PendingIntent sendNowPending = PendingIntent.getBroadcast(context, 20023, sendNowIntent, flags);

        boolean hasContacts = swimminsparrow.ricordamelo.core.sos.SOSContactsManager.hasConfiguredContacts(context);
        String subtitle = hasContacts ?
                context.getString(R.string.fall_alert_subtitle) :
                context.getString(R.string.fall_alert_subtitle_no_contacts);
        String actionBtnText = hasContacts ?
                context.getString(R.string.fall_alert_send_now_btn) :
                context.getString(R.string.fall_alert_call_emergency_btn);
        int actionIcon = hasContacts ? R.drawable.baseline_sos_24 : R.drawable.ic_call_24;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.FALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(context.getString(R.string.fall_alert_title))
                .setContentText(subtitle)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(null)
                .setSilent(true)
                .setContentIntent(fullScreenPending)
                .setFullScreenIntent(fullScreenPending, true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + (countdownSeconds * 1000L))
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.baseline_done_24, context.getString(R.string.fall_alert_cancel_btn), cancelPending)
                .addAction(actionIcon, actionBtnText, sendNowPending)
                .setAutoCancel(false)
                .setOngoing(true);

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        safeNotify(context, Constants.FALL_ALERT_NOTIFICATION_ID, notification);
        return fullScreenPending;
    }

    /**
     * Shows confirmation notification after emergency SOS has been confirmed and dispatched.
     */
    public void showFallAlertConfirmedNotification(@NonNull Context context) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(context.getString(R.string.fall_detected_notification_title))
                .setContentText(context.getString(R.string.fall_detected_notification_body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        safeNotify(context, Constants.FALL_ALERT_NOTIFICATION_ID, builder.build());
    }

    /**
     * Shows notification after fall alert confirmed when no SOS contacts are configured,
     * inviting the user to call emergency services if needed.
     */
    public void showFallAlertNoContactsNotification(@NonNull Context context) {
        Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"));
        dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent dialPending = PendingIntent.getActivity(context, 20025, dialIntent, pendingFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(context.getString(R.string.fall_detected_no_contacts_title))
                .setContentText(context.getString(R.string.fall_detected_no_contacts_body))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(R.string.fall_detected_no_contacts_body)))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(dialPending)
                .addAction(R.drawable.ic_call_24, context.getString(R.string.fall_alert_call_emergency_btn), dialPending)
                .setAutoCancel(true);

        safeNotify(context, Constants.FALL_ALERT_NOTIFICATION_ID, builder.build());
    }

    /**
     * Dismisses the active Fall Pre-Alarm notification.
     */
    public void cancelFallAlertNotification(@NonNull Context context) {
        cancel(context, Constants.FALL_ALERT_NOTIFICATION_ID);
    }

    /**
     * Pill reminder alarm notification with custom RemoteViews (Spegni / Posponi),
     * full-screen intent for locked/sleeping states, and continuous AlarmRinger playback.
     */
    public void showAlarmNotification(@NonNull Context context, int id, @Nullable String orario) {
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);

        // Get pill name from DB
        Farmaco farmaco = FarmacoDao.getInstance(context).getById(id);
        String pillName = (farmaco != null) ? farmaco.getNome() : "";

        // Maximize volume if max volume mode is enabled (saving pre-override volume levels)
        if (SettingsDB.getInstance(context).isMaxVolumeMode()) {
            VolumeUtils.overrideVolumeToMax(context);
        }

        // Full Screen Intent targets FullAlarmActivity
        Intent fullScreenIntent = new Intent(context, FullAlarmActivity.class);
        fullScreenIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        fullScreenIntent.putExtra("ID", id);
        fullScreenIntent.putExtra("Orario", orario);
        android.os.Bundle optionsBundle = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                    android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            );
            optionsBundle = options.toBundle();
        }

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(context, id * 10 + 3, fullScreenIntent,
                pendingFlags, optionsBundle);

        // Tap opens full screen alarm UI as well
        PendingIntent tapPendingIntent = fullScreenPendingIntent;

        // Dismiss action ("Spegni")
        Intent dismissIntent = new Intent(context, NotificationDismissReceiver.class);
        dismissIntent.putExtra("ID", id);
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(context, id * 10 + 1, dismissIntent,
                pendingFlags);

        // Snooze action ("Ritarda di 15 min")
        int defaultSnoozeMinutes = 15;
        Intent snoozeIntent = new Intent(context, SnoozeReceiver.class);
        snoozeIntent.putExtra("ID", id);
        snoozeIntent.putExtra("Orario", orario);
        snoozeIntent.putExtra("snooze_minutes", defaultSnoozeMinutes);
        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(context, id * 10 + 2, snoozeIntent,
                pendingFlags);

        // Intercept swipe dismiss to ensure alarm notification remains active if user attempts to swipe it away
        Intent swipeIntent = new Intent(context, ReminderReceiver.class);
        swipeIntent.setAction(ReminderReceiver.ACTION_ALARM_NOTIFICATION_SWIPED);
        swipeIntent.putExtra("ID", id);
        swipeIntent.putExtra("Orario", orario);
        PendingIntent swipePendingIntent = PendingIntent.getBroadcast(context, id * 10 + 4, swipeIntent,
                pendingFlags);

        // Format alarm time string
        String displayTime = orario;
        if (displayTime == null || displayTime.isEmpty() || "TEST".equalsIgnoreCase(displayTime)) {
            displayTime = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date());
        }

        // Determine alarm name string
        String alarmName = pillName;
        if (alarmName == null || alarmName.isEmpty()) {
            if (id == Constants.TEST_ALARM_NOTIFICATION_ID || "TEST".equalsIgnoreCase(orario)) {
                alarmName = context.getString(R.string.test_alarm_title);
            } else {
                alarmName = context.getString(R.string.app_name);
            }
        }

        // Build Custom RemoteViews for Alarm Pop-up Banner & Drawer
        RemoteViews customView = new RemoteViews(context.getPackageName(), R.layout.notification_alarm_popup);
        customView.setTextViewText(R.id.notification_alarm_title, alarmName);
        customView.setTextViewText(R.id.notification_alarm_time, displayTime);
        customView.setTextViewText(R.id.btn_notification_dismiss, context.getString(R.string.alarm_dismiss));
        customView.setTextViewText(R.id.btn_notification_snooze, context.getString(R.string.snooze));

        // Bind action buttons directly inside RemoteViews
        customView.setOnClickPendingIntent(R.id.btn_notification_dismiss_container, dismissPendingIntent);
        customView.setOnClickPendingIntent(R.id.btn_notification_dismiss, dismissPendingIntent);
        customView.setOnClickPendingIntent(R.id.btn_notification_snooze_container, snoozePendingIntent);
        customView.setOnClickPendingIntent(R.id.btn_notification_snooze, snoozePendingIntent);

        // Start looping alarm audio, continuous vibration, and duration auto-dismiss timing
        AlarmRinger.getInstance().start(context, id);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(alarmName)
                .setContentText(displayTime)
                .setCustomContentView(customView)
                .setCustomBigContentView(customView)
                .setCustomHeadsUpContentView(customView)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(tapPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setDeleteIntent(swipePendingIntent)
                .setVibrate(new long[] { 0, 400, 200, 400 })
                .setAutoCancel(false)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        // Post notification first so status bar banner is active
        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        safeNotify(context, id, notification);

        // Directly launch FullAlarmActivity
        try {
            android.app.Activity currentActivity = swimminsparrow.ricordamelo.PillSOS.getCurrentActivity();
            if (currentActivity != null && !currentActivity.isFinishing()) {
                currentActivity.startActivity(fullScreenIntent);
            } else {
                context.startActivity(fullScreenIntent);
            }
        } catch (Exception e) {
            SpockLogger.info(TAG, "Direct startActivity failed, fullScreenIntent will handle it: " + e.getMessage());
        }
    }
}
