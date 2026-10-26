package com.quran.labs.androidquran.feature.khatmah

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.ShortcutsActivity
import com.quran.labs.androidquran.feature.home.AppTab
import com.quran.labs.androidquran.feature.home.WithBottomBar
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import dev.zacsweers.metro.Inject

/** Set up a khatmah and follow its progress and streak. */
class KhatmahActivity : AppCompatActivity() {
  @Inject lateinit var khatmahStore: KhatmahStore

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)

    // from the bottom bar there's no page handed over, so use the last one read
    val latestPage = intent.getIntExtra(EXTRA_LATEST_PAGE, -1).takeIf { it > 0 }
      ?: QuranSettings.getInstance(this).lastPage.takeIf { it > 0 } ?: 1
    setContentView(ComposeView(this).apply {
      setContent {
        QuranTheme {
          val progress by khatmahStore.progress.collectAsState()
          WithBottomBar(this@KhatmahActivity, AppTab.KHATMAH) {
          KhatmahScreen(
            progress = progress,
            totalPages = khatmahStore.totalPages,
            latestPage = latestPage,
            onStart = { days, startPage -> khatmahStore.startPlan(days, startPage) },
            onEnd = { khatmahStore.endPlan() },
            onPause = { khatmahStore.pausePlan() },
            onResume = { khatmahStore.resumePlan() },
            onContinue = {
              if (callingActivity != null) {
                setResult(RESULT_CONTINUE_READING)
              } else {
                startActivity(
                  Intent(this@KhatmahActivity, QuranActivity::class.java)
                    .setAction(ShortcutsActivity.ACTION_JUMP_TO_LATEST)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
              }
              finish()
            },
            onBack = { finish() }
          )
          }
        }
      }
    })
  }

  override fun onResume() {
    super.onResume()
    // the day may have changed while the screen was away
    khatmahStore.refresh()
  }

  /** Opens the screen; the result is true when the reader asked to continue reading. */
  class Open : ActivityResultContract<Int, Boolean>() {
    override fun createIntent(context: Context, input: Int): Intent =
      Intent(context, KhatmahActivity::class.java).putExtra(EXTRA_LATEST_PAGE, input)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
      resultCode == RESULT_CONTINUE_READING
  }

  companion object {
    private const val EXTRA_LATEST_PAGE = "latest_page"
    private const val RESULT_CONTINUE_READING = RESULT_FIRST_USER + 1
  }
}
