package swimminsparrow.ricordamelo.ui.sos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.sos.ConnectionRequestManager;
import swimminsparrow.ricordamelo.core.sos.SOSContact;
import swimminsparrow.ricordamelo.core.sos.SOSContactsManager;

public class SosFragment extends Fragment {

    private View rootView;
    private RecyclerView rvContacts;
    private View layoutEmptyState;
    private SOSContactsAdapter adapter;

    private final List<SOSContact> allContacts = new ArrayList<>();

    private final BroadcastReceiver contactsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isAdded() && getContext() != null) {
                loadContacts();
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_sos, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvContacts = rootView.findViewById(R.id.rvContacts);
        layoutEmptyState = rootView.findViewById(R.id.layoutEmptyState);

        rvContacts.setLayoutManager(new LinearLayoutManager(requireContext()));
        if (rvContacts.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) rvContacts.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        adapter = new SOSContactsAdapter(requireContext());
        rvContacts.setAdapter(adapter);

        setupConnectCard();
        loadContacts();
    }

    private void setupConnectCard() {
        View.OnClickListener connectClick = v -> openConnectChoiceDialog();

        View card = rootView.findViewById(R.id.cardConnectContact);
        View btn = rootView.findViewById(R.id.btnConnectContact);

        if (card != null) card.setOnClickListener(connectClick);
        if (btn != null) btn.setOnClickListener(connectClick);

        View btnLearnWhenSent = rootView.findViewById(R.id.btnSosLearnWhenSent);
        if (btnLearnWhenSent != null) {
            btnLearnWhenSent.setOnClickListener(v -> showSosInfoDialog());
        }
    }

    private void showSosInfoDialog() {
        if (getContext() == null) return;
        Context context = requireContext();
        int titleColor = ContextCompat.getColor(context, R.color.primary_text);

        String partA = getString(R.string.faq_sos_a);
        String titleB = getString(R.string.faq_sos_b);
        String partB = getString(R.string.faq_sos_b_desc);

        SpannableStringBuilder ssb = new SpannableStringBuilder();

        // 1. Contenuto prima sezione
        int startA = ssb.length();
        ssb.append(partA);
        formatSubHeaders(ssb, startA, ssb.length(), titleColor);

        // Separazione tra le sezioni
        ssb.append("\n\n\n");

        // 2. Titolo seconda sezione: stesso stile identico del titolo del dialog "Quando vengono inviati gli SOS?"
        // (20sp, bold, primary_text #111827, centrato)
        int startBTitle = ssb.length();
        ssb.append(titleB).append("\n");
        int endBTitle = ssb.length();

        ssb.setSpan(new StyleSpan(Typeface.BOLD), startBTitle, endBTitle - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new AbsoluteSizeSpan(20, true), startBTitle, endBTitle - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(titleColor), startBTitle, endBTitle - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), startBTitle, endBTitle, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Spaziatura dopo il titolo
        ssb.append("\n");

        // 3. Contenuto seconda sezione
        int startBDesc = ssb.length();
        ssb.append(partB);
        formatSubHeaders(ssb, startBDesc, ssb.length(), titleColor);

        new swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder(context, swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder.Type.INFO)
                .setIcon(R.drawable.baseline_help_24)
                .setTitleText(getString(R.string.faq_sos))
                .setContentText(ssb)
                .setMessageGravity(Gravity.START)
                .setConfirmText(getString(android.R.string.ok))
                .show();
    }

    private void formatSubHeaders(SpannableStringBuilder ssb, int start, int end, int titleColor) {
        String text = ssb.subSequence(start, end).toString();
        int currentPos = 0;
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            int colonIdx = line.indexOf(':');
            if (colonIdx != -1) {
                int spanStart = start + currentPos;
                int spanEnd = spanStart + colonIdx + 1;
                ssb.setSpan(new StyleSpan(Typeface.BOLD), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(titleColor), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            currentPos += line.length() + 1;
        }
    }

    private void openConnectChoiceDialog() {
        ConnectChoiceBottomSheetDialogFragment choiceDialog = ConnectChoiceBottomSheetDialogFragment.newInstance();
        choiceDialog.setListener(new ConnectChoiceBottomSheetDialogFragment.OnOptionSelectedListener() {
            @Override
            public void onSharePinSelected() {
                PinPairingBottomSheetDialogFragment shareDialog = PinPairingBottomSheetDialogFragment.newInstance();
                shareDialog.show(getChildFragmentManager(), "PinPairingBottomSheet");
            }

            @Override
            public void onEnterPinSelected() {
                EnterPinBottomSheetDialogFragment enterDialog = EnterPinBottomSheetDialogFragment.newInstance();
                enterDialog.setOnConnectedListener(() -> loadContacts());
                enterDialog.show(getChildFragmentManager(), "EnterPinBottomSheet");
            }
        });
        choiceDialog.show(getChildFragmentManager(), "ConnectChoiceBottomSheet");
    }

    private void loadContacts() {
        if (!isAdded() || getContext() == null) return;

        allContacts.clear();
        allContacts.addAll(SOSContactsManager.getContacts(requireContext()));
        adapter.setContacts(allContacts);

        boolean empty = allContacts.isEmpty();
        rvContacts.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.sos_emergency_network);
        }
        loadContacts();
        if (getActivity() != null) {
            ConnectionRequestManager.checkPendingRequests(getActivity());
        }
        if (getContext() != null && !allContacts.isEmpty()) {
            String myUuid = swimminsparrow.ricordamelo.core.data.SettingsDB.getInstance(requireContext()).getUserUUID();
            if (myUuid != null) {
                swimminsparrow.ricordamelo.core.ntfy.NtfyManager ntfy =
                        swimminsparrow.ricordamelo.core.ntfy.NtfyManager.getInstance(requireContext(), myUuid);
                for (SOSContact c : allContacts) {
                    if (c.getTopic() != null) {
                        ntfy.pingContact(c.getTopic());
                    }
                }
            }
        }
        if (getContext() != null) {
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
            } catch (Exception ignored) {}
        }
    }
}
