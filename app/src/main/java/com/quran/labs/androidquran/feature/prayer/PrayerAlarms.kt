package com.quran.labs.androidquran.feature.prayer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.quran.labs.androidquran.R
import java.text.DateFormat
import java.util.Date

/**
 * One alarm at a time: the next prayer with alerts on. When it rings it posts the
 * notification and sets the one after it. Reboots and clock changes set it again.
 */
object PrayerAlarms {
  private const val OLD_CHANNEL_ID = "prayer_times"
  private const val CHANNEL_PREFIX = "prayer_alert_"
  private val NOTIFICATION_ID = OLD_CHANNEL_ID.hashCode()
  private const val EXTRA_PRAYER = "prayer"
  private const val EXTRA_TIME = "time"
  /** an alarm that comes this late (phone off, say) is skipped rather than shown */
  private const val STALE_MILLIS = 30 * 60 * 1000L

  fun schedule(context: Context) {
    val alarms = context.getSystemService<AlarmManager>() ?: return
    val settings = PrayerStore.get(context).settings.value
    val place = settings.place
    val next = if (place == null || settings.alerts.isEmpty()) null
    else PrayerMath.next(settings, place, Date(), settings.alerts)
    val intent = Intent(context, PrayerAlarmReceiver::class.java).setAction(ACTION_ALERT)
    if (next == null) {
      alarms.cancel(PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
      return
    }
    intent.putExtra(EXTRA_PRAYER, next.name.name).putExtra(EXTRA_TIME, next.time.time)
    val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val at = next.time.time
    // exact when the system lets us; otherwise within a few minutes
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
      alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    } else {
      alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
  }

  fun canAlertOnTime(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true

  internal fun alert(context: Context, intent: Intent) {
    val prayer = PrayerName.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_PRAYER) } ?: return
    val time = intent.getLongExtra(EXTRA_TIME, 0L)
    if (System.currentTimeMillis() - time > STALE_MILLIS) return
    if (prayer !in PrayerStore.get(context).settings.value.alerts) return
    post(context, prayer, time)
  }

  /** an alert now, to hear the chosen sound */
  fun test(context: Context) {
    val next = PrayerName.entries.first { it.isPrayer }
    post(context, next, System.currentTimeMillis())
  }

  /**
   * A channel's sound can't change once it exists, so each sound gets its own channel and the
   * old ones go.
   */
  private fun channelFor(context: Context, settings: PrayerSettings): String {
    val id = CHANNEL_PREFIX + settings.sound.name.lowercase() + "_" + (settings.soundUri?.hashCode() ?: 0).toUInt()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return id
    val system = context.getSystemService(NotificationManager::class.java)
    if (system.getNotificationChannel(id) != null) return id
    system.notificationChannels
      .filter { (it.id.startsWith(CHANNEL_PREFIX) || it.id == OLD_CHANNEL_ID) && it.id != id }
      .forEach { system.deleteNotificationChannel(it.id) }
    val attributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build()
    system.createNotificationChannel(
      NotificationChannel(id, context.getString(R.string.prayer_channel), NotificationManager.IMPORTANCE_HIGH).apply {
        when (settings.sound) {
          AlertSound.SILENT -> {
            setSound(null, null)
            enableVibration(false)
          }
          AlertSound.VIBRATE -> {
            setSound(null, null)
            enableVibration(true)
          }
          else -> {
            setSound(soundUri(context, settings), attributes)
            enableVibration(true)
          }
        }
      }
    )
    return id
  }

  private fun soundUri(context: Context, settings: PrayerSettings): Uri {
    val chosen = settings.soundUri?.takeIf { settings.sound == AlertSound.ADHAN || settings.sound == AlertSound.PHONE }
      ?.let(Uri::parse)
    if (chosen != null && settings.sound == AlertSound.ADHAN) {
      // the adhan file is ours; let the system ui, which plays it, read it
      runCatching { context.grantUriPermission("com.android.systemui", chosen, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    return chosen ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
  }

  private fun post(context: Context, prayer: PrayerName, time: Long) {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    val settings = PrayerStore.get(context).settings.value
    val channel = channelFor(context, settings)
    val open = PendingIntent.getActivity(
      context, 0, PrayerActivity.intent(context),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val stop = PendingIntent.getBroadcast(
      context, 1, Intent(context, PrayerAlarmReceiver::class.java).setAction(ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val name = context.getString(prayer.label)
    val title = if (prayer.isPrayer) context.getString(R.string.prayer_alert_title, name) else name
    val builder = NotificationCompat.Builder(context, channel)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(title)
      .setContentText(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time)) + " · " + settings.place?.name.orEmpty())
      .setContentIntent(open)
      .setDeleteIntent(stop)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
    if (settings.sound == AlertSound.ADHAN) {
      // the adhan plays for minutes; this ends it
      builder.addAction(0, context.getString(R.string.prayer_stop_adhan), stop)
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      when (settings.sound) {
        AlertSound.SILENT -> Unit
        AlertSound.VIBRATE -> builder.setVibrate(longArrayOf(0, 400, 200, 400))
        else -> builder.setSound(soundUri(context, settings))
      }
    }
    try {
      manager.notify(NOTIFICATION_ID, builder.build())
    } catch (_: SecurityException) {
      // notification permission was revoked between the check and the post
    }
  }

  /** cancelling the notification stops its sound */
  internal fun stop(context: Context) {
    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
  }

  const val ACTION_ALERT = "com.quran.labs.androidquran.PRAYER_ALERT"
  const val ACTION_STOP = "com.quran.labs.androidquran.PRAYER_STOP"
}

class PrayerAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      PrayerAlarms.ACTION_ALERT -> PrayerAlarms.alert(context, intent)
      PrayerAlarms.ACTION_STOP -> {
        PrayerAlarms.stop(context)
        return
      }
    }
    // after an alert, a reboot, a clock or time zone change, or an update: set the next one
    PrayerAlarms.schedule(context)
  }
}
