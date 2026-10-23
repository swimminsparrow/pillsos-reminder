package swimminsparrow.ricordamelo.ui.common;

import android.app.Activity;
import android.view.ViewGroup;

public interface IAdsManager {
    void init(Activity activity, ViewGroup container);
    void onResume();
    void onPause();
    void onDestroy();
}