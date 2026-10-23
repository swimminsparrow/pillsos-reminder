package swimminsparrow.ricordamelo.ui.images;

import android.os.Bundle;

import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.ui.BaseActivity;


public class PhotoPickerActivity extends BaseActivity {
    private NavController navController;
    private Toolbar toolbar;
    private AppBarConfiguration appBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_picker);

        setupNavigator();
        setupToolbar();
    }

    private void setupNavigator(){
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_onboarding);
        navController = navHostFragment.getNavController();
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Empty Builder() ensures all destinations show the Up (Back) arrow
        appBarConfiguration = new AppBarConfiguration.Builder().build();

        NavigationUI.setupWithNavController(
                toolbar,
                navController,
                appBarConfiguration
        );

        String customTitle = getIntent().getStringExtra("title");
        if (customTitle != null && !customTitle.isEmpty()) {
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                if (destination.getId() == R.id.nav_photo_picker) {
                    toolbar.setTitle(customTitle);
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(customTitle);
                    }
                }
            });
        }

        toolbar.setNavigationOnClickListener(v -> {
            if (!navController.navigateUp()) {
                finish();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}
