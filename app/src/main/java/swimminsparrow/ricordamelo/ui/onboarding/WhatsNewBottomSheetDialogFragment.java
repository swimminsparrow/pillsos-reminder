package swimminsparrow.ricordamelo.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.ui.sos.BaseBottomSheetDialogFragment;

public class WhatsNewBottomSheetDialogFragment extends BaseBottomSheetDialogFragment {

    public static WhatsNewBottomSheetDialogFragment newInstance() {
        return new WhatsNewBottomSheetDialogFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_whats_new_bottomsheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnGotIt = view.findViewById(R.id.btnWhatsNewGotIt);
        if (btnGotIt != null) {
            btnGotIt.setOnClickListener(v -> {
                if (getContext() != null) {
                    SettingsDB.getInstance(requireContext()).setWhatsNew(2);
                }
                dismiss();
            });
        }
    }
}
