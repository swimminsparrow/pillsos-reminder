package swimminsparrow.ricordamelo.ui.alarms;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import swimminsparrow.ricordamelo.ui.rateme.RateMeManager;

import java.util.ArrayList;
import java.util.List;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.data.dao.FarmacoDao;
import swimminsparrow.ricordamelo.core.data.models.Farmaco;
import android.content.Context;
import swimminsparrow.ricordamelo.core.utils.AppExecutors;
import swimminsparrow.ricordamelo.ui.common.CommonSnackbar;
import swimminsparrow.ricordamelo.ui.common.Dialogs.DialogContoAllaRovescia;


public class AlarmsFragment extends Fragment {
    private final String TAG = AlarmsFragment.class.getSimpleName();

    //  UI   //
    private AlarmAdapter alarmAdapter;
    private GridView griglia;

    private View rootView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_ricordamelo, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initGrid();

        RateMeManager.showIfNeeded(requireActivity());

        View.OnClickListener addReminderListener = v ->
                NavHostFragment.findNavController(this).navigate(R.id.nav_nuovo_ricordamelo);

        rootView.findViewById(R.id.fab_add_reminder).setOnClickListener(addReminderListener);

        View emptyAddBtn = rootView.findViewById(R.id.btn_empty_add_reminder);
        if (emptyAddBtn != null) {
            emptyAddBtn.setOnClickListener(addReminderListener);
        }

