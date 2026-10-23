package swimminsparrow.ricordamelo.ui.sos;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.sos.ConnectionRequestManager;
import swimminsparrow.ricordamelo.core.sos.PinPairingManager;
import swimminsparrow.ricordamelo.core.sos.SOSContactsManager;
import swimminsparrow.ricordamelo.core.sos.SOSManager;
import swimminsparrow.ricordamelo.core.utils.ImageUtils;
import swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder;

public class EnterPinBottomSheetDialogFragment extends BaseBottomSheetDialogFragment {

    private EditText[] pinInputs;
    private View layoutInputState;
    private View layoutWaitingState;
    private MaterialButton btnConnect;
    private ShapeableImageView ivWaitingAvatar;
    private TextView tvWaitingTitle;

    public interface OnConnectedListener {
        void onConnected();
    }

    private OnConnectedListener onConnectedListener;

    public static EnterPinBottomSheetDialogFragment newInstance() {
        return new EnterPinBottomSheetDialogFragment();
    }

    public void setOnConnectedListener(OnConnectedListener listener) {
        this.onConnectedListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_enter_pin_bottomsheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        layoutInputState = view.findViewById(R.id.layoutInputState);
        layoutWaitingState = view.findViewById(R.id.layoutWaitingState);
        btnConnect = view.findViewById(R.id.btnConnect);
        ivWaitingAvatar = view.findViewById(R.id.ivWaitingAvatar);
        tvWaitingTitle = view.findViewById(R.id.tvWaitingTitle);

        setupPinInputLogic(view);

        btnConnect.setOnClickListener(v -> connectSOS());

        if (pinInputs != null && pinInputs[0] != null) {
            view.postDelayed(() -> {
                if (isAdded() && getContext() != null && pinInputs[0] != null) {
                    pinInputs[0].requestFocus();
                    InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(pinInputs[0], InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }, 300);
        }
    }

    private void setupPinInputLogic(View view) {
        pinInputs = new EditText[]{
                view.findViewById(R.id.etPin1),
                view.findViewById(R.id.etPin2),
                view.findViewById(R.id.etPin3),
                view.findViewById(R.id.etPin4),
                view.findViewById(R.id.etPin5),
                view.findViewById(R.id.etPin6)
        };

        NestedScrollView scrollView = view.findViewById(R.id.scrollEnterPin);
        View layoutPinBoxes = view.findViewById(R.id.layoutPinBoxes);

        for (int i = 0; i < pinInputs.length; i++) {
            final int index = i;
            if (pinInputs[i] == null) continue;

            pinInputs[i].setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && scrollView != null && layoutPinBoxes != null) {
                    scrollView.post(() -> {
                        int scrollY = Math.max(0, layoutPinBoxes.getTop() - 30);
                        scrollView.smoothScrollTo(0, scrollY);
                    });
                }
            });

            if (i == 5) {
                pinInputs[i].setOnEditorActionListener((v, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        connectSOS();
                        return true;
                    }
                    return false;
                });
            }

            pinInputs[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() > 1) {
                        String clean = s.toString().replaceAll("[^0-9]", "");
                        for (int j = 0; j < Math.min(clean.length(), 6); j++) {
                            if (pinInputs[j] != null) {
                                pinInputs[j].setText(String.valueOf(clean.charAt(j)));
                            }
                        }
                        if (clean.length() >= 6 && pinInputs[5] != null) {
                            pinInputs[5].requestFocus();
                            pinInputs[5].setSelection(1);
                        }
                        return;
                    }

