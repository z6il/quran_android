package com.quran.labs.androidquran.feature.adhkar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.feature.home.AppIcons
import com.quran.labs.androidquran.feature.khatmah.ProgressRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import com.quran.mobile.common.ui.core.R as UiCoreR

/** How the reader likes adhkar shown and counted, kept across chapters. */
data class AdhkarSettings(
  val fontScale: Float = 1f,
  /** one long list rather than a card at a time */
  val list: Boolean = false,
  val vibrate: Boolean = true,
  /** the volume buttons count, for counting without looking */
  val volumeKeys: Boolean = true,
  val keepScreenOn: Boolean = true
) {
  fun save(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
    putFloat("font_scale", fontScale)
    putBoolean("list", list)
    putBoolean("vibrate", vibrate)
    putBoolean("volume_keys", volumeKeys)
    putBoolean("keep_on", keepScreenOn)
  }

  companion object {
    private const val PREFS = "adhkar_settings"
    val SCALES = listOf(0.8f, 0.9f, 1f, 1.15f, 1.3f, 1.5f, 1.75f)

    fun load(context: Context): AdhkarSettings {
      val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      return AdhkarSettings(
        p.getFloat("font_scale", 1f), p.getBoolean("list", false), p.getBoolean("vibrate", true),
        p.getBoolean("volume_keys", true), p.getBoolean("keep_on", true)
      )
    }
  }
}

/** What the screen around the reader does for it: the volume buttons and keeping the screen on. */
interface AdhkarHost {
  /** while set, a volume button press counts once */
  var onVolume: (() -> Unit)?
  fun keepScreenOn(on: Boolean)
}

@Composable
private fun cardTint(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f).compositeOver(MaterialTheme.colorScheme.surface)

@Composable
private fun kitab(): FontFamily {
  val assets = LocalContext.current.assets
  return remember(assets) { FontFamily(Font("kitab.ttf", assets)) }
}

private fun Int.local(): String = String.format(Locale.getDefault(), "%d", this)

private fun arabicUi() = Locale.getDefault().language == "ar"

