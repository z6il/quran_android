package com.quran.labs.androidquran.feature.prayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.feature.home.AppTab
import com.quran.labs.androidquran.feature.home.WithBottomBar
import kotlinx.coroutines.launch

/** Prayer times for where the reader is, with an alert at each. */
class PrayerActivity : AppCompatActivity() {
  private val store by lazy { PrayerStore.get(this) }
  private var showSettings by mutableStateOf(false)
  private var choosingCity by mutableStateOf(false)
  private var locating by mutableStateOf(false)
  private var notificationsAllowed by mutableStateOf(true)
  private var exactAllowed by mutableStateOf(true)

  private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    if (granted.values.any { it }) locate() else Toast.makeText(this, R.string.prayer_location_denied, Toast.LENGTH_LONG).show()
  }
  private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermissions() }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    showSettings = savedInstanceState?.getBoolean(STATE_SETTINGS) ?: false
    onBackPressedDispatcher.addCallback(this) { if (showSettings) showSettings = false else finish() }

    setContentView(ComposeView(this).apply {
      setContent {
        QuranTheme {
          val settings by store.settings.collectAsState()
          WithBottomBar(this@PrayerActivity, AppTab.PRAYER, showBar = !showSettings) {
            AnimatedContent(
              targetState = showSettings,
              transitionSpec = {
                val forward = targetState
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                  (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
              },
              label = "prayer"
            ) { settingsOpen ->
              if (settingsOpen) {
                PrayerSettingsScreen(
                  settings = settings,
                  onChange = { store.update(it) },
                  onChooseCity = { choosingCity = true },
                  onBack = { showSettings = false }
                )
              } else {
                PrayerScreen(
                  settings = settings,
                  locating = locating,
                  notificationsAllowed = notificationsAllowed,
                  exactAllowed = exactAllowed,
                  actions = PrayerActions(
                    onToggleAlert = { name ->
                      store.update { s -> s.copy(alerts = if (name in s.alerts) s.alerts - name else s.alerts + name) }
                      if (!notificationsAllowed) askNotifications()
                    },
                    onSettings = { showSettings = true },
                    onQibla = { startActivity(Intent(this@PrayerActivity, QiblaActivity::class.java)) },
                    onUseLocation = ::useLocation,
                    onChooseCity = { choosingCity = true },
                    onAllowNotifications = ::askNotifications,
                    onAllowExact = ::askExact
                  )
                )
              }
            }
          }
          if (choosingCity) {
            CityDialog(
              search = { PrayerLocation.search(this@PrayerActivity, it) },
              onUseLocation = { choosingCity = false; useLocation() },
              onPick = { place ->
                choosingCity = false
                store.update { it.copy(place = place) }
              },
              onDismiss = { choosingCity = false }
            )
          }
        }
      }
    })
  }

  override fun onResume() {
    super.onResume()
    refreshPermissions()
    PrayerAlarms.schedule(this)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(STATE_SETTINGS, showSettings)
  }

  private fun refreshPermissions() {
    notificationsAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
    exactAllowed = PrayerAlarms.canAlertOnTime(this)
  }

  private fun useLocation() {
    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
  }

  private fun locate() {
    if (locating) return
    locating = true
    lifecycleScope.launch {
      val location = PrayerLocation.current(this@PrayerActivity)
      if (location == null) {
        Toast.makeText(this@PrayerActivity, R.string.prayer_location_failed, Toast.LENGTH_LONG).show()
      } else {
        val place = PrayerLocation.describe(this@PrayerActivity, location.latitude, location.longitude)
        store.update { it.copy(place = place) }
      }
      locating = false
    }
  }

  private fun askNotifications() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }
  }

  private fun askExact() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
    }
  }

  companion object {
    private const val STATE_SETTINGS = "settings"

    fun intent(context: Context): Intent = Intent(context, PrayerActivity::class.java)
  }
}
