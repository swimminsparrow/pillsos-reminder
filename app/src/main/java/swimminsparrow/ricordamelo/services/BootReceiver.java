package swimminsparrow.ricordamelo.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import swimminsparrow.ricordamelo.core.utils.SpockLogger;


/**
 * BroadcastReceiver to restore alarms on boot.
 */

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        swimminsparrow.ricordamelo.core.utils.AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                ReminderScheduler.refreshAlarms(context.getApplicationContext());
            } finally {
                pendingResult.finish();
            }
        });

        ProtectionService.sync(context);

        if (swimminsparrow.ricordamelo.core.data.dao.SOSOutboxDao.getInstance(context).hasPendingMessages()) {
            swimminsparrow.ricordamelo.core.sos.SOSOutboxWorker.scheduleRetry(context);
        }

        SpockLogger.info("BootReceiver", "Alarms restored on boot via ReminderScheduler");
    }
}

