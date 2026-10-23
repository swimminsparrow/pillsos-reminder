package swimminsparrow.ricordamelo.ui.images;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import swimminsparrow.ricordamelo.ui.BaseActivity;
import androidx.navigation.fragment.NavHostFragment;

import java.io.File;
import java.io.IOException;

import pl.aprilapps.easyphotopicker.EasyImage;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;
import swimminsparrow.ricordamelo.core.utils.ImageUtils;
import swimminsparrow.ricordamelo.ui.common.Toaster;
import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.R;

public class PhotoPickerFragment extends Fragment {


    public static final int IMAGELOADED_RESULTCODE = 21;
    public static final int IMAGE_NOTLOADED_RESULT = -1;


    private View rootView;

    private ImageView mPhoto;
    private File destinationFile;
    private int placeholderRes = R.drawable.avatar;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Receive the destination file path from arguments
        if (getArguments() != null && getArguments().containsKey("destinationFile")) {
            String destinationFilePath = getArguments().getString("destinationFile");
            if (destinationFilePath != null) {
                destinationFile = new File(destinationFilePath);
            }
        } else if (requireActivity().getIntent() != null && requireActivity().getIntent().hasExtra("destinationFile")) {
            String destinationFilePath = requireActivity().getIntent().getStringExtra("destinationFile");
            if (destinationFilePath != null) {
                destinationFile = new File(destinationFilePath);
            }
        }

        // Receive placeholder drawable resource if provided (e.g. default_photo for medications)
        if (getArguments() != null && getArguments().containsKey("placeholderRes")) {
            placeholderRes = getArguments().getInt("placeholderRes", R.drawable.avatar);
        } else if (requireActivity().getIntent() != null && requireActivity().getIntent().hasExtra("placeholderRes")) {
            placeholderRes = requireActivity().getIntent().getIntExtra("placeholderRes", R.drawable.avatar);
        }

    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_photo_picker, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupViews();
        setupCameraListener();
        setupGalleryListener();
        setupCropListener();
    }

    private void setupViews() {
        mPhoto = rootView.findViewById(R.id.pu_mPhoto);

        if (destinationFile != null && destinationFile.exists()) {
            Bitmap bitmap = ImageUtils.loadBitmapWithExifRotation(destinationFile, 800);
            if (bitmap != null) {
                mPhoto.setImageBitmap(bitmap);
            } else {
                mPhoto.setImageResource(placeholderRes);
            }
        } else {
            mPhoto.setImageResource(placeholderRes);
        }

        View.OnClickListener alClick = view -> {
            int id = view.getId();
            if (id == R.id.pu_b1) {
                preparaScatto();
            } else if (id == R.id.pu_b2) {
                preparaGalleria();
            } else if (id == R.id.pu_b3) {
                sendResultBack();
            }
        };

        rootView.findViewById(R.id.pu_b1).setOnClickListener(alClick);
        rootView.findViewById(R.id.pu_b2).setOnClickListener(alClick);
        rootView.findViewById(R.id.pu_b3).setOnClickListener(alClick);
    }

    private void preparaGalleria() {
        getParentFragmentManager().beginTransaction()
                .add(GalleryProxyFragment.newInstance(), "gallery_proxy")
                .commit();
    }

    private void preparaScatto() {
        ((BaseActivity) requireActivity()).checkCameraPermission(() -> {
            getParentFragmentManager().beginTransaction()
                    .add(CameraProxyFragment.newInstance(), "camera_proxy")
                    .commit();
        });
    }

    private void sendResultBack() {
        int resultCode = IMAGELOADED_RESULTCODE;
        if (destinationFile == null || !destinationFile.exists() || destinationFile.length() == 0) {
            resultCode = IMAGE_NOTLOADED_RESULT;
        } else {
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(destinationFile.getPath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                resultCode = IMAGE_NOTLOADED_RESULT;
            }
        }

        if (requireActivity() instanceof swimminsparrow.ricordamelo.ui.images.PhotoPickerActivity) {
            Intent resultIntent = new Intent();
            if (destinationFile != null) {
                resultIntent.putExtra("imagePath", destinationFile.getPath());
            }
            requireActivity().setResult(resultCode, resultIntent);
            requireActivity().finish();
        } else {
            // Send result back to the calling fragment
            Bundle result = new Bundle();
            result.putInt("resultCode", resultCode);
            if (destinationFile != null) {
                result.putString("imagePath", destinationFile.getPath());
            }

            getParentFragmentManager().setFragmentResult("photo_picker_result", result);

            // Navigate back
            NavHostFragment.findNavController(this).navigateUp();
        }
    }

    private void callCroppaImageActivity() {
        Bundle bundle = new Bundle();
        bundle.putString("imagePath", destinationFile.getPath());

        NavHostFragment.findNavController(this).navigate(R.id.nav_photo_cropper, bundle);
    }

    private void setupCropListener() {
        // Listen for result from PhotoCropFragment
        getParentFragmentManager().setFragmentResultListener("crop_request",
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    int requestCode = result.getInt("request_code");

                    if (requestCode == Constants.CROPPED_IMAGE_REQUESTCODE) {
                        destinationFile = new File(destinationFile.getPath());
                        //  Reload File Bitmap  //
                        Bitmap bitmap = ImageUtils.loadBitmapWithExifRotation(destinationFile, 800);
                        if (bitmap != null) {
                            mPhoto.setImageBitmap(bitmap);
                        }
                    }
                });
    }

    private void setupCameraListener() {
        getParentFragmentManager().setFragmentResultListener("camera_request",
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    if (result.getBoolean("success")) {
                        savePickedImage(result.getString("file_path"));
                        callCroppaImageActivity();
                    } else {
                        Toaster.errorToast(requireContext(), result.getString("error_message"));
                    }
                });
    }

    private void setupGalleryListener() {
        getParentFragmentManager().setFragmentResultListener("gallery_request",
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    if (result.getBoolean("success")) {
                        savePickedImage(result.getString("file_path"));
                        callCroppaImageActivity();
                    } else {
                        Toaster.errorToast(requireContext(), result.getString("error_message"));
                    }
                });
    }

    private void savePickedImage(String filePath) {
        try {
            File pickedFile = new File(filePath);
            Bitmap bitmap = ImageUtils.loadBitmapWithExifRotation(pickedFile, 2048);
            if (bitmap != null) {
                ImageUtils.saveImage(requireContext(), bitmap, destinationFile);
            }
        } catch (Exception e) {
            SpockLogger.errorLogWithToast(requireContext(), getClass(), "Errore salvataggio file temporaneo", e);
        }
    }

}