/** A chapter of adhkar: a card at a time, or all in a list, each counted by a tap anywhere on it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhkarReader(
  book: AdhkarChapter,
  initialSession: AdhkarSession,
  host: AdhkarHost,
  streak: Int,
  onFinish: (AdhkarSet) -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  var session by rememberSaveable { mutableStateOf(initialSession) }
  val chapter = remember(book, session) { AdhkarSessions.chapter(book, session) }
  var settings by remember { mutableStateOf(AdhkarSettings.load(context)) }
  val update = { s: AdhkarSettings ->
    settings = s
    s.save(context)
  }
  val total = chapter.adhkar.size
  // each session counts on its own
  val counts: SnapshotStateList<Int> = rememberSaveable(
    chapter.id, session,
    saver = androidx.compose.runtime.saveable.listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
  ) { List(total) { 0 }.toMutableStateList() }
  val haptics = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val pager = rememberPagerState(pageCount = { total + 1 })
  val list = rememberLazyListState()
  var menu by remember { mutableStateOf(false) }
  val finished = counts.indices.count { counts[it] >= chapter.adhkar[it].count }
  val allDone = finished == total
  val set = AdhkarSet(chapter.id, if (chapter.id == AdhkarBook.MORNING_EVENING) session else AdhkarSession.NONE)

  /** one more of dhikr [i]; done, on to the next */
  val count = { i: Int ->
    val d = chapter.adhkar[i]
    if (counts[i] < d.count) {
      counts[i] = counts[i] + 1
      val complete = counts[i] == d.count
      if (settings.vibrate) haptics.performHapticFeedback(if (complete) HapticFeedbackType.Confirm else HapticFeedbackType.TextHandleMove)
      if (complete) scope.launch {
        delay(380)
        val next = (i + 1 until total).firstOrNull { counts[it] < chapter.adhkar[it].count } ?: total
        if (settings.list) {
          if (next < total) list.animateScrollToItem(next)
        } else if (pager.currentPage == i) {
          pager.animateScrollToPage(next)
        }
      }
    }
  }
  // the volume buttons count the dhikr in view
  val current by rememberUpdatedState(if (settings.list) list.firstVisibleItemIndex.coerceAtMost(total - 1) else pager.currentPage)
  DisposableEffect(settings.volumeKeys, chapter) {
    host.onVolume = if (settings.volumeKeys) {
      {
        val i = if (settings.list) (0 until total).firstOrNull { counts[it] < chapter.adhkar[it].count } ?: -1 else current
        if (i in 0 until total) count(i)
      }
    } else null
    onDispose { host.onVolume = null }
  }
  DisposableEffect(settings.keepScreenOn) {
    host.keepScreenOn(settings.keepScreenOn)
    onDispose { host.keepScreenOn(false) }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(chapter.title(arabicUi()), maxLines = 1) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(QuranIcons.ArrowBack, contentDescription = stringResource(R.string.adhkar_back)) } },
        actions = {
          IconButton(onClick = { update(settings.copy(list = !settings.list)) }) {
            Icon(if (settings.list) AppIcons.Book else AppIcons.Contents, contentDescription = stringResource(if (settings.list) R.string.adhkar_view_cards else R.string.adhkar_view_list))
          }
          Box {
            IconButton(onClick = { menu = true }) { Text("Aa", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            ReaderMenu(menu, settings, update, onReset = {
              menu = false
              for (i in counts.indices) counts[i] = 0
              scope.launch { if (settings.list) list.scrollToItem(0) else pager.scrollToPage(0) }
            }, onDismiss = { menu = false })
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
      if (book.id == AdhkarBook.MORNING_EVENING) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
          listOf(AdhkarSession.MORNING to R.string.adhkar_morning_short, AdhkarSession.EVENING to R.string.adhkar_evening_short)
            .forEachIndexed { i, (s, label) ->
              SegmentedButton(
                selected = session == s,
                onClick = { session = s },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
                icon = { Icon(if (s == AdhkarSession.MORNING) SunIcon else MoonIcon, contentDescription = null, modifier = Modifier.size(18.dp)) }
              ) { Text(stringResource(label)) }
            }
        }
      }
      Progress(finished, total)
      if (settings.list) {
        LazyColumn(
          state = list,
          contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          itemsIndexed(chapter.adhkar, key = { _, d -> session.name + d.number }) { i, d ->
            ListDhikr(d, i + 1, counts[i], settings.fontScale, onCount = { count(i) })
          }
          item {
            Finished(allDone, streak, onFinish = { onFinish(set) })
          }
        }
      } else {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), key = { session.name + it }) { page ->
          if (page == total) {
            Finished(true, streak, onFinish = { onFinish(set) }, modifier = Modifier.fillMaxSize())
          } else {
            CardDhikr(chapter.adhkar[page], page + 1, total, counts[page], settings.fontScale, onCount = { count(page) })
          }
        }
      }
    }
  }
}

@Composable
private fun Progress(finished: Int, total: Int) {
  val fraction by animateFloatAsState(finished / total.coerceAtLeast(1).toFloat(), label = "progress")
  Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        if (finished == total) stringResource(R.string.adhkar_all_done)
        else pluralStringResource(R.plurals.adhkar_left, total - finished, (total - finished).local()),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
      )
      Text("${finished.local()} / ${total.local()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))) {
      Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
    }
  }
}

