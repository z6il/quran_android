package com.quran.labs.androidquran.feature.adhkar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.feature.khatmah.ProgressRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val CardShape = RoundedCornerShape(12.dp)

private fun icon(name: String, path: String): ImageVector =
  ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
    .build()

private val Sun by lazy {
  icon(
    "Sun",
    "M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5 5,-2.24 5,-5 -2.24,-5 -5,-5zM2,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1L2,11" +
      "c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM20,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1h-2c-0.55,0 -1,0.45 -1,1s0.45,1 1,1z" +
      "M11,2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1L13,2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zM11,20v2c0,0.55 0.45,1 1,1s1,-0.45 " +
      "1,-1v-2c0,-0.55 -0.45,-1 -1,-1 -0.55,0 -1,0.45 -1,1z"
  )
}
private val Moon by lazy {
  icon(
    "Moon",
    "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 " +
      "-2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4 -0.44,-0.06 -0.9,-0.1 -1.36,-0.1z"
  )
}
private val Bedtime by lazy {
  // a bed, so sleep doesn't look like the evening moon
  icon(
    "Bed",
    "M7,13c1.66,0 3,-1.34 3,-3S8.66,7 7,7s-3,1.34 -3,3 1.34,3 3,3zM19,7h-8v7H3V5H1v15h2v-3h18v3h2v-9c0,-2.21 -1.79,-4 -4,-4z"
  )
}
private val Bell by lazy {
  icon(
    "Bell",
    "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5" +
      "s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"
  )
}
private val Star by lazy {
  icon("Star", "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z")
}
private val StarBorder by lazy {
  icon(
    "StarBorder",
    "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24zM12,15.4l-3.76,2.27 " +
      "1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z"
  )
}

@Composable
private fun cardColor(): Color =
  MaterialTheme.colorScheme.primary.copy(alpha = 0.07f).compositeOver(MaterialTheme.colorScheme.surface)

@Composable
private fun arabicFont(): FontFamily {
  val assets = LocalContext.current.assets
  return remember(assets) { FontFamily(Font("kitab.ttf", assets)) }
}

private fun isArabicUi() = Locale.getDefault().language == "ar"

private fun Int.localized(): String = String.format(Locale.getDefault(), "%d", this)

/** A small tile for one of today's sets, ticked once done. */
@Composable
private fun SessionTile(
  label: String,
  icon: ImageVector,
  done: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val container by animateColorAsState(
    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
    label = "tile"
  )
  val content = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = container,
    contentColor = content,
    modifier = modifier
  ) {
    Column(
      Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      AnimatedContent(done, label = "tileIcon") { isDone ->
        Icon(if (isDone) QuranIcons.Check else icon, contentDescription = null, modifier = Modifier.size(22.dp))
      }
      Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
  }
}

@Composable
private fun TodaySets(
  state: AdhkarState,
  onOpen: (AdhkarSet) -> Unit,
  modifier: Modifier = Modifier
) {
  val morning = AdhkarSet(AdhkarBook.MORNING_EVENING, AdhkarSession.MORNING)
  val evening = AdhkarSet(AdhkarBook.MORNING_EVENING, AdhkarSession.EVENING)
  val sleep = AdhkarSet(AdhkarBook.SLEEP)
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    SessionTile(stringResource(R.string.adhkar_morning_short), Sun, state.isDone(morning), { onOpen(morning) }, Modifier.weight(1f))
    SessionTile(stringResource(R.string.adhkar_evening_short), Moon, state.isDone(evening), { onOpen(evening) }, Modifier.weight(1f))
    SessionTile(stringResource(R.string.adhkar_sleep_short), Bedtime, state.isDone(sleep), { onOpen(sleep) }, Modifier.weight(1f))
  }
}

/** The adhkar card on the home screen. */
@Composable
fun AdhkarCard(
  state: AdhkarState,
  onOpenList: () -> Unit,
  onOpen: (AdhkarSet) -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    onClick = onOpenList,
    shape = CardShape,
    colors = CardDefaults.cardColors(containerColor = cardColor(), contentColor = MaterialTheme.colorScheme.onSurface),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.adhkar_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(QuranIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      TodaySets(state, onOpen)
    }
  }
}