        setupImOkBanner();
        setupNotificationWarningBanner();
        setupOverlayWarningBanner();
    }

    private void setupOverlayWarningBanner() {
        View banner = rootView.findViewById(R.id.card_overlay_disabled);
        if (banner != null) {
            View.OnClickListener clickListener = v -> {
                swimminsparrow.ricordamelo.core.utils.OverlayPermissionHelper.requestOverlayPermission(requireContext());
            };
            banner.setOnClickListener(clickListener);
            View btnAction = rootView.findViewById(R.id.btn_enable_overlay);
            if (btnAction != null) {
                btnAction.setOnClickListener(clickListener);
            }
        }
    }

    private void updateOverlayWarningBannerVisibility() {
        if (rootView == null || getContext() == null) return;
        View banner = rootView.findViewById(R.id.card_overlay_disabled);
        if (banner == null) return;
        boolean fallActive = SettingsDB.getInstance(requireContext()).getFallDetectionMode();
        boolean hasOverlay = swimminsparrow.ricordamelo.core.utils.OverlayPermissionHelper.canDrawOverlays(requireContext());
        banner.setVisibility((fallActive && !hasOverlay) ? View.VISIBLE : View.GONE);
    }

    private void setupNotificationWarningBanner() {
        View banner = rootView.findViewById(R.id.card_notifications_disabled);
        if (banner != null) {
            View.OnClickListener clickListener = v -> requestOrOpenNotificationSettings();
            banner.setOnClickListener(clickListener);
            View btnAction = rootView.findViewById(R.id.btn_enable_notifications);
            if (btnAction != null) {
                btnAction.setOnClickListener(clickListener);
            }
        }
    }

    private void requestOrOpenNotificationSettings() {
        if (!isAdded() || getActivity() == null) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (getActivity() instanceof swimminsparrow.ricordamelo.ui.BaseActivity) {
                ((swimminsparrow.ricordamelo.ui.BaseActivity) getActivity()).checkNotificationPermission(new swimminsparrow.ricordamelo.core.utils.PermissionCallback() {
                    @Override
                    public void onPermissionGranted() {
                        updateNotificationWarningBannerVisibility();
                    }

                    @Override
                    public void onPermissionDenied() {
                        openAppNotificationSettings();
                    }
                });
                return;
            }
        }
        openAppNotificationSettings();
    }

    private void openAppNotificationSettings() {
        try {
            Intent intent = new Intent();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                intent.setAction(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
            } else {
                intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.fromParts("package", requireContext().getPackageName(), null));
            }
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallback.setData(android.net.Uri.fromParts("package", requireContext().getPackageName(), null));
                startActivity(fallback);
            } catch (Exception ignored) {}
        }
    }

    private void updateNotificationWarningBannerVisibility() {
        if (rootView == null || getContext() == null) return;
        View banner = rootView.findViewById(R.id.card_notifications_disabled);
        if (banner == null) return;
        boolean granted = swimminsparrow.ricordamelo.core.utils.CheckPermissionsHandler.isNotificationGranted(requireContext());
        banner.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    private void setupImOkBanner() {
        View cardImOk = rootView.findViewById(R.id.card_im_ok_banner);
        if (cardImOk != null) {
            View.OnClickListener clickListener = v -> triggerImOk();
            cardImOk.setOnClickListener(clickListener);
            View btnAction = rootView.findViewById(R.id.btn_im_ok_action);
            if (btnAction != null) {
                btnAction.setOnClickListener(clickListener);
            }
        }
    }

    private void updateImOkBannerVisibility() {
        if (rootView == null || getContext() == null) return;
        View cardImOk = rootView.findViewById(R.id.card_im_ok_banner);
        if (cardImOk == null) return;
        cardImOk.setVisibility(View.VISIBLE);
    }

    private void triggerImOk() {
        if (getContext() == null) return;
        SettingsDB settingsDB = SettingsDB.getInstance(requireContext());
        if (!settingsDB.isImOkExplained()) {
            new swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder(requireContext(), swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder.Type.APPROVAL)
                    .setIcon(R.drawable.ic_favorite_24)
                    .setTitleText(getString(R.string.im_ok))
                    .setContentText(getString(R.string.im_ok_dialog_description))
                    .setConfirmText(getString(R.string.im_ok_dialog_send))
                    .setCancelText(getString(R.string.dialog_cancel))
                    .showCancelButton(true)
                    .setConfirmClickListener(d -> {
                        settingsDB.setImOkExplained(true);
                        sendImOk();
                    })
                    .show();
        } else {
            sendImOk();
        }
    }

    private void sendImOk() {
        String msg = getString(R.string.im_ok_status_msg);
        boolean sent = swimminsparrow.ricordamelo.core.sos.SOSManager.sendImOkMessage(requireContext(), msg, false);
        if (sent) {
            CommonSnackbar.showWithIcon(getActivity(), getString(R.string.im_ok_sent), R.drawable.baseline_done_24);
        } else {
            new swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder(requireContext(), swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder.Type.WARNING)
                    .setTitleText(getString(R.string.no_connected_contacts_title))
                    .setContentText(getString(R.string.no_connected_contacts_msg))
                    .show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.app_name);
        }
        loadFarmaciData();
        updateImOkBannerVisibility();
        updateNotificationWarningBannerVisibility();
        updateOverlayWarningBannerVisibility();
    }

    private void loadFarmaciData() {
        if (!isAdded() || getContext() == null) return;
        final Context appContext = requireContext().getApplicationContext();

        AppExecutors.getInstance().diskIO().execute(() -> {
            List<Farmaco> farmaciList = FarmacoDao.getInstance(appContext).getAll();
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (!isAdded() || getContext() == null || rootView == null || alarmAdapter == null) {
                    return;
                }
                View emptyView = rootView.findViewById(R.id.emptyMessage);
                if (farmaciList == null || farmaciList.isEmpty()) {
                    if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
                    if (griglia != null) griglia.setVisibility(View.GONE);
                    alarmAdapter.setFarmaciList(new ArrayList<>());
                } else {
                    if (emptyView != null) emptyView.setVisibility(View.GONE);
                    if (griglia != null) griglia.setVisibility(View.VISIBLE);
                    alarmAdapter.setFarmaciList(farmaciList);
                }
            });
        });
    }

    private void initGrid() {
        if (this.alarmAdapter == null){
            this.alarmAdapter = new AlarmAdapter(requireContext());
        }
        
        this.griglia = (GridView) rootView.findViewById(R.id.griglia);
        this.griglia.setAdapter(this.alarmAdapter);

        griglia.setOnItemClickListener((adapterView, view, i, id) -> {
            Farmaco farmaco = (Farmaco) alarmAdapter.getItem(i);
            DialogContoAllaRovescia dialog = new DialogContoAllaRovescia(requireContext(), farmaco, editPosition -> {
                        Bundle args = new Bundle();
                        args.putBoolean("CambiaTextview", true);
                        args.putInt("id", farmaco.getId());
                        Navigation.findNavController(requireView()).navigate(R.id.nav_nuovo_ricordamelo, args);
                    });

            dialog.setOnDismissListener(d -> loadFarmaciData());
            dialog.show();

        });
    }



}
