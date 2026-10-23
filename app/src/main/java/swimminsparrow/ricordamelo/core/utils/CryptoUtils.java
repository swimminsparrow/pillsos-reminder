package swimminsparrow.ricordamelo.core.utils;

import android.util.Base64;
import android.util.Log;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {

    private static final String TAG = "CryptoUtils";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // in bits

    /**
     * Generates a secure, random 256-bit AES key.
     * @return Base64 URL-safe encoded key string.
     */
    public static String generateRandomKey() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] key = new byte[16]; // 128 bits AES key for compact sharing
        secureRandom.nextBytes(key);
        return Base64.encodeToString(key, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /**
     * Decodes Base64 string safely, supporting standard Base64, URL-safe Base64, unpadded, and URL-encoded input.
     */
    public static byte[] decodeBase64(String input) {
        if (input == null) return null;
        String clean = input.trim();
        if (clean.isEmpty()) return null;

        try {
            if (clean.contains("%")) {
                try {
                    clean = java.net.URLDecoder.decode(clean, StandardCharsets.UTF_8.name());
                } catch (Exception ignored) {}
            }

            clean = clean.replace(' ', '+').replace('-', '+').replace('_', '/');

            int mod = clean.length() % 4;
            if (mod > 0) {
                StringBuilder sb = new StringBuilder(clean);
                for (int i = 0; i < (4 - mod); i++) {
                    sb.append('=');
                }
                clean = sb.toString();
            }

            return Base64.decode(clean, Base64.DEFAULT);
        } catch (Exception e) {
            Log.w(TAG, "Failed Base64 decode for input length " + clean.length() + ": " + e.getMessage());
            return null;
        }
    }

    public static byte[] ensureAesKeyBytes(String base64Key) {
        if (base64Key == null) return null;
        byte[] keyBytes = decodeBase64(base64Key);
        if (keyBytes != null && (keyBytes.length == 16 || keyBytes.length == 32)) {
            return keyBytes;
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base64Key.getBytes(StandardCharsets.UTF_8));
            byte[] key16 = new byte[16];
            System.arraycopy(hash, 0, key16, 0, 16);
            return key16;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Encrypts a plaintext string using AES-GCM.
     * @param plaintext The text to encrypt.
     * @param base64Key The Base64 encoded AES key.
     * @return Base64 encoded string containing the IV concatenated with the ciphertext.
     */
    public static String encrypt(String plaintext, String base64Key) {
        if (base64Key == null || plaintext == null) return null;
        try {
            byte[] keyBytes = ensureAesKeyBytes(base64Key);
            if (keyBytes == null) return null;
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] encryptedData = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, encryptedData, 0, iv.length);
            System.arraycopy(ciphertext, 0, encryptedData, iv.length, ciphertext.length);

            return Base64.encodeToString(encryptedData, Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            return null;
        }
    }

    /**
     * Decrypts an AES-GCM encrypted string.
     * @param encryptedBase64 The Base64 encoded string containing IV + ciphertext.
     * @param base64Key The Base64 encoded AES key.
     * @return The decrypted plaintext string, or null if decryption fails (e.g. wrong key).
     */
    public static String decrypt(String encryptedBase64, String base64Key) {
        if (base64Key == null || encryptedBase64 == null) return null;
        try {
            byte[] encryptedData = decodeBase64(encryptedBase64);
            byte[] keyBytes = ensureAesKeyBytes(base64Key);
            if (encryptedData == null || keyBytes == null || encryptedData.length < GCM_IV_LENGTH) return null;
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedData, 0, iv, 0, iv.length);

            int ciphertextLength = encryptedData.length - GCM_IV_LENGTH;
            byte[] ciphertext = new byte[ciphertextLength];
            System.arraycopy(encryptedData, GCM_IV_LENGTH, ciphertext, 0, ciphertextLength);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            Log.d(TAG, "Decryption failed or invalid key: " + e.getMessage());
            return null;
        }
    }

    /**
     * Derives a Base64 key from SHA-256 hash of a string (e.g. PIN + salt).
     */
    public static String sha256Key(String input) {
        if (input == null) return null;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            byte[] key16 = new byte[16];
            System.arraycopy(hash, 0, key16, 0, 16);
            return Base64.encodeToString(key16, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "SHA-256 derivation failed", e);
            return null;
        }
    }
}
