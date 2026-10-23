package swimminsparrow.ricordamelo.config;


/**
 * This Class contains common Project Constants.
 *
 * @author swimminsparrow
 * @since 07/03/2017
 */

public class Constants {

    public static int CROPPED_IMAGE_REQUESTCODE = 12;
    public static int IMAGELOADED_RESULTCODE = 21;

    /**
     * NOTIFICATION CONSTANTS
     */
    public static final int PROTECTION_NOTIFICATION_ID = 2;
    public static final int FALL_ALERT_NOTIFICATION_ID = 2002;
    public static final int TEST_ALARM_NOTIFICATION_ID = 9999;
    public static final String SERVICE_CHANNEL_ID = "pillsos_service_channel";
    public static final String ALARM_CHANNEL_ID = "pillsos_alarms_channel";
    public static final String SOS_CHANNEL_ID = "pillsos_sos_channel";
    public static final String FALL_CHANNEL_ID = "pillsos_fall_channel_v2";

    public static final String ACTION_CANCEL_FALL_ALERT = "swimminsparrow.ricordamelo.action.CANCEL_FALL_ALERT";
    public static final String ACTION_TRIGGER_SOS_NOW = "swimminsparrow.ricordamelo.action.TRIGGER_SOS_NOW";

    // PATH //
    public static final String PHOTOS_DIR_PATH = "photos/";
    public static final String AVATAR_FILE_PATH = "me.jpg";

    // DONATION / COFFEE LINK //
    public static final String DONATE_URL = "https://buymeacoffee.com/swimminsparrow";
    public static final String PRIVACY_POLICY_URL = "https://swimminsparrow.github.io/pillsos-reminder/privacy-policy.html";

    // NTFY SERVER CONFIG //
    public static final String NTFY_BASE_URL = "https://ntfy.sh/";

    public static String getNtfyTopicUrl(String topic) {
        if (topic == null)
            return NTFY_BASE_URL;
        return NTFY_BASE_URL + topic;
    }

}
