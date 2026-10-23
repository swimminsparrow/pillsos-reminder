package swimminsparrow.ricordamelo.core.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;


import swimminsparrow.ricordamelo.ui.common.Toaster;

/**
 * Created by swimminsparrow on 07/05/16.
 *
 * @version 83 09/03/2019
 * 1. Renamed from LogMe to SpockLogger
 * 2. Added errorLog
 * 3. Added new methods with Class instead of TAG.
 */
public class SpockLogger {

    /**
     * This function is deprecated
     * @param TAG
     * @param msg
     */
    public static void info(String TAG, String msg){
        Log.i(TAG,msg);
    }

    public static void info(Class callingClass, String msg){
        Log.i(callingClass.getSimpleName(), msg);
    }

    public static void error(Class callingClass, String msg ){
        Log.e(callingClass.getSimpleName(), msg);
    }

    public static void error(String TAG, String msg ){
        Log.e(TAG, msg);
    }

    public static void errorLogWithToast(Context context, Class callingClass, String msg, Exception e){
        if (TextUtils.isEmpty(msg)){
            msg = "Si è verificato un errore. Riprovare";
        }
        msg +="\n";

        Log.e(callingClass.getSimpleName(), String.format("%s. Detail: %s", msg, e.getMessage()));
        Toaster.errorToast(context, msg);
    }


}
