package swimminsparrow.ricordamelo.ui.images;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.canhub.cropper.CropImageView;
import com.google.android.material.bottomappbar.BottomAppBar;

import java.io.File;
import java.io.IOException;

import swimminsparrow.ricordamelo.core.utils.ImageUtils;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;
import swimminsparrow.ricordamelo.ui.BaseActivity;
import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.R;


public class PhotoCropFragment extends Fragment {
    private View rootView;

    private File mOutputFile = null;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_photo_crop, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String imagePath = getArguments() != null ? getArguments().getString("imagePath") : null;
        if (imagePath != null) {
            mOutputFile = new File(imagePath);
        }
        Bitmap datifoto = null;

        try {
            if (mOutputFile != null && mOutputFile.exists()) {
                datifoto = ImageUtils.loadBitmapWithExifRotation(mOutputFile, 2048);
            }
        } catch (Exception e) {
            SpockLogger.errorLogWithToast(requireContext(), getClass(), null, e);
        }

        CropImageView cropImageView = rootView.findViewById(R.id.CropImageView);

        if (datifoto != null) {
            cropImageView.setImageBitmap(datifoto);
        }
        cropImageView.setAspectRatio(5, 5);//area cropping
        cropImageView.setFixedAspectRatio(true);//qadrato
        cropImageView.setGuidelines(CropImageView.Guidelines.ON);//guide
        cropImageView.setCropShape(CropImageView.CropShape.OVAL);
        cropImageView.setScaleType(CropImageView.ScaleType.FIT_CENTER);//scala e centra l'immagine correttamente

        BottomAppBar bottomAppBar = rootView.findViewById(R.id.bottomAppBar);
        bottomAppBar.setNavigationOnClickListener(v -> cropImageView.rotateImage(90));

        rootView.findViewById(R.id.button_ok).setOnClickListener(v -> {
            Bitmap croppedImage = cropImageView.getCroppedImage();
            ImageUtils.saveImage(requireContext(), croppedImage, mOutputFile);
            sendResultBack();
        });

        rootView.findViewById(R.id.rotate_btn).setOnClickListener(v -> {
            cropImageView.rotateImage(90);
        });

    }

    private void sendResultBack(){
        // Send result back via FragmentResultListener
        Bundle result = new Bundle();
        result.putString("image_path", mOutputFile.getAbsolutePath());
        result.putInt("request_code", Constants.CROPPED_IMAGE_REQUESTCODE);

        getParentFragmentManager().setFragmentResult("crop_request", result);

        // Close the Fragment/Activity
        NavHostFragment.findNavController(this).navigateUp();
    }


}
