package swimminsparrow.ricordamelo.ui.alarms;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.content.Context;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.transition.ChangeBounds;
import androidx.transition.Fade;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.imageview.ShapeableImageView;

import swimminsparrow.ricordamelo.ui.common.CommonSnackbar;
import swimminsparrow.ricordamelo.ui.common.WheelTimePickerBottomSheet;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder;
import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.core.data.dao.FarmacoDao;
import swimminsparrow.ricordamelo.core.data.models.Farmaco;
import swimminsparrow.ricordamelo.core.utils.AppExecutors;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;

public class NewAlarmFragment extends Fragment {
    private static final String TAG = "NuovoRicordamelo";

    private static final int[] DAY_BUTTON_IDS = {
            R.id.mDomenica, R.id.mLunedi, R.id.mMartedi, R.id.mMercoledi,
            R.id.mGiovedi, R.id.mVenerdi, R.id.mSabato
    };

    // UI
    private CheckBox mForever;
    private TextView dataFine;
    private ShapeableImageView mFoto;
    private TextView mEditOrari, mNome;
    private ChipGroup chipGroupOrari;
    private View buttonEmptyAddTime;
    private View rootView;
    private View toggleDurationForever;
    private View toggleDurationUntil;
    private ImageView ivDurationForever;
    private ImageView ivDurationUntil;
    private TextView tvDurationForever;
    private TextView tvDurationUntil;
    private View containerDurationUntil;
    private TextView tvDurationRemaining;

    // Days Segmented UI
    private View toggleDaysEveryday;
    private View toggleDaysCustom;
    private ImageView ivDaysEveryday;
    private ImageView ivDaysCustom;
    private TextView tvDaysEveryday;
    private TextView tvDaysCustom;
    private LinearLayout layoutDaysContainer;

    // State
    private boolean isEditMode = false;
    private boolean isDirty = false;
    private OnBackPressedCallback backPressedCallback;
    private final ArrayList<String> ore = new ArrayList<>();
    private final ArrayList<String> minuti = new ArrayList<>();
    private final ArrayList<Integer> days = new ArrayList<>(Arrays.asList(1, 1, 1, 1, 1, 1, 1));
    private GregorianCalendar whenToFinish;

    private Farmaco farmaco;

