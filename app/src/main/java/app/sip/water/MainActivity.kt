package app.sip.water

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings as SystemSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sip.water.reminders.Notifications
import app.sip.water.ui.HistoryScreen
import app.sip.water.ui.MainViewModel
import app.sip.water.ui.SettingsScreen
import app.sip.water.ui.TodayScreen
import app.sip.water.ui.theme.SipTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            SipTheme(dynamicColor = state.settings.dynamicColor) { SipApp(vm) }
        }
    }
}

private enum class Tab(val label: String) { Today("Today"), History("History"), Settings("Settings") }

@Composable
private fun SipApp(vm: MainViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    val snackbar = remember { SnackbarHostState() }

    // Back from History or Settings returns to Today rather than leaving the app.
    BackHandler(enabled = tab != Tab.Today) { tab = Tab.Today }

    // A new message replaces the one on screen, so rapid adds never queue up.
    LaunchedEffect(Unit) {
        vm.messages.collectLatest { msg ->
            val result = snackbar.showSnackbar(msg.text, msg.actionLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) msg.action?.invoke()
        }
    }

    // Notification permission: re-checked whenever you come back to the app.
    var notificationsAllowed by remember { mutableStateOf(Notifications.canPost(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsAllowed = Notifications.canPost(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = Notifications.canPost(context)
    }
    val enableNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !state.settings.askedNotifications) {
            vm.markAskedNotifications()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Already asked once: Android will not show the prompt again, so open the settings page.
            context.startActivity(
                Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }
    // Ask once on first launch; reminders are the point of the app.
    LaunchedEffect(state.loaded) {
        if (state.loaded && !notificationsAllowed && !state.settings.askedNotifications &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            enableNotifications()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.Today -> Icons.Rounded.WaterDrop
                                    Tab.History -> Icons.Rounded.Insights
                                    Tab.Settings -> Icons.Rounded.Settings
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Crossfade(targetState = tab, label = "tab") { current ->
            when (current) {
                Tab.Today -> TodayScreen(
                    state = state,
                    notificationsAllowed = notificationsAllowed,
                    onEnableNotifications = enableNotifications,
                    onAdd = { ml, at -> vm.addDrink(ml, at) },
                    onRemove = { vm.removeEntry(it) },
                    contentPadding = padding,
                )
                Tab.History -> HistoryScreen(state, padding)
                Tab.Settings -> SettingsScreen(vm, state, padding)
            }
        }
    }
}
