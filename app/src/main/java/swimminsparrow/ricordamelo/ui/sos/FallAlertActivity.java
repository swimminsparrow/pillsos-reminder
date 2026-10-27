package swimminsparrow.ricordamelo.ui.sos;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.protection.FallAlertController;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;

/**
 * Full-screen Emergency Pre-Alarm Activity displayed when a potential fall is detected.
 * Provides a 30-second live countdown and a prominent "STO BENE / ANNULLA" button
 * to prevent false alarms before emergency messages are dispatched.
 */
public class FallAlertActivity extends AppCompatActivity implements FallAlertController.AlertListener {

    private static final String TAG = "FallAlertActivity";

    private TextView tvCountdown;
    private View btnCancel;
    private View btnSendNow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        setupLockscreenFlags();
        setContentView(R.layout.activity_fall_alert);

        tvCountdown = findViewById(R.id.tv_countdown_seconds);
        btnCancel = findViewById(R.id.btn_fall_cancel);
        btnSendNow = findViewById(R.id.btn_fall_send_now);

        FallAlertController controller = FallAlertController.getInstance();
        if (!controller.isAlerting()) {
            SpockLogger.info(TAG, "Controller is not alerting, finishing activity");
            finishAndRemoveTask();
            return;
        }

        tvCountdown.setText(String.valueOf(controller.getSecondsRemaining()));

        btnCancel.setOnClickListener(v -> {
            SpockLogger.info(TAG, "User clicked I'M OK / CANCEL");
            controller.cancelAlert(FallAlertActivity.this);
            finishAndRemoveTask();
        });

        boolean hasContacts = swimminsparrow.ricordamelo.core.sos.SOSContactsManager.hasConfiguredContacts(this);
        TextView tvSubtitle = findViewById(R.id.tv_fall_subtitle);
        TextView tvSendNow = findViewById(R.id.tv_fall_send_now);
        android.widget.ImageView ivSendNow = findViewById(R.id.iv_fall_send_now);

        if (!hasContacts) {
            if (tvSubtitle != null) {
                tvSubtitle.setText(R.string.fall_alert_subtitle_no_contacts);
            }
            if (tvSendNow != null) {
                tvSendNow.setText(R.string.fall_alert_call_emergency_btn);
            }
            if (ivSendNow != null) {
                ivSendNow.setImageResource(R.drawable.ic_call_24);
            }
            btnSendNow.setOnClickListener(v -> {
                SpockLogger.info(TAG, "No contacts configured, user clicked CALL 112");
                controller.cancelAlert(FallAlertActivity.this);
                try {
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:112"));
                    dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(dialIntent);
                } catch (Exception e) {
                    SpockLogger.error(TAG, "Failed to launch dialer for 112: " + e.getMessage());
                }
                finishAndRemoveTask();
            });
        } else {
            btnSendNow.setOnClickListener(v -> {
                SpockLogger.info(TAG, "User clicked SEND SOS NOW");
                controller.confirmAndSendSOS(FallAlertActivity.this);
                finishAndRemoveTask();
            });
        }

        controller.setAlertListener(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        setupLockscreenFlags();
        FallAlertController controller = FallAlertController.getInstance();
        if (tvCountdown != null) {
            tvCountdown.setText(String.valueOf(controller.getSecondsRemaining()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupLockscreenFlags();
        FallAlertController controller = FallAlertController.getInstance();
        if (tvCountdown != null) {
            tvCountdown.setText(String.valueOf(controller.getSecondsRemaining()));
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
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        if (Build.VERSION.SDK_INT < 35) {
            getWindow().setStatusBarColor(android.graphics.Color.parseColor("#2A0808"));
            getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#0F0303"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getWindow().setNavigationBarContrastEnforced(false);
                getWindow().setStatusBarContrastEnforced(false);
            }
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    @Override
    public void onTick(int secondsRemaining) {
        runOnUiThread(() -> {
            if (tvCountdown != null) {
                tvCountdown.setText(String.valueOf(secondsRemaining));
            }
        });
    }

    @Override
    public void onAlertCancelled() {
        runOnUiThread(this::finishAndRemoveTask);
    }

    @Override
    public void onAlertConfirmed() {
        runOnUiThread(this::finishAndRemoveTask);
    }

    @Override
    protected void onDestroy() {
        FallAlertController.getInstance().setAlertListener(null);
        super.onDestroy();
    }
}
