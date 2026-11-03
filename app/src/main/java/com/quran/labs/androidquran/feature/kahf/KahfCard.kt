package com.quran.labs.androidquran.feature.kahf

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.feature.home.AppIcons
import kotlin.math.cos
import kotlin.math.sin

private val NIGHT = Color(0xFF14213D)
private val DUSK = Color(0xFF1F3A5F)
private val LIGHT = Color(0xFFF5D98B)

/**
 * On the Quran's home in al-Kahf's time: a night sky that fills with light, a lamp for each of
 * its twelve pages lit as it's read, the hadith of the light between the two Fridays, and a
 * button to carry on where the reader stopped. Finished, the whole arc glows.
 */
@Composable
fun KahfCard(onOpen: (Int) -> Unit) {
  val context = LocalContext.current
  val store = KahfStore.get(context)
  val state by store.state.collectAsState()
  if (!state.inTime || state.dismissed) return
  val pages = KahfStore.PAGES.count()
  val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
  val fraction = state.read.size / pages.toFloat()
  val pulse by rememberInfiniteTransition(label = "kahf").animateFloat(
    initialValue = 0.55f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "glow"
  )
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = NIGHT,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    Box {
      // the light grows with each page read
      Canvas(Modifier.matchParentSize()) {
        drawRect(Brush.verticalGradient(listOf(NIGHT, DUSK)))
        // low on the right, clear of the buttons above
        val centre = Offset(size.width * 0.8f, size.height * 0.66f)
        drawCircle(
          Brush.radialGradient(
            listOf(LIGHT.copy(alpha = (0.18f + 0.5f * fraction) * pulse), Color.Transparent),
            center = centre, radius = size.minDimension * (0.45f + 0.5f * fraction)
          ),
          radius = size.minDimension * (0.45f + 0.5f * fraction), center = centre
        )
        // a lamp for each page, along an arc
        val radius = size.minDimension * 0.28f
        for (i in 0 until pages) {
          val angle = Math.toRadians(200.0 + 140.0 * i / (pages - 1))
          val at = Offset(centre.x + (radius * cos(angle)).toFloat(), centre.y + (radius * sin(angle)).toFloat())
          // the first page at the start of the reading direction
          val page = if (rtl) KahfStore.PAGES.last - i else KahfStore.PAGES.first + i
          val lit = page in state.read
          if (lit) drawCircle(LIGHT.copy(alpha = 0.35f * pulse), radius = 9.dp.toPx(), center = at)
          drawCircle(if (lit) LIGHT else Color.White.copy(alpha = 0.22f), radius = 3.5.dp.toPx(), center = at)
        }
      }
      Column(Modifier.padding(20.dp, 16.dp, 20.dp, 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            stringResource(if (state.eve) R.string.kahf_eve else R.string.kahf_day),
            color = LIGHT, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f)
          )
          IconButton(onClick = { store.setReminders(!state.reminders) }, modifier = Modifier.size(36.dp)) {
            Icon(
              if (state.reminders) AppIcons.Bell else AppIcons.BellOff,
              contentDescription = stringResource(R.string.kahf_reminders), tint = Color.White.copy(alpha = 0.8f)
            )
          }
          IconButton(onClick = store::dismiss, modifier = Modifier.size(36.dp)) {
            Icon(QuranIcons.Close, contentDescription = stringResource(R.string.kahf_hide), tint = Color.White.copy(alpha = 0.6f))
          }
        }
        Text(stringResource(R.string.kahf_title), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
          if (state.done) stringResource(R.string.kahf_done) else stringResource(R.string.kahf_hadith),
          color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.fillMaxWidth(0.58f)
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          if (!state.done) {
            Button(
              onClick = { onOpen(state.next) },
              colors = ButtonDefaults.buttonColors(containerColor = LIGHT, contentColor = NIGHT),
              shape = RoundedCornerShape(50)
            ) {
              Text(stringResource(if (state.read.isEmpty()) R.string.kahf_read else R.string.kahf_continue), fontWeight = FontWeight.Bold)
            }
          }
          Column {
            Text(
              if (state.done) stringResource(R.string.kahf_all_read)
              else pluralStringResource(R.plurals.kahf_pages_read, state.read.size, state.read.size, pages),
              color = LIGHT, style = MaterialTheme.typography.labelLarge
            )
            if (state.streak > 1) {
              Text(
                pluralStringResource(R.plurals.kahf_streak, state.streak, state.streak),
                color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall
              )
            }
          }
        }
      }
      // a thin line of light along the bottom: how much is read
      Box(
        Modifier.align(Alignment.BottomStart).fillMaxWidth(fraction.coerceAtLeast(0.001f)).height(3.dp)
          .clip(RoundedCornerShape(50))
      ) {
        Canvas(Modifier.matchParentSize()) { drawRect(LIGHT.copy(alpha = 0.9f)) }
      }
    }
  }
}
