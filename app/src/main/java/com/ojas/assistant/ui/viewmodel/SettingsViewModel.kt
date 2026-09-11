package com.ojas.assistant.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ojas.assistant.data.repository.OjasSettings
import com.ojas.assistant.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionState(
    val exactAlarms: Boolean = true,
    val notifications: Boolean = true,
    val usageAccess: Boolean = false,
    val overlay: Boolean = false,
    val batteryUnrestricted: Boolean = false
)

data class SettingsUiState(
    val settings: OjasSettings = OjasSettings(),
    val permissions: PermissionState = PermissionState()
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val permissions = MutableStateFlow(PermissionState())

    val state: StateFlow<SettingsUiState> = combine(
        container.settings.settings,
        permissions
    ) { settings, perms ->
        SettingsUiState(settings, perms)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /**
     * Re-read on every resume: all of these can be revoked from system settings while
     * the app is in the background, and a stale "granted" would silently break alarms.
     */
    fun refreshPermissions(context: Context) {
        val power = context.getSystemService<PowerManager>()
        permissions.value = PermissionState(
            exactAlarms = container.scheduler.canScheduleExact(),
            notifications = com.ojas.assistant.alarm.NotificationCenter.canPost(context),
            usageAccess = container.screenTime.hasUsageAccess(),
            overlay = Settings.canDrawOverlays(context),
            batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        )
    }

    fun setName(value: String) = edit { setName(value) }
    fun setUse24h(value: Boolean) = edit { setUse24h(value) }
    fun setHaptics(value: Boolean) = edit { setHaptics(value) }
    fun setStarCount(value: Int) = edit { setStarCount(value) }
    fun setReduceMotion(value: Boolean) = edit { setReduceMotion(value) }
    fun setGalaxyInteractive(value: Boolean) = edit { setGalaxyInteractive(value) }
    fun setMeditationBell(value: Boolean) = edit { setMeditationBell(value) }
    fun setRestCue(value: Boolean) = edit { setRestCue(value) }
    fun setOnboarded(value: Boolean) = edit { setOnboarded(value) }
    fun setScreenTimeGoal(minutes: Int) = edit { setScreenTimeGoal(minutes) }

    private fun edit(block: suspend com.ojas.assistant.data.repository.SettingsRepository.() -> Unit) {
        viewModelScope.launch { container.settings.block() }
    }

    // ------------------------------------------------------- system settings

    fun exactAlarmSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", context.packageName, null))
        } else {
            null
        }

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun usageAccessIntent(): Intent = container.screenTime.usageAccessSettingsIntent()

    fun overlayIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", context.packageName, null)
        )

    fun batteryIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.fromParts("package", context.packageName, null)
        )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
