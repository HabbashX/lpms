package com.lpms;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.lpms.core.auth.SessionEvent;
import com.lpms.databinding.ActivityMainBinding;
import com.lpms.ui.shell.ShellViewModel;

import java.util.HashSet;
import java.util.Set;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Single activity hosting every screen.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Bottom navigation is shown only on the authenticated top-level destinations.</li>
 *   <li>Session events raised anywhere (401 that could not be refreshed, logout, forced
 *       password change) are turned into navigation here, so no feature code has to.</li>
 *   <li>{@code /auth/me} is refreshed on every resume so role-based UI stays accurate.</li>
 * </ul>
 */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ShellViewModel viewModel;
    private NavController navController;

    /** Destinations that show the bottom bar; each one is a bottom-nav menu item id. */
    private static final Set<Integer> TOP_LEVEL = new HashSet<>();

    static {
        TOP_LEVEL.add(R.id.dashboardFragment);
        TOP_LEVEL.add(R.id.posFragment);
        TOP_LEVEL.add(R.id.moreFragment);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ShellViewModel.class);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (host == null) {
            throw new IllegalStateException("NavHostFragment missing from activity_main");
        }
        navController = host.getNavController();

        NavigationUI.setupWithNavController(binding.bottomNav, navController);
        binding.bottomNav.setOnItemReselectedListener(item -> {
            // Re-tapping the current tab pops that tab's stack to its root.
            navController.popBackStack(item.getItemId(), false);
        });

        navController.addOnDestinationChangedListener((controller, destination, args) ->
                updateChrome(destination.getId()));

        viewModel.events().observe(this, this::handleSessionEvent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.onAppResumed();
    }

    private void updateChrome(int destinationId) {
        boolean topLevel = TOP_LEVEL.contains(destinationId);
        binding.bottomNav.setVisibility(topLevel ? android.view.View.VISIBLE : android.view.View.GONE);
        if (topLevel && viewModel.mustChangePassword()) {
            // While mustChangePassword is true the server answers everything except
            // change-password/logout/me with 403; keep the user on that screen.
            navController.navigate(R.id.changePasswordFragment);
        }
    }

    private void handleSessionEvent(@Nullable SessionEvent event) {
        if (event == null) {
            return;
        }
        switch (event.getType()) {
            case LOGIN_REQUIRED:
            case SESSION_EXPIRED:
                navController.navigate(R.id.loginFragment);
                break;
            case LOGGED_OUT:
                navController.navigate(R.id.loginFragment);
                break;
            case PASSWORD_CHANGE_REQUIRED:
                navController.navigate(R.id.changePasswordFragment);
                break;
            case PROFILE_CHANGED:
            default:
                // Role changed server-side: destination listeners re-evaluate the UI.
                break;
        }
    }

    @NonNull
    public ShellViewModel shellViewModel() {
        return viewModel;
    }
}