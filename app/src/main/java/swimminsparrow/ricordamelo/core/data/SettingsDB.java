package swimminsparrow.ricordamelo.core.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.File;
import java.security.SecureRandom;

import swimminsparrow.ricordamelo.core.utils.SpockLogger;
import swimminsparrow.ricordamelo.core.utils.StorageUtils;
import swimminsparrow.ricordamelo.R;

/**
 * @version 82 08/03/2019 Added Gmail Support.
 */

public class SettingsDB {
    private static SettingsDB instance;
    private SharedPreferences databasePreferences;
    private SharedPreferences personalInfoPrefs;
    private SharedPreferences securePrefs;
    private Context context;

    // Private constructor prevents direct instantiation
    private SettingsDB(Context context) {
        this.context = context.getApplicationContext();
        this.databasePreferences = context.getSharedPreferences("databasePreferences", Context.MODE_PRIVATE);
        this.personalInfoPrefs = context.getSharedPreferences("personalInfo", Context.MODE_PRIVATE);
    }

    // Thread-safe getInstance using double-checked locking
    public static SettingsDB getInstance(Context context) {
        if (instance == null) {
            synchronized (SettingsDB.class) {
                if (instance == null) {
                    instance = new SettingsDB(context);
                }
            }
        } else if (context != null && instance.context != context.getApplicationContext()) {
            synchronized (SettingsDB.class) {
                instance.context = context.getApplicationContext();
                instance.databasePreferences = instance.context.getSharedPreferences("databasePreferences", Context.MODE_PRIVATE);
                instance.personalInfoPrefs = instance.context.getSharedPreferences("personalInfo", Context.MODE_PRIVATE);
            }
        }
        return instance;
    }

    public static final String SOUND_MODE_MAX_VOLUME = "max_volume";
    public static final String SOUND_MODE_SYSTEM = "system";

    public void setAlarmSoundMode(String mode) {
        SharedPreferences.Editor edit = databasePreferences.edit();
        edit.putString("d_alarm_sound_mode", mode);
        edit.putBoolean("silentMode", SOUND_MODE_SYSTEM.equals(mode));
        edit.putBoolean("vibroMode", SOUND_MODE_MAX_VOLUME.equals(mode));
        edit.apply();
    }

    public String getAlarmSoundMode() {
        return databasePreferences.getString("d_alarm_sound_mode", SOUND_MODE_MAX_VOLUME);
    }

    public boolean isMaxVolumeMode() {
        return SOUND_MODE_MAX_VOLUME.equals(getAlarmSoundMode());
    }

    //added on 23 Jan 2017 for silent mode
    public void setSilentMode(boolean SilentMode) {
        SharedPreferences.Editor edit = databasePreferences.edit();
        edit.putBoolean("silentMode", SilentMode);
        edit.putString("d_alarm_sound_mode", SilentMode ? SOUND_MODE_SYSTEM : SOUND_MODE_MAX_VOLUME);
        edit.apply();
    }

    public Boolean getSilentMode() {
        if (databasePreferences.contains("d_alarm_sound_mode")) {
            return SOUND_MODE_SYSTEM.equals(getAlarmSoundMode());
        }
        return databasePreferences.getBoolean("silentMode", false);
    }

    public boolean isVolumeOverridden() {
        return databasePreferences.getBoolean("volumeOverridden", false);
    }

    public void savePreOverrideVolumes(int alarmVol, int ringVol) {
        SharedPreferences.Editor edit = databasePreferences.edit();
        edit.putBoolean("volumeOverridden", true);
        edit.putInt("preOverrideAlarmVolume", alarmVol);
        edit.putInt("preOverrideRingVolume", ringVol);
        edit.apply();
    }

    public int getSavedAlarmVolume() {
        return databasePreferences.getInt("preOverrideAlarmVolume", -1);
    }

    public int getSavedRingVolume() {
        return databasePreferences.getInt("preOverrideRingVolume", -1);
    }

    public void clearVolumeOverrideState() {
        SharedPreferences.Editor edit = databasePreferences.edit();
        edit.putBoolean("volumeOverridden", false);
        edit.remove("preOverrideAlarmVolume");
        edit.remove("preOverrideRingVolume");
        edit.apply();
    }

    public void setVibrationMode(boolean vibro) {
        SharedPreferences.Editor edit = databasePreferences.edit();
        edit.putBoolean("vibroMode", vibro);
        edit.apply();
    }

    public Boolean getVibrationMode() {
        if (databasePreferences.contains("d_alarm_sound_mode")) {
            return SOUND_MODE_MAX_VOLUME.equals(getAlarmSoundMode());
        }
        return databasePreferences.getBoolean("vibroMode", true);
    }

