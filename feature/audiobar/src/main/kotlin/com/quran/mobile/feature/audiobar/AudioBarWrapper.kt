package com.quran.mobile.feature.audiobar

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.audiobar.presenter.AudioBarPresenter
import com.quran.mobile.feature.audiobar.ui.AudioBar
import dev.zacsweers.metro.Inject

class AudioBarWrapper @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

  @Inject
  lateinit var audioBarPresenter: AudioBarPresenter

  init {
    (context as? AudioBarInjector)?.inject(this)
  }

  @Composable
  override fun Content() {
    QuranTheme {
      val scope = rememberCoroutineScope()
      val flow = remember {
        scope.launchMolecule(mode = RecompositionMode.ContextClock) {
          audioBarPresenter.audioBarPresenter()
        }
      }
      val eventListeners = remember(audioBarPresenter) { audioBarPresenter.eventListeners() }

      // a floating card above the page, clear of the navigation bar and cutouts
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            .compositeOver(MaterialTheme.colorScheme.surface),
          contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            WindowInsets.navigationBars.add(WindowInsets.displayCutout)
              .only(WindowInsetsSides.Horizontal.plus(WindowInsetsSides.Bottom))
              .asPaddingValues()
          )
          .padding(horizontal = 12.dp, vertical = 10.dp)
      ) {
        AudioBar(
          flow,
          eventListeners,
          modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .heightIn(min = dimensionResource(id = R.dimen.audiobar_height))
        )
      }
    }
  }
}
