package swimminsparrow.ricordamelo.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.data.dao.FarmacoDao;
import swimminsparrow.ricordamelo.core.data.models.Farmaco;
import swimminsparrow.ricordamelo.core.sos.SOSManager;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;
import swimminsparrow.ricordamelo.core.utils.VolumeUtils;

public class MissedAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "MissedAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        int idRicevuto = intent.getIntExtra("ID", -1);
        
        SpockLogger.info(TAG, "Missed alarm triggered for ID: " + idRicevuto);

        // Recover old volume settings if sound was overridden
        VolumeUtils.restoreVolume(context);

        if (idRicevuto != -1) {

            Farmaco farmaco = FarmacoDao.getInstance(context).getById(idRicevuto);
            String pillName;
            if (farmaco != null && farmaco.getNome() != null && !farmaco.getNome().trim().isEmpty()) {
                pillName = farmaco.getNome().trim();
            } else if (idRicevuto == Constants.TEST_ALARM_NOTIFICATION_ID) {
                pillName = context.getString(R.string.test_alarm_title);
            } else {
                pillName = context.getString(R.string.app_name);
            }

            String personalName = SettingsDB.getInstance(context).getPersonalName();
            if (personalName == null || personalName.trim().isEmpty()) {
                personalName = context.getString(R.string.mario);
            }

            String message = context.getString(R.string.sos_missed_alarm_format, personalName, pillName);
            
            SpockLogger.info(TAG, "Sending missed alarm SOS: " + message);
            SOSManager.sendSOSMessage(context, message);
        }
    }
}
