package com.quran.labs.androidquran.feature.adhkar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.quran.labs.androidquran.R
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Daily morning / evening adhkar reminders. Each is a one-off WorkManager job set for the
 * next occurrence of its time; when it fires it posts the notification and books the next
 * day's. WorkManager keeps the job across reboots.
 */
object AdhkarReminders {
  private const val CHANNEL_ID = "adhkar_reminders"
  private const val KEY_SESSION = "session"

  /**
   * [fromWorker]: called by the reminder job itself, which must queue the next one behind
   * it rather than replace (and so cancel) the job that is still running.
   */
  fun schedule(context: Context, session: AdhkarSession, reminder: AdhkarReminder, fromWorker: Boolean = false) {
    val work = WorkManager.getInstance(context)
    val name = workName(session)
    if (!reminder.enabled) {
      work.cancelUniqueWork(name)
      return
    }
    val request = OneTimeWorkRequestBuilder<AdhkarReminderWorker>()
      .setInitialDelay(millisUntil(reminder.minuteOfDay), TimeUnit.MILLISECONDS)
      .setInputData(workDataOf(KEY_SESSION to session.name))
      .build()
    val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
    work.enqueueUniqueWork(name, policy, request)
  }

  private fun workName(session: AdhkarSession) = "adhkar_reminder_" + session.name.lowercase()

  /** time until the next [minuteOfDay], today if it's still ahead, otherwise tomorrow */
  internal fun millisUntil(minuteOfDay: Int, now: Calendar = Calendar.getInstance()): Long {
    val next = (now.clone() as Calendar).apply {
      set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
      set(Calendar.MINUTE, minuteOfDay % 60)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
    }
    return next.timeInMillis - now.timeInMillis
  }

  internal fun notify(context: Context, session: AdhkarSession) {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val systemManager = context.getSystemService(NotificationManager::class.java)
      if (systemManager.getNotificationChannel(CHANNEL_ID) == null) {
        systemManager.createNotificationChannel(
          NotificationChannel(
            CHANNEL_ID, context.getString(R.string.adhkar_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT
          )
        )
      }
    }
    val open = PendingIntent.getActivity(
      context,
      session.ordinal,
      AdhkarActivity.intent(context, AdhkarBook.MORNING_EVENING, session),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val title = context.getString(
      if (session == AdhkarSession.EVENING) R.string.adhkar_evening else R.string.adhkar_morning
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(title)
      .setContentText(context.getString(R.string.adhkar_reminder_text))
      .setContentIntent(open)
      .setAutoCancel(true)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .build()
    try {
      manager.notify(CHANNEL_ID.hashCode() + session.ordinal, notification)
    } catch (_: SecurityException) {
      // notification permission was revoked between the check and the post
    }
  }

  internal fun sessionOf(name: String?) =
    AdhkarSession.entries.firstOrNull { it.name == name } ?: AdhkarSession.MORNING
}

class AdhkarReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
  override fun doWork(): Result {
    val session = AdhkarReminders.sessionOf(inputData.getString("session"))
    val reminder = AdhkarStore(applicationContext).reminder(session)
    if (reminder.enabled) {
      AdhkarReminders.notify(applicationContext, session)
      // book tomorrow's
      AdhkarReminders.schedule(applicationContext, session, reminder, fromWorker = true)
    }
    return Result.success()
  }
}
