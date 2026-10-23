package swimminsparrow.ricordamelo.ui.sos;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import swimminsparrow.ricordamelo.R;

public class ConnectChoiceBottomSheetDialogFragment extends BaseBottomSheetDialogFragment {

    public interface OnOptionSelectedListener {
        void onSharePinSelected();
        void onEnterPinSelected();
    }

    private OnOptionSelectedListener listener;

    public static ConnectChoiceBottomSheetDialogFragment newInstance() {
        return new ConnectChoiceBottomSheetDialogFragment();
    }

    public void setListener(OnOptionSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_connect_choice_bottomsheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.cardSharePin).setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onSharePinSelected();
            }
        });

        view.findViewById(R.id.cardEnterPin).setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onEnterPinSelected();
            }
        });
    }
}