@Composable
private fun NumberBadge(number: Int) {
  Box(
    Modifier
      .size(40.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    contentAlignment = Alignment.Center
  ) {
    Text(number.localized(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
  }
}

@Composable
private fun ChapterRow(
  chapter: AdhkarChapter,
  favorite: Boolean,
  onOpen: () -> Unit,
  onToggleFavorite: () -> Unit
) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onOpen)
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    NumberBadge(chapter.id)
    Column(Modifier.weight(1f)) {
      Text(chapter.title(isArabicUi()), style = MaterialTheme.typography.titleMedium)
      Text(
        pluralStringResource(R.plurals.adhkar_count, chapter.adhkar.size, chapter.adhkar.size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    IconButton(onClick = onToggleFavorite) {
      AnimatedContent(favorite, label = "star") { fav ->
        Icon(
          if (fav) Star else StarBorder,
          contentDescription = stringResource(R.string.adhkar_favorite),
          tint = if (fav) Color(0xFFE8A30A) else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text.uppercase(Locale.getDefault()),
    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
  )
}

/** The list of adhkar chapters, with today's sets at the top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhkarHome(
  state: AdhkarState,
  chapters: List<AdhkarChapter>,
  onOpen: (AdhkarSet) -> Unit,
  onToggleFavorite: (Int) -> Unit,
  onReminders: () -> Unit,
  onBack: () -> Unit
) {
  var query by rememberSaveable { mutableStateOf("") }
  val matches = remember(query, chapters) {
    val q = query.trim()
    if (q.isEmpty()) chapters
    else chapters.filter { it.titleAr.contains(q) || it.titleEn.contains(q, ignoreCase = true) }
  }
  val byId = remember(chapters) { chapters.associateBy { it.id } }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.adhkar_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(QuranIcons.ArrowBack, contentDescription = stringResource(R.string.adhkar_back)) }
        },
        actions = {
          IconButton(onClick = onReminders) { Icon(Bell, contentDescription = stringResource(R.string.adhkar_reminders)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.padding(padding)) {
      item {
        Surface(
          shape = CardShape,
          color = cardColor(),
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
        ) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(stringResource(R.string.adhkar_today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
              Text(
                pluralStringResource(R.plurals.khatmah_streak_days, state.streak, state.streak),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
              )
            }
            TodaySets(state, onOpen)
          }
        }
      }
      item {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          singleLine = true,
          leadingIcon = { Icon(QuranIcons.Search, contentDescription = null) },
          placeholder = { Text(stringResource(R.string.adhkar_search)) },
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
        )
      }
      if (query.isBlank()) {
        val favorites = state.favorites.mapNotNull { byId[it] }.sortedBy { it.id }
        if (favorites.isNotEmpty()) {
          item { SectionLabel(stringResource(R.string.adhkar_favorites)) }
          items(favorites, key = { "f" + it.id }) { c ->
            ChapterRow(c, true, { onOpen(AdhkarSet(c.id)) }, { onToggleFavorite(c.id) })
          }
        }
        item { SectionLabel(stringResource(R.string.adhkar_all)) }
      }
      if (matches.isEmpty()) {
        item {
          Text(
            stringResource(R.string.adhkar_no_results),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
          )
        }
      }
      // featured chapters first, then the rest in book order
      val ordered = matches.sortedBy { c -> AdhkarBook.FEATURED.indexOf(c.id).let { if (it < 0) 100 + c.id else it } }
      items(ordered, key = { "c" + it.id }) { c ->
        ChapterRow(c, c.id in state.favorites, { onOpen(AdhkarSet(c.id)) }, { onToggleFavorite(c.id) })
      }
      item {
        Text(
          stringResource(R.string.adhkar_source_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(24.dp)
        )
      }
    }
  }
}

/** One chapter, a dhikr per page, with a counter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhkarReader(
  chapter: AdhkarChapter,
  initialSession: AdhkarSession,
  onFinish: (AdhkarSet) -> Unit,
  onBack: () -> Unit
) {
  val total = chapter.adhkar.size
  val pager = rememberPagerState(pageCount = { total + 1 })
  val scope = rememberCoroutineScope()
  val haptics = LocalHapticFeedback.current
  val counts = rememberSaveable(
    chapter.id,
    saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
  ) { List(total) { 0 }.toMutableStateList() }
  var session by rememberSaveable { mutableStateOf(initialSession) }
  val arabic = arabicFont()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(chapter.title(isArabicUi()), maxLines = 1) },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(QuranIcons.ArrowBack, contentDescription = stringResource(R.string.adhkar_back)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
      if (chapter.id == AdhkarBook.MORNING_EVENING) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
          listOf(AdhkarSession.MORNING to R.string.adhkar_morning_short, AdhkarSession.EVENING to R.string.adhkar_evening_short)
            .forEachIndexed { i, (s, label) ->
              SegmentedButton(
                selected = session == s,
                onClick = { session = s },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
                icon = { Icon(if (s == AdhkarSession.MORNING) Sun else Moon, contentDescription = null, modifier = Modifier.size(18.dp)) }
              ) { Text(stringResource(label)) }
            }
        }
      }
      // how far through the chapter
      val finished = counts.indices.count { counts[it] >= chapter.adhkar[it].count }
      val fraction = finished / total.toFloat()
      Box(
        Modifier
          .padding(horizontal = 16.dp, vertical = 8.dp)
          .fillMaxWidth()
          .height(4.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
      ) {
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
      }

      HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
        if (page == total) {
          DonePage { onFinish(AdhkarSet(chapter.id, if (chapter.id == AdhkarBook.MORNING_EVENING) session else AdhkarSession.NONE)) }
        } else {
          val dhikr = chapter.adhkar[page]
          DhikrPage(
            dhikr = dhikr,
            position = page + 1,
            total = total,
            done = counts[page],
            arabicFont = arabic,
            onTap = {
              if (counts[page] < dhikr.count) {
                counts[page] = counts[page] + 1
                if (counts[page] == dhikr.count) {
                  haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                  scope.launch {
                    delay(350)
                    if (pager.currentPage == page) pager.animateScrollToPage(page + 1)
                  }
                } else {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
              }
            }
          )
        }
      }
    }
  }
}

@Composable
private fun DhikrPage(
  dhikr: Dhikr,
  position: Int,
  total: Int,
  done: Int,
  arabicFont: FontFamily,
  onTap: () -> Unit
) {
  var showReference by rememberSaveable(dhikr.number) { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    Column(
      Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        stringResource(R.string.adhkar_position, position, total),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        dhikr.text,
        style = TextStyle(
          fontFamily = arabicFont,
          fontSize = 24.sp,
          lineHeight = 46.sp,
          textDirection = TextDirection.Rtl,
          textAlign = TextAlign.Center
        ),
        modifier = Modifier.fillMaxWidth()
      )
      if (dhikr.reference.isNotBlank()) {
        Surface(
          onClick = { showReference = !showReference },
          shape = RoundedCornerShape(8.dp),
          color = cardColor(),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.adhkar_reference), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            AnimatedVisibility(showReference, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
              // the references are English (book, volume/page), so keep them left to right
              Text(
                dhikr.reference,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
              )
            }
          }
        }
      }
    }
    Counter(dhikr, done, onTap, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))
  }
}

@Composable
private fun Counter(dhikr: Dhikr, done: Int, onTap: () -> Unit, modifier: Modifier = Modifier) {
  val complete = done >= dhikr.count
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val phase = dhikr.phaseAt(done)
    AnimatedContent(phase?.first?.text, label = "phase") { text ->
      if (text != null && phase != null) {
        Text(
          "$text  ${(phase.second).localized()}/${phase.first.count.localized()}",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary
        )
      } else {
        Spacer(Modifier.height(20.dp))
      }
    }
    ProgressRing(progress = done / dhikr.count.toFloat(), modifier = Modifier.size(132.dp), strokeWidth = 8f) {
      Surface(
        onClick = onTap,
        enabled = !complete,
        shape = CircleShape,
        color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.size(108.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          AnimatedContent(
            targetState = complete to (dhikr.count - done),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "count"
          ) { (isComplete, left) ->
            if (isComplete) {
              Icon(QuranIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(44.dp))
            } else {
              Text(left.localized(), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
    }
    Text(stringResource(R.string.adhkar_tap), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun DonePage(onFinish: () -> Unit) {
  Column(
    Modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    ProgressRing(1f, Modifier.size(120.dp), strokeWidth = 8f) {
      Icon(QuranIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.adhkar_done_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.adhkar_done_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onFinish, shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.adhkar_finish)) }
  }
}

/** Morning and evening reminder switches with their times. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersDialog(
  state: AdhkarState,
  notificationsAllowed: Boolean,
  onChange: (AdhkarSession, AdhkarReminder) -> Unit,
  onDismiss: () -> Unit
) {
  var picking by remember { mutableStateOf<AdhkarSession?>(null) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.adhkar_reminders)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReminderRow(stringResource(R.string.adhkar_morning), Sun, state.morningReminder,
          onToggle = { onChange(AdhkarSession.MORNING, state.morningReminder.copy(enabled = it)) },
          onPickTime = { picking = AdhkarSession.MORNING })
        ReminderRow(stringResource(R.string.adhkar_evening), Moon, state.eveningReminder,
          onToggle = { onChange(AdhkarSession.EVENING, state.eveningReminder.copy(enabled = it)) },
          onPickTime = { picking = AdhkarSession.EVENING })
        if (!notificationsAllowed) {
          Text(stringResource(R.string.adhkar_notifications_off), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.adhkar_close)) } }
  )

  val session = picking
  if (session != null) {
    val current = if (session == AdhkarSession.EVENING) state.eveningReminder else state.morningReminder
    val timeState = rememberTimePickerState(current.minuteOfDay / 60, current.minuteOfDay % 60)
    AlertDialog(
      onDismissRequest = { picking = null },
      text = { TimePicker(state = timeState) },
      confirmButton = {
        TextButton(onClick = {
          onChange(session, AdhkarReminder(enabled = true, minuteOfDay = timeState.hour * 60 + timeState.minute))
          picking = null
        }) { Text(stringResource(R.string.adhkar_ok)) }
      },
      dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.adhkar_cancel)) } }
    )
  }
}

@Composable
private fun ReminderRow(
  title: String,
  icon: ImageVector,
  reminder: AdhkarReminder,
  onToggle: (Boolean) -> Unit,
  onPickTime: () -> Unit
) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      TextButton(onClick = onPickTime, contentPadding = PaddingValues(0.dp)) {
        Text(
          String.format(Locale.getDefault(), "%02d:%02d", reminder.minuteOfDay / 60, reminder.minuteOfDay % 60),
          style = MaterialTheme.typography.titleMedium
        )
      }
    }
    Switch(checked = reminder.enabled, onCheckedChange = onToggle)
  }
}
