package swimminsparrow.ricordamelo.ui.onboarding;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import swimminsparrow.ricordamelo.PillSOS;
import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.utils.BatteryOptimizationsHelper;
import swimminsparrow.ricordamelo.core.utils.CheckPermissionsHandler;
import swimminsparrow.ricordamelo.core.utils.OverlayPermissionHelper;
import swimminsparrow.ricordamelo.core.utils.PermissionCallback;
import swimminsparrow.ricordamelo.ui.BaseActivity;
import swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder;

public class PrimoAvvio2 extends Fragment {

    private View rootView;
    private Button butStartMainActivity;
    private MaterialButton btnBatteryOpt;
    private MaterialButton btnNotificationPerm;
    private MaterialButton btnOverlayPerm;
    private MaterialCardView cardNotificationPerm;
    private MaterialCardView cardBatteryOpt;
    private MaterialCardView cardOverlayPerm;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_onboarding_2, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cardNotificationPerm = rootView.findViewById(R.id.cardNotificationPerm);
        btnNotificationPerm = rootView.findViewById(R.id.btnNotificationPerm);
        View.OnClickListener notifClick = v -> {
            if (!CheckPermissionsHandler.isNotificationGranted(requireContext())) {
                requestNotificationPermission();
            }
        };
        if (btnNotificationPerm != null) {
            btnNotificationPerm.setOnClickListener(notifClick);
        }
        if (cardNotificationPerm != null) {
            cardNotificationPerm.setOnClickListener(notifClick);
        }

        cardBatteryOpt = rootView.findViewById(R.id.cardBatteryOpt);
        btnBatteryOpt = rootView.findViewById(R.id.btnBatteryOpt);
        View.OnClickListener batteryClick = v -> {
            if (!BatteryOptimizationsHelper.isIgnoringBatteryOptimizations(requireContext())) {
                BatteryOptimizationsHelper.openStandardBatteryOptimizationSettings(requireContext());
            }
        };
        if (btnBatteryOpt != null) {
            btnBatteryOpt.setOnClickListener(batteryClick);
        }
        if (cardBatteryOpt != null) {
            cardBatteryOpt.setOnClickListener(batteryClick);
        }

        cardOverlayPerm = rootView.findViewById(R.id.cardOverlayPerm);
        btnOverlayPerm = rootView.findViewById(R.id.btnOverlayPerm);
        View.OnClickListener overlayClick = v -> {
            if (!OverlayPermissionHelper.canDrawOverlays(requireContext())) {
                OverlayPermissionHelper.requestOverlayPermission(requireContext());
            }
        };
        if (btnOverlayPerm != null) {
            btnOverlayPerm.setOnClickListener(overlayClick);
        }
        if (cardOverlayPerm != null) {
            cardOverlayPerm.setOnClickListener(overlayClick);
        }

        butStartMainActivity = rootView.findViewById(R.id.button_start_MainActivity);
        if (butStartMainActivity != null) {
            butStartMainActivity.setOnClickListener(v -> onStartClicked());
        }

        updateNotificationStatus();
        updateBatteryStatus();
        updateOverlayStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateNotificationStatus();
        updateBatteryStatus();
        updateOverlayStatus();
    }

    private void requestNotificationPermission() {
        if (!isAdded() || getActivity() == null)
            return;
        if (getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).checkNotificationPermission(new PermissionCallback() {
                @Override
                public void onPermissionGranted() {
                    updateNotificationStatus();
                }

                @Override
                public void onPermissionDenied() {
                    updateNotificationStatus();
                }
            });
        }
    }

    private void updateNotificationStatus() {
        if (getContext() == null || btnNotificationPerm == null)
            return;
        boolean granted = CheckPermissionsHandler.isNotificationGranted(requireContext());
        applyButtonState(btnNotificationPerm, granted);
    }

    private void updateBatteryStatus() {
        if (getContext() == null || btnBatteryOpt == null)
            return;
        boolean ignoring = BatteryOptimizationsHelper.isIgnoringBatteryOptimizations(requireContext());
        applyButtonState(btnBatteryOpt, ignoring);
    }

    private void updateOverlayStatus() {
        if (getContext() == null || btnOverlayPerm == null)
            return;
        boolean granted = OverlayPermissionHelper.canDrawOverlays(requireContext());
        applyButtonState(btnOverlayPerm, granted);
    }

    private void applyButtonState(MaterialButton btn, boolean granted) {
        if (granted) {
            btn.setText(R.string.onboarding_perm_active);
            btn.setIconResource(R.drawable.baseline_done_24);
            btn.setEnabled(false);
            btn.setAlpha(1.0f);
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E8F5E9")));
            btn.setTextColor(Color.parseColor("#1B5E38"));
            btn.setIconTint(ColorStateList.valueOf(Color.parseColor("#1B5E38")));
            btn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#A3D9B5")));
            btn.setStrokeWidth(2);
        } else {
            btn.setText(R.string.onboarding_perm_enable);
            btn.setIcon(null);
            btn.setEnabled(true);
            btn.setAlpha(1.0f);
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1E6F3E")));
            btn.setTextColor(Color.WHITE);
            btn.setStrokeWidth(0);
        }
    }

    private void onStartClicked() {
        if (getContext() == null) return;

        boolean notifGranted = CheckPermissionsHandler.isNotificationGranted(requireContext());
        boolean batteryIgnored = BatteryOptimizationsHelper.isIgnoringBatteryOptimizations(requireContext());

        if (!notifGranted || !batteryIgnored) {
            showSafetyWarningDialog(!notifGranted);
        } else {
            finishPrimoAvvioTour();
        }
    }

    private void showSafetyWarningDialog(boolean missingNotification) {
        new DialogBuilder(requireContext(), DialogBuilder.Type.WARNING)
                .setTitleText(getString(R.string.onboarding_warning_dialog_title))
                .setContentText(getString(R.string.onboarding_warning_dialog_desc))
                .setConfirmText(getString(R.string.onboarding_warning_dialog_fix))
                .setCancelText(getString(R.string.onboarding_warning_dialog_skip))
                .setConfirmClickListener(dialog -> {
                    if (missingNotification) {
                        requestNotificationPermission();
                    } else {
                        BatteryOptimizationsHelper.openStandardBatteryOptimizationSettings(requireContext());
                    }
                })
                .setCancelClickListener(dialog -> finishPrimoAvvioTour())
                .show();
    }

    private void finishPrimoAvvioTour() {
        if (getContext() == null) return;
        SettingsDB.getInstance(requireContext()).setPrimoAvvio(true);
        PillSOS.callRicordamelo(requireActivity());
    }
}
