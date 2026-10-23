package swimminsparrow.ricordamelo.core.sos;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.data.dao.SOSOutboxDao;
import swimminsparrow.ricordamelo.core.data.models.SOSOutboxMessage;
import swimminsparrow.ricordamelo.core.ntfy.NtfyManager;
import swimminsparrow.ricordamelo.core.ntfy.NtfyMessageType;
import swimminsparrow.ricordamelo.core.utils.CryptoUtils;

/**
 * WorkManager Worker responsible for retrying outgoing messages queued offline.
 * Automatically triggered when network connectivity is restored.
 */
public class SOSOutboxWorker extends Worker {
    private static final String TAG = "SOSOutboxWorker";
    public static final String UNIQUE_WORK_NAME = "SOS_OUTBOX_RETRY_WORK";
    private static final long MAX_MESSAGE_AGE_MS = 24L * 60 * 60 * 1000L; // 24 hours

    public SOSOutboxWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * Enqueues a retry work request to run as soon as internet connection is available.
     */
    public static void scheduleRetry(Context context) {
        if (context == null) return;
        try {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(SOSOutboxWorker.class)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .build();

            WorkManager.getInstance(context.getApplicationContext())
                    .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, workRequest);
            Log.d(TAG, "Scheduled SOSOutboxWorker with CONNECTED network constraint.");
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling SOSOutboxWorker", e);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SOSOutboxDao dao = SOSOutboxDao.getInstance(context);

        // Prune messages older than 24 hours
        dao.clearExpiredMessages(MAX_MESSAGE_AGE_MS);

        List<SOSOutboxMessage> pending = dao.getPendingMessages();
        if (pending == null || pending.isEmpty()) {
            Log.d(TAG, "No pending outbox messages to retry.");
            return Result.success();
        }

        Log.d(TAG, "Processing " + pending.size() + " outbox messages...");
        boolean allSent = true;
        int sentCount = 0;

        for (SOSOutboxMessage item : pending) {
            boolean sent = trySend(context, item);
            if (sent) {
                dao.removeMessage(item.getId());
                boolean isControlMsg = item.getMessage() != null && item.getMessage().startsWith("DISCONNECT_SOS");
                if (!isControlMsg) {
                    sentCount++;
                    // Update conversation history as SENT
                    SOSContactsManager.updateLastMessage(context, item.getTopic(),
                            NtfyMessageType.extractContent(item.getMessage()), "SENT");
                } else {
                    Log.d(TAG, "Successfully delivered offline control message: " + item.getMessage() + " to topic: " + item.getTopic());
                }
            } else {
                dao.incrementAttempts(item.getId());
                allSent = false;
                // Network failed; break loop to avoid burning attempts while offline
                break;
            }
        }

        if (sentCount > 0) {
            swimminsparrow.ricordamelo.core.notification.AppNotificationManager.getInstance().showOutboxDelivered(context);
        }

        if (allSent && !dao.hasPendingMessages()) {
            Log.d(TAG, "All outbox messages successfully delivered.");
            return Result.success();
        } else {
            Log.w(TAG, "Outbox messages remaining. Will retry with WorkManager.");
            return Result.retry();
        }
    }

    private boolean trySend(Context context, SOSOutboxMessage item) {
        HttpURLConnection conn = null;
        try {
            String key = item.getEncryptionKey();
            if (key == null || key.trim().isEmpty()) {
                key = SettingsDB.getInstance(context).getSecurePairingKey();
            }
            if (key == null) return false;

            String encryptedMessage = CryptoUtils.encrypt(item.getMessage(), key);
            if (encryptedMessage == null) return false;

            URL url = new URL(Constants.getNtfyTopicUrl(item.getTopic()));
            conn = (HttpURLConnection) url.openConnection();
            swimminsparrow.ricordamelo.core.utils.ForceIPv4SSLSocketFactory.applyTo(conn);
            conn.setRequestMethod("POST");
            if (item.getTtl() != null && !item.getTtl().trim().isEmpty()) {
                conn.setRequestProperty("TTL", item.getTtl().trim());
            }
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = encryptedMessage.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            boolean ok = responseCode >= 200 && responseCode < 300;
            Log.d(TAG, "Delivered outbox msg ID " + item.getId() + " to topic " + item.getTopic() + ", status: " + responseCode);
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "Failed delivering outbox msg ID " + item.getId(), e);
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
