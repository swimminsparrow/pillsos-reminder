package swimminsparrow.ricordamelo.ui.sos;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.sos.ConnectionRequestManager;
import swimminsparrow.ricordamelo.core.sos.PinPairingManager;

public class PinPairingBottomSheetDialogFragment extends BaseBottomSheetDialogFragment {

    private String currentOtpRaw;
    private String currentOtpFormatted;

    public static PinPairingBottomSheetDialogFragment newInstance() {
        return new PinPairingBottomSheetDialogFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_pin_pairing_bottomsheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvCode = view.findViewById(R.id.tvCode);
        Button btnShare = view.findViewById(R.id.btnShare);
        Button btnClose = view.findViewById(R.id.btnCloseSheet);

        // Dismiss any stale pending approval dialogs from previous attempts
        ConnectionRequestManager.dismissActiveApprovalDialog();

        // Generate fresh OTP for pairing
        currentOtpRaw = PinPairingManager.generateNewOtp();
        currentOtpFormatted = PinPairingManager.formatPinForDisplay(currentOtpRaw);
        PinPairingManager.publishPairingPayload(requireContext(), currentOtpRaw);

        String clean = currentOtpRaw.replaceAll("[^0-9]", "");
        if (clean.length() == 6) {
            TextView[] tvDigits = new TextView[]{
                    view.findViewById(R.id.tvDigit1),
                    view.findViewById(R.id.tvDigit2),
                    view.findViewById(R.id.tvDigit3),
                    view.findViewById(R.id.tvDigit4),
                    view.findViewById(R.id.tvDigit5),
                    view.findViewById(R.id.tvDigit6)
            };
            for (int i = 0; i < 6; i++) {
                if (tvDigits[i] != null) {
                    tvDigits[i].setText(String.valueOf(clean.charAt(i)));
                }
            }
        }

        if (tvCode != null) {
            tvCode.setText(currentOtpFormatted);
        }

        btnShare.setOnClickListener(v -> {
            if (currentOtpFormatted == null) return;
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            String shareMsg = getString(R.string.sos_otp_share_format, currentOtpFormatted);
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareMsg);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)));
        });

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }
    }
}
