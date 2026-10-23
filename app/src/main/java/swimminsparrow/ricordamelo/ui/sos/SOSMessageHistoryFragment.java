package swimminsparrow.ricordamelo.ui.sos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import swimminsparrow.ricordamelo.ui.common.CommonSnackbar;

import java.util.List;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.data.models.SOSMessage;
import swimminsparrow.ricordamelo.core.data.dao.SOSMessageDao;
import swimminsparrow.ricordamelo.core.sos.SOSContact;
import swimminsparrow.ricordamelo.core.sos.SOSContactsManager;
import swimminsparrow.ricordamelo.core.utils.AppExecutors;
import swimminsparrow.ricordamelo.core.sos.SOSManager;

public class SOSMessageHistoryFragment extends Fragment {

    public static final String ARG_TOPIC = "topic";
    public static final String ARG_NAME = "name";
    public static final String ARG_AVATAR = "avatar";

    private String topic;
    private String name;
    private String avatarBase64;
    private View toolbarCustomView;
    private int originalContentInsetStart = -1;
    private int originalContentInsetStartWithNavigation = -1;

    private RecyclerView rvHistory;
    private TextView tvEmpty;
    private SOSMessageHistoryAdapter adapter;

    private final BroadcastReceiver contactsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isAdded() && getContext() != null) {
                if (!SOSContactsManager.isContactPaired(requireContext(), topic)) {
                    if (isRemoving() || !isResumed()) return;
                    if (getView() != null) {
                        try {
                            NavController navController = Navigation.findNavController(getView());
                            if (navController.getCurrentDestination() != null
                                    && navController.getCurrentDestination().getId() == R.id.nav_sos_history) {
                                if (!navController.popBackStack(R.id.nav_sos, false)) {
                                    navController.navigate(R.id.nav_sos);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    return;
                }
                refreshContactInfo();
                loadHistory();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sos_message_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            topic = getArguments().getString(ARG_TOPIC);
            name = getArguments().getString(ARG_NAME, "Contatto");
            avatarBase64 = getArguments().getString(ARG_AVATAR);
        }

        setupToolbarContact();

        rvHistory = view.findViewById(R.id.rvMessageHistory);
        tvEmpty = view.findViewById(R.id.tvEmptyHistory);
        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        if (rvHistory.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) rvHistory.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        adapter = new SOSMessageHistoryAdapter(requireContext(), new java.util.ArrayList<>());
        rvHistory.setAdapter(adapter);

        refreshContactInfo();
        loadHistory();

        // Setup Unified Context Menu (⋮)
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menu.clear();
                menuInflater.inflate(R.menu.menu_sos_contact_detail, menu);
                MenuItem simulateItem = menu.findItem(R.id.action_simulate_sos);
                if (simulateItem != null) {
                    simulateItem.setVisible(true);
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == R.id.action_simulate_sos) {
                    handleSimulateSos();
                    return true;
                } else if (id == R.id.action_clear_history) {
                    confirmClearHistory();
                    return true;
                } else if (id == R.id.action_disconnect) {
                    handleDisconnect();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void refreshContactInfo() {
        if (getContext() == null || topic == null)
            return;

        SOSContact contact = SOSContactsManager.getContactByTopic(requireContext(), topic);

        if (contact != null) {
            if (avatarBase64 == null || avatarBase64.isEmpty()) {
                avatarBase64 = contact.getAvatarBase64();
            }
            if (name == null || name.isEmpty() || "Contatto".equals(name)) {
                name = contact.getName();
            }
        }

        setupToolbarContact();
    }

    private void handleSimulateSos() {
        if (getContext() == null)
            return;
        boolean sent = SOSManager.sendSOSMessage(requireContext(), getString(R.string.sos_simulated_alert_msg));
        if (sent) {
            CommonSnackbar.showWithIcon(getActivity(), getString(R.string.test_sos_sent), R.drawable.baseline_done_24);
            loadHistory();
        } else {
            new swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder(requireContext(),
                    swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder.Type.WARNING)
                    .setTitleText(getString(R.string.sos_no_connected_contacts_title))
                    .setContentText(getString(R.string.sos_no_connected_contacts_desc))
                    .show();
        }
    }

    private void confirmClearHistory() {
        if (getContext() == null || topic == null)
            return;
        String contactDisplayName = (name != null && !name.trim().isEmpty()) ? name : "";
        new swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder(requireContext(),
                swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder.Type.DESTRUCTIVE)
                .setTitleText(getString(R.string.sos_clear_history_title))
                .setContentText(getString(R.string.sos_clear_history_confirm, contactDisplayName))
                .setConfirmText(getString(R.string.btn_clear))
                .setCancelText(getString(R.string.dialog_cancel))
                .setConfirmClickListener(d -> {
                    SOSMessageDao.getInstance(requireContext()).clearHistory(topic);
                    CommonSnackbar.showWithIcon(getActivity(), getString(R.string.sos_history_cleared),
                            R.drawable.baseline_done_24);
                    loadHistory();
                })
                .show();
    }

    private void handleDisconnect() {
        if (!isAdded() || getContext() == null || topic == null)
            return;
        new swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder(requireContext(),
                swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder.Type.DESTRUCTIVE)
                .setTitleText(getString(R.string.sos_disconnect_complete_title))
                .setContentText(getString(R.string.sos_disconnect_complete_desc))
                .setConfirmText(getString(R.string.sos_disconnect_btn))
                .setCancelText(getString(R.string.dialog_cancel))
                .setConfirmClickListener(d -> {
                    SOSContactsManager.disconnectContact(requireContext(), topic);
                    showDisconnectToast();
                    if (isAdded() && getView() != null) {
                        try {
                            NavController navController = Navigation.findNavController(getView());
                            if (navController.getCurrentDestination() != null
                                    && navController.getCurrentDestination().getId() == R.id.nav_sos_history) {
                                if (!navController.popBackStack(R.id.nav_sos, false)) {
                                    navController.navigate(R.id.nav_sos);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                })
                .show();
    }

    private void showDisconnectToast() {
        if (getContext() == null) return;
        boolean online = swimminsparrow.ricordamelo.core.utils.NetworkUtils.isNetworkAvailable(requireContext());
        int resId = online ? R.string.sos_contact_disconnected_toast : R.string.sos_contact_disconnected_offline_toast;
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show();
    }

    private void loadHistory() {
        if (!isAdded() || getContext() == null || topic == null)
            return;

        final Context appContext = requireContext().getApplicationContext();
        final String currentTopic = topic;

        AppExecutors.getInstance().diskIO().execute(() -> {
            List<SOSMessage> historyList = SOSMessageDao.getInstance(appContext).getMessageHistory(currentTopic);
            if (historyList != null) {
                java.util.Collections.reverse(historyList);
            }
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (!isAdded() || getContext() == null || rvHistory == null || tvEmpty == null) {
                    return;
                }
                if (historyList == null || historyList.isEmpty()) {
                    rvHistory.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    if (adapter != null) {
                        adapter.updateMessages(new java.util.ArrayList<>());
                    }
                } else {
                    rvHistory.setVisibility(View.VISIBLE);
                    tvEmpty.setVisibility(View.GONE);
                    if (adapter != null) {
                        boolean wasNearBottom = !rvHistory.canScrollVertically(1);
                        boolean wasEmpty = adapter.getItemCount() <= 1;
                        adapter.updateMessages(historyList);
                        if (wasEmpty || wasNearBottom) {
                            rvHistory.scrollToPosition(adapter.getItemCount() - 1);
                        }
                    }
                }
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshContactInfo();
        loadHistory();
        if (getContext() != null) {
            if (topic != null) {
                swimminsparrow.ricordamelo.core.ntfy.NtfyManager.getInstance(requireContext(), topic).startReceiving();
            }
            String myUuid = swimminsparrow.ricordamelo.core.data.SettingsDB.getInstance(requireContext()).getUserUUID();
            if (myUuid != null && !myUuid.isEmpty()) {
                swimminsparrow.ricordamelo.core.ntfy.NtfyManager.getInstance(requireContext(), myUuid).startReceiving();
            }
            IntentFilter filter = new IntentFilter(SOSContactsManager.ACTION_SOS_CONTACTS_UPDATED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(contactsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                requireContext().registerReceiver(contactsReceiver, filter);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getContext() != null) {
            try {
                requireContext().unregisterReceiver(contactsReceiver);
            } catch (Exception ignored) {
            }
        }
    }

    private void setupToolbarContact() {
        if (!isAdded() || getActivity() == null)
            return;

        Toolbar toolbar = getActivity().findViewById(R.id.toolbar);
        if (toolbar != null) {
            if (originalContentInsetStart == -1) {
                originalContentInsetStart = toolbar.getContentInsetStart();
                originalContentInsetStartWithNavigation = toolbar.getContentInsetStartWithNavigation();
            }
            toolbar.setContentInsetsRelative(0, toolbar.getContentInsetEnd());
            toolbar.setContentInsetStartWithNavigation(0);
        }

        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                if (toolbarCustomView == null) {
                    toolbarCustomView = LayoutInflater.from(requireContext()).inflate(R.layout.toolbar_contact_title,
                            null);
                }
                ShapeableImageView ivAvatar = toolbarCustomView.findViewById(R.id.ivToolbarAvatar);
                TextView tvName = toolbarCustomView.findViewById(R.id.tvToolbarName);

                if (tvName != null) {
                    tvName.setText((name != null && !name.trim().isEmpty()) ? name : "Contatto");
                }

                if (ivAvatar != null) {
                    bindAvatar(ivAvatar, avatarBase64);
                }

                if (actionBar.getCustomView() != toolbarCustomView) {
                    actionBar.setDisplayShowTitleEnabled(false);
                    actionBar.setCustomView(toolbarCustomView, new ActionBar.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.START | Gravity.CENTER_VERTICAL));
                    actionBar.setDisplayShowCustomEnabled(true);
                }
            }
        }
    }

    private void bindAvatar(ShapeableImageView ivAvatar, String avatarBase64) {
        swimminsparrow.ricordamelo.core.utils.ImageUtils.bindAvatar(
                ivAvatar, avatarBase64, R.drawable.avatar);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() != null) {
            Toolbar toolbar = getActivity().findViewById(R.id.toolbar);
            if (toolbar != null && originalContentInsetStart != -1) {
                toolbar.setContentInsetsRelative(originalContentInsetStart, toolbar.getContentInsetEnd());
                toolbar.setContentInsetStartWithNavigation(originalContentInsetStartWithNavigation);
            }
        }
        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayShowCustomEnabled(false);
                actionBar.setCustomView(null);
                actionBar.setDisplayShowTitleEnabled(true);
                try {
                    NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment);
                    NavDestination currentDest = navController.getCurrentDestination();
                    if (currentDest != null && currentDest.getLabel() != null) {
                        actionBar.setTitle(currentDest.getLabel());
                        getActivity().setTitle(currentDest.getLabel());
                    }
                } catch (Exception ignored) {
                }
            }
        }
        toolbarCustomView = null;
    }
}
