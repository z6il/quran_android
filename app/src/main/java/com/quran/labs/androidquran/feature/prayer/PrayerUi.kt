package com.quran.labs.androidquran.feature.prayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.feature.home.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(16.dp)

@Composable
internal fun cardColor(): Color =
  MaterialTheme.colorScheme.primary.copy(alpha = 0.07f).compositeOver(MaterialTheme.colorScheme.surface)

@Composable
internal fun SectionCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable () -> Unit) {
  Surface(shape = CardShape, color = cardColor(), contentColor = MaterialTheme.colorScheme.onSurface, modifier = modifier.fillMaxWidth()) {
    Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
  }
}

@Composable
internal fun SectionTitle(text: String) {
  Text(
    text.uppercase(Locale.getDefault()),
    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
  TopAppBar(
    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    navigationIcon = {
      if (onBack != null) IconButton(onClick = onBack) { Icon(QuranIcons.ArrowBack, contentDescription = stringResource(R.string.research_back)) }
    },
    actions = { actions() },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
  )
}

private fun timeText(date: Date): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(date)

private fun countdown(millis: Long): String {
  val total = (millis / 1000).coerceAtLeast(0)
  return String.format(Locale.getDefault(), "%d:%02d:%02d", total / 3600, total / 60 % 60, total % 60)
}

/** ticks every second, for countdowns */
@Composable
private fun rememberNow(): Long {
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(1000 - System.currentTimeMillis() % 1000)
      now = System.currentTimeMillis()
    }
  }
  return now
}

/** what the screen needs from the activity */
class PrayerActions(
  val onToggleAlert: (PrayerName) -> Unit,
  val onSettings: () -> Unit,
  val onQibla: () -> Unit,
  val onUseLocation: () -> Unit,
  val onChooseCity: () -> Unit,
  val onAllowNotifications: () -> Unit,
  val onAllowExact: () -> Unit
)

/** Today's times, the next prayer's countdown, and the alerts for each. */
@Composable
fun PrayerScreen(
  settings: PrayerSettings,
  locating: Boolean,
  notificationsAllowed: Boolean,
  exactAllowed: Boolean,
  actions: PrayerActions
) {
  var dayOffset by rememberSaveable { mutableIntStateOf(0) }
  Scaffold(
    topBar = {
      AppTopBar(stringResource(R.string.prayer_title), onBack = null) {
        IconButton(onClick = actions.onQibla) { Icon(AppIcons.Kaaba, contentDescription = stringResource(R.string.qibla_title)) }
        IconButton(onClick = actions.onSettings) { Icon(QuranIcons.Settings, contentDescription = stringResource(R.string.prayer_settings)) }
      }
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    val place = settings.place
    if (place == null) {
      NoPlace(locating, actions, Modifier.padding(padding))
      return@Scaffold
    }
    val now = rememberNow()
    val today = remember(now / 60_000, settings, dayOffset) {
      val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
      day to PrayerMath.day(settings, place, day)
    }
    val countable = remember { PrayerName.entries.filter { it.isPrayer || it == PrayerName.SUNRISE }.toSet() }
    val next = remember(now / 60_000, settings) { PrayerMath.next(settings, place, Date(now), countable) }
    val previous = remember(now / 60_000, settings) { PrayerMath.previous(settings, place, Date(now), countable) }

    LazyColumn(
      Modifier.padding(padding),
      contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item {
        Hero(place, next, previous, now, locating, onPlace = actions.onChooseCity)
      }
      item {
        DayRow(
          day = today.first,
          hijriAdjust = settings.hijriAdjust,
          isToday = dayOffset == 0,
          onPrevious = { dayOffset-- },
          onNext = { dayOffset++ },
          onToday = { dayOffset = 0 }
        )
      }
      item {
        SectionCard(padding = PaddingValues(vertical = 6.dp)) {
          Column {
            today.second.forEachIndexed { i, t ->
              if (t.name == PrayerName.MIDNIGHT) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
              }
              PrayerRow(
                time = t,
                isNext = dayOffset == 0 && next?.name == t.name && sameDay(next.time, t.time),
                alert = t.name in settings.alerts,
                onToggle = { actions.onToggleAlert(t.name) }
              )
              if (i == today.second.lastIndex) Spacer(Modifier.height(2.dp))
            }
          }
        }
      }
      if (settings.alerts.isNotEmpty() && !notificationsAllowed) {
        item { Notice(stringResource(R.string.prayer_notifications_off), stringResource(R.string.prayer_allow), actions.onAllowNotifications) }
      } else if (settings.alerts.isNotEmpty() && !exactAllowed) {
        item { Notice(stringResource(R.string.prayer_exact_off), stringResource(R.string.prayer_allow), actions.onAllowExact) }
      }
      item {
        Surface(onClick = actions.onSettings, shape = CardShape, color = cardColor(), modifier = Modifier.fillMaxWidth()) {
          Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(stringResource(settings.effectiveMethod.label), style = MaterialTheme.typography.titleSmall)
              Text(
                stringResource(R.string.prayer_asr_method) + ": " + stringResource(settings.asr.label),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

private fun sameDay(a: Date, b: Date): Boolean = kotlin.math.abs(a.time - b.time) < 60_000

@Composable
private fun Hero(place: Place, next: PrayerTime?, previous: PrayerTime?, now: Long, locating: Boolean, onPlace: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Surface(shape = RoundedCornerShape(24.dp), color = colors.primary, contentColor = colors.onPrimary, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onPlace).background(colors.onPrimary.copy(alpha = 0.14f))
          .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(AppIcons.Pin, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(place.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false))
        Icon(QuranIcons.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
      }
      if (locating) LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.onPrimary, trackColor = colors.onPrimary.copy(alpha = 0.2f))
      if (next != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.prayer_next), style = MaterialTheme.typography.labelLarge, color = colors.onPrimary.copy(alpha = 0.75f))
            Text(stringResource(next.name.label), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(timeText(next.time), style = MaterialTheme.typography.titleLarge, color = colors.onPrimary.copy(alpha = 0.9f))
          }
          val span = previous?.let { next.time.time - it.time.time } ?: 1L
          val fraction = previous?.let { ((now - it.time.time).toFloat() / span).coerceIn(0f, 1f) } ?: 0f
          CountdownRing(fraction, countdown(next.time.time - now))
        }
      }
    }
  }
}