                    if (s.length() == 1 && index < 5 && pinInputs[index + 1] != null) {
                        pinInputs[index + 1].requestFocus();
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            pinInputs[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (pinInputs[index].getText().length() == 0 && index > 0 && pinInputs[index - 1] != null) {
                        pinInputs[index - 1].requestFocus();
                        pinInputs[index - 1].setText("");
                        return true;
                    }
                }
                return false;
            });
        }
    }

    private String getEnteredPin() {
        StringBuilder sb = new StringBuilder();
        if (pinInputs != null) {
            for (EditText et : pinInputs) {
                if (et != null && et.getText() != null) {
                    sb.append(et.getText().toString().trim());
                }
            }
        }
        return sb.toString();
    }

    private void hideKeyboard() {
        try {
            if (getView() != null && getContext() != null) {
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
                }
            }
        } catch (Exception ignored) {}
    }

    private void connectSOS() {
        String cleanPin = getEnteredPin().replaceAll("[^0-9]", "");
        if (cleanPin.length() != 6) {
            Toast.makeText(requireContext(), R.string.otp_must_be_6_digits, Toast.LENGTH_SHORT).show();
            return;
        }

        hideKeyboard();
        btnConnect.setEnabled(false);
        btnConnect.setText(R.string.otp_verifying);

        final Context appContext = requireContext().getApplicationContext();

        PinPairingManager.resolveAndRequest(appContext, cleanPin,
                new PinPairingManager.PairingRequestCallback() {
                    @Override
                    public void onRequestSent(String targetName, String targetTopic, String masterKey, String initialTargetAvatar, String reqId) {
                        // 1. Post persistent background notification with cancel action
                        ConnectionRequestManager.postWaitingForApprovalNotification(appContext, targetName, reqId);

                        // 2. Transition inline UI inside bottom sheet if still visible
                        if (isAdded() && getView() != null) {
                            if (layoutInputState != null) layoutInputState.setVisibility(View.GONE);
                            if (layoutWaitingState != null) layoutWaitingState.setVisibility(View.VISIBLE);
                            if (tvWaitingTitle != null) {
                                tvWaitingTitle.setText(getString(R.string.sos_waiting_approval, targetName));
                            }
                            if (ivWaitingAvatar != null) {
                                ImageUtils.bindAvatar(ivWaitingAvatar, initialTargetAvatar, R.drawable.avatar);
                            }
                        }

                        // 3. Listen for User B's approval response
                        PinPairingManager.listenForApprovalResponse(appContext, masterKey, reqId,
                                new PinPairingManager.ApprovalCallback() {
                                    @Override
                                    public void onApproved(String targetName, String targetTopic, String approvedMasterKey, String targetAvatar) {
                                        ConnectionRequestManager.cancelWaitingNotification(appContext);

                                        String avatarToUse = (targetAvatar != null && !targetAvatar.isEmpty())
                                                ? targetAvatar
                                                : initialTargetAvatar;
                                        SOSContactsManager.addMutualContact(appContext, targetTopic, targetName, approvedMasterKey, avatarToUse);
                                        SOSManager.startServices(appContext);

                                        if (isAdded() && getContext() != null) {
                                            new DialogBuilder(requireContext(), DialogBuilder.Type.SUCCESS)
                                                    .setTitleText(getString(R.string.connected))
                                                    .setContentText(getString(R.string.sos_connected_receive_from, targetName))
                                                    .setConfirmClickListener(d -> {
                                                        dismiss();
                                                        if (onConnectedListener != null) {
                                                            onConnectedListener.onConnected();
                                                        }
                                                    })
                                                    .show();
                                        } else {
                                            ConnectionRequestManager.postApprovalResultNotification(
                                                    appContext,
                                                    appContext.getString(R.string.connected),
                                                    appContext.getString(R.string.sos_connected_receive_from, targetName));
                                        }
                                    }

                                    @Override
                                    public void onAlreadyConnected(String targetName, String targetTopic, String approvedMasterKey, String targetAvatar) {
                                        ConnectionRequestManager.cancelWaitingNotification(appContext);

                                        String avatarToUse = (targetAvatar != null && !targetAvatar.trim().isEmpty())
                                                ? targetAvatar
                                                : initialTargetAvatar;
                                        SOSContactsManager.addMutualContact(appContext, targetTopic, targetName, approvedMasterKey, avatarToUse);
                                        SOSManager.startServices(appContext);

                                        if (isAdded() && getContext() != null) {
                                            new DialogBuilder(requireContext(), DialogBuilder.Type.WARNING)
                                                    .setTitleText(getString(R.string.sos_already_connected_title))
                                                    .setContentText(getString(R.string.sos_already_connected_receive_msg, targetName))
                                                    .setConfirmText(getString(android.R.string.ok))
                                                    .setConfirmClickListener(d -> {
                                                        dismiss();
                                                        if (onConnectedListener != null) {
                                                            onConnectedListener.onConnected();
                                                        }
                                                    })
                                                    .show();
                                        } else {
                                            ConnectionRequestManager.postApprovalResultNotification(
                                                    appContext,
                                                    appContext.getString(R.string.sos_already_connected_title),
                                                    appContext.getString(R.string.sos_already_connected_receive_msg, targetName));
                                        }
                                    }

                                    @Override
                                    public void onAlreadyConnected(String targetName) {
                                        onAlreadyConnected(targetName, "", "", "");
                                    }

                                    @Override
                                    public void onRejected(String targetName) {
                                        ConnectionRequestManager.cancelWaitingNotification(appContext);
                                        if (isAdded() && getContext() != null) {
                                            restoreInputState();
                                            showResultDialog(DialogBuilder.Type.ERROR,
                                                    getString(R.string.sos_request_rejected_title),
                                                    getString(R.string.sos_request_rejected_desc, targetName), null);
                                        } else {
                                            ConnectionRequestManager.postApprovalResultNotification(
                                                    appContext,
                                                    appContext.getString(R.string.sos_request_rejected_title),
                                                    appContext.getString(R.string.sos_request_rejected_desc, targetName));
                                        }
                                    }

                                    @Override
                                    public void onError(String error) {
                                        ConnectionRequestManager.cancelWaitingNotification(appContext);
                                        if (isAdded() && getContext() != null) {
                                            restoreInputState();
                                            showResultDialog(DialogBuilder.Type.ERROR,
                                                    getString(R.string.error),
                                                    getString(R.string.sos_connect_failed_prefix) + " " + error, null);
                                        } else {
                                            ConnectionRequestManager.postApprovalResultNotification(
                                                    appContext,
                                                    appContext.getString(R.string.error),
                                                    error);
                                        }
                                    }
                                });
                    }

                    @Override
                    public void onAlreadyConnected(String targetName) {
                        restoreInputState();
                        showResultDialog(DialogBuilder.Type.WARNING,
                                getString(R.string.sos_already_connected_title),
                                getString(R.string.sos_already_connected_receive_msg, targetName),
                                getString(R.string.btn_close));
                    }

                    @Override
                    public void onSelfConnectionError() {
                        restoreInputState();
                        showResultDialog(DialogBuilder.Type.ERROR,
                                getString(R.string.sos_self_connection_error_title),
                                getString(R.string.sos_self_connection_error_msg),
                                getString(R.string.btn_close));
                    }

                    @Override
                    public void onError(String error) {
                        restoreInputState();
                        showResultDialog(DialogBuilder.Type.ERROR,
                                getString(R.string.sos_invalid_code_title),
                                getString(R.string.sos_invalid_code_desc), null);
                    }
                });
    }

    private void restoreInputState() {
        if (!isAdded() || getView() == null) return;
        if (layoutWaitingState != null) layoutWaitingState.setVisibility(View.GONE);
        if (layoutInputState != null) layoutInputState.setVisibility(View.VISIBLE);
        if (btnConnect != null) {
            btnConnect.setEnabled(true);
            btnConnect.setText(R.string.sos_connect_btn);
        }
    }

    private void showResultDialog(DialogBuilder.Type type, String title, String message, String confirmText) {
        if (!isAdded() || getContext() == null) return;
        DialogBuilder builder = new DialogBuilder(requireContext(), type)
                .setTitleText(title)
                .setContentText(message);
        if (confirmText != null) {
            builder.setConfirmText(confirmText);
        }
        builder.show();
    }
}
