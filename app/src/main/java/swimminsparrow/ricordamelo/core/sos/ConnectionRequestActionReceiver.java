package swimminsparrow.ricordamelo.core.sos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ConnectionRequestActionReceiver extends BroadcastReceiver {

    public static final String ACTION_APPROVE = "swimminsparrow.ricordamelo.ACTION_APPROVE_CONNECTION_REQUEST";
    public static final String ACTION_REJECT = "swimminsparrow.ricordamelo.ACTION_REJECT_CONNECTION_REQUEST";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null)
            return;
        String action = intent.getAction();
        if (action == null)
            return;

        ConnectionRequest request = ConnectionRequest.fromIntent(intent);
        if (request == null)
            return;

        if (ACTION_APPROVE.equals(action)) {
            ConnectionRequestManager.acceptRequest(context, request);
        } else if (ACTION_REJECT.equals(action)) {
            ConnectionRequestManager.rejectRequest(context, request);
        }
    }
}
