package com.nextgen.courtvision.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.nextgen.courtvision.CourtVisionApp
import com.nextgen.courtvision.R
import com.nextgen.courtvision.domain.model.Coach
import com.nextgen.courtvision.domain.model.Player
import com.nextgen.courtvision.domain.model.User
import com.nextgen.courtvision.ui.login.RegisterFragment
import com.nextgen.courtvision.viewmodel.AuthUiState
import com.nextgen.courtvision.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

/**
 * Single-activity host. Navigation between auth states and role-specific home
 * screens is centralised here so fragments never decide global routing.
 */
class MainActivity : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory((application as CourtVisionApp).container.authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.uiState.collect { state ->
                    when (state) {
                        is AuthUiState.Authenticated -> navigateHome(navController, state.user)
                        is AuthUiState.NeedsProfile -> navigateToProfileCompletion(navController)
                        is AuthUiState.Idle -> navigateToLoginIfSignedOut(navController)
                        else -> Unit
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            authViewModel.restoreSession()
        }
    }

    private fun navigateHome(navController: NavController, user: User) {
        val destination = when (user) {
            is Player -> R.id.drillLibraryFragment
            is Coach -> R.id.coachDashboardFragment
        }
        if (navController.currentDestination?.id != destination) {
            navController.navigate(destination, null, clearBackStack())
        }
    }

    private fun navigateToProfileCompletion(navController: NavController) {
        if (navController.currentDestination?.id != R.id.registerFragment) {
            navController.navigate(
                R.id.registerFragment,
                bundleOf(RegisterFragment.ARG_COMPLETE_PROFILE_MODE to true),
            )
        }
    }

    private fun navigateToLoginIfSignedOut(navController: NavController) {
        // Idle after sign-out from a home screen returns the user to Login;
        // Idle during the auth flow (e.g. after an acknowledged error) must not.
        val current = navController.currentDestination?.id
        if (current == R.id.drillLibraryFragment || current == R.id.coachDashboardFragment) {
            navController.navigate(R.id.loginFragment, null, clearBackStack())
        }
    }

    private fun clearBackStack() = navOptions {
        popUpTo(R.id.nav_graph) { inclusive = true }
    }
}
