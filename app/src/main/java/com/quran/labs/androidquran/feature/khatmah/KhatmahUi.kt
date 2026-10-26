package com.quran.labs.androidquran.feature.khatmah

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(12.dp)

/** A faint wash of the primary teal over the surface, for cards. */
@Composable
private fun cardColor(): Color =
  MaterialTheme.colorScheme.primary.copy(alpha = 0.07f).compositeOver(MaterialTheme.colorScheme.surface)

/** Streaks burn orange in both themes; grey while there is no streak. */
@Composable
private fun flameColor(active: Boolean): Color {
  val target = when {
    !active -> MaterialTheme.colorScheme.outline
    isSystemInDarkTheme() -> Color(0xFFFFA65C)
    else -> Color(0xFFE8710A)
  }
  return animateColorAsState(target, label = "flame").value
}
private val PresetDays = listOf(7, 10, 15, 30, 60)

private val Flame: ImageVector by lazy {
  ImageVector.Builder("Flame", 24.dp, 24.dp, 24f, 24f).addPath(
    pathData = addPathNodes(
      "M19.48,12.35c-1.57,-4.08 -7.16,-4.3 -5.81,-10.23c0.1,-0.44 -0.37,-0.78 -0.75,-0.55C9.29,3.71 " +
        "6.68,8 8.87,13.62c0.18,0.46 -0.36,0.89 -0.75,0.59c-1.81,-1.37 -2,-3.34 -1.84,-4.75c0.06,-0.52 " +
        "-0.62,-0.77 -0.91,-0.34C4.69,10.16 4,11.84 4,14.37c0.38,5.6 5.11,7.32 6.81,7.54c2.43,0.31 " +
        "5.06,-0.14 6.95,-1.87C19.84,18.11 20.6,15.03 19.48,12.35zM10.2,17.38c1.44,-0.35 2.18,-1.39 " +
        "2.38,-2.31c0.33,-1.43 -0.96,-2.83 -0.09,-5.09c0.33,1.87 3.27,3.04 3.27,5.08C15.84,17.59 " +
        "13.1,19.76 10.2,17.38z"
    ),
    fill = SolidColor(Color.Black)
  ).build()
}

private fun Int.localized(): String = String.format(Locale.getDefault(), "%d", this)

private fun KhatmahProgress.fraction(): Float {
  val plan = plan ?: return 0f
  return (pagesRead / plan.pageCount.toFloat()).coerceIn(0f, 1f)
}

/** A ring that sweeps to [progress] with a spring, drawn from twelve o'clock. */
@Composable
fun ProgressRing(
  progress: Float,
  modifier: Modifier = Modifier,
  strokeWidth: Float = 10f,
  content: @Composable () -> Unit = {}
) {
  val animated by animateFloatAsState(
    targetValue = progress,
    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
    label = "ring"
  )
  val track = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
  val color = MaterialTheme.colorScheme.primary
  Box(modifier, contentAlignment = Alignment.Center) {
    Canvas(Modifier.matchParentSize()) {
      val stroke = strokeWidth.dp.toPx()
      val inset = stroke / 2
      val arcSize = Size(size.width - stroke, size.height - stroke)
      val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
      drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
      if (animated > 0f) {
        drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
      }
    }
    content()
  }
}

