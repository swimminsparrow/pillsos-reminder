package swimminsparrow.ricordamelo.core.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Base64;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Created by naples96 on 01/04/18.
 * @author swimminsparrow
 * @version 76
 */

/**
 * This Class is a collection of functions to handle, manage, save and read images.
 */
public class ImageUtils {

    private static final LruCache<String, Bitmap> AVATAR_CACHE = new LruCache<>(50);

    /**
     * Binds a Base64-encoded avatar to an ImageView with in-memory LruCache.
     * If the string is null/empty or decoding fails, sets the default drawable.
     */
    public static void bindAvatar(ImageView imageView, String avatarBase64, int defaultDrawableRes) {
        if (imageView == null) return;
        if (avatarBase64 != null && !avatarBase64.trim().isEmpty()) {
            try {
                Bitmap cached = AVATAR_CACHE.get(avatarBase64);
                if (cached == null) {
                    byte[] decodedBytes = Base64.decode(avatarBase64, Base64.DEFAULT);
                    cached = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                    if (cached != null) {
                        AVATAR_CACHE.put(avatarBase64, cached);
                    }
                }
                if (cached != null) {
                    imageView.setImageBitmap(cached);
                    imageView.setBackgroundColor(Color.TRANSPARENT);
                    imageView.clearColorFilter();
                    imageView.setPadding(0, 0, 0, 0);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        if (defaultDrawableRes != 0) {
            imageView.setImageResource(defaultDrawableRes);
        }
        imageView.setBackgroundColor(Color.TRANSPARENT);
        imageView.clearColorFilter();
        imageView.setPadding(0, 0, 0, 0);
    }

    public static void saveImage(Context c, Bitmap bitmap, File destinationFile){
        try {
            if (destinationFile != null && destinationFile.exists())
                destinationFile.delete();

            if (destinationFile != null) {
                File parent = destinationFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
            }

            if (destinationFile != null) {
                destinationFile.createNewFile();

                bitmap = resizeBitmap(bitmap);

                FileOutputStream fOut = new FileOutputStream(destinationFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fOut);
                fOut.flush();
                fOut.close();
            }

        } catch (Exception e) {
            SpockLogger.errorLogWithToast(c, ImageUtils.class, "Errore salvataggio Immagine", e);
        }

    }

    /**
     * This Function resize the bitmap and returns the bitmap resized
     * @param bitmap è l'immagine da ridimensionare
     *
     */
    private static Bitmap resizeBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int targetWidth = 600;
        if (width <= targetWidth) {
            return bitmap;
        }

        // Keep aspect ratio: scale height proportionally to width
        float scaleFactor = (float) targetWidth / width;
        int newWidth = targetWidth;
        int newHeight = Math.max(1, (int) (height * scaleFactor));

        SpockLogger.info(ImageUtils.class, "Scale: " + scaleFactor + " → " + newWidth + "x" + newHeight);

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    public static Bitmap rotateBitmap(Bitmap source, float angle) {
        if (source == null) return null;
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    /**
     * Loads a bitmap from file, downsamples if needed to avoid OutOfMemoryError,
     * and automatically rotates it according to EXIF orientation metadata so that it is displayed upright.
     */
    public static Bitmap loadBitmapWithExifRotation(File file, int maxDimension) {
        if (file == null || !file.exists() || file.length() == 0) {
            return null;
        }

        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            int width = options.outWidth;
            int height = options.outHeight;
            if (width <= 0 || height <= 0) {
                return null;
            }

            int inSampleSize = 1;
            if (maxDimension > 0) {
                while ((width / inSampleSize) > maxDimension * 2 || (height / inSampleSize) > maxDimension * 2) {
                    inSampleSize *= 2;
                }
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;

            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) {
                return null;
            }

            return rotateBitmapAccordingToExif(bitmap, file.getAbsolutePath());
        } catch (Throwable e) {
            SpockLogger.info(ImageUtils.class.getSimpleName(), "Error loading bitmap with EXIF rotation: " + e.getMessage());
            return null;
        }
    }

    /**
     * Rotates bitmap according to EXIF orientation tag in the file.
     */
    public static Bitmap rotateBitmapAccordingToExif(Bitmap bitmap, String filePath) {
        if (bitmap == null || filePath == null) {
            return bitmap;
        }

        try {
            ExifInterface exif = new ExifInterface(filePath);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.preScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.preScale(1, -1);
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix.postRotate(90);
                    matrix.preScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix.postRotate(270);
                    matrix.preScale(-1, 1);
                    break;
                default:
                    return bitmap;
            }

            Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (oriented != bitmap) {
                bitmap.recycle();
            }
            return oriented;
        } catch (Exception e) {
            SpockLogger.info(ImageUtils.class.getSimpleName(), "Could not parse EXIF orientation: " + e.getMessage());
            return bitmap;
        }
    }

}



