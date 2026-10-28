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
  private const val CHANNEL_ID = "prayer_times"
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
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val system = context.getSystemService(NotificationManager::class.java)
      if (system.getNotificationChannel(CHANNEL_ID) == null) {
        system.createNotificationChannel(
          NotificationChannel(CHANNEL_ID, context.getString(R.string.prayer_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(
              RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
              AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build()
            )
          }
        )
      }
    }
    val open = PendingIntent.getActivity(
      context, 0, PrayerActivity.intent(context),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val name = context.getString(prayer.label)
    val title = if (prayer.isPrayer) context.getString(R.string.prayer_alert_title, name) else name
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(title)
      .setContentText(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time)) + " · " +
        PrayerStore.get(context).settings.value.place?.name.orEmpty())
      .setContentIntent(open)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .build()
    try {
      manager.notify(CHANNEL_ID.hashCode(), notification)
    } catch (_: SecurityException) {
      // notification permission was revoked between the check and the post
    }
  }

  const val ACTION_ALERT = "com.quran.labs.androidquran.PRAYER_ALERT"
}

class PrayerAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == PrayerAlarms.ACTION_ALERT) PrayerAlarms.alert(context, intent)
    // after an alert, a reboot, a clock or time zone change, or an update: set the next one
    PrayerAlarms.schedule(context)
  }
}
