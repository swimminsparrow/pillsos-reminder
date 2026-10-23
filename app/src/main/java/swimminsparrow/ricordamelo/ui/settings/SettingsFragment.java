package swimminsparrow.ricordamelo.ui.settings;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.IOException;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;
import swimminsparrow.ricordamelo.ui.alarms.NewAlarmFragment;
import swimminsparrow.ricordamelo.ui.images.PhotoPickerFragment;

public class SettingsFragment extends Fragment {
    private static final String TAG = "SettingsFragment";
    private ImageView profileAvatar;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // Load PreferenceFragment inside container
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsPreferenceFragment())
                .commit();


        setupAvatarChangeListener(view);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAvatar();
    }

    private void loadAvatar() {
        if (profileAvatar != null && getContext() != null) {
            profileAvatar.setImageDrawable(swimminsparrow.ricordamelo.core.data.SettingsDB.getInstance(requireContext()).getAvatarImage());
        }
    }

    private void setupAvatarChangeListener(View view) {
        profileAvatar = view.findViewById(R.id.mFoto);
        loadAvatar();

        profileAvatar.setOnClickListener(v -> {
            launchPhotoUtility();
        });
        view.findViewById(R.id.editPhotoText).setOnClickListener(v -> {
            launchPhotoUtility();
        });
        View photoContainer = view.findViewById(R.id.photo_container);
        if (photoContainer != null) {
            photoContainer.setOnClickListener(v -> launchPhotoUtility());
        }
    }

    private void launchPhotoUtility() {
        Intent photoUtilityIntent = new Intent(getActivity(), swimminsparrow.ricordamelo.ui.images.PhotoPickerActivity.class);
        File dest = swimminsparrow.ricordamelo.core.utils.StorageUtils.getAvatarImage(requireContext());
        photoUtilityIntent.putExtra("destinationFile", dest.getAbsolutePath());
        photoLauncher.launch(photoUtilityIntent);
    }

    private final ActivityResultLauncher<Intent> photoLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == PhotoPickerFragment.IMAGELOADED_RESULTCODE) {
                    loadAvatar();
                    // Also notify connected SOS contacts of the new avatar update
                    String name = swimminsparrow.ricordamelo.core.data.SettingsDB.getInstance(requireContext()).getPersonalName();
                    swimminsparrow.ricordamelo.core.ntfy.NtfyManager
                            .sendNameUpdateToAllContacts(requireContext(), name);
                }
            });
}
