package swimminsparrow.ricordamelo.ui.common.Dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.GregorianCalendar;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.data.dao.FarmacoDao;
import swimminsparrow.ricordamelo.core.data.models.Farmaco;
import swimminsparrow.ricordamelo.ui.common.CommonSnackbar;
import swimminsparrow.ricordamelo.ui.common.CountdownWheelView;

public class DialogContoAllaRovescia extends Dialog {

    private final Farmaco farmaco;
    private final Context context;
    private CountdownWheelView wheelHours, wheelMinutes, wheelSeconds;
    private CountDownTimer countDownTimer;
    private final OnEditRequestedListener onEditRequested;

    public interface OnEditRequestedListener {
        void onEditRequested(int farmacoId);
    }

    public DialogContoAllaRovescia(Context context, Farmaco farmaco, OnEditRequestedListener onEditRequested) {
        super(context);
        this.context = context;
        this.farmaco = farmaco;
        this.onEditRequested = onEditRequested;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make window background transparent so rounded card corners clip cleanly
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        setContentView(R.layout.dialog_contoallarovescia);
        setTitle(context.getString(R.string.countdown));

        bindViews();
        setupCountdown();
        setupButtons();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Force 90% screen width so dialog never squishes horizontally
        if (getWindow() != null) {
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int dialogWidth = (int) (screenWidth * 0.90f);
            getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void bindViews() {
        wheelHours = findViewById(R.id.wheel_hours);
        wheelMinutes = findViewById(R.id.wheel_minutes);
        wheelSeconds = findViewById(R.id.wheel_seconds);

        TextView tvDrugName = findViewById(R.id.tvDrugName);
        if (tvDrugName != null && farmaco != null) {
            tvDrugName.setText(farmaco.getNome());
        }

        ImageView btnCloseDialog = findViewById(R.id.btnCloseDialog);
        if (btnCloseDialog != null) {
            btnCloseDialog.setOnClickListener(v -> dismiss());
        }
    }

    private void setupCountdown() {
        long milliDiff = farmaco.getNextAlarm() - new GregorianCalendar().getTimeInMillis();

        if (milliDiff <= 0) {
            resetWheels();
            return;
        }

        countDownTimer = new CountDownTimer(milliDiff, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long totalSeconds = millisUntilFinished / 1000;
                int hours = (int) (totalSeconds / 3600) % 24;
                int minutes = (int) (totalSeconds % 3600) / 60;
                int seconds = (int) (totalSeconds % 60);

                wheelHours.setLabel(String.format(java.util.Locale.getDefault(), "%02d", hours));
                wheelHours.setProgress(hours / 24f);

                wheelMinutes.setLabel(String.format(java.util.Locale.getDefault(), "%02d", minutes));
                wheelMinutes.setProgress(minutes / 60f);

                wheelSeconds.setLabel(String.format(java.util.Locale.getDefault(), "%02d", seconds));
                wheelSeconds.setProgress(seconds / 60f);
            }

            @Override
            public void onFinish() {
                resetWheels();
            }
        }.start();
    }

    private void resetWheels() {
        wheelHours.setLabel("00");
        wheelHours.setProgress(0);
        wheelMinutes.setLabel("00");
        wheelMinutes.setProgress(0);
        wheelSeconds.setLabel("00");
        wheelSeconds.setProgress(0);
    }

    private void setupButtons() {
        Button mEditButton = findViewById(R.id.mEditButton);
        Button mDeleteButton = findViewById(R.id.mDeleteButton);

        mEditButton.setOnClickListener(v -> {
            dismiss();
            if (onEditRequested != null)
                onEditRequested.onEditRequested(farmaco.getId());
        });

        mDeleteButton.setOnClickListener(v -> {
            new DialogBuilder(context, DialogBuilder.Type.DESTRUCTIVE)
                    .setTitleText(context.getString(R.string.dialog_confirm))
                    .setContentText(context.getString(R.string.dialog_s_2) + " " + this.farmaco.getNome() + "?")
                    .setCancelText(context.getString(R.string.no))
                    .setConfirmText(context.getString(R.string.si))
                    .setConfirmClickListener(sDialog -> {
                        String name = farmaco.getNome();
                        Context appContext = context.getApplicationContext();
                        swimminsparrow.ricordamelo.core.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                            FarmacoDao.getInstance(appContext).delete(farmaco.getId());
                        });
                        dismiss();
                        Activity activity = DialogBuilder.getActivityFromContext(context);
                        String msg = context.getString(R.string.reminder_deleted, name);
                        CommonSnackbar.showWithIcon(activity, msg, R.drawable.ic_delete_outline_24);
                    })
                    .show();
        });
    }

    @Override
    public void dismiss() {
        if (countDownTimer != null)
            countDownTimer.cancel();
        super.dismiss();
    }
}