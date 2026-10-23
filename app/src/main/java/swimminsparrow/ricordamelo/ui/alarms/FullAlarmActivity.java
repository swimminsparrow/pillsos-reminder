package swimminsparrow.ricordamelo.ui.alarms;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import java.io.File;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.data.dao.FarmacoDao;
import swimminsparrow.ricordamelo.core.data.models.Farmaco;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;
import swimminsparrow.ricordamelo.services.NotificationDismissReceiver;
import swimminsparrow.ricordamelo.services.SnoozeReceiver;

public class FullAlarmActivity extends AppCompatActivity {

    private int alarmId = -1;
    private String alarmOrario = "";
    private static final int DEFAULT_SNOOZE_MINUTES = 15;
    private int snoozeMinutes = DEFAULT_SNOOZE_MINUTES;

    private TextView tvTitle;
    private ImageView ivPhoto;
    private TextView btnSnooze;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn on screen and display over keyguard/lockscreen
        setupLockscreenFlags();

        setContentView(R.layout.activity_full_alarm);

        alarmId = getIntent().getIntExtra("ID", -1);
        alarmOrario = getIntent().getStringExtra("Orario");


        tvTitle = findViewById(R.id.tv_alarm_title);
        ivPhoto = findViewById(R.id.iv_alarm_pill_photo);
        btnSnooze = findViewById(R.id.btn_alarm_snooze);

        View btnDismiss = findViewById(R.id.btn_alarm_dismiss);
        View dismissContainer = findViewById(R.id.btn_alarm_dismiss_container);

        populateAlarmDetails();
        updateSnoozeText();

        // Dismiss Action (X) - wire both circle and container
        if (btnDismiss != null) btnDismiss.setOnClickListener(v -> dismissAlarm());
        if (dismissContainer != null) dismissContainer.setOnClickListener(v -> dismissAlarm());

        // Snooze Action
        btnSnooze.setOnClickListener(v -> snoozeAlarm());

        // Connect to AlarmRinger (handles looping sound, continuous vibration, and duration timeout)
        swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().setAlarmEventListener(this::finishAndRemoveTask);
        swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().start(this, alarmId);
    }

    private void populateAlarmDetails() {
        Farmaco farmaco = FarmacoDao.getInstance(this).getById(alarmId);
        if (farmaco != null && farmaco.getNome() != null && !farmaco.getNome().isEmpty()) {
            if (tvTitle != null) tvTitle.setText(farmaco.getNome());
            loadPillImage(farmaco.getImagePath());
        } else {
            if (tvTitle != null) {
                if (alarmId == swimminsparrow.ricordamelo.config.Constants.TEST_ALARM_NOTIFICATION_ID || "TEST".equalsIgnoreCase(alarmOrario)) {
                    tvTitle.setText(R.string.test_alarm_title);
                } else {
                    tvTitle.setText(R.string.app_name);
                }
            }
            loadPillImage(null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) {
            alarmId = intent.getIntExtra("ID", -1);
            alarmOrario = intent.getStringExtra("Orario");
            populateAlarmDetails();
            swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().start(this, alarmId);
        }
    }

    private void updateSnoozeText() {
        if (btnSnooze != null) {
            btnSnooze.setText(getString(R.string.snooze_format, snoozeMinutes));
        }
    }

    private void dismissAlarm() {
        SpockLogger.info(getClass().getSimpleName(), "Dismissing alarm " + alarmId + " from FullScreenActivity");
        
        swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().stop(this);

        // 1. Immediately cancel the notification from NotificationManager
        try {
            NotificationManagerCompat nm = NotificationManagerCompat.from(this);
            if (alarmId != -1) {
                nm.cancel(alarmId);
            } else {
                nm.cancel(swimminsparrow.ricordamelo.config.Constants.TEST_ALARM_NOTIFICATION_ID);
            }
        } catch (Exception ignored) {}

        // 2. Restore volumes if overridden
        swimminsparrow.ricordamelo.core.utils.VolumeUtils.restoreVolume(this);

        // 3. Cancel any pending missed alarm
        try {
            Intent missedIntent = new Intent(this, swimminsparrow.ricordamelo.services.MissedAlarmReceiver.class);
            android.app.PendingIntent missedPending = swimminsparrow.ricordamelo.core.utils.PendingIntentHelper.getBroadcast(
                    this, alarmId, missedIntent, true);
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(missedPending);
            }
        } catch (Exception ignored) {}

        // 4. Send broadcast to NotificationDismissReceiver as additional safety
        Intent dismissIntent = new Intent(this, NotificationDismissReceiver.class);
        dismissIntent.putExtra("ID", alarmId);
        sendBroadcast(dismissIntent);

        finishAndRemoveTask();
    }

    private void snoozeAlarm() {
        SpockLogger.info(getClass().getSimpleName(), "Snoozing alarm " + alarmId + " for " + snoozeMinutes + " mins from FullScreenActivity");

        swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().stop(this);

        // Immediately cancel the current notification
        try {
            NotificationManagerCompat nm = NotificationManagerCompat.from(this);
            if (alarmId != -1) {
                nm.cancel(alarmId);
            } else {
                nm.cancel(swimminsparrow.ricordamelo.config.Constants.TEST_ALARM_NOTIFICATION_ID);
            }
        } catch (Exception ignored) {}

        Intent snoozeIntent = new Intent(this, SnoozeReceiver.class);
        snoozeIntent.putExtra("ID", alarmId);
        snoozeIntent.putExtra("Orario", alarmOrario);
        snoozeIntent.putExtra("snooze_minutes", snoozeMinutes);
        sendBroadcast(snoozeIntent);

        finishAndRemoveTask();
    }

    @Override
    public void onBackPressed() {
        // Pressing back behaves like Home: moves activity to background without dismissing the alarm
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().setAlarmEventListener(null);
        if (isFinishing()) {
            swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().stop(this);
        }
        super.onDestroy();
    }

    private void setupLockscreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        );

        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#061608"));
        getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#1B4918"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private void loadPillImage(String imagePath) {
        if (ivPhoto == null) return;
        ivPhoto.setVisibility(View.VISIBLE);

        if (imagePath != null && !imagePath.trim().isEmpty()) {
            File imgFile = new File(imagePath);
            if (!imgFile.exists()) {
                imgFile = swimminsparrow.ricordamelo.core.utils.StorageUtils.getApplicationFile(this, imagePath);
            }
            if (imgFile.exists()) {
                try {
                    Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                    if (myBitmap != null) {
                        ivPhoto.setImageBitmap(myBitmap);
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        ivPhoto.setImageResource(R.drawable.default_pill);
    }
}
