package com.sleepmusictimer.app.presentation

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sleepmusictimer.app.data.repository.PreferencesRepository
import com.sleepmusictimer.app.domain.model.SleepSettings
import com.sleepmusictimer.app.domain.model.ThemePreference
import com.sleepmusictimer.app.presentation.main.MainScreen
import com.sleepmusictimer.app.presentation.main.MainViewModel
import com.sleepmusictimer.app.presentation.settings.SettingsScreen
import com.sleepmusictimer.app.presentation.settings.SettingsViewModel
import com.sleepmusictimer.app.presentation.theme.SleepTimerTheme
import com.sleepmusictimer.app.presentation.diagnostics.DiagnosticsScreen
import com.sleepmusictimer.app.presentation.diagnostics.DiagnosticsViewModel
import com.sleepmusictimer.app.presentation.permissions.PermissionsScreen
import com.sleepmusictimer.app.presentation.splash.SplashScreen
import com.sleepmusictimer.app.presentation.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    // Android 13+ Notification Permission Launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted")
        } else {
            Log.w("MainActivity", "Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-edge support for Android 15 / modern APIs
        enableEdgeToEdge()

        // Check/Request permissions on Android 13+
        checkNotificationPermission()

        setContent {
            val settings by preferencesRepository.sleepSettingsFlow.collectAsState(initial = SleepSettings())

            val isDarkTheme = when (settings.themePreference) {
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }

            SleepTimerTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "splash"
                ) {
                    composable("splash") {
                        SplashScreen(
                            preferencesRepository = preferencesRepository,
                            onNavigateToOnboarding = {
                                navController.navigate("onboarding") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            },
                            onNavigateToMain = {
                                navController.navigate("main") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("onboarding") {
                        OnboardingScreen(
                            preferencesRepository = preferencesRepository,
                            onNavigateToMain = {
                                navController.navigate("main") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("main") {
                        val mainViewModel: MainViewModel = hiltViewModel()
                        MainScreen(
                            viewModel = mainViewModel,
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        val settingsViewModel: SettingsViewModel = hiltViewModel()
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToDiagnostics = { navController.navigate("diagnostics") },
                            onNavigateToPermissions = { navController.navigate("permissions") }
                        )
                    }
                    composable("diagnostics") {
                        val diagnosticsViewModel: DiagnosticsViewModel = hiltViewModel()
                        DiagnosticsScreen(
                            viewModel = diagnosticsViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("permissions") {
                        PermissionsScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
