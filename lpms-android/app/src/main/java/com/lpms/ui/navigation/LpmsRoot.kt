package com.lpms.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lpms.R
import com.lpms.data.model.AuthUser
import com.lpms.data.remote.ApiError
import com.lpms.data.remote.ApiResult
import com.lpms.data.session.AuthRepository
import com.lpms.data.session.AuthState
import com.lpms.data.session.SessionStore
import com.lpms.formatting.MoneyFormatter
import com.lpms.ui.components.LpmsErrorState
import com.lpms.ui.components.LocalMoney
import com.lpms.ui.screens.auth.ChangePasswordScreen
import com.lpms.ui.screens.auth.ChangePasswordViewModel
import com.lpms.ui.screens.auth.LoginScreen
import com.lpms.ui.screens.auth.LoginViewModel
import com.lpms.ui.theme.LpmsTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The app's outermost composable: theme, session-driven navigation, and the
 * global snackbar host.
 *
 * Which screen shows is *only* a function of [SessionStore.authState], so
 * signing out from any deep screen falls all the way back to login without
 * any screen having to unwind a back stack.
 */
@Composable
fun LpmsRoot() {
    val money = stringResource(R.string.currency_code)
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val darkTheme = sessionViewModel.darkTheme

    LpmsTheme(darkTheme = darkTheme ?: androidx.compose.foundation.isSystemInDarkTheme()) {
        CompositionLocalProvider(LocalMoney provides MoneyFormatter(money)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                val sessionViewModel: SessionViewModel = hiltViewModel()
                val authState by sessionViewModel.authState.collectAsStateWithLifecycle()

                when (val state = authState) {
                    AuthState.Initializing -> SessionLaunch(viewModel = sessionViewModel)

                    AuthState.Unauthenticated -> {
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = Routes.Login) {
                            composable(Routes.Login) {
                                val viewModel: LoginViewModel = hiltViewModel()
                                LoginScreen(viewModel = viewModel)
                            }
                        }
                    }

                    is AuthState.Authenticated -> {
                        if (state.user.mustChangePassword) {
                            val navController = rememberNavController()
                            NavHost(
                                navController = navController,
                                startDestination = Routes.ChangePassword,
                            ) {
                                composable(Routes.ChangePassword) {
                                    val viewModel: ChangePasswordViewModel = hiltViewModel()
                                    ChangePasswordScreen(viewModel = viewModel)
                                }
                            }
                        } else {
                            MainNavGraph(
                                user = state.user,
                                snackbarHostState = remember { SnackbarHostState() },
                                onSignOut = { sessionViewModel.logout() },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shown while a stored token is being re-confirmed by `/auth/me`.
 *
 * The spinner is deliberately featureless: a failure here only ever means
 * "check the connection and try again", never "sign in again".
 */
@Composable
private fun SessionLaunch(viewModel: SessionViewModel) {
    val restoreState by viewModel.restoreState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = restoreState) {
                is RestoreState.Checking -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )

                is RestoreState.Failed -> LpmsErrorState(
                    error = state.error,
                    onRetry = viewModel::retryRestore,
                )
            }
        }
    }
}

sealed interface RestoreState {
    data object Checking : RestoreState
    data class Failed(val error: ApiError) : RestoreState
}

/**
 * Owns the launch-time session check.
 *
 * [hiltViewModel] at the root means one instance survives everything below
 * it, which is what lets a single observable hold the auth state.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    sessionStore: SessionStore,
    private val authRepository: AuthRepository,
    private val uiPreferences: com.lpms.data.session.UiPreferences,
) : ViewModel() {

    val authState: StateFlow<AuthState> = sessionStore.authState

    val darkTheme: Boolean? = uiPreferences.darkTheme

    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Checking)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    init {
        restore()
    }

    fun retryRestore() = restore()

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    private fun restore() {
        _restoreState.value = RestoreState.Checking
        viewModelScope.launch {
            val result = authRepository.restore()
            if (result is ApiResult.Failure && authState.value is AuthState.Initializing) {
                // An HTTP failure already cleared the session, which flips
                // authState to Unauthenticated and leaves this screen; only a
                // transport failure parks us here with a retry button.
                _restoreState.value = RestoreState.Failed(result.error)
            }
        }
    }
}
