package swimminsparrow.ricordamelo.core.sos;

import android.content.Context;
import android.util.Log;

import java.util.List;

import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.ntfy.NtfyManager;
import swimminsparrow.ricordamelo.services.ProtectionService;

public class SOSManager {

    private static final String TAG = "SOSManager";

    public static void startServices(Context context) {
        if (context == null) return;
        ProtectionService.sync(context);
        ConnectionRequestManager.startListening(context);
    }

    public static boolean sendImOkMessage(Context context, String message, boolean notifyUser) {
        if (context == null || message == null) return false;
        String payload = message.startsWith("IM_OK_SOS:") ? message : ("IM_OK_SOS:" + message);
        return dispatchNtfyMessage(context, payload, notifyUser);
    }

    public static boolean sendSOSMessage(Context context, String message) {
        return sendSOSMessage(context, message, true);
    }

    public static boolean sendSOSMessage(Context context, String message, boolean notifyUser) {
        if (context == null || message == null) return false;
        String payload = message.startsWith("ALARM_SOS:") ? message : ("ALARM_SOS:" + message);
        return dispatchNtfyMessage(context, payload, notifyUser);
    }

    private static boolean dispatchNtfyMessage(Context context, String payload, boolean notifyUser) {
        List<SOSContact> contacts = SOSContactsManager.getContacts(context);
        if (contacts == null || contacts.isEmpty()) {
            Log.w(TAG, "No connected contacts available. Message will not be sent.");
            return false;
        }
        NtfyManager.getInstance(context, SettingsDB.getInstance(context).getUserUUID()).publishMessage(payload, notifyUser);
        return true;
    }
}
