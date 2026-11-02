package swimminsparrow.ricordamelo.core.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.List;
import java.util.Locale;

public class TTSManager implements TextToSpeech.OnInitListener {

    private static final String TAG = "TTSManager";
    public static final long DEFAULT_REPEAT_INTERVAL_MS = 15000L; // 15 seconds

    private TextToSpeech tts;
    private boolean isLoaded = false;
    private String pendingText = null;

    private boolean isRepeating = false;
    private String repeatingText = null;
    private long repeatIntervalMs = DEFAULT_REPEAT_INTERVAL_MS;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable repeatRunnable = null;

    /**
     * Checks synchronously if at least one TTS engine is installed on the system.
     */
    public static boolean isTTSEngineInstalled(Context context) {
        if (context == null) return false;
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = new Intent("android.intent.action.TTS_SERVICE");
            List<ResolveInfo> engines = pm.queryIntentServices(intent, 0);
            return engines != null && !engines.isEmpty();
        } catch (Exception e) {
            Log.e(TAG, "Error checking TTS engines: " + e.getMessage());
            return false;
        }
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public TTSManager(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            Locale defaultLocale = Locale.getDefault();
            int result = tts.setLanguage(defaultLocale);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Language " + defaultLocale + " not supported or missing data, trying US fallback");
                result = tts.setLanguage(Locale.US);
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS language not supported: " + defaultLocale);
            } else {
                isLoaded = true;
                setupAudioAttributes();
                setupUtteranceListener();

                if (isRepeating && repeatingText != null) {
                    performSpeak(repeatingText);
                } else if (pendingText != null) {
                    performSpeak(pendingText);
                    pendingText = null;
                }
            }
        } else {
            Log.e(TAG, "TTS initialization failed with status: " + status);
        }
    }

    private void setupAudioAttributes() {
        if (tts != null) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            tts.setAudioAttributes(audioAttributes);
        }
    }

    private void setupUtteranceListener() {
        if (tts != null) {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    Log.d(TAG, "TTS started utterance: " + utteranceId);
                }

                @Override
                public void onDone(String utteranceId) {
                    Log.d(TAG, "TTS completed utterance: " + utteranceId);
                    handleSpeechCompleted();
                }

                @Override
                public void onError(String utteranceId) {
                    Log.e(TAG, "TTS error on utterance: " + utteranceId);
                    handleSpeechCompleted();
                }
            });
        }
    }

    private void handleSpeechCompleted() {
        handler.post(() -> {
            if (isRepeating && repeatingText != null) {
                scheduleNextSpeech();
            }
        });
    }

    private void scheduleNextSpeech() {
        cancelScheduledSpeech();
        repeatRunnable = () -> {
            if (isRepeating && repeatingText != null) {
                performSpeak(repeatingText);
            }
        };
        handler.postDelayed(repeatRunnable, repeatIntervalMs);
    }

    private void cancelScheduledSpeech() {
        if (repeatRunnable != null) {
            handler.removeCallbacks(repeatRunnable);
            repeatRunnable = null;
        }
    }

    private synchronized void performSpeak(String text) {
        if (tts == null || !isLoaded) return;
        try {
            // Stop any ongoing speech and flush queue to prevent overlap
            tts.stop();
            String utteranceId = "alarm_tts_" + System.currentTimeMillis();
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } catch (Exception e) {
            Log.e(TAG, "Error during performSpeak: " + e.getMessage());
        }
    }

    /**
     * Speaks the given text repeatedly every intervalMs (e.g. 15s).
     * The pause interval begins AFTER speech completion to guarantee zero overlap.
     */
    public synchronized void speakRepeating(String text, long intervalMs) {
        if (text == null || text.trim().isEmpty()) return;
        this.repeatingText = text;
        this.repeatIntervalMs = (intervalMs > 0) ? intervalMs : DEFAULT_REPEAT_INTERVAL_MS;
        this.isRepeating = true;
        cancelScheduledSpeech();

        if (isLoaded) {
            performSpeak(text);
        } else {
            pendingText = text;
        }
    }

    public synchronized void stopRepeating() {
        isRepeating = false;
        repeatingText = null;
        cancelScheduledSpeech();
        stop();
    }

    public void speak(String text) {
        isRepeating = false;
        repeatingText = null;
        cancelScheduledSpeech();

        if (isLoaded) {
            performSpeak(text);
        } else {
            pendingText = text;
        }
    }

    public void stop() {
        cancelScheduledSpeech();
        if (tts != null) {
            try {
                tts.stop();
            } catch (Exception ignored) {}
        }
    }

    public void shutdown() {
        stopRepeating();
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {}
            tts = null;
        }
    }
}