@Composable
private fun ReaderMenu(open: Boolean, settings: AdhkarSettings, update: (AdhkarSettings) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
  DropdownMenu(open, onDismissRequest = onDismiss) {
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      val i = AdhkarSettings.SCALES.indexOf(settings.fontScale).let { if (it < 0) 2 else it }
      TextButton(enabled = i > 0, onClick = { update(settings.copy(fontScale = AdhkarSettings.SCALES[i - 1])) }) { Text("A−", fontSize = 15.sp) }
      Text("${(settings.fontScale * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
      TextButton(enabled = i < AdhkarSettings.SCALES.lastIndex, onClick = { update(settings.copy(fontScale = AdhkarSettings.SCALES[i + 1])) }) { Text("A+", fontSize = 20.sp) }
    }
    HorizontalDivider()
    Toggle(R.string.adhkar_vibrate, settings.vibrate) { update(settings.copy(vibrate = it)) }
    Toggle(R.string.adhkar_volume_keys, settings.volumeKeys) { update(settings.copy(volumeKeys = it)) }
    Toggle(R.string.adhkar_keep_on, settings.keepScreenOn) { update(settings.copy(keepScreenOn = it)) }
    HorizontalDivider()
    DropdownMenuItem(text = { Text(stringResource(R.string.adhkar_reset)) }, onClick = onReset)
  }
}

@Composable
private fun Toggle(text: Int, on: Boolean, onChange: (Boolean) -> Unit) {
  DropdownMenuItem(
    text = { Text(stringResource(text)) },
    trailingIcon = { Switch(checked = on, onCheckedChange = null) },
    onClick = { onChange(!on) }
  )
}

/** a dhikr on its own card, the whole card tapped to count, and the counter under it */
@Composable
private fun CardDhikr(dhikr: Dhikr, position: Int, total: Int, done: Int, scale: Float, onCount: () -> Unit) {
  val complete = done >= dhikr.count
  Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = cardTint(),
      modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !complete, onClick = onCount)
    ) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DhikrHead(dhikr, position, total)
        Text(
          dhikr.text,
          style = TextStyle(
            fontFamily = kitab(), fontSize = (24 * scale).sp, lineHeight = (46 * scale).sp,
            textDirection = TextDirection.Rtl, textAlign = TextAlign.Center
          ),
          modifier = Modifier.fillMaxWidth()
        )
        Extras(dhikr)
      }
    }
    Counter(dhikr, done, onCount, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp))
  }
}

