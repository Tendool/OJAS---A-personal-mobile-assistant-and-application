package com.ojas.assistant

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ojas.assistant.di.ojas
import com.ojas.assistant.ui.navigation.LocalOjas
import com.ojas.assistant.ui.navigation.OjasNavHost
import com.ojas.assistant.ui.navigation.Routes
import com.ojas.assistant.ui.navigation.navigateTop
import com.ojas.assistant.ui.theme.OjasTheme
import com.ojas.assistant.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result is advisory */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = ojas

        setContent {
            OjasTheme {
                CompositionLocalProvider(LocalOjas provides container) {
                    val nav = rememberNavController()
                    navController = nav

                    val settingsViewModel: SettingsViewModel =
                        viewModel(factory = SettingsViewModel.factory(container))
                    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()

                    // Route requested by whichever notification launched us.
                    val launchRoute = remember { Routes.fromNotification(intent?.getStringExtra(EXTRA_ROUTE)) }
                    LaunchedEffect(launchRoute) {
                        launchRoute?.let { nav.navigateTop(it) }
                    }

                    OjasNavHost(navController = nav, settings = settingsState)
                }
            }
        }

        requestNotificationPermissionIfNeeded()
    }

    /** A notification tap while we are already running arrives here rather than onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Routes.fromNotification(intent.getStringExtra(EXTRA_ROUTE))?.let { route ->
            navController?.navigateTop(route)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Reminders and alarm heads-up notices are the whole point of the app, so this is
        // asked for once at launch rather than buried in settings.
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_ROUTE = "ojas.extra.route"
    }
}
