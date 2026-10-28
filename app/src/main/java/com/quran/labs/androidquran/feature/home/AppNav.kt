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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.feature.hadith.HadithActivity
import com.quran.labs.androidquran.feature.prayer.PrayerActivity
import com.quran.labs.androidquran.feature.research.ResearchActivity
import com.quran.labs.androidquran.ui.QuranActivity

/** The app's main places, in the bottom bar. */
enum class AppTab(@StringRes val label: Int) {
  QURAN(R.string.nav_quran),
  HADITH(R.string.nav_hadith),
  PRAYER(R.string.nav_prayer),
  RESEARCH(R.string.research_title),
  MORE(R.string.nav_more);

  val icon: ImageVector
    get() = when (this) {
      QURAN -> QuranIcons.MenuBook
      HADITH -> AppIcons.Book
      PRAYER -> AppIcons.Clock
      RESEARCH -> AppIcons.Bulb
      MORE -> AppIcons.Grid
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
      AppTab.HADITH -> Intent(from, HadithActivity::class.java)
      AppTab.PRAYER -> PrayerActivity.intent(from)
      AppTab.RESEARCH -> ResearchActivity.intent(from)
      AppTab.MORE -> Intent(from, MoreActivity::class.java)
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