@Composable
private fun StreakBadge(streak: Int, modifier: Modifier = Modifier) {
  val tint = flameColor(streak > 0)
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(Flame, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    AnimatedNumber(streak, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    Text(
      stringResource(R.string.khatmah_streak),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

/** Numbers roll up or down when they change. */
@Composable
private fun AnimatedNumber(
  value: Int,
  style: androidx.compose.ui.text.TextStyle,
  modifier: Modifier = Modifier,
  suffix: String = ""
) {
  AnimatedContent(
    targetState = value,
    transitionSpec = {
      val up = targetState > initialState
      (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
        (slideOutVertically { if (up) -it else it } + fadeOut())
    },
    label = "number",
    modifier = modifier
  ) { v -> Text(v.localized() + suffix, style = style) }
}

@Composable
private fun PaceChip(progress: KhatmahProgress) {
  val ahead = progress.aheadBy
  val (text, container, onContainer) = when {
    progress.isPaused -> Triple(
      stringResource(R.string.khatmah_paused),
      MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer
    )
    abs(ahead) <= 1 -> Triple(
      stringResource(R.string.khatmah_on_track),
      MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer
    )
    ahead > 0 -> Triple(
      pluralStringResource(R.plurals.khatmah_ahead, ahead, ahead),
      MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer
    )
    else -> Triple(
      pluralStringResource(R.plurals.khatmah_behind, -ahead, -ahead),
      MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer
    )
  }
  Surface(shape = RoundedCornerShape(6.dp), color = container) {
    Text(
      text,
      color = onContainer,
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
    )
  }
}

@Composable
private fun TodayBar(progress: KhatmahProgress, modifier: Modifier = Modifier) {
  val target = progress.dailyTarget.coerceAtLeast(1)
  val fraction by animateFloatAsState(
    (progress.todayPages / target.toFloat()).coerceIn(0f, 1f),
    animationSpec = tween(700),
    label = "today"
  )
  val color by animateColorAsState(
    if (progress.todayDone) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
    label = "todayColor"
  )
  Box(
    modifier
      .fillMaxWidth()
      .height(6.dp)
      .clip(RoundedCornerShape(3.dp))
      .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
  ) {
    Box(
      Modifier
        .fillMaxWidth(fraction)
        .height(6.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(color)
    )
  }
}

@Composable
private fun TodayLine(progress: KhatmahProgress) {
  if (progress.isPaused) {
    Text(
      stringResource(R.string.khatmah_paused_detail),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    return
  }
  AnimatedContent(progress.todayDone, label = "todayLine") { done ->
    Text(
      if (done) stringResource(R.string.khatmah_today_done)
      else stringResource(R.string.khatmah_today_progress, progress.todayPages, progress.dailyTarget),
      style = MaterialTheme.typography.bodyMedium,
      color = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
      fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal
    )
  }
}

/** The card on the home screen. */
@Composable
fun KhatmahCard(
  progress: KhatmahProgress,
  onOpen: () -> Unit,
  onContinue: () -> Unit,
  onResume: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    onClick = onOpen,
    shape = CardShape,
    colors = CardDefaults.cardColors(
      containerColor = cardColor(),
      contentColor = MaterialTheme.colorScheme.onSurface
    ),
    modifier = modifier.fillMaxWidth()
  ) {
    AnimatedContent(
      targetState = when {
        progress.plan == null -> 0
        progress.isComplete -> 2
        else -> 1
      },
      label = "cardState"
    ) { state ->
      when (state) {
        0 -> Row(
          Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          if (progress.streak > 0) {
            StreakBadge(progress.streak)
          } else {
            Box(
              Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(QuranIcons.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
          }
          Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.khatmah_start_cta), style = MaterialTheme.typography.titleMedium)
            Text(
              stringResource(R.string.khatmah_start_cta_detail),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Icon(QuranIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        2 -> Row(
          Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          ProgressRing(1f, Modifier.size(56.dp), strokeWidth = 6f) {
            Icon(QuranIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          }
          Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.khatmah_complete), style = MaterialTheme.typography.titleMedium)
            Text(
              stringResource(R.string.khatmah_complete_detail),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          StreakBadge(progress.streak)
        }
        else -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressRing(progress.fraction(), Modifier.size(60.dp), strokeWidth = 6f) {
              AnimatedNumber(
                (progress.fraction() * 100).roundToInt(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                suffix = "%"
              )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  stringResource(R.string.khatmah_day_of, progress.dayNumber, progress.plan?.days ?: 0),
                  style = MaterialTheme.typography.titleMedium
                )
                PaceChip(progress)
              }
              // the pace chip already says "Paused"
              if (!progress.isPaused) {
                TodayLine(progress)
                TodayBar(progress)
              }
            }
            StreakBadge(progress.streak)
          }
          if (progress.isPaused) {
            FilledTonalButton(onClick = onResume, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
              Icon(QuranIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(stringResource(R.string.khatmah_resume))
            }
          } else {
            FilledTonalButton(onClick = onContinue, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
              Icon(QuranIcons.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(stringResource(R.string.khatmah_continue))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun HistoryGrid(progress: KhatmahProgress, modifier: Modifier = Modifier) {
  val target = progress.dailyTarget.takeIf { progress.plan != null && it > 0 } ?: 20
  fun level(pages: Int) = when {
    pages <= 0 -> 0
    pages < ceil(target / 2.0) -> 1
    pages < target -> 2
    pages < target * 2 -> 3
    else -> 4
  }
  val primary = MaterialTheme.colorScheme.primary
  val empty = MaterialTheme.colorScheme.surfaceVariant
  val shades = listOf(empty, primary.copy(alpha = 0.3f), primary.copy(alpha = 0.55f), primary.copy(alpha = 0.8f), primary)
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // one row per week, oldest at the top, today in the bottom-right corner
    val weeks = progress.history.chunked(7)
    Column(
      Modifier.width(280.dp).align(Alignment.CenterHorizontally),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      weeks.forEach { week ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          week.forEachIndexed { i, pages ->
            val isToday = week === weeks.last() && i == week.lastIndex
            Box(
              Modifier
                .weight(1f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(5.dp))
                .background(shades[level(pages)])
                .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(5.dp)) else Modifier)
            )
          }
        }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      Spacer(Modifier.weight(1f))
      Text(stringResource(R.string.khatmah_less), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      shades.forEach { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(it)) }
      Text(stringResource(R.string.khatmah_more), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Surface(
    shape = CardShape,
    color = cardColor(),
    contentColor = MaterialTheme.colorScheme.onSurface,
    modifier = modifier.fillMaxWidth()
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text.uppercase(Locale.getDefault()),
    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanSetup(
  totalPages: Int,
  latestPage: Int,
  onStart: (days: Int, startPage: Int) -> Unit
) {
  val haptics = LocalHapticFeedback.current
  var days by rememberSaveable { mutableIntStateOf(30) }
  var custom by rememberSaveable { mutableStateOf(false) }
  var fromCurrent by rememberSaveable { mutableStateOf(false) }
  val startPage = if (fromCurrent && latestPage > 1) latestPage else 1
  val perDay = ceil((totalPages - startPage + 1) / days.toDouble()).toInt()

  SectionCard {
    SectionTitle(stringResource(R.string.khatmah_duration))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      PresetDays.forEach { d ->
        FilterChip(
          selected = !custom && days == d,
          onClick = { custom = false; days = d },
          label = { Text(pluralStringResource(R.plurals.khatmah_days, d, d)) },
          shape = RoundedCornerShape(8.dp)
        )
      }
      FilterChip(
        selected = custom,
        onClick = { custom = true },
        label = { Text(stringResource(R.string.khatmah_custom)) },
        shape = RoundedCornerShape(8.dp)
      )
    }
    AnimatedVisibility(custom, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
      Column {
        Text(pluralStringResource(R.plurals.khatmah_days, days, days), style = MaterialTheme.typography.titleMedium)
        Slider(
          value = days.toFloat(),
          onValueChange = { days = it.roundToInt() },
          valueRange = 3f..365f
        )
      }
    }

    SectionTitle(stringResource(R.string.khatmah_start_from))
    StartOption(stringResource(R.string.khatmah_from_beginning), selected = !fromCurrent) { fromCurrent = false }
    if (latestPage > 1) {
      StartOption(stringResource(R.string.khatmah_from_current, latestPage), selected = fromCurrent) { fromCurrent = true }
    }

    AnimatedContent(perDay, label = "perDay") { n ->
      Text(
        pluralStringResource(R.plurals.khatmah_per_day, n, n),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
      )
    }
    Button(
      onClick = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onStart(days, startPage)
      },
      shape = RoundedCornerShape(8.dp),
      modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.khatmah_start)) }
  }
}

@Composable
private fun StartOption(label: String, selected: Boolean, onSelect: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
      .padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    RadioButton(selected = selected, onClick = null)
    Spacer(Modifier.width(8.dp))
    Text(label, style = MaterialTheme.typography.bodyLarge)
  }
}

/** The khatmah screen: progress and stats when a plan is running, setup otherwise. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KhatmahScreen(
  progress: KhatmahProgress,
  totalPages: Int,
  latestPage: Int,
  onStart: (days: Int, startPage: Int) -> Unit,
  onEnd: () -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onContinue: () -> Unit,
  onBack: () -> Unit
) {
  var setupWhileRunning by rememberSaveable { mutableStateOf(false) }
  var confirmEnd by rememberSaveable { mutableStateOf(false) }
  val running = progress.plan != null && !progress.isComplete
  val showSetup = progress.plan == null || setupWhileRunning

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.khatmah_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(QuranIcons.ArrowBack, contentDescription = stringResource(R.string.khatmah_back))
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    Column(
      Modifier
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      if (progress.plan != null) {
        Hero(progress)
      }

      AnimatedVisibility(running && !showSetup, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        SectionCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              TodayLine(progress)
              if (!progress.isPaused) TodayBar(progress)
            }
          }
          if (progress.isPaused) {
            Button(onClick = onResume, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
              Icon(QuranIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(stringResource(R.string.khatmah_resume))
            }
          } else {
            Button(onClick = onContinue, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
              Icon(QuranIcons.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(stringResource(R.string.khatmah_continue))
            }
          }
        }
      }

      AnimatedVisibility(showSetup, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.khatmah_setup_title), style = MaterialTheme.typography.headlineSmall)
          PlanSetup(totalPages, latestPage) { d, p ->
            setupWhileRunning = false
            onStart(d, p)
          }
        }
      }

      SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          val tint = flameColor(progress.streak > 0)
          Icon(Flame, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
          Column(Modifier.weight(1f)) {
            Text(
              stringResource(R.string.khatmah_streak),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              pluralStringResource(R.plurals.khatmah_streak_days, progress.streak, progress.streak),
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              stringResource(R.string.khatmah_best_streak, progress.bestStreak),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        SectionTitle(stringResource(R.string.khatmah_history))
        HistoryGrid(progress)
      }

      if (progress.plan != null && !showSetup) {
        AnimatedContent(confirmEnd, label = "endPlan") { confirming ->
          if (confirming) {
            SectionCard {
              Text(stringResource(R.string.khatmah_end_confirm), style = MaterialTheme.typography.bodyLarge)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                  onClick = { confirmEnd = false; onEnd() },
                  shape = RoundedCornerShape(8.dp),
                  colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                  )
                ) { Text(stringResource(R.string.khatmah_end_yes)) }
                OutlinedButton(onClick = { confirmEnd = false }, shape = RoundedCornerShape(8.dp)) {
                  Text(stringResource(R.string.khatmah_cancel))
                }
              }
            }
          } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              OutlinedButton(onClick = { setupWhileRunning = true }, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.khatmah_new))
              }
              if (!progress.isComplete && !progress.isPaused) {
                OutlinedButton(onClick = onPause, shape = RoundedCornerShape(8.dp)) {
                  Icon(QuranIcons.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(stringResource(R.string.khatmah_pause))
                }
              }
              if (!progress.isComplete) {
                TextButton(onClick = { confirmEnd = true }) {
                  Text(stringResource(R.string.khatmah_end_plan), color = MaterialTheme.colorScheme.error)
                }
              }
            }
          }
        }
      }

      Text(
        stringResource(R.string.khatmah_how_counts),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
      )
    }
  }
}

@Composable
private fun Hero(progress: KhatmahProgress) {
  val plan = progress.plan ?: return
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    ProgressRing(progress.fraction(), Modifier.size(184.dp), strokeWidth = 14f) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress.isComplete) {
          Icon(QuranIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        } else {
          AnimatedNumber(
            (progress.fraction() * 100).roundToInt(),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            suffix = "%"
          )
        }
        Text(
          stringResource(R.string.khatmah_pages_read, progress.pagesRead, plan.pageCount),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 20.dp)
        )
      }
    }
    if (progress.isComplete) {
      Text(stringResource(R.string.khatmah_complete), style = MaterialTheme.typography.headlineSmall)
      Text(stringResource(R.string.khatmah_complete_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.khatmah_day_of, progress.dayNumber, plan.days), style = MaterialTheme.typography.titleLarge)
        PaceChip(progress)
      }
      val left = plan.pageCount - progress.pagesRead
      Text(
        if (progress.daysLeft <= 0) stringResource(R.string.khatmah_time_up)
        else pluralStringResource(R.plurals.khatmah_pages_left, left, left),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
    }
  }
}
