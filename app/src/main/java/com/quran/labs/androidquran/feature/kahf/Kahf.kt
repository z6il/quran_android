package com.quran.labs.androidquran.feature.kahf

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.feature.prayer.PrayerMath
import com.quran.labs.androidquran.feature.prayer.PrayerName
import com.quran.labs.androidquran.feature.prayer.PrayerStore
import com.quran.labs.androidquran.ui.PagerActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Surah al-Kahf on Fridays: «من قرأ سورة الكهف في يوم الجمعة أضاء له من النور ما بين الجمعتين».
 * Its time runs from Maghrib on Thursday to Maghrib on Friday. The pages read in that time are
 * counted (the reading screen counts a page once it has stayed open a few seconds), and so are
 * the Fridays it was finished, in a row.
 */
class KahfStore private constructor(private val context: Context) {
  private val prefs = context.getSharedPreferences("kahf", Context.MODE_PRIVATE)
  private val mutable = MutableStateFlow(read())
  val state: StateFlow<KahfState> = mutable.asStateFlow()

  /** the reading screen saw [page]: a page of al-Kahf, in its time, counts */
  fun markRead(page: Int) {
    if (page !in PAGES) return
    val friday = currentFriday(context) ?: return
    val key = keyOf(friday)
    val read = if (prefs.getString(KEY_FRIDAY, null) == key) prefs.getStringSet(KEY_READ, emptySet()).orEmpty() else emptySet()
    if (page.toString() in read) return
    val now = read + page.toString()
    prefs.edit {
      putString(KEY_FRIDAY, key)
      putStringSet(KEY_READ, now)
      if (now.size == PAGES.count() && prefs.getString(KEY_LAST_DONE, null) != key) {
        // a streak runs while each Friday follows the one before
        val last = prefs.getString(KEY_LAST_DONE, null)
        val streak = if (last != null && last == keyOf((friday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) })) {
          prefs.getInt(KEY_STREAK, 0) + 1
        } else 1
        putString(KEY_LAST_DONE, key)
        putInt(KEY_STREAK, streak)
        putInt(KEY_TOTAL, prefs.getInt(KEY_TOTAL, 0) + 1)
      }
    }
    mutable.value = read()
  }

  fun dismiss() {
    val friday = currentFriday(context) ?: return
    prefs.edit { putString(KEY_DISMISSED, keyOf(friday)) }
    mutable.value = read()
  }

  fun setReminders(on: Boolean) {
    prefs.edit { putBoolean(KEY_REMINDERS, on) }
    KahfReminders.schedule(context)
    mutable.value = read()
  }

  fun refresh() {
    mutable.value = read()
  }

  private fun read(): KahfState {
    val friday = currentFriday(context)
    val key = friday?.let(::keyOf)
    val read = if (key != null && prefs.getString(KEY_FRIDAY, null) == key) {
      prefs.getStringSet(KEY_READ, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
    } else emptySet()
    // a streak that missed last Friday is over
    val last = prefs.getString(KEY_LAST_DONE, null)
    val alive = last != null && (last == key || friday != null && last == keyOf((friday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }))
    return KahfState(
      inTime = friday != null,
      eve = friday != null && Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.THURSDAY,
      read = read,
      streak = if (alive) prefs.getInt(KEY_STREAK, 0) else 0,
      total = prefs.getInt(KEY_TOTAL, 0),
      dismissed = key != null && prefs.getString(KEY_DISMISSED, null) == key,
      reminders = prefs.getBoolean(KEY_REMINDERS, true)
    )
  }

  companion object {
    /** al-Kahf in the Madani mushaf */
    val PAGES = 293..304
    private const val KEY_FRIDAY = "friday"
    private const val KEY_READ = "read"
    private const val KEY_LAST_DONE = "last_done"
    private const val KEY_STREAK = "streak"
    private const val KEY_TOTAL = "total"
    private const val KEY_DISMISSED = "dismissed"
    private const val KEY_REMINDERS = "reminders"

    @Volatile
    private var instance: KahfStore? = null

    fun get(context: Context): KahfStore =
      instance ?: synchronized(this) { instance ?: KahfStore(context.applicationContext).also { instance = it } }

    private fun keyOf(day: Calendar) = "%04d-%02d-%02d".format(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH))

    /** Maghrib on [day] where the reader is, or six in the evening when the app doesn't know where that is */
    internal fun maghrib(context: Context, day: Calendar): Calendar {
      val settings = PrayerStore.get(context).settings.value
      val place = settings.place
      val time = place?.let { p -> runCatching { PrayerMath.day(settings, p, day).firstOrNull { it.name == PrayerName.MAGHRIB }?.time }.getOrNull() }
      return (day.clone() as Calendar).apply {
        if (time != null) timeInMillis = time.time else {
          set(Calendar.HOUR_OF_DAY, 18)
          set(Calendar.MINUTE, 0)
          set(Calendar.SECOND, 0)
        }
      }
    }

    /** the Friday whose time it is now (Thursday's Maghrib to Friday's), or null */
    fun currentFriday(context: Context, now: Calendar = Calendar.getInstance()): Calendar? {
      val day = now.get(Calendar.DAY_OF_WEEK)
      return when {
        day == Calendar.THURSDAY && !now.before(maghrib(context, now)) -> (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        day == Calendar.FRIDAY && now.before(maghrib(context, now)) -> now.clone() as Calendar
        else -> null
      }
    }

    /** opens the mushaf at [page] */
    fun open(context: Context, page: Int): Intent = Intent(context, PagerActivity::class.java).putExtra("page", page)
  }
}