@Composable
private fun CountdownRing(fraction: Float, label: String) {
  val color = MaterialTheme.colorScheme.onPrimary
  Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
      val stroke = 10.dp.toPx()
      val inset = stroke / 2
      val arcSize = Size(size.width - stroke, size.height - stroke)
      drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
      drawArc(color, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
      Text(stringResource(R.string.prayer_remaining), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f))
    }
  }
}

@Composable
private fun DayRow(day: Calendar, hijriAdjust: Int, isToday: Boolean, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = onPrevious) { Icon(AppIcons.ChevronLeft, contentDescription = stringResource(R.string.prayer_previous_day)) }
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        DateFormat.getDateInstance(DateFormat.FULL).format(day.time),
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
      )
      Text(
        PrayerMath.hijriDate(day.time, hijriAdjust),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center
      )
      AnimatedVisibility(!isToday) {
        TextButton(onClick = onToday) { Text(stringResource(R.string.prayer_today)) }
      }
    }
    IconButton(onClick = onNext) { Icon(AppIcons.ChevronRight, contentDescription = stringResource(R.string.prayer_next_day)) }
  }
}

@Composable
private fun PrayerRow(time: PrayerTime, isNext: Boolean, alert: Boolean, onToggle: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  // the next prayer stands out in the app colour
  val background by animateColorAsState(if (isNext) colors.primary else Color.Transparent, label = "next")
  val faded = !time.name.isPrayer
  val textColor = if (isNext) colors.onPrimary else if (faded) colors.onSurfaceVariant else colors.onSurface
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp)).background(background)
      .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      stringResource(time.name.label),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
      color = textColor,
      modifier = Modifier.weight(1f)
    )
    Text(
      timeText(time.time),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = textColor
    )
    IconButton(onClick = onToggle) {
      Icon(
        if (alert) AppIcons.Bell else AppIcons.BellOff,
        contentDescription = stringResource(if (alert) R.string.prayer_alert_on else R.string.prayer_alert_off),
        tint = when {
          isNext -> colors.onPrimary.copy(alpha = if (alert) 1f else 0.6f)
          alert -> colors.primary
          else -> colors.onSurfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier.size(22.dp)
      )
    }
  }
}

@Composable
private fun Notice(text: String, button: String, onClick: () -> Unit) {
  Surface(shape = CardShape, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
      TextButton(onClick = onClick) { Text(button, fontWeight = FontWeight.Bold) }
    }
  }
}

@Composable
private fun NoPlace(locating: Boolean, actions: PrayerActions, modifier: Modifier) {
  Column(
    modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      Modifier.size(88.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(AppIcons.Clock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
    }
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.prayer_where_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
      stringResource(R.string.prayer_where_detail),
      color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))
    if (locating) {
      LinearProgressIndicator(Modifier.fillMaxWidth())
    } else {
      Button(onClick = actions.onUseLocation, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Icon(AppIcons.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.prayer_use_location))
      }
      Spacer(Modifier.height(10.dp))
      OutlinedButton(onClick = actions.onChooseCity, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Icon(QuranIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.prayer_choose_city))
      }
    }
  }
}

