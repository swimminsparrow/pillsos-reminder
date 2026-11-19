package swimminsparrow.ricordamelo.ui.common;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.preference.PreferenceFragmentCompat;

public interface IProFeatureManager {
    void init(Activity activity);
    void setupToolbarMenu(Activity activity, Menu menu, boolean isTopLevel);
    boolean onToolbarItemSelected(Activity activity, MenuItem item);
    void updateHomeBanner(Activity activity, ViewGroup bannerContainer, boolean hasWarningAbove);
    void setupSettingsPreferences(PreferenceFragmentCompat fragment);
    void addBannerUpdateListener(Runnable listener);
    void removeBannerUpdateListener(Runnable listener);
    void onDestroy();
}
