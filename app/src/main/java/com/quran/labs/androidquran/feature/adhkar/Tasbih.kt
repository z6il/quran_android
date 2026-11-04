package com.quran.labs.androidquran.feature.adhkar

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.feature.home.AppIcons
import com.quran.labs.androidquran.feature.khatmah.ProgressRing
import kotlinx.coroutines.launch
import java.util.Locale

/** The phrases the sebha offers, then the reader's own. */
private val PRESETS = listOf(
  "سُبْحَانَ اللَّهِ",
  "الْحَمْدُ لِلَّهِ",
  "اللَّهُ أَكْبَرُ",
  "لَا إِلَهَ إِلَّا اللَّهُ",
  "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
  "أَسْتَغْفِرُ اللَّهَ",
  "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ",
  "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ"
)

/** a round's length; 0 counts on with no end */
private val TARGETS = listOf(33, 100, 1000, 0)

/** The sebha's phrase, round and counts, and every phrase's count over time. */
class TasbihStore(context: Context) {
  private val prefs = context.getSharedPreferences("tasbih", Context.MODE_PRIVATE)

  var phrase: String
    get() = prefs.getString("phrase", PRESETS[0])!!
    set(v) = prefs.edit { putString("phrase", v) }
  var target: Int
    get() = prefs.getInt("target", 33)
    set(v) = prefs.edit { putInt("target", v) }
  var count: Int
    get() = prefs.getInt("count", 0)
    set(v) = prefs.edit { putInt("count", v) }
  var rounds: Int
    get() = prefs.getInt("rounds", 0)
    set(v) = prefs.edit { putInt("rounds", v) }

  val custom: List<String> get() = prefs.getString("custom", "").orEmpty().split('\n').filter { it.isNotBlank() }

  fun addCustom(text: String) = prefs.edit { putString("custom", (custom + text).distinct().joinToString("\n")) }

  fun removeCustom(text: String) = prefs.edit { putString("custom", (custom - text).joinToString("\n")) }

  /** all the times [phrase] was said here */
  fun total(phrase: String): Int = prefs.getInt("total_" + phrase.hashCode(), 0)

  fun addOne(phrase: String) = prefs.edit { putInt("total_" + phrase.hashCode(), total(phrase) + 1) }
}

private fun Int.local(): String = String.format(Locale.getDefault(), "%d", this)

/** An electronic sebha: a phrase, a round of 33 or 100 or more, tap anywhere or press a volume button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasbihScreen(host: AdhkarHost, onBack: () -> Unit) {
  val context = LocalContext.current
  val store = remember { TasbihStore(context) }
  val settings = remember { AdhkarSettings.load(context) }
  var phrase by remember { mutableStateOf(store.phrase) }
  var target by remember { mutableIntStateOf(store.target) }
  var count by remember { mutableIntStateOf(store.count) }
  var rounds by remember { mutableIntStateOf(store.rounds) }
  var total by remember { mutableIntStateOf(store.total(store.phrase)) }
  var custom by remember { mutableStateOf(store.custom) }
  var adding by remember { mutableStateOf(false) }
  var confirmReset by remember { mutableStateOf(false) }
  val haptics = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val pulse = remember { Animatable(1f) }
  val font = remember { FontFamily(Font("kitab.ttf", context.assets)) }

  val tap = {
    count += 1
    total += 1
    store.addOne(phrase)
    val roundDone = target > 0 && count >= target
    if (roundDone) {
      count = 0
      rounds += 1
      store.rounds = rounds
    }
    store.count = count
    if (settings.vibrate) haptics.performHapticFeedback(if (roundDone) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
    scope.launch {
      pulse.snapTo(0.94f)
      pulse.animateTo(1f, tween(140))
    }
    Unit
  }
  val currentTap by rememberUpdatedState(tap)
  DisposableEffect(settings.volumeKeys) {
    host.onVolume = if (settings.volumeKeys) ({ currentTap() }) else null
    host.keepScreenOn(settings.keepScreenOn)
    onDispose {
      host.onVolume = null
      host.keepScreenOn(false)
    }
  }
  val choose = { p: String ->
    phrase = p
    store.phrase = p
    count = 0
    rounds = 0
    store.count = 0
    store.rounds = 0
    total = store.total(p)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.tasbih_title)) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(QuranIcons.ArrowBack, contentDescription = stringResource(R.string.adhkar_back)) } },
        actions = {
          TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.adhkar_reset)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    Column(
      Modifier.padding(padding).fillMaxSize()
        // the whole screen counts
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = tap),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PRESETS + custom) { p ->
          FilterChip(
            selected = p == phrase, onClick = { choose(p) }, shape = RoundedCornerShape(50),
            label = { Text(p, style = TextStyle(fontFamily = font, fontSize = 15.sp)) }
          )
        }
        item {
          FilterChip(selected = false, onClick = { adding = true }, shape = RoundedCornerShape(50), leadingIcon = { Icon(AppIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp)) }, label = { Text(stringResource(R.string.tasbih_add)) })
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TARGETS.forEach { t ->
          FilterChip(
            selected = t == target,
            onClick = {
              target = t
              store.target = t
              count = 0
              store.count = 0
            },
            shape = RoundedCornerShape(50),
            label = { Text(if (t == 0) stringResource(R.string.tasbih_free) else t.local()) }
          )
        }
      }
      Spacer(Modifier.weight(0.6f))
      Text(
        phrase,
        style = TextStyle(fontFamily = font, fontSize = 30.sp, lineHeight = 48.sp, textAlign = TextAlign.Center),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp)
      )
      Spacer(Modifier.height(24.dp))
      ProgressRing(
        progress = if (target > 0) count / target.toFloat() else 0f,
        modifier = Modifier.size(240.dp).scale(pulse.value),
        strokeWidth = 14f
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface),
          modifier = Modifier.size(200.dp)
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(count.local(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            if (target > 0) Text("/ ${target.local()}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      Spacer(Modifier.height(20.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Stat(stringResource(R.string.tasbih_rounds), rounds.local())
        Stat(stringResource(R.string.tasbih_total), total.local())
      }
      Spacer(Modifier.weight(1f))
      Text(
        stringResource(if (settings.volumeKeys) R.string.tasbih_hint_volume else R.string.tasbih_hint),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)
      )
    }
  }

  if (adding) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { adding = false },
      title = { Text(stringResource(R.string.tasbih_add)) },
      text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
      confirmButton = {
        TextButton(enabled = text.isNotBlank(), onClick = {
          adding = false
          store.addCustom(text.trim())
          custom = store.custom
          choose(text.trim())
        }) { Text(stringResource(R.string.adhkar_ok)) }
      },
      dismissButton = {
        Row {
          if (phrase in custom) TextButton(onClick = {
            adding = false
            store.removeCustom(phrase)
            custom = store.custom
            choose(PRESETS[0])
          }) { Text(stringResource(R.string.tasbih_remove)) }
          TextButton(onClick = { adding = false }) { Text(stringResource(R.string.adhkar_cancel)) }
        }
      }
    )
  }
  if (confirmReset) {
    AlertDialog(
      onDismissRequest = { confirmReset = false },
      text = { Text(stringResource(R.string.tasbih_reset_confirm)) },
      confirmButton = {
        TextButton(onClick = {
          confirmReset = false
          count = 0
          rounds = 0
          store.count = 0
          store.rounds = 0
        }) { Text(stringResource(R.string.adhkar_reset)) }
      },
      dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.adhkar_cancel)) } }
    )
  }
}

@Composable
private fun Stat(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
