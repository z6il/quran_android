package com.quran.labs.androidquran.feature.home

import android.app.Activity
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.feature.adhkar.AdhkarActivity
import com.quran.labs.androidquran.feature.khatmah.KhatmahActivity
import com.quran.labs.androidquran.feature.research.ResearchActivity
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.QuranPreferenceActivity

private fun icon(name: String, path: String): ImageVector =
  ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()

private val Flag by lazy { icon("Khatmah", "M14.4,6L14,4H5v17h2v-7h5.6l0.4,2h7V6z") }
private val Tap by lazy {
  icon(
    "Adhkar",
    "M9,11.24V7.5C9,6.12 10.12,5 11.5,5S14,6.12 14,7.5v3.74c1.21,-0.81 2,-2.18 2,-3.74C16,5.01 13.99,3 11.5,3S7,5.01 7,7.5" +
      "c0,1.56 0.79,2.93 2,3.74zM18.84,15.87l-4.54,-2.26c-0.17,-0.07 -0.35,-0.11 -0.54,-0.11H13v-6C13,6.67 12.33,6 11.5,6" +
      "S10,6.67 10,7.5v10.74l-3.43,-0.72c-0.08,-0.01 -0.15,-0.03 -0.24,-0.03 -0.31,0 -0.59,0.13 -0.79,0.33l-0.79,0.8 4.94,4.94" +
      "c0.27,0.27 0.65,0.44 1.06,0.44h6.79c0.75,0 1.33,-0.55 1.44,-1.28l0.75,-5.27c0.01,-0.07 0.02,-0.14 0.02,-0.2 0,-0.62 -0.38,-1.16 -0.91,-1.38z"
  )
}
private val Bulb by lazy {
  icon("Research", "M9,21c0,0.55 0.45,1 1,1h4c0.55,0 1,-0.45 1,-1v-1L9,20v1zM12,2C8.14,2 5,5.14 5,9c0,2.38 1.19,4.47 3,5.74L8,17" +
    "c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1v-2.26c1.81,-1.27 3,-3.36 3,-5.74 0,-3.86 -3.14,-7 -7,-7z")
}

/** The app's main places, in the bottom bar. */
enum class AppTab(@StringRes val label: Int) {
  QURAN(R.string.nav_quran),
  KHATMAH(R.string.nav_khatmah),
  ADHKAR(R.string.nav_adhkar),
  RESEARCH(R.string.research_title),
  SETTINGS(R.string.menu_settings);

  val icon: ImageVector
    get() = when (this) {
      QURAN -> QuranIcons.MenuBook
      KHATMAH -> Flag
      ADHKAR -> Tap
      RESEARCH -> Bulb
      SETTINGS -> QuranIcons.Settings
    }
}

object AppNav {
  /**
   * Goes from the [current] tab to [to]. The Quran tab stays at the bottom of the back stack,
   * so moving between the others replaces the one being left and back always lands home.
   */
  fun go(from: Activity, current: AppTab, to: AppTab) {
    if (to == current) return
    val intent = when (to) {
      AppTab.QURAN -> Intent(from, QuranActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      AppTab.KHATMAH -> Intent(from, KhatmahActivity::class.java)
      AppTab.ADHKAR -> AdhkarActivity.intent(from)
      AppTab.RESEARCH -> ResearchActivity.intent(from)
      AppTab.SETTINGS -> Intent(from, QuranPreferenceActivity::class.java)
    }
    val fade = ActivityOptionsCompat.makeCustomAnimation(from, android.R.anim.fade_in, android.R.anim.fade_out)
    from.startActivity(intent, fade.toBundle())
    if (current != AppTab.QURAN) from.finish()
  }
}

@Composable
fun AppBottomBar(current: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Column(modifier) {
    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
  NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
    AppTab.entries.forEach { tab ->
      val selected = tab == current
      NavigationBarItem(
        selected = selected,
        onClick = { onSelect(tab) },
        icon = { Icon(tab.icon, contentDescription = null) },
        label = {
          Text(
            stringResource(tab.label),
            maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
          )
        },
        colors = NavigationBarItemDefaults.colors(
          selectedIconColor = colors.primary,
          selectedTextColor = colors.primary,
          indicatorColor = colors.primary.copy(alpha = 0.14f),
          unselectedIconColor = colors.onSurfaceVariant,
          unselectedTextColor = colors.onSurfaceVariant
        )
      )
    }
  }
  }
}

/**
 * A top-level screen with the bottom bar under it, shown while [showBar]. The screen above
 * doesn't pad for the navigation bar while the bottom bar does.
 */
@Composable
fun WithBottomBar(
  activity: Activity,
  current: AppTab,
  showBar: Boolean = true,
  content: @Composable () -> Unit
) {
  Column(Modifier.fillMaxSize()) {
    Box(
      Modifier
        .weight(1f)
        .fillMaxWidth()
        .then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)
    ) {
      content()
    }
    if (showBar) AppBottomBar(current, onSelect = { AppNav.go(activity, current, it) })
  }
}
