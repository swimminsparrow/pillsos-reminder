package swimminsparrow.ricordamelo.ui.common;

import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import swimminsparrow.ricordamelo.ui.sos.BaseBottomSheetDialogFragment;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.widget.EditText;
import androidx.core.content.ContextCompat;
import java.lang.reflect.Field;
import java.util.Locale;

import swimminsparrow.ricordamelo.R;

/**
 * Modern Wheel / Drum-roll Time Picker Bottom Sheet with haptic feedback ticks on scroll.
 */
public class WheelTimePickerBottomSheet extends BaseBottomSheetDialogFragment {

    public interface OnTimeSetListener {
        void onTimeSet(int hourOfDay, int minute);
    }

    private static final String ARG_HOUR = "arg_hour";
    private static final String ARG_MINUTE = "arg_minute";

    private OnTimeSetListener listener;
    private NumberPicker npHours;
    private NumberPicker npMinutes;

    public static WheelTimePickerBottomSheet newInstance(int initialHour, int initialMinute) {
        WheelTimePickerBottomSheet fragment = new WheelTimePickerBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_HOUR, initialHour);
        args.putInt(ARG_MINUTE, initialMinute);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnTimeSetListener(OnTimeSetListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_wheel_time_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int initialHour = 8;
        int initialMinute = 0;
        if (getArguments() != null) {
            initialHour = getArguments().getInt(ARG_HOUR, 8);
            initialMinute = getArguments().getInt(ARG_MINUTE, 0);
        }

        npHours = view.findViewById(R.id.np_hours);
        npMinutes = view.findViewById(R.id.np_minutes);
        View btnCancel = view.findViewById(R.id.btn_time_cancel);
        View btnConfirm = view.findViewById(R.id.btn_time_confirm);

        NumberPicker.Formatter twoDigitFormatter = i -> String.format(Locale.getDefault(), "%02d", i);

        // Configure Hours
        if (npHours != null) {
            npHours.setMinValue(0);
            npHours.setMaxValue(23);
            npHours.setFormatter(twoDigitFormatter);
            npHours.setValue(initialHour);
            npHours.setWrapSelectorWheel(true);
            styleNumberPicker(npHours);
            npHours.setOnValueChangedListener((picker, oldVal, newVal) -> triggerHaptic(picker));
        }

        // Configure Minutes
        if (npMinutes != null) {
            npMinutes.setMinValue(0);
            npMinutes.setMaxValue(59);
            npMinutes.setFormatter(twoDigitFormatter);
            npMinutes.setValue(initialMinute);
            npMinutes.setWrapSelectorWheel(true);
            styleNumberPicker(npMinutes);
            npMinutes.setOnValueChangedListener((picker, oldVal, newVal) -> triggerHaptic(picker));
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dismiss());
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                if (listener != null && npHours != null && npMinutes != null) {
                    listener.onTimeSet(npHours.getValue(), npMinutes.getValue());
                }
                dismiss();
            });
        }
    }

    private void styleNumberPicker(NumberPicker picker) {
        if (picker == null || getContext() == null) return;

        int primaryColor = ContextCompat.getColor(requireContext(), R.color.defaultColorDark);
        float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 34, getResources().getDisplayMetrics());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            picker.setTextSize(textSizePx);
            picker.setTextColor(primaryColor);
            picker.setSelectionDividerHeight(0);
        } else {
            try {
                Field f = NumberPicker.class.getDeclaredField("mSelectionDivider");
                f.setAccessible(true);
                f.set(picker, new ColorDrawable(Color.TRANSPARENT));
            } catch (Exception ignored) {
            }
        }

        for (int i = 0; i < picker.getChildCount(); i++) {
            View child = picker.getChildAt(i);
            if (child instanceof EditText) {
                EditText et = (EditText) child;
                et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
                et.setTextColor(primaryColor);
                et.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                et.setFocusable(false);
            }
        }
    }

    private void triggerHaptic(View view) {
        if (view == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }
}
