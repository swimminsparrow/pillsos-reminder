package swimminsparrow.ricordamelo.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.core.protection.FallAlertController;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;

/**
 * BroadcastReceiver for handling Fall Alert notification actions:
 * - "STO BENE / ANNULLA"
 * - "INVIA SOS ADESSO"
 */
public class FallAlertReceiver extends BroadcastReceiver {

    private static final String TAG = "FallAlertReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        SpockLogger.info(TAG, "Received action: " + action);

        if (Constants.ACTION_CANCEL_FALL_ALERT.equals(action)) {
            FallAlertController.getInstance().cancelAlert(context);
        } else if (Constants.ACTION_TRIGGER_SOS_NOW.equals(action)) {
            boolean hasContacts = swimminsparrow.ricordamelo.core.sos.SOSContactsManager.hasConfiguredContacts(context);
            if (!hasContacts) {
                SpockLogger.info(TAG, "No contacts configured, opening emergency dialer (112)");
                FallAlertController.getInstance().cancelAlert(context);
                try {
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:112"));
                    dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(dialIntent);
                } catch (Exception e) {
                    SpockLogger.error(TAG, "Failed to launch dialer from notification: " + e.getMessage());
                }
            } else {
                FallAlertController.getInstance().confirmAndSendSOS(context);
            }
        }
    }
}
