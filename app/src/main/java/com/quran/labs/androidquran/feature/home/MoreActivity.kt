package com.quran.labs.androidquran.feature.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.QuranPreferenceActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.feature.research.ResearchActivity
import com.quran.labs.androidquran.feature.khatmah.KhatmahActivity
import com.quran.labs.androidquran.feature.prayer.AppTopBar
import com.quran.labs.androidquran.feature.prayer.QiblaActivity
import com.quran.labs.androidquran.feature.prayer.cardColor
import com.quran.labs.androidquran.feature.update.AppUpdater
import com.quran.labs.androidquran.feature.update.UpdateController
import com.quran.labs.androidquran.feature.update.UpdatePrompt

/** Everything that isn't in the bottom bar. */
class MoreActivity : AppCompatActivity() {
  private class Entry(val icon: ImageVector, val title: Int, val detail: Int, val open: () -> Intent)

  private val updates = UpdateController(this)

  override fun onResume() {
    super.onResume()
    updates.onResume()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val entries = listOf(
      Entry(AppIcons.Bulb, R.string.research_title, R.string.more_research_detail) { ResearchActivity.intent(this) },
      Entry(AppIcons.Kaaba, R.string.qibla_title, R.string.more_qibla_detail) { Intent(this, QiblaActivity::class.java) },
      Entry(AppIcons.Flag, R.string.nav_khatmah, R.string.more_khatmah_detail) { Intent(this, KhatmahActivity::class.java) },
      Entry(QuranIcons.Settings, R.string.menu_settings, R.string.more_settings_detail) { Intent(this, QuranPreferenceActivity::class.java) }
    )
    setContentView(ComposeView(this).apply {
      setContent {
        QuranTheme {
          UpdatePrompt(updates)
          WithBottomBar(this@MoreActivity, AppTab.MORE) {
            Scaffold(topBar = { AppTopBar(stringResource(R.string.nav_more), onBack = null) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
              LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                  UpdateTile(checking = updates.checking, onCheck = updates::checkNow)
                }
                items(entries) { entry ->
                  Surface(
                    onClick = { startActivity(entry.open()) },
                    shape = RoundedCornerShape(20.dp),
                    color = cardColor(),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Column(Modifier.padding(18.dp)) {
                      Box(
                        Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                      }
                      Spacer(Modifier.height(14.dp))
                      Text(stringResource(entry.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                      Spacer(Modifier.height(2.dp))
                      Text(
                        stringResource(entry.detail), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    })
  }
}

/** this version, and a check for a newer one on GitHub */
@androidx.compose.runtime.Composable
private fun UpdateTile(checking: Boolean, onCheck: () -> Unit) {
  Surface(onClick = onCheck, enabled = !checking, shape = RoundedCornerShape(20.dp), color = cardColor(), modifier = Modifier.fillMaxWidth()) {
    androidx.compose.foundation.layout.Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
      ) {
        if (checking) {
          androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
        } else {
          Icon(AppIcons.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        }
      }
      Spacer(Modifier.size(14.dp))
      Column(Modifier.weight(1f)) {
        Text(stringResource(R.string.app_update_check), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
          stringResource(R.string.app_update_this_version, "3.6.4-z6il." + AppUpdater.currentBuild),
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
