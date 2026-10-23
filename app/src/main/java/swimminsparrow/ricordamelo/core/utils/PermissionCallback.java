package swimminsparrow.ricordamelo.core.utils;

public interface PermissionCallback {
    void onPermissionGranted();

    default void onPermissionDenied() {
        // Optional - do nothing by default
    }
}