    public void setTTSMode(boolean enabled) {
        SharedPreferences.Editor edit = databasePreferences.edit();
        edit.putBoolean("ttsMode", enabled);
        edit.apply();
    }

    public Boolean getTTSMode() {
        return databasePreferences.getBoolean("ttsMode", true);
    }

    public void setFallDetectionMode(boolean enabled) {
        SharedPreferences.Editor edit = databasePreferences.edit();
        edit.putBoolean("fallDetectionMode", enabled);
        edit.apply();
    }

    public Boolean getFallDetectionMode() {
        return databasePreferences.getBoolean("fallDetectionMode", true);
    }

    public void setFallDetectionSensitivity(int sensitivity) {
        SharedPreferences.Editor edit = databasePreferences.edit();
        edit.putInt("d_fall_sensitivity_threshold", sensitivity);
        edit.apply();
    }

    public double getFallDetectionImpactThreshold() {
        try {
            if (databasePreferences.contains("d_fall_sensitivity_threshold")) {
                int val = databasePreferences.getInt("d_fall_sensitivity_threshold", 60);
                // Inverted mapping: higher slider value -> lower impact threshold G -> higher sensitivity
                double threshold = 8.5 - (val * 0.05);
                return Math.max(2.5, Math.min(8.5, threshold));
            } else if (databasePreferences.contains("d_fall_sensitivity")) {
                String oldVal = databasePreferences.getString("d_fall_sensitivity", "1");
                int sens = Integer.parseInt(oldVal);
                return (sens == 2) ? 4.0 : (sens == 0) ? 7.0 : 5.0;
            }
        } catch (Exception ignored) { }
        return 5.0;
    }

    public int getFallDetectionSensitivity() {
        return databasePreferences.getInt("d_fall_sensitivity_threshold", 60);
    }

    public String getPersonalName() {
        String defaultName = "Mario";
        try {
            if (context != null) {
                defaultName = context.getString(R.string.mario);
            }
        } catch (Exception ignored) { }
        return personalInfoPrefs.getString("name", defaultName);
    }

    public void setPersonalName(String name) {
        SharedPreferences.Editor edit = personalInfoPrefs.edit();
        edit.putString("name", name);
        edit.apply();
    }

    public Drawable getDefaultAvatarImage(){
        return ContextCompat.getDrawable(context, R.drawable.avatar);
    }

    public Drawable getAvatarImage() {
        try {
            File file = StorageUtils.getAvatarImage(context);

            Bitmap bitmap = (file != null && file.exists() && file.length() > 0)
                    ? BitmapFactory.decodeFile(file.getAbsolutePath())
                    : null;

            return bitmap != null
                    ? new BitmapDrawable(context.getResources(), bitmap)
                    : getDefaultAvatarImage();

        } catch (Exception e) {
            return getDefaultAvatarImage();
        }
    }

    public Boolean isSOSSendEnabled() {
        if (context != null && swimminsparrow.ricordamelo.core.sos.SOSContactsManager.hasConfiguredContacts(context)) {
            return true;
        }
        return personalInfoPrefs.getBoolean("SOSSendEnabled", false);
    }

    public void setSOSSendEnabled(Boolean value) {
        personalInfoPrefs.edit().putBoolean("SOSSendEnabled", value).apply();
    }

    public void setSound(String url) {
        SharedPreferences.Editor edit = personalInfoPrefs.edit();
        edit.putString("sound", url);
        edit.apply();
    }

    public static final String DEFAULT_SOUND = "Default";

    public Uri getDefaultSoundUri() {
        return Uri.parse(android.content.ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName() + "/" + R.raw.notifica);
    }

    /**
     * This Function returns String containing SOUND URI if valid.
     */
    public String getSound() {
        String actualSound = personalInfoPrefs.getString("sound", DEFAULT_SOUND);
        if (actualSound == null) {
            actualSound = DEFAULT_SOUND;
        }
        try {
            if (!DEFAULT_SOUND.equals(actualSound)) {
                MediaPlayer mp = MediaPlayer.create(context, Uri.parse(actualSound));
                if (mp == null) {
                    setSound(DEFAULT_SOUND);
                    actualSound = DEFAULT_SOUND;
                } else {
                    mp.release();
                }
            }
        } catch (Exception e) {
            setSound(DEFAULT_SOUND);
            actualSound = DEFAULT_SOUND;
            SpockLogger.info(this.getClass().getSimpleName(), "Sound URI Not Valid: setting default: " + e.getLocalizedMessage());
        }

        return actualSound;
    }

    public Uri getSoundUri() {
        String sound = getSound();
        if (sound != null && !DEFAULT_SOUND.equals(sound)) {
            try {
                return Uri.parse(sound);
            } catch (Exception e) {
                return getDefaultSoundUri();
            }
        } else {
            return getDefaultSoundUri();
        }
    }


