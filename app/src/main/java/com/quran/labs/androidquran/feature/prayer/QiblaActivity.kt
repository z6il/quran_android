package com.quran.labs.androidquran.feature.prayer

import android.Manifest
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.feature.home.AppIcons
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A compass that turns to the Kaaba. */
class QiblaActivity : AppCompatActivity() {
  private var locating by mutableStateOf(false)
  private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    if (granted.values.any { it }) locate() else Toast.makeText(this, R.string.prayer_location_denied, Toast.LENGTH_LONG).show()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val store = PrayerStore.get(this)
    setContentView(ComposeView(this).apply {
      setContent {
        QuranTheme {
          val settings by store.settings.collectAsState()
          QiblaScreen(
            place = settings.place,
            locating = locating,
            onUseLocation = {
              locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            },
            onBack = { finish() }
          )
        }
      }
    })
  }

  private fun locate() {
    locating = true
    lifecycleScope.launch {
      val location = PrayerLocation.current(this@QiblaActivity)
      if (location == null) {
        Toast.makeText(this@QiblaActivity, R.string.prayer_location_failed, Toast.LENGTH_LONG).show()
      } else {
        val place = PrayerLocation.describe(this@QiblaActivity, location.latitude, location.longitude)
        PrayerStore.get(this@QiblaActivity).update { it.copy(place = place) }
      }
      locating = false
    }
  }
}

private class CompassReading(val heading: Float?, val accuracy: Int, val microTesla: Float)

/** the phone's heading from true north, smoothed; null on phones without a compass */
@Composable
private fun rememberCompass(place: Place?): CompassReading {
  val context = LocalContext.current
  var heading by remember { mutableStateOf<Float?>(null) }
  var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
  var strength by remember { mutableFloatStateOf(0f) }
  DisposableEffect(place) {
    val sensors = context.getSystemService<SensorManager>()
    val rotation = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    val magnetic = sensors?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    val declination = place?.let {
      GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(), 0f, System.currentTimeMillis()).declination
    } ?: 0f
    val matrix = FloatArray(9)
    val orientation = FloatArray(3)
    val listener = object : SensorEventListener {
      override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
          Sensor.TYPE_ROTATION_VECTOR -> {
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            SensorManager.getOrientation(matrix, orientation)
            val raw = (Math.toDegrees(orientation[0].toDouble()).toFloat() + declination + 360f) % 360f
            val previous = heading
            // ease toward the new reading the short way round
            heading = if (previous == null) raw else {
              val delta = ((raw - previous + 540f) % 360f) - 180f
              (previous + delta * 0.2f + 360f) % 360f
            }
          }
          Sensor.TYPE_MAGNETIC_FIELD -> {
            val (x, y, z) = event.values
            strength = kotlin.math.sqrt(x * x + y * y + z * z)
          }
        }
      }

      override fun onAccuracyChanged(sensor: Sensor, value: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) accuracy = value
      }
    }
    rotation?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
    magnetic?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
    onDispose { sensors?.unregisterListener(listener) }
  }
  return CompassReading(heading, accuracy, strength)
}

