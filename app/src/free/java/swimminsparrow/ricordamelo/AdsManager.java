package swimminsparrow.ricordamelo;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import swimminsparrow.ricordamelo.ui.common.IAdsManager;

public class AdsManager implements IAdsManager {

    @Override
    public void init(Activity activity, ViewGroup container) {
        // Free flavor: ads disabled completely, no Play Services dependencies
        if (container != null) {
            container.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        // no-op
    }

    @Override
    public void onPause() {
        // no-op
    }

    @Override
    public void onDestroy() {
        // no-op
    }
}