    private final ActivityResultLauncher<Intent> photoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Constants.IMAGELOADED_RESULTCODE) {
                    try {
                        Intent data = result.getData();
                        if (data == null)
                            return;
                        String path = data.getStringExtra("imagePath");
                        if (path == null)
                            return;

                        farmaco.setImagePath(path);
                        Bitmap datiFoto = BitmapFactory.decodeFile(path);
                        if (datiFoto != null) {
                            mFoto.setImageBitmap(datiFoto);
                        }
                        isDirty = true;
                        SpockLogger.info(TAG, "Image loaded from " + path);
                    } catch (Exception e) {
                        SpockLogger.errorLogWithToast(requireContext(), NewAlarmFragment.class, null, e);
                    }
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_newalarm, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews();
        setupInitialState();

        // FIX 3: getIntent() → arguments Bundle (Fragment receives args, not Intents)
        Bundle args = getArguments();
        this.farmaco = new Farmaco();
        if (args != null) {
            isEditMode = args.getBoolean("CambiaTextview", false);
            int farmacoId = args.getInt("id", -1);

            if (isEditMode) {
                this.farmaco = FarmacoDao.getInstance(requireContext()).getById(farmacoId);

                populateUIForEditing();
            }
        }

        setupListeners();
        setupBackNavigationHandler();
    }

    private void setupBackNavigationHandler() {
        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isDirty) {
                    showUnsavedChangesDialog();
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);
    }

    private void showUnsavedChangesDialog() {
        new DialogBuilder(requireContext(), DialogBuilder.Type.WARNING)
                .setTitleText(getString(R.string.attenzione))
                .setContentText(getString(R.string.unsaved_changes_message))
                .setConfirmText(getString(R.string.unsaved_changes_discard))
                .setCancelText(getString(R.string.unsaved_changes_keep_editing))
                .setConfirmButtonDestructive(true)
                .setConfirmClickListener(sDialog -> {
                    isDirty = false;
                    if (backPressedCallback != null) {
                        backPressedCallback.setEnabled(false);
                    }
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                })
                .show();
    }

    private void bindViews() {
        mEditOrari = rootView.findViewById(R.id.editOrari);
        mFoto = rootView.findViewById(R.id.mFoto);
        mNome = rootView.findViewById(R.id.mNome);
        mForever = rootView.findViewById(R.id.mForever);
        dataFine = rootView.findViewById(R.id.dataFine);
        chipGroupOrari = rootView.findViewById(R.id.chipGroupOrari);
        toggleDurationForever = rootView.findViewById(R.id.toggle_duration_forever);
        toggleDurationUntil = rootView.findViewById(R.id.toggle_duration_until);
        ivDurationForever = rootView.findViewById(R.id.iv_duration_forever);
        ivDurationUntil = rootView.findViewById(R.id.iv_duration_until);
        tvDurationForever = rootView.findViewById(R.id.tv_duration_forever);
        tvDurationUntil = rootView.findViewById(R.id.tv_duration_until);
        containerDurationUntil = rootView.findViewById(R.id.container_duration_until);
        tvDurationRemaining = rootView.findViewById(R.id.tvDurationRemaining);
        buttonEmptyAddTime = rootView.findViewById(R.id.button_empty_add_time);
        if (buttonEmptyAddTime != null) {
            buttonEmptyAddTime.setOnClickListener(v -> showTimePicker());
        }

        toggleDaysEveryday = rootView.findViewById(R.id.toggle_days_everyday);
        toggleDaysCustom = rootView.findViewById(R.id.toggle_days_custom);
        ivDaysEveryday = rootView.findViewById(R.id.iv_days_everyday);
        ivDaysCustom = rootView.findViewById(R.id.iv_days_custom);
        tvDaysEveryday = rootView.findViewById(R.id.tv_days_everyday);
        tvDaysCustom = rootView.findViewById(R.id.tv_days_custom);
        layoutDaysContainer = rootView.findViewById(R.id.layout_days_container);
    }

    private void setupInitialState() {
        whenToFinish = (GregorianCalendar) GregorianCalendar.getInstance();
        whenToFinish.add(Calendar.DAY_OF_YEAR, 7);
        if (mForever != null) {
            mForever.setChecked(true);
        }
        updateDurationUI(true);

        if (toggleDurationForever != null) {
            toggleDurationForever.setOnClickListener(v -> {
                if (mForever != null)
                    mForever.setChecked(true);
                updateDurationUI(true);
                isDirty = true;
            });
        }
        if (toggleDurationUntil != null) {
            toggleDurationUntil.setOnClickListener(v -> {
                if (mForever != null)
                    mForever.setChecked(false);
                updateDurationUI(false);
                isDirty = true;
            });
        }

        if (toggleDaysEveryday != null) {
            toggleDaysEveryday.setOnClickListener(v -> {
                updateDaysModeUI(true, true);
                isDirty = true;
            });
        }
        if (toggleDaysCustom != null) {
            toggleDaysCustom.setOnClickListener(v -> {
                boolean wasEveryday = !days.contains(0);
                updateDaysModeUI(false, true);
                if (wasEveryday) {
                    for (int i = 0; i < 7; i++) {
                        days.set(i, 0);
                        colorizeButton(i);
                    }
                }
                isDirty = true;
            });
        }

        mNome.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isDirty = true;
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        renderTimeChips();
        setupDaysUI();
        updateDaysModeUI(true);
        isDirty = false;
    }

    private void setupListeners() {
        View.OnClickListener listener = view -> {
            int viewId = view.getId();
            if (viewId == R.id.button_save)
                saveFarmaco();
            else if (viewId == R.id.button_cancella)
                removeOrario();
            else if (viewId == R.id.button_orario)
                showTimePicker();
            else if (viewId == R.id.row_date_picker)
                showDatePickerDialog();
            else if (viewId == R.id.mFoto || viewId == R.id.editPhotoText || viewId == R.id.photo_container)
                pickPhoto();
            else if (viewId == R.id.mLunedi)
                selectDay(1);
            else if (viewId == R.id.mMartedi)
                selectDay(2);
            else if (viewId == R.id.mMercoledi)
                selectDay(3);
            else if (viewId == R.id.mGiovedi)
                selectDay(4);
            else if (viewId == R.id.mVenerdi)
                selectDay(5);
            else if (viewId == R.id.mSabato)
                selectDay(6);
            else if (viewId == R.id.mDomenica)
                selectDay(0);
        };

        int[] buttonIds = {
                R.id.button_save, R.id.button_cancella, R.id.button_orario,
                R.id.row_date_picker, R.id.mFoto, R.id.editPhotoText,
                R.id.photo_container,
                R.id.mLunedi, R.id.mMartedi, R.id.mMercoledi, R.id.mGiovedi,
                R.id.mVenerdi, R.id.mSabato, R.id.mDomenica
        };
        for (int btnId : buttonIds) {
            View v = rootView.findViewById(btnId);
            if (v != null)
                v.setOnClickListener(listener);
        }
    }

    private void populateUIForEditing() {

        mNome.setText(this.farmaco.getNome());

        // FIX 6: toolbar.setTitle() from fragment →
        // update Activity's action bar title
        requireActivity().setTitle(getString(R.string.modifica) + " " + this.farmaco.getNome());

        String imgPath = this.farmaco.getImagePath();
        if (imgPath != null && !imgPath.isEmpty() && !"null".equalsIgnoreCase(imgPath)) {
            File imgFile = new File(imgPath);
            if (imgFile.exists()) {
                mFoto.setImageBitmap(BitmapFactory.decodeFile(imgPath));
            }
        }

        for (int i = 0; i < this.farmaco.getHourSize(); i++) {
            addOrario(this.farmaco.getHour(i),
                    this.farmaco.getMinute(i));
        }

        for (int i = 0; i < 7; i++) {
            days.set(i, this.farmaco.getDay(i));
            colorizeButton(i);
        }
        boolean isEveryday = !days.contains(0);
        updateDaysModeUI(isEveryday);

        boolean forever = this.farmaco.isForever();
        if (mForever != null) {
            mForever.setChecked(forever);
        }
        if (!forever) {
            whenToFinish.setTimeInMillis(this.farmaco.getWhenToFinish());
        }
        updateDurationUI(forever);

        isDirty = false;
    }

    private void showTimePicker() {
        Calendar now = Calendar.getInstance();
        WheelTimePickerBottomSheet picker = WheelTimePickerBottomSheet.newInstance(
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE)
        );
        picker.setOnTimeSetListener((hourOfDay, minute) -> {
            String hour = hourOfDay < 10 ? "0" + hourOfDay : String.valueOf(hourOfDay);
            String mins = minute < 10 ? "0" + minute : String.valueOf(minute);
            addOrario(hour, mins);
        });
        picker.show(getParentFragmentManager(), "wheel_time_picker");
    }

    private String getImgFilename() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
                .format(new Date()) + ".jpg";
    }

    private void pickPhoto() {
        try {
            // If the alarm already has an image, reuse that path so PhotoPickerFragment
            // can pre-load and display the existing photo. Only generate a fresh file
            // when there is no image yet.
            String existingPath = farmaco != null ? farmaco.getImagePath() : null;
            boolean hasExisting = existingPath != null
                    && !existingPath.isEmpty()
                    && !"null".equalsIgnoreCase(existingPath)
                    && new File(existingPath).exists();

            File dest;
            if (hasExisting) {
                dest = new File(existingPath);
            } else {
                dest = swimminsparrow.ricordamelo.core.utils.StorageUtils.getImage(
                        requireContext(), getImgFilename());
            }

            Intent photoIntent = new Intent(requireContext(),
                    swimminsparrow.ricordamelo.ui.images.PhotoPickerActivity.class);
            photoIntent.putExtra("destinationFile", dest.getAbsolutePath());
            photoIntent.putExtra("placeholderRes", R.drawable.default_pill);
            photoIntent.putExtra("title", getString(R.string.medication_photo_title));
            photoLauncher.launch(photoIntent);
        } catch (Exception e) {
            SpockLogger.errorLogWithToast(requireContext(), NewAlarmFragment.class, null, e);
        }
    }

    private void saveFarmaco() {
        String nome = mNome.getText().toString().trim();

        if (nome.isEmpty()) {
            DialogBuilder.errorDialog(requireContext(), getString(R.string.attenzione), getString(R.string.message14));
        } else if (mEditOrari.getText().toString().isEmpty()) {
            DialogBuilder.errorDialog(requireContext(), getString(R.string.attenzione), getString(R.string.message16));
        } else if (!days.contains(1)) {
            DialogBuilder.errorDialog(requireContext(), getString(R.string.attenzione),
                    getString(R.string.no_day_selected));
        } else {
            final Context appContext = requireContext().getApplicationContext();
            final int forever = mForever.isChecked() ? 1 : 0;
            final long finishMillis = whenToFinish.getTimeInMillis();
            final String imgPath = farmaco != null ? farmaco.getImagePath() : null;
            final ArrayList<String> oreCopy = new ArrayList<>(ore);
            final ArrayList<String> minutiCopy = new ArrayList<>(minuti);
            final ArrayList<Integer> daysCopy = new ArrayList<>(days);

            AppExecutors.getInstance().diskIO().execute(() -> {
                if (FarmacoDao.getInstance(appContext).existsByName(nome) && !isEditMode) {
                    AppExecutors.getInstance().mainThread().execute(() -> {
                        if (!isAdded() || getContext() == null) return;
                        DialogBuilder.errorDialog(requireContext(), nome + " " + getString(R.string.dialog_t_3),
                                nome + " " + getString(R.string.dialog_s_7));
                    });
                    return;
                }

                if (isEditMode) {
                    Farmaco f = new Farmaco(farmaco.getId(), nome, oreCopy, minutiCopy, imgPath, daysCopy,
                            finishMillis, forever);
                    f.ordinaOrari();
                    FarmacoDao.getInstance(appContext).update(farmaco.getId(), f);
                } else {
                    Farmaco f = new Farmaco(nome, oreCopy, minutiCopy, imgPath, daysCopy,
                            finishMillis, forever);
                    f.ordinaOrari();
                    FarmacoDao.getInstance(appContext).insert(f);
                }

                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (!isAdded() || getContext() == null) return;
                    isDirty = false;
                    if (backPressedCallback != null) {
                        backPressedCallback.setEnabled(false);
                    }

                    FragmentActivity activity = getActivity();
                    navigateBack();
                    CommonSnackbar.show(activity, R.string.reminder_saved);
                });
            });
        }
    }

    private void navigateBack() {

        androidx.navigation.Navigation
                .findNavController(requireView())
                .popBackStack();
    }

    private void selectDay(int daySelected) {
        days.set(daySelected, days.get(daySelected) == 0 ? 1 : 0);
        colorizeButton(daySelected);
        isDirty = true;
    }

    private void setupDaysUI() {
        java.text.DateFormatSymbols dfs = java.text.DateFormatSymbols.getInstance(Locale.getDefault());
        String[] weekdays = dfs.getWeekdays();
        int[] calDays = {
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        };

        for (int i = 0; i < 7; i++) {
            swimminsparrow.ricordamelo.ui.common.CapsuleDayButton btn = rootView.findViewById(DAY_BUTTON_IDS[i]);
            if (btn != null) {
                String rawName = weekdays[calDays[i]];
                if (rawName != null && !rawName.isEmpty()) {
                    String capitalized = rawName.substring(0, 1).toUpperCase(Locale.getDefault())
                            + rawName.substring(1);
                    btn.setText(capitalized);
                }
                btn.setChecked(days.get(i) == 1);
            }
        }
    }

    private void colorizeButton(int dayIndex) {
        if (dayIndex < 0 || dayIndex >= DAY_BUTTON_IDS.length || rootView == null)
            return;
        swimminsparrow.ricordamelo.ui.common.CapsuleDayButton btn = rootView.findViewById(DAY_BUTTON_IDS[dayIndex]);
        if (btn == null)
            return;
        btn.setChecked(days.get(dayIndex) == 1);
    }

    private void updateDaysModeUI(boolean everyday) {
        updateDaysModeUI(everyday, false);
    }

    private void updateDaysModeUI(boolean everyday, boolean animate) {
        if (!isAdded() || getContext() == null) return;

        if (animate && layoutDaysContainer != null && layoutDaysContainer.getParent() instanceof ViewGroup) {
            ChangeBounds transition = new ChangeBounds();
            transition.setDuration(220);
            transition.setInterpolator(new FastOutSlowInInterpolator());
            TransitionManager.beginDelayedTransition((ViewGroup) layoutDaysContainer.getParent(), transition);
        }

        int colorDefault = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.defaultColor);
        int colorPrimaryText = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary_text);

        if (toggleDaysEveryday != null) {
            toggleDaysEveryday.setBackgroundResource(
                    everyday ? R.drawable.bg_segmented_item_active : R.drawable.bg_segmented_item_inactive);
        }
        if (ivDaysEveryday != null) {
            ivDaysEveryday.setImageTintList(android.content.res.ColorStateList.valueOf(everyday ? Color.WHITE : colorDefault));
        }
        if (tvDaysEveryday != null) {
            tvDaysEveryday.setTextColor(everyday ? Color.WHITE : colorPrimaryText);
        }

        if (toggleDaysCustom != null) {
            toggleDaysCustom.setBackgroundResource(
                    !everyday ? R.drawable.bg_segmented_item_active : R.drawable.bg_segmented_item_inactive);
        }
        if (ivDaysCustom != null) {
            ivDaysCustom.setImageTintList(android.content.res.ColorStateList.valueOf(!everyday ? Color.WHITE : colorDefault));
        }
        if (tvDaysCustom != null) {
            tvDaysCustom.setTextColor(!everyday ? Color.WHITE : colorPrimaryText);
        }


        if (layoutDaysContainer != null) {
            layoutDaysContainer.setVisibility(everyday ? View.GONE : View.VISIBLE);
        }

        if (everyday) {
            for (int i = 0; i < 7; i++) {
                days.set(i, 1);
            }
        }
    }

    private void updateDurationUI(boolean forever) {
        int colorDefault = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.defaultColor);
        int colorPrimaryText = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary_text);

        if (toggleDurationForever != null) {
            toggleDurationForever.setBackgroundResource(
                    forever ? R.drawable.bg_segmented_item_active : R.drawable.bg_segmented_item_inactive);
        }
        if (ivDurationForever != null) {
            ivDurationForever
                    .setImageTintList(android.content.res.ColorStateList.valueOf(forever ? Color.WHITE : colorDefault));
        }
        if (tvDurationForever != null) {
            tvDurationForever.setTextColor(forever ? Color.WHITE : colorPrimaryText);
        }

        if (toggleDurationUntil != null) {
            toggleDurationUntil.setBackgroundResource(
                    !forever ? R.drawable.bg_segmented_item_active : R.drawable.bg_segmented_item_inactive);
        }
        if (ivDurationUntil != null) {
            ivDurationUntil.setImageTintList(
                    android.content.res.ColorStateList.valueOf(!forever ? Color.WHITE : colorDefault));
        }
        if (tvDurationUntil != null) {
            tvDurationUntil.setTextColor(!forever ? Color.WHITE : colorPrimaryText);
        }


        if (containerDurationUntil != null) {
            containerDurationUntil.setVisibility(forever ? View.GONE : View.VISIBLE);
        }
        if (!forever) {
            updateDateDisplay();
        }
    }

    private void updateDateDisplay() {
        if (dataFine != null && whenToFinish != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE d MMMM yyyy", Locale.getDefault());
            String formatted = sdf.format(whenToFinish.getTime());
            if (formatted.length() > 0) {
                formatted = formatted.substring(0, 1).toUpperCase(Locale.getDefault()) + formatted.substring(1);
            }
            dataFine.setText(formatted);

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            Calendar target = (Calendar) whenToFinish.clone();
            target.set(Calendar.HOUR_OF_DAY, 0);
            target.set(Calendar.MINUTE, 0);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);

            long diffMillis = target.getTimeInMillis() - today.getTimeInMillis();
            long diffDays = Math.round((double) diffMillis / (24 * 60 * 60 * 1000));

            if (tvDurationRemaining != null) {
                if (diffDays <= 0) {
                    tvDurationRemaining.setText(R.string.treatment_duration_today);
                } else if (diffDays == 1) {
                    tvDurationRemaining.setText(R.string.treatment_duration_tomorrow);
                } else {
                    tvDurationRemaining.setText(getString(R.string.treatment_duration_days, diffDays));
                }
            }
        }
    }

    private void showDatePickerDialog() {
        Calendar minDate = Calendar.getInstance();
        minDate.add(Calendar.DAY_OF_YEAR, 1);
        Calendar maxDate = Calendar.getInstance();
        maxDate.add(Calendar.YEAR, 1);

        DatePickerDialog picker = new DatePickerDialog(
                requireContext(),
                R.style.GreenTimePickerTheme,
                (v, year, month, day) -> {
                    GregorianCalendar now = (GregorianCalendar) GregorianCalendar.getInstance();
                    whenToFinish.set(year, month, day,
                            now.get(GregorianCalendar.HOUR_OF_DAY),
                            now.get(GregorianCalendar.MINUTE));
                    updateDateDisplay();
                    isDirty = true;
                },
                whenToFinish.get(Calendar.YEAR),
                whenToFinish.get(Calendar.MONTH),
                whenToFinish.get(Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMinDate(minDate.getTimeInMillis());
        picker.getDatePicker().setMaxDate(maxDate.getTimeInMillis());
        picker.show();
    }

    private void renderTimeChips() {
        if (chipGroupOrari == null)
            return;
        chipGroupOrari.removeAllViews();
        if (ore.isEmpty()) {
            if (buttonEmptyAddTime != null)
                buttonEmptyAddTime.setVisibility(View.VISIBLE);
            chipGroupOrari.setVisibility(View.GONE);
        } else {
            if (buttonEmptyAddTime != null)
                buttonEmptyAddTime.setVisibility(View.GONE);
            chipGroupOrari.setVisibility(View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            for (int i = 0; i < ore.size(); i++) {
                final int index = i;
                Chip chip = (Chip) inflater.inflate(R.layout.item_chip_alarm_time, chipGroupOrari, false);
                chip.setText(ore.get(i) + ":" + minuti.get(i));
                chip.setOnCloseIconClickListener(v -> removeOrarioAt(index));
                chip.setOnClickListener(v -> showTimePickerToEdit(index));
                chipGroupOrari.addView(chip);
            }
            Chip addChip = (Chip) inflater.inflate(R.layout.item_chip_add_time, chipGroupOrari, false);
            addChip.setOnClickListener(v -> showTimePicker());
            chipGroupOrari.addView(addChip);
        }
    }

    private void syncEditOrariText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ore.size(); i++) {
            if (i > 0)
                sb.append(";");
            sb.append(ore.get(i)).append(":").append(minuti.get(i));
        }
        if (mEditOrari != null) {
            mEditOrari.setText(sb.toString());
        }
    }

    private void addOrario(String hour, String minute) {
        ore.add(hour);
        minuti.add(minute);
        syncEditOrariText();
        renderTimeChips();
        isDirty = true;
    }

    private void removeOrarioAt(int index) {
        if (index >= 0 && index < ore.size()) {
            ore.remove(index);
            minuti.remove(index);
            syncEditOrariText();
            renderTimeChips();
            isDirty = true;
        }
    }

    private void removeOrario() {
        if (!ore.isEmpty()) {
            removeOrarioAt(ore.size() - 1);
        }
    }

    private void editOrarioAt(int index, String hour, String minute) {
        if (index >= 0 && index < ore.size()) {
            ore.set(index, hour);
            minuti.set(index, minute);
            syncEditOrariText();
            renderTimeChips();
            isDirty = true;
        }
    }

    private void showTimePickerToEdit(int index) {
        if (index < 0 || index >= ore.size())
            return;
        int initialHour = 8;
        int initialMin = 0;
        try {
            initialHour = Integer.parseInt(ore.get(index));
            initialMin = Integer.parseInt(minuti.get(index));
        } catch (NumberFormatException ignored) {
        }

        WheelTimePickerBottomSheet picker = WheelTimePickerBottomSheet.newInstance(initialHour, initialMin);
        picker.setOnTimeSetListener((hourOfDay, minute) -> {
            String hour = hourOfDay < 10 ? "0" + hourOfDay : String.valueOf(hourOfDay);
            String mins = minute < 10 ? "0" + minute : String.valueOf(minute);
            editOrarioAt(index, hour, mins);
        });
        picker.show(getParentFragmentManager(), "wheel_time_picker_edit");
    }

    // FIX 14: how to launch this fragment with arguments (replaces new Intent +
    // extras)
    public static NewAlarmFragment newInstance(boolean editMode, int position) {
        NewAlarmFragment fragment = new NewAlarmFragment();
        Bundle args = new Bundle();
        args.putBoolean("CambiaTextview", editMode);
        args.putInt("position", position);
        fragment.setArguments(args);
        return fragment;
    }

}