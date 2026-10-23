package swimminsparrow.ricordamelo.ui.onboarding;

import android.os.Bundle;

import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.ui.BaseActivity;

public class OnboardingActivity extends BaseActivity {

    private NavController navController;
    private Toolbar toolbar;
    private AppBarConfiguration appBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        setupNavigator();
        setupToolbar();
    }

    private void setupNavigator() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_onboarding);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (navController != null) {
            appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.nav_onboarding_step_1
            ).build();

            NavigationUI.setupWithNavController(
                    toolbar,
                    navController,
                    appBarConfiguration
            );
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (navController != null) {
            return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
        }
        return super.onSupportNavigateUp();
    }
}