/** Search a city by name; the geocoder answers. */
@Composable
fun CityDialog(
  search: suspend (String) -> List<Place>,
  onUseLocation: () -> Unit,
  onPick: (Place) -> Unit,
  onDismiss: () -> Unit
) {
  var query by rememberSaveable { mutableStateOf("") }
  var results by remember { mutableStateOf<List<Place>?>(null) }
  var searching by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val run = {
    if (query.isNotBlank()) scope.launch {
      searching = true
      results = search(query)
      searching = false
    }
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.prayer_choose_city)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          query, { query = it }, singleLine = true,
          placeholder = { Text(stringResource(R.string.prayer_city_hint)) },
          trailingIcon = { IconButton(onClick = { run() }) { Icon(QuranIcons.Search, contentDescription = stringResource(R.string.prayer_search)) } },
          keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { run() }),
          keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        )
        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        val r = results
        if (r != null && r.isEmpty() && !searching) {
          Text(stringResource(R.string.prayer_city_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.heightIn(max = 240.dp)) {
          items(r.orEmpty()) { place ->
            Row(
              Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(place) }.padding(horizontal = 8.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(AppIcons.Pin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(10.dp))
              Text(place.name)
            }
          }
        }
        TextButton(onClick = onUseLocation) {
          Icon(AppIcons.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.prayer_use_location))
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.prayer_close)) } }
  )
}

/** How the times are calculated, and the Hijri date. */
@Composable
fun PrayerSettingsScreen(
  settings: PrayerSettings,
  onChange: ((PrayerSettings) -> PrayerSettings) -> Unit,
  onChooseCity: () -> Unit,
  onBack: () -> Unit
) {
  Scaffold(topBar = { AppTopBar(stringResource(R.string.prayer_settings), onBack) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
    Column(
      Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      SectionTitle(stringResource(R.string.prayer_location))
      Surface(onClick = onChooseCity, shape = CardShape, color = cardColor(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(AppIcons.Pin, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(12.dp))
          Text(settings.place?.name ?: stringResource(R.string.prayer_choose_city), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
          Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }

      SectionTitle(stringResource(R.string.prayer_method))
      SectionCard(padding = PaddingValues(vertical = 6.dp)) {
        Column {
          Method.entries.forEach { m ->
            val detail = if (m == Method.AUTO) {
              stringResource(R.string.prayer_method_auto_now, stringResource(Method.forCountry(settings.place?.country).label))
            } else {
              stringResource(m.detail)
            }
            Choice(stringResource(m.label), detail, settings.method == m) { onChange { it.copy(method = m) } }
          }
        }
      }

      SectionTitle(stringResource(R.string.prayer_asr_method))
      SectionCard(padding = PaddingValues(vertical = 6.dp)) {
        Column { AsrMethod.entries.forEach { a -> Choice(stringResource(a.label), null, settings.asr == a) { onChange { it.copy(asr = a) } } } }
      }

      SectionTitle(stringResource(R.string.prayer_high_lat))
      SectionCard(padding = PaddingValues(vertical = 6.dp)) {
        Column {
          HighLatitude.entries.forEach { h -> Choice(stringResource(h.label), null, settings.highLatitude == h) { onChange { it.copy(highLatitude = h) } } }
        }
      }

      if (settings.effectiveMethod == Method.UMM_AL_QURA) {
        SectionCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(stringResource(R.string.prayer_ramadan_isha), style = MaterialTheme.typography.titleSmall)
              Text(stringResource(R.string.prayer_ramadan_isha_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(settings.ramadanIsha, onCheckedChange = { on -> onChange { it.copy(ramadanIsha = on) } })
          }
        }
      }

      SectionTitle(stringResource(R.string.prayer_hijri))
      SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.prayer_hijri_adjust), style = MaterialTheme.typography.titleSmall)
            Text(PrayerMath.hijriDate(Date(), settings.hijriAdjust), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
          }
          Stepper(settings.hijriAdjust, -2..2) { v -> onChange { it.copy(hijriAdjust = v) } }
        }
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun Choice(title: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    RadioButton(selected, onClick = onClick)
    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
      Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
      if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedButton(onClick = { onChange(value - 1) }, enabled = value > range.first, shape = RoundedCornerShape(50),
      contentPadding = PaddingValues(0.dp), modifier = Modifier.size(36.dp)) { Text("−") }
    Text(
      if (value > 0) "+$value" else "$value",
      style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp)
    )
    OutlinedButton(onClick = { onChange(value + 1) }, enabled = value < range.last, shape = RoundedCornerShape(50),
      contentPadding = PaddingValues(0.dp), modifier = Modifier.size(36.dp)) { Text("+") }
  }
}