data class KahfState(
  val inTime: Boolean,
  /** the time began with Thursday's Maghrib: it's the night of Friday */
  val eve: Boolean,
  val read: Set<Int>,
  val streak: Int,
  val total: Int,
  val dismissed: Boolean,
  val reminders: Boolean
) {
  val done: Boolean get() = read.size >= KahfStore.PAGES.count()
  /** where to carry on: the first page not yet read */
  val next: Int get() = KahfStore.PAGES.firstOrNull { it !in read } ?: KahfStore.PAGES.first
}

/**
 * Two reminders each Friday: in the morning, and an hour before Maghrib when al-Kahf isn't
 * finished. Each is a one-off job for its next time that books the one after it.
 */
object KahfReminders {
  private const val CHANNEL = "kahf"
  private const val WORK = "kahf_reminder"
  private const val MORNING_HOUR = 9

  fun schedule(context: Context, fromWorker: Boolean = false) {
    val work = WorkManager.getInstance(context)
    if (!context.getSharedPreferences("kahf", Context.MODE_PRIVATE).getBoolean("reminders", true)) {
      work.cancelUniqueWork(WORK)
      return
    }
    val request = OneTimeWorkRequestBuilder<KahfReminderWorker>()
      .setInitialDelay(nextReminder(context) - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
      .build()
    work.enqueueUniqueWork(WORK, if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
  }

  /** the next Friday morning, or this Friday's hour before Maghrib */
  internal fun nextReminder(context: Context, now: Calendar = Calendar.getInstance()): Long {
    val candidates = (0..7).flatMap { days ->
      val day = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, days) }
      if (day.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) return@flatMap emptyList()
      val morning = (day.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, MORNING_HOUR)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
      }
      val beforeMaghrib = KahfStore.maghrib(context, day).apply { add(Calendar.HOUR_OF_DAY, -1) }
      listOf(morning.timeInMillis, beforeMaghrib.timeInMillis)
    }
    return candidates.filter { it > now.timeInMillis + 60_000 }.minOrNull() ?: (now.timeInMillis + TimeUnit.DAYS.toMillis(7))
  }

  internal fun notify(context: Context) {
    val state = KahfStore.get(context).also { it.refresh() }.state.value
    if (!state.inTime || state.done) return
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val system = context.getSystemService(NotificationManager::class.java)
      if (system.getNotificationChannel(CHANNEL) == null) {
        system.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.kahf_channel), NotificationManager.IMPORTANCE_DEFAULT))
      }
    }
    val open = PendingIntent.getActivity(
      context, 7302, KahfStore.open(context, state.next), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val left = KahfStore.PAGES.count() - state.read.size
    val text = if (state.read.isEmpty()) context.getString(R.string.kahf_reminder_text)
    else context.resources.getQuantityString(R.plurals.kahf_pages_left, left, left)
    val notification = NotificationCompat.Builder(context, CHANNEL)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(R.string.kahf_title))
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n" + context.getString(R.string.kahf_hadith)))
      .setContentIntent(open)
      .setAutoCancel(true)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .build()
    try {
      manager.notify(CHANNEL.hashCode(), notification)
    } catch (_: SecurityException) {
      // notification permission was revoked between the check and the post
    }
  }
}

class KahfReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
  override fun doWork(): Result {
    KahfReminders.notify(applicationContext)
    KahfReminders.schedule(applicationContext, fromWorker = true)
    return Result.success()
  }
}