/** a dhikr in the list: tap it to count; finished, it steps back */
@Composable
private fun ListDhikr(dhikr: Dhikr, position: Int, done: Int, scale: Float, onCount: () -> Unit) {
  val complete = done >= dhikr.count
  val fade by animateFloatAsState(if (complete) 0.55f else 1f, label = "fade")
  val tint by animateColorAsState(if (complete) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else cardTint(), label = "tint")
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = tint,
    modifier = Modifier.fillMaxWidth().alpha(fade)
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !complete, onClick = onCount)
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      DhikrHead(dhikr, position, null)
      Text(
        dhikr.text,
        style = TextStyle(fontFamily = kitab(), fontSize = (21 * scale).sp, lineHeight = (40 * scale).sp, textDirection = TextDirection.Rtl, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth()
      )
      Extras(dhikr)
      // the count, large enough to tap
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
          onClick = onCount, enabled = !complete, shape = RoundedCornerShape(50),
          color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        ) {
          Row(Modifier.padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (complete) {
              Icon(QuranIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            } else {
              Text(
                "${done.local()} / ${dhikr.count.local()}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }
      }
    }
  }
}

/** where the dhikr is in the chapter, how many times, and copying or sharing it */
@Composable
private fun DhikrHead(dhikr: Dhikr, position: Int, total: Int?) {
  val context = LocalContext.current
  Row(verticalAlignment = Alignment.CenterVertically) {
    Chip(if (total != null) "${position.local()} / ${total.local()}" else position.local())
    if (dhikr.count > 1) {
      Spacer(Modifier.width(6.dp))
      Chip(pluralStringResource(R.plurals.adhkar_times, dhikr.count, dhikr.count.local()), strong = true)
    }
    Spacer(Modifier.weight(1f))
    IconButton(onClick = { copy(context, dhikr) }, modifier = Modifier.size(36.dp)) {
      Icon(AppIcons.Copy, contentDescription = stringResource(R.string.adhkar_copy), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    IconButton(onClick = { share(context, dhikr) }, modifier = Modifier.size(36.dp)) {
      Icon(AppIcons.Share, contentDescription = stringResource(R.string.adhkar_share), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun Chip(text: String, strong: Boolean = false) {
  Text(
    text,
    style = MaterialTheme.typography.labelMedium, fontWeight = if (strong) FontWeight.Bold else null,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = if (strong) 0.16f else 0.08f)).padding(10.dp, 4.dp)
  )
}

/** its virtue, folded until asked for, and its source */
@Composable
private fun Extras(dhikr: Dhikr) {
  val virtue = remember(dhikr.number) { AdhkarVirtues.of(dhikr) }
  var open by rememberSaveable(dhikr.number) { mutableStateOf(false) }
  if (virtue != null) {
    Surface(onClick = { open = !open }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(stringResource(R.string.adhkar_virtue), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
          Icon(QuranIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
          Column(Modifier.padding(top = 6.dp)) { VirtueCard(virtue, kitab()) }
        }
      }
    }
  }
  val suraNames = LocalContext.current.resources.getStringArray(UiCoreR.array.sura_names)
  val source = remember(dhikr.reference) {
    if (arabicUi()) AdhkarSources.arabic(dhikr.reference) { suraNames[it - 1] } else dhikr.reference.takeIf { it.length < 140 }
  }
  if (!source.isNullOrBlank()) {
    Text(
      source,
      style = MaterialTheme.typography.bodySmall.copy(textDirection = if (arabicUi()) TextDirection.Rtl else TextDirection.Ltr),
      color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
  }
}

/** the big counter: what's left, a ring for how far, and the phase for one said in parts */
@Composable
private fun Counter(dhikr: Dhikr, done: Int, onTap: () -> Unit, modifier: Modifier = Modifier) {
  val complete = done >= dhikr.count
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    val phase = dhikr.phaseAt(done)
    if (phase != null) {
      Text(
        "${phase.first.text}  ${phase.second.local()}/${phase.first.count.local()}",
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary
      )
    }
    ProgressRing(progress = done / dhikr.count.toFloat(), modifier = Modifier.size(112.dp), strokeWidth = 8f) {
      Surface(
        onClick = onTap,
        enabled = !complete,
        shape = CircleShape,
        color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.size(92.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          AnimatedContent(complete to (dhikr.count - done), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "count") { (isComplete, left) ->
            if (isComplete) {
              Icon(QuranIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
            } else {
              Text(left.local(), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
    }
    Text(stringResource(R.string.adhkar_tap_card), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** the end of a chapter: a dua for acceptance, the streak, and the way back */
@Composable
private fun Finished(done: Boolean, streak: Int, onFinish: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier.padding(24.dp).fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    ProgressRing(if (done) 1f else 0f, Modifier.size(104.dp), strokeWidth = 8f) {
      Icon(QuranIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
    }
    Spacer(Modifier.height(14.dp))
    Text(stringResource(R.string.adhkar_done_title), style = MaterialTheme.typography.headlineSmall)
    Text(
      stringResource(R.string.adhkar_done_dua),
      style = TextStyle(fontFamily = kitab(), fontSize = 20.sp, lineHeight = 34.sp, textAlign = TextAlign.Center),
      color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)
    )
    if (streak > 0) {
      Text(
        pluralStringResource(R.plurals.khatmah_streak_days, streak, streak),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
      )
    }
    Spacer(Modifier.height(18.dp))
    Button(onClick = onFinish, shape = RoundedCornerShape(50)) { Text(stringResource(R.string.adhkar_finish)) }
  }
}

private fun copy(context: Context, dhikr: Dhikr) {
  context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("dhikr", dhikr.text))
  Toast.makeText(context, R.string.adhkar_copied, Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, dhikr: Dhikr) {
  val text = dhikr.text + "\n\n— " + context.getString(R.string.adhkar_share_source)
  context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

internal val SunIcon: ImageVector get() = AdhkarIcons.Sun
internal val MoonIcon: ImageVector get() = AdhkarIcons.Moon
