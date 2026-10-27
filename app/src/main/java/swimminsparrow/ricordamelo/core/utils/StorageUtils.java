package swimminsparrow.ricordamelo.core.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;

import swimminsparrow.ricordamelo.config.Constants;

public class StorageUtils {


    public static File getApplicationFile(Context c, String subPath) {
        return new File(c.getFilesDir(), subPath);
    }

    public static void isProjectDirsExisting(Context c) {
        File photoDir = StorageUtils.getImageDir(c);
        photoDir.mkdirs();
    }

    public static File getImageDir(Context c) {
        return StorageUtils.getApplicationFile(c, Constants.PHOTOS_DIR_PATH);
    }

    public static File getImage(Context c, String imageFileName) {
        return StorageUtils.getApplicationFile(c, Constants.PHOTOS_DIR_PATH + "/" + imageFileName);
    }

    public static File getAvatarImage(Context c) {
        return StorageUtils.getApplicationFile(c, Constants.AVATAR_FILE_PATH);
    }

    public static String getAvatarBase64Thumbnail(Context c) {
        try {
            File file = getAvatarImage(c);
            if (file != null && file.exists() && file.length() > 0) {
                Bitmap original = ImageUtils.loadBitmapWithExifRotation(file, 96);
                if (original != null) {
                    Bitmap resized = Bitmap.createScaledBitmap(original, 96, 96, true);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    resized.compress(Bitmap.CompressFormat.JPEG, 60, baos);
                    byte[] bytes = baos.toByteArray();
                    return Base64.encodeToString(bytes, Base64.NO_WRAP);
                }
            }
        } catch (Exception ignored) { }
        return "";
    }
}
