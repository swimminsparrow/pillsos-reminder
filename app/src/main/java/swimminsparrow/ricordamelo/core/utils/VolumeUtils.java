package swimminsparrow.ricordamelo.core.utils;

import android.content.Context;
import android.media.AudioManager;

import swimminsparrow.ricordamelo.core.data.SettingsDB;

public class VolumeUtils {

    /**
     * Overrides alarm and ring volume streams to maximum if silent mode is disabled.
     * Saves the pre-override volume levels so they can be restored later.
     */
    public static synchronized void overrideVolumeToMax(Context context) {
        overrideVolumeToMax(context, false);
    }

    /**
     * Overrides alarm and ring volume streams to maximum.
     * If forceEvenIfSilent is true, forces volume to max even if silent mode is enabled (emergency use).
     */
    public static synchronized void overrideVolumeToMax(Context context, boolean forceEvenIfSilent) {
        if (context == null) return;
        
        SettingsDB settingsDB = SettingsDB.getInstance(context);
        if (!forceEvenIfSilent && settingsDB.getSilentMode()) {
            return;
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        try {
            // Save current volume levels only if not already saved/overridden
            if (!settingsDB.isVolumeOverridden()) {
                int currentAlarmVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
                int currentRingVol = audioManager.getStreamVolume(AudioManager.STREAM_RING);
                settingsDB.savePreOverrideVolumes(currentAlarmVol, currentRingVol);
                SpockLogger.info("VolumeUtils", "Saved pre-override volumes - Alarm: " + currentAlarmVol + ", Ring: " + currentRingVol);
            }

            int maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, 0);

            int maxRingVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
            audioManager.setStreamVolume(AudioManager.STREAM_RING, maxRingVol, 0);
            
            SpockLogger.info("VolumeUtils", "Volume overridden to max - Alarm: " + maxAlarmVol + ", Ring: " + maxRingVol);
        } catch (SecurityException e) {
            SpockLogger.error(VolumeUtils.class, "Cannot change volume due to Do Not Disturb policy: " + e.getMessage());
        }
    }

    /**
     * Restores the volume levels for alarm and ring streams to their pre-override state.
     */
    public static synchronized void restoreVolume(Context context) {
        if (context == null) return;

        SettingsDB settingsDB = SettingsDB.getInstance(context);
        if (!settingsDB.isVolumeOverridden()) {
            return;
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            try {
                int savedAlarmVol = settingsDB.getSavedAlarmVolume();
                int savedRingVol = settingsDB.getSavedRingVolume();

                if (savedAlarmVol >= 0) {
                    int maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                    int targetAlarmVol = Math.min(savedAlarmVol, maxAlarmVol);
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetAlarmVol, 0);
                }

                if (savedRingVol >= 0) {
                    int maxRingVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
                    int targetRingVol = Math.min(savedRingVol, maxRingVol);
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, targetRingVol, 0);
                }
                
                SpockLogger.info("VolumeUtils", "Restored volume levels - Alarm: " + savedAlarmVol + ", Ring: " + savedRingVol);
            } catch (SecurityException e) {
                SpockLogger.error(VolumeUtils.class, "Cannot restore volume due to Do Not Disturb policy: " + e.getMessage());
            } finally {
                settingsDB.clearVolumeOverrideState();
            }
        } else {
            settingsDB.clearVolumeOverrideState();
        }
    }
}
