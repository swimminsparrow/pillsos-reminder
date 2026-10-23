package swimminsparrow.ricordamelo.ui.onboarding;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;
import swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder;
import swimminsparrow.ricordamelo.ui.images.PhotoPickerFragment;

public class OnboardingStepOneFragment extends Fragment {
    private final String TAG = OnboardingStepOneFragment.class.getSimpleName();

    private View rootView;
    private ImageView mFoto;
    private EditText editNome;
    private TextInputLayout inputLayoutNome;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_onboarding_1, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mFoto = rootView.findViewById(R.id.mFoto);
        editNome = rootView.findViewById(R.id.editNome);
        inputLayoutNome = rootView.findViewById(R.id.inputLayoutNome);

        this.init();

        setupPhotoPickerListener();

        View.OnClickListener photoClickListener = v -> pickPhoto();
        if (mFoto != null) mFoto.setOnClickListener(photoClickListener);

        View changePhoto = rootView.findViewById(R.id.changePhoto);
        if (changePhoto != null) changePhoto.setOnClickListener(photoClickListener);

        View tvChangePhotoHint = rootView.findViewById(R.id.tvChangePhotoHint);
        if (tvChangePhotoHint != null) tvChangePhotoHint.setOnClickListener(photoClickListener);

        View buttonSave = rootView.findViewById(R.id.button_save_1);
        if (buttonSave != null) {
            buttonSave.setOnClickListener(v -> save());
        }

        if (editNome != null) {
            editNome.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (inputLayoutNome != null && inputLayoutNome.getError() != null) {
                        inputLayoutNome.setError(null);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void init() {
        if (editNome != null) {
            String savedName = SettingsDB.getInstance(requireContext()).getPersonalName();
            if (savedName != null && !savedName.isEmpty()) {
                editNome.setText(savedName);
            }
        }
        if (mFoto != null) {
            mFoto.setImageDrawable(SettingsDB.getInstance(requireContext()).getAvatarImage());
        }
    }

    private void save() {
        String nome = editNome != null ? editNome.getText().toString().trim() : "";
        if (nome.isEmpty()) {
            if (inputLayoutNome != null) {
                inputLayoutNome.setError(getString(R.string.empty_name_error));
                editNome.requestFocus();
            } else {
                DialogBuilder.errorDialog(requireContext(), R.string.error, R.string.empty_name_error);
            }
            return;
        }

        SettingsDB.getInstance(requireContext()).setPersonalName(nome);
        NavHostFragment.findNavController(this).navigate(R.id.nav_onboarding_step_2);
    }

    private void pickPhoto() {
        try {
            Bundle args = new Bundle();
            File dest = swimminsparrow.ricordamelo.core.utils.StorageUtils.getAvatarImage(requireContext());
            args.putString("destinationFile", dest.getAbsolutePath());

            NavHostFragment.findNavController(this).navigate(R.id.nav_photo_picker, args);

        } catch (Exception e) {
            SpockLogger.errorLogWithToast(requireContext(), OnboardingStepOneFragment.class, "Errore immagine", e);
        }
    }

    private void setupPhotoPickerListener() {
        getParentFragmentManager().setFragmentResultListener("photo_picker_result",
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    int resultCode = result.getInt("resultCode");
                    if (resultCode == PhotoPickerFragment.IMAGELOADED_RESULTCODE) {
                        try {
                            String path = result.getString("imagePath");
                            if (path == null) {
                                throw new IOException("Path is null");
                            }
                            File file = new File(path);

                            Bitmap datiFoto = MediaStore.Images.Media.getBitmap(
                                    requireActivity().getContentResolver(),
                                    Uri.fromFile(file)
                            );
                            if (mFoto != null) {
                                mFoto.setImageBitmap(datiFoto);
                            }
                            SpockLogger.info(TAG, "Image loaded from " + file.getPath());
                        } catch (IOException e) {
                            SpockLogger.errorLogWithToast(requireContext(), OnboardingStepOneFragment.class, "Errore immagine", e);
                        }
                    }
                });
    }
}
