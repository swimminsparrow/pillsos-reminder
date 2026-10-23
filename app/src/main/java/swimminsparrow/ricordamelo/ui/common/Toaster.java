package swimminsparrow.ricordamelo.ui.common;

import android.content.Context;
import android.widget.Toast;

/**
 * Created by naples96 on 12/05/18
 * @author swimminsparrow
 * @version 76
 */

/**
 * This Class contains common functions to show Toasts with specific color and message
 */

public class Toaster {

    //todo implements colors

    public static void errorToast(Context c,String m){
        Toast.makeText(c,m,Toast.LENGTH_LONG).show();
    }

    public static void warningToast(Context c,String m){
        Toast.makeText(c,m,Toast.LENGTH_LONG).show();

    }

    public static void successToast(Context c,String m){
        Toast.makeText(c,m,Toast.LENGTH_SHORT).show();
    }

}
