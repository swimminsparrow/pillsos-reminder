package swimminsparrow.ricordamelo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.ui.common.IProFeatureManager;

/**
 * Free / F-Droid implementation:
 * Completely free of proprietary billing SDKs, ad-free by default,
 * no pro upgrade banners, only supports open-source coffee donations.
 */
public class ProFeatureManager implements IProFeatureManager {

    private static ProFeatureManager instance;

    public static synchronized ProFeatureManager getInstance() {
        if (instance == null) {
            instance = new ProFeatureManager();
        }
        return instance;
    }

    @Override
    public void init(Activity activity) {
        // No-op for free / F-Droid build
    }

    @Override
    public void setupToolbarMenu(Activity activity, Menu menu, boolean isTopLevel) {
        MenuItem coffeeItem = menu.findItem(R.id.action_coffee);
        if (coffeeItem != null) {
            coffeeItem.setVisible(isTopLevel);
        }
    }

    @Override
    public boolean onToolbarItemSelected(Activity activity, MenuItem item) {
        if (item.getItemId() == R.id.action_coffee) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.DONATE_URL));
                activity.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(activity, activity.getString(R.string.cannot_open_browser), Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return false;
    }

    @Override
    public void updateHomeBanner(Activity activity, ViewGroup bannerContainer, boolean hasWarningAbove) {
        if (bannerContainer != null) {
            bannerContainer.setVisibility(View.GONE);
        }
    }

    @Override
    public void setupSettingsPreferences(PreferenceFragmentCompat fragment) {
        Preference coffeePref = fragment.findPreference("d_coffee");
        if (coffeePref != null) {
            coffeePref.setVisible(true);
            coffeePref.setOnPreferenceClickListener(pref -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.DONATE_URL));
                    fragment.startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(fragment.getContext(), "Impossibile aprire il browser", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }
    }

    @Override
    public void addBannerUpdateListener(Runnable listener) {
        // No-op
    }

    @Override
    public void removeBannerUpdateListener(Runnable listener) {
        // No-op
    }

    @Override
    public void onDestroy() {
        // No-op
    }
}
