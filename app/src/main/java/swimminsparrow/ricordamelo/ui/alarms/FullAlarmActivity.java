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
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.utils.ImageUtils;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.data.dao.FarmacoDao;
import swimminsparrow.ricordamelo.core.data.models.Farmaco;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;

public class FullAlarmActivity extends AppCompatActivity {

    private int alarmId = -1;
    private String alarmOrario = "";
    private static final int DEFAULT_SNOOZE_MINUTES = 15;
    private int snoozeMinutes = DEFAULT_SNOOZE_MINUTES;
    private boolean isDismissing = false;
    private static volatile int activeVisibleAlarmId = -1;

    public static boolean isAlarmVisible(int id) {
        return activeVisibleAlarmId != -1 && (id == -1 || activeVisibleAlarmId == id);
    }

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
        if (alarmId == -1) {
            alarmId = swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().getActiveAlarmId();
        }
        alarmOrario = getIntent().getStringExtra("Orario");
        isDismissing = false;


        tvTitle = findViewById(R.id.tv_alarm_title);
        ivPhoto = findViewById(R.id.iv_alarm_pill_photo);
        btnSnooze = findViewById(R.id.btn_alarm_snooze);

        View btnDismiss = findViewById(R.id.btn_alarm_dismiss);
        View dismissContainer = findViewById(R.id.btn_alarm_dismiss_container);
        View tvDismiss = findViewById(R.id.tv_alarm_dismiss);

        populateAlarmDetails();
        updateSnoozeText();

        // Dismiss Action (X) - wire circle, container, and text label
        if (btnDismiss != null) btnDismiss.setOnClickListener(v -> dismissAlarm());
        if (dismissContainer != null) dismissContainer.setOnClickListener(v -> dismissAlarm());
        if (tvDismiss != null) tvDismiss.setOnClickListener(v -> dismissAlarm());

        // Snooze Action
        btnSnooze.setOnClickListener(v -> snoozeAlarm());

        // Intercept back gesture / press to auto-snooze for 5 minutes and show countdown notification
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                autoSnoozeAlarm(5);
            }
        });

        // Connect to AlarmRinger (handles looping sound, continuous vibration, and duration timeout)
        swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().setAlarmEventListener(this::finishSafely);
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
        if (isFinishing() || isDestroyed()) {
            return;
        }
        isDismissing = false;
        setIntent(intent);
        if (intent != null) {
            alarmId = intent.getIntExtra("ID", -1);
            if (alarmId == -1) {
                alarmId = swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().getActiveAlarmId();
            }
            alarmOrario = intent.getStringExtra("Orario");
            populateAlarmDetails();
            swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().start(this, alarmId);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isDismissing = false;
        activeVisibleAlarmId = alarmId;
        swimminsparrow.ricordamelo.core.notification.AppNotificationManager.getInstance().cancelNotificationFallback();
        swimminsparrow.ricordamelo.core.notification.AppNotificationManager.getInstance().cancelNotificationOnly(this, alarmId);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                swimminsparrow.ricordamelo.core.notification.AppNotificationManager.getInstance().cancelNotificationOnly(this, alarmId);
            }
        }, 300);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (activeVisibleAlarmId == alarmId) {
            activeVisibleAlarmId = -1;
        }
    }

    private void updateSnoozeText() {
        if (btnSnooze != null) {
            btnSnooze.setText(getString(R.string.snooze_format, snoozeMinutes));
        }
    }

    private void finishSafely() {
        if (!isFinishing() && !isDestroyed()) {
            finishAndRemoveTask();
        }
    }

    private void dismissAlarm() {
        if (isDismissing) {
            return;
        }
        isDismissing = true;
        SpockLogger.info(getClass().getSimpleName(), "Dismissing alarm " + alarmId + " from FullAlarmActivity");
        swimminsparrow.ricordamelo.core.notification.AppNotificationManager.getInstance().dismissAlarm(this, alarmId);
        finishSafely();
    }

    private void snoozeAlarm() {
        if (isDismissing) {
            return;
        }
        isDismissing = true;
        SpockLogger.info(getClass().getSimpleName(), "Snoozing alarm " + alarmId + " for " + snoozeMinutes + " mins from FullAlarmActivity");
        swimminsparrow.ricordamelo.core.notification.AppNotificationManager.getInstance().snoozeAlarm(this, alarmId, alarmOrario, snoozeMinutes);
        finishSafely();
    }

    private void autoSnoozeAlarm(int minutes) {
        if (isDismissing || isFinishing()) {
            return;
        }
        isDismissing = true;
        SpockLogger.info(getClass().getSimpleName(), "Auto-snoozing alarm " + alarmId + " for " + minutes + " mins via Home/Back");
        swimminsparrow.ricordamelo.core.notification.AppNotificationManager.getInstance().snoozeAlarm(this, alarmId, alarmOrario, minutes);
        finishSafely();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!isDismissing && !isFinishing()) {
            autoSnoozeAlarm(5);
        }
    }

    @Override
    public void onBackPressed() {
        if (!isDismissing && !isFinishing()) {
            autoSnoozeAlarm(5);
        }
    }

    @Override
    protected void onDestroy() {
        if (activeVisibleAlarmId == alarmId) {
            activeVisibleAlarmId = -1;
        }
        swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().setAlarmEventListener(null);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    private void applyImmersiveMode() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
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

        if (Build.VERSION.SDK_INT < 35) {
            getWindow().setStatusBarColor(android.graphics.Color.parseColor("#061608"));
            getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#1B4918"));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applyImmersiveMode();
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
                    Bitmap myBitmap = ImageUtils.loadBitmapWithExifRotation(imgFile, 600);
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
