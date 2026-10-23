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

import swimminsparrow.ricordamelo.PillSOS;
import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.data.SettingsDB;

import com.google.android.material.button.MaterialButton;
import swimminsparrow.ricordamelo.core.utils.BatteryOptimizationsHelper;

public class PrimoAvvio2 extends Fragment {

    private View rootView;
    private Button butStartMainActivity;
    private MaterialButton btnBatteryOpt;
    private MaterialButton btnNotificationPerm;
    private MaterialButton btnOverlayPerm;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_onboarding_2, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnNotificationPerm = rootView.findViewById(R.id.btnNotificationPerm);
        if (btnNotificationPerm != null) {
            btnNotificationPerm.setOnClickListener(v -> requestNotificationPermission());
        }

        btnBatteryOpt = rootView.findViewById(R.id.btnBatteryOpt);
        if (btnBatteryOpt != null) {
            btnBatteryOpt.setOnClickListener(
                    v -> BatteryOptimizationsHelper.openBatteryOptimizationSettings(requireContext()));
        }

        btnOverlayPerm = rootView.findViewById(R.id.btnOverlayPerm);
        if (btnOverlayPerm != null) {
            btnOverlayPerm.setOnClickListener(
                    v -> swimminsparrow.ricordamelo.core.utils.OverlayPermissionHelper.requestOverlayPermission(requireContext()));
        }

        butStartMainActivity = rootView.findViewById(R.id.button_start_MainActivity);
        if (butStartMainActivity != null) {
            butStartMainActivity.setOnClickListener(v -> finishPrimoAvvioTour());
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
        if (getActivity() instanceof swimminsparrow.ricordamelo.ui.BaseActivity) {
            ((swimminsparrow.ricordamelo.ui.BaseActivity) getActivity())
                    .checkNotificationPermission(new swimminsparrow.ricordamelo.core.utils.PermissionCallback() {
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
        boolean granted = swimminsparrow.ricordamelo.core.utils.CheckPermissionsHandler
                .isNotificationGranted(requireContext());
        if (granted) {
            btnNotificationPerm.setText(R.string.onboarding_perm_notif_enabled);
            btnNotificationPerm.setIconResource(R.drawable.baseline_done_24);
            btnNotificationPerm.setEnabled(false);
            btnNotificationPerm.setAlpha(1.0f);
            btnNotificationPerm.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E8F5E9")));
            btnNotificationPerm.setTextColor(Color.parseColor("#1B5E38"));
            btnNotificationPerm.setIconTint(ColorStateList.valueOf(Color.parseColor("#1B5E38")));
            btnNotificationPerm.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#A3D9B5")));
            btnNotificationPerm.setStrokeWidth(2);
        } else {
            btnNotificationPerm.setText(R.string.onboarding_perm_notif_enable);
            btnNotificationPerm.setIconResource(R.drawable.ic_notifications_bell_24);
            btnNotificationPerm.setEnabled(true);
            btnNotificationPerm.setAlpha(1.0f);
            btnNotificationPerm.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F1F5F9")));
            btnNotificationPerm.setTextColor(Color.parseColor("#1E293B"));
            btnNotificationPerm.setIconTint(ColorStateList.valueOf(Color.parseColor("#1E6F3E")));
            btnNotificationPerm.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#CBD5E1")));
            btnNotificationPerm.setStrokeWidth(2);
        }
    }

    private void updateBatteryStatus() {
        if (getContext() == null || btnBatteryOpt == null)
            return;
        boolean ignoring = BatteryOptimizationsHelper.isIgnoringBatteryOptimizations(requireContext());
        if (ignoring) {
            btnBatteryOpt.setText(R.string.onboarding_battery_unrestricted);
            btnBatteryOpt.setIconResource(R.drawable.baseline_done_24);
            btnBatteryOpt.setEnabled(false);
            btnBatteryOpt.setAlpha(1.0f);
            btnBatteryOpt.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E8F5E9")));
            btnBatteryOpt.setTextColor(Color.parseColor("#1B5E38"));
            btnBatteryOpt.setIconTint(ColorStateList.valueOf(Color.parseColor("#1B5E38")));
            btnBatteryOpt.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#A3D9B5")));
            btnBatteryOpt.setStrokeWidth(2);
        } else {
            btnBatteryOpt.setText(R.string.onboarding_battery_disable_restrictions);
            btnBatteryOpt.setIconResource(R.drawable.outline_battery_status_good_24);
            btnBatteryOpt.setEnabled(true);
            btnBatteryOpt.setAlpha(1.0f);
            btnBatteryOpt.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F1F5F9")));
            btnBatteryOpt.setTextColor(Color.parseColor("#1E293B"));
            btnBatteryOpt.setIconTint(ColorStateList.valueOf(Color.parseColor("#1E6F3E")));
            btnBatteryOpt.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#CBD5E1")));
            btnBatteryOpt.setStrokeWidth(2);
        }
    }

    private void updateOverlayStatus() {
        if (getContext() == null || btnOverlayPerm == null)
            return;
        boolean granted = swimminsparrow.ricordamelo.core.utils.OverlayPermissionHelper.canDrawOverlays(requireContext());
        if (granted) {
            btnOverlayPerm.setText(R.string.onboarding_overlay_enabled);
            btnOverlayPerm.setIconResource(R.drawable.baseline_done_24);
            btnOverlayPerm.setEnabled(false);
            btnOverlayPerm.setAlpha(1.0f);
            btnOverlayPerm.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E8F5E9")));
            btnOverlayPerm.setTextColor(Color.parseColor("#1B5E38"));
            btnOverlayPerm.setIconTint(ColorStateList.valueOf(Color.parseColor("#1B5E38")));
            btnOverlayPerm.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#A3D9B5")));
            btnOverlayPerm.setStrokeWidth(2);
        } else {
            btnOverlayPerm.setText(R.string.onboarding_overlay_enable);
            btnOverlayPerm.setIconResource(R.drawable.ic_fall_detection_24);
            btnOverlayPerm.setEnabled(true);
            btnOverlayPerm.setAlpha(1.0f);
            btnOverlayPerm.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F1F5F9")));
            btnOverlayPerm.setTextColor(Color.parseColor("#1E293B"));
            btnOverlayPerm.setIconTint(ColorStateList.valueOf(Color.parseColor("#1E6F3E")));
            btnOverlayPerm.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#CBD5E1")));
            btnOverlayPerm.setStrokeWidth(2);
        }
    }

    private void finishPrimoAvvioTour() {
        SettingsDB.getInstance(requireContext()).setPrimoAvvio(true);
        PillSOS.callRicordamelo(requireActivity());
    }
}