    public Boolean getRated() {
        return personalInfoPrefs.getBoolean("rated", false);
    }

    public void setRated(Boolean rated) {
        SharedPreferences.Editor edit = personalInfoPrefs.edit();
        edit.putBoolean("rated", rated);
        edit.apply();
    }

    public void setPrimoAvvio(Boolean p) {
        SharedPreferences.Editor edit = databasePreferences.edit();
        edit.putBoolean("primoAvvio", p);
        edit.apply();
    }

    public Boolean getPrimoAvvio() {
        return databasePreferences.getBoolean("primoAvvio", false);
    }

    public void setWhatsNew(int id) {
        SharedPreferences.Editor edit = databasePreferences.edit();
        edit.putBoolean("whatsNew" + id, true);
        edit.apply();
    }

    public Boolean getWhatsNew(int id) {
        return databasePreferences.getBoolean("whatsNew" + id, false);
    }

    //added on 11 Set 2017
    public void setAlarmDuration(long timeMinutes) {
        if (timeMinutes < 1) timeMinutes = 10;
        if (timeMinutes > 20) timeMinutes = 20;
        //convert time to millis
        long timeMs = timeMinutes * 60 * 1000L;
        SharedPreferences.Editor edit = personalInfoPrefs.edit();
        edit.putLong("alarmDuration", timeMs);
        edit.apply();
    }

    public long getAlarmDuration() {
        long duration = personalInfoPrefs.getLong("alarmDuration", 600000L); // 10 min default
        long minMs = 1 * 60 * 1000L;
        long maxMs = 20 * 60 * 1000L;
        if (duration < minMs || duration > maxMs) {
            return 600000L; // default 10 min (600,000 ms)
        }
        return duration;
    }



    private synchronized SharedPreferences getSecurePrefs() {
        if (securePrefs != null) {
            return securePrefs;
        }
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            securePrefs = EncryptedSharedPreferences.create(
                    context,
                    "secure_sos_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            return securePrefs;
        } catch (Exception e) {
            Log.e("SettingsDB", "Security error initializing secure_sos_prefs, attempting recovery", e);
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences("secure_sos_prefs");
                } else {
                    context.getSharedPreferences("secure_sos_prefs", Context.MODE_PRIVATE).edit().clear().apply();
                }
                MasterKey masterKey = new MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                securePrefs = EncryptedSharedPreferences.create(
                        context,
                        "secure_sos_prefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
                return securePrefs;
            } catch (Exception ex) {
                Log.e("SettingsDB", "Recovery failed, falling back to private prefs", ex);
                securePrefs = context.getSharedPreferences("secure_sos_prefs_fallback", Context.MODE_PRIVATE);
                return securePrefs;
            }
        }
    }

    public String getSecurePairingKey() {
        SharedPreferences securePrefs = getSecurePrefs();
        if (securePrefs == null) return null;
        
        String key = securePrefs.getString("pairing_key", null);
        if (key == null) {
            key = swimminsparrow.ricordamelo.core.utils.CryptoUtils.generateRandomKey();
            securePrefs.edit().putString("pairing_key", key).apply();
        }
        return key;
    }



    private String generateShortId() {
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    public String getUserUUID() {
        String existing = personalInfoPrefs.getString("userUUID", null);
        if (existing != null) return existing;

        String id = generateShortId();
        personalInfoPrefs.edit().putString("userUUID", id).apply();
        return id;
    }


    public void setAppLaunchTime(int appLaunchTime) {
        SharedPreferences.Editor edit = personalInfoPrefs.edit();
        edit.putInt("app_launch_time", appLaunchTime);
        edit.apply();
    }

    public int getAppLaunchTime() {
        return personalInfoPrefs.getInt("app_launch_time", 0);
    }

    public int getPreviousAlarmsCount() {
        return personalInfoPrefs.getInt("previousAlarmsCount", 0);
    }

    public void setPreviousAlarmsCount(int previousAlarmsCount) {
        personalInfoPrefs.edit().putInt("previousAlarmsCount", previousAlarmsCount).apply();
    }

    public boolean isDeveloperModeEnabled() {
        return databasePreferences.getBoolean("developer_mode", false);
    }

    public void setDeveloperModeEnabled(boolean enabled) {
        databasePreferences.edit().putBoolean("developer_mode", enabled).apply();
    }

    public boolean isImOkExplained() {
        return databasePreferences.getBoolean("im_ok_explained", false);
    }

    public void setImOkExplained(boolean explained) {
        databasePreferences.edit().putBoolean("im_ok_explained", explained).apply();
    }
}