@Composable
private fun QiblaScreen(place: Place?, locating: Boolean, onUseLocation: () -> Unit, onBack: () -> Unit) {
  Scaffold(topBar = { AppTopBar(stringResource(R.string.qibla_title), onBack) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
    if (place == null) {
      Column(
        Modifier.padding(padding).fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(AppIcons.Kaaba, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.qibla_need_place), textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        if (locating) LinearProgressIndicator(Modifier.fillMaxWidth())
        else Button(onClick = onUseLocation, shape = RoundedCornerShape(50)) {
          Icon(AppIcons.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.prayer_use_location))
        }
      }
      return@Scaffold
    }
    val qibla = remember(place) { PrayerMath.qibla(place.latitude, place.longitude).toFloat() }
    val km = remember(place) { PrayerMath.kmToKaaba(place.latitude, place.longitude) }
    val compass = rememberCompass(place)
    val heading = compass.heading
    // how far to turn: negative is left
    val turn = heading?.let { ((qibla - it + 540f) % 360f) - 180f }
    val facing = turn != null && abs(turn) <= 3f
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(facing) { if (facing) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    Column(
      Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat(stringResource(R.string.qibla_heading), heading?.let { degrees(it) } ?: "—", Modifier.weight(1f))
        Stat(stringResource(R.string.qibla_direction), degrees(qibla), Modifier.weight(1f), highlight = true)
        Stat(stringResource(R.string.qibla_distance), String.format(Locale.getDefault(), "%,d km", km.roundToInt()), Modifier.weight(1f))
      }

      Compass(heading ?: 0f, qibla, facing, Modifier.fillMaxWidth().widthIn(max = 360.dp).aspectRatio(1f))

      val statusColor by animateColorAsState(
        if (facing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, label = "facing"
      )
      Surface(shape = RoundedCornerShape(50), color = statusColor,
        contentColor = if (facing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(
          when {
            heading == null -> stringResource(R.string.qibla_no_compass)
            facing -> stringResource(R.string.qibla_facing)
            turn!! > 0 -> stringResource(R.string.qibla_turn_right, abs(turn).roundToInt())
            else -> stringResource(R.string.qibla_turn_left, abs(turn).roundToInt())
          },
          style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Pin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(place.name, style = MaterialTheme.typography.bodyMedium)
      }

      if (heading != null && compass.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
        SectionCard {
          Text(stringResource(R.string.qibla_calibrate), style = MaterialTheme.typography.titleSmall)
          Text(stringResource(R.string.qibla_calibrate_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      if (compass.microTesla > 0f) {
        Text(
          stringResource(R.string.qibla_field, compass.microTesla.roundToInt()),
          style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private fun degrees(value: Float) = String.format(Locale.getDefault(), "%d°", value.roundToInt() % 360)

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, highlight: Boolean = false) {
  Surface(shape = RoundedCornerShape(16.dp), color = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else cardColor(), modifier = modifier) {
    Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
      Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
        color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
  }
}

/** The dial turns with the phone so north stays north; the needle points at the Kaaba. */
@Composable
private fun Compass(heading: Float, qibla: Float, facing: Boolean, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val measurer = rememberTextMeasurer()
  val needle by animateColorAsState(if (facing) colors.primary else colors.onSurface, label = "needle")
  val face = cardColor()
  val cardinal = listOf("N", "E", "S", "W")
  Box(modifier, contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
      val r = size.minDimension / 2
      val c = center
      drawCircle(face, r)
      drawCircle(colors.primary.copy(alpha = 0.18f), r, style = Stroke(2.dp.toPx()))
      rotate(-heading, c) {
        for (deg in 0 until 360 step 5) {
          val major = deg % 30 == 0
          val len = if (major) r * 0.09f else r * 0.045f
          val a = Math.toRadians(deg.toDouble() - 90)
          val outer = r * 0.94f
          drawLine(
            if (major) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.5f),
            Offset(c.x + (outer * cos(a)).toFloat(), c.y + (outer * sin(a)).toFloat()),
            Offset(c.x + ((outer - len) * cos(a)).toFloat(), c.y + ((outer - len) * sin(a)).toFloat()),
            strokeWidth = if (major) 2.5.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round
          )
        }
        cardinal.forEachIndexed { i, label ->
          labelAt(measurer, label, i * 90f, r * 0.68f, if (i == 0) colors.error else colors.onSurface, 20f, FontWeight.Bold)
        }
        for (deg in 30 until 360 step 30) {
          // the Kaaba sits on the ring there instead
          val nearQibla = abs(((deg - qibla + 540f) % 360f) - 180f) < 12f
          if (deg % 90 != 0 && !nearQibla) labelAt(measurer, "$deg", deg.toFloat(), r * 0.72f, colors.onSurfaceVariant, 11f, FontWeight.Medium)
        }
        // the Kaaba on the ring
        val a = Math.toRadians(qibla.toDouble() - 90)
        val at = Offset(c.x + (r * 0.80f * cos(a)).toFloat(), c.y + (r * 0.80f * sin(a)).toFloat())
        kaaba(at, r * 0.075f, colors.primary)
      }
      // the needle: a long point toward the Kaaba, a short faded tail behind
      rotate(qibla - heading, c) {
        val w = r * 0.075f
        val point = Path().apply {
          moveTo(c.x, c.y - r * 0.6f)
          lineTo(c.x - w, c.y)
          lineTo(c.x + w, c.y)
          close()
        }
        val tail = Path().apply {
          moveTo(c.x, c.y + r * 0.3f)
          lineTo(c.x - w, c.y)
          lineTo(c.x + w, c.y)
          close()
        }
        drawPath(tail, needle.copy(alpha = 0.25f))
        drawPath(point, needle)
      }
      drawCircle(colors.surface, r * 0.05f, c)
      drawCircle(needle, r * 0.05f, c, style = Stroke(2.dp.toPx()))
      // where the phone points
      val marker = Path().apply {
        moveTo(c.x, c.y - r * 0.99f)
        lineTo(c.x - r * 0.045f, c.y - r * 1.07f)
        lineTo(c.x + r * 0.045f, c.y - r * 1.07f)
        close()
      }
      translate(top = r * 0.10f) { drawPath(marker, if (facing) colors.primary else colors.onSurfaceVariant) }
    }
  }
}

private fun DrawScope.labelAt(
  measurer: androidx.compose.ui.text.TextMeasurer, text: String, deg: Float, radius: Float, color: Color, sp: Float, weight: FontWeight
) {
  val layout = measurer.measure(text, TextStyle(fontSize = sp.sp, fontWeight = weight, color = color))
  val a = Math.toRadians(deg.toDouble() - 90)
  val x = center.x + (radius * cos(a)).toFloat()
  val y = center.y + (radius * sin(a)).toFloat()
  rotate(deg, Offset(x, y)) {
    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f))
  }
}

private fun DrawScope.kaaba(at: Offset, half: Float, color: Color) {
  drawRoundRect(
    color, topLeft = Offset(at.x - half, at.y - half), size = androidx.compose.ui.geometry.Size(half * 2, half * 2),
    cornerRadius = androidx.compose.ui.geometry.CornerRadius(half * 0.25f)
  )
  drawRect(Color(0xFFE8C766), topLeft = Offset(at.x - half, at.y - half * 0.45f), size = androidx.compose.ui.geometry.Size(half * 2, half * 0.32f))
}
