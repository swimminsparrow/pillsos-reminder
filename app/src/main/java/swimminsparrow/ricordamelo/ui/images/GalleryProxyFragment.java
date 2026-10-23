package swimminsparrow.ricordamelo.ui.images;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import pl.aprilapps.easyphotopicker.EasyImage;
import pl.aprilapps.easyphotopicker.MediaFile;
import pl.aprilapps.easyphotopicker.MediaSource;

public class GalleryProxyFragment extends Fragment {

    private EasyImage easyImage;

    public static GalleryProxyFragment newInstance() {
        return new GalleryProxyFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        easyImage = new EasyImage.Builder(requireContext())
                .setCopyImagesToPublicGalleryFolder(false)
                .build();

        // Launch immediately on creation
        easyImage.openGallery(this);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        easyImage.handleActivityResult(requestCode, resultCode, data, requireActivity(), new EasyImage.Callbacks() {

            @Override
            public void onMediaFilesPicked(@NonNull MediaFile[] imageFiles, @NonNull MediaSource source) {
                Bundle result = new Bundle();
                result.putBoolean("success", true);
                result.putString("file_path", imageFiles[0].getFile().getAbsolutePath());
                getParentFragmentManager().setFragmentResult("gallery_request", result);

                getParentFragmentManager().beginTransaction().remove(GalleryProxyFragment.this).commit();
            }

            @Override
            public void onImagePickerError(@NonNull Throwable error, @NonNull MediaSource source) {
                Bundle result = new Bundle();
                result.putBoolean("success", false);
                result.putString("error_message", error.toString());
                getParentFragmentManager().setFragmentResult("gallery_request", result);
                getParentFragmentManager().beginTransaction().remove(GalleryProxyFragment.this).commit();
            }

            @Override
            public void onCanceled(@NonNull MediaSource source) {
                getParentFragmentManager().beginTransaction().remove(GalleryProxyFragment.this).commit();
            }
        });
    }
}
