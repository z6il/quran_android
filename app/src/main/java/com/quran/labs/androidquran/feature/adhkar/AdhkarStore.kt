package com.quran.labs.androidquran.feature.adhkar

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.feature.khatmah.KhatmahMath
import com.quran.labs.androidquran.feature.khatmah.localEpochDay
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdhkarReminder(val enabled: Boolean, val minuteOfDay: Int)

data class AdhkarState(
  val favorites: Set<Int>,
  /** keys of the sets completed today, see [AdhkarSet.key] */
  val doneToday: Set<String>,
  val streak: Int,
  val bestStreak: Int,
  val morningReminder: AdhkarReminder,
  val eveningReminder: AdhkarReminder
) {
  fun isDone(set: AdhkarSet) = set.key in doneToday
}

/** Favorites, which adhkar sets were completed on which day, and reminder times. */
@SingleIn(AppScope::class)
class AdhkarStore @Inject constructor(
  @ApplicationContext context: Context
) {
  private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
  private val mutableState = MutableStateFlow(compute())

  val state: StateFlow<AdhkarState> = mutableState.asStateFlow()

  fun refresh() {
    mutableState.value = compute()
  }

  @Synchronized
  fun toggleFavorite(chapterId: Int) {
    val favorites = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toMutableSet()
    val id = chapterId.toString()
    if (!favorites.remove(id)) favorites.add(id)
    prefs.edit { putStringSet(KEY_FAVORITES, favorites) }
    refresh()
  }

  @Synchronized
  fun markDone(set: AdhkarSet, today: Long = localEpochDay()) {
    val key = KEY_DAY_PREFIX + today
    val done = prefs.getStringSet(key, emptySet()).orEmpty()
    if (set.key in done) return
    prefs.edit {
      putStringSet(key, done + set.key)
      prefs.all.keys
        .filter { it.startsWith(KEY_DAY_PREFIX) }
        .filter { (it.removePrefix(KEY_DAY_PREFIX).toLongOrNull() ?: 0) < today - LOG_DAYS }
        .forEach { remove(it) }
    }
    val updated = compute(today)
    if (updated.bestStreak > prefs.getInt(KEY_BEST_STREAK, 0)) {
      prefs.edit { putInt(KEY_BEST_STREAK, updated.bestStreak) }
    }
    mutableState.value = updated
  }

  @Synchronized
  fun setReminder(session: AdhkarSession, reminder: AdhkarReminder) {
    val (enabledKey, timeKey) = reminderKeys(session)
    prefs.edit {
      putBoolean(enabledKey, reminder.enabled)
      putInt(timeKey, reminder.minuteOfDay)
    }
    refresh()
  }

  fun reminder(session: AdhkarSession): AdhkarReminder {
    val (enabledKey, timeKey) = reminderKeys(session)
    val default = if (session == AdhkarSession.EVENING) DEFAULT_EVENING else DEFAULT_MORNING
    return AdhkarReminder(prefs.getBoolean(enabledKey, false), prefs.getInt(timeKey, default))
  }

  private fun reminderKeys(session: AdhkarSession) =
    if (session == AdhkarSession.EVENING) KEY_EVENING_ON to KEY_EVENING_TIME else KEY_MORNING_ON to KEY_MORNING_TIME

  private fun compute(today: Long = localEpochDay()): AdhkarState {
    val byDay = prefs.all.keys
      .filter { it.startsWith(KEY_DAY_PREFIX) }
      .mapNotNull { key ->
        val day = key.removePrefix(KEY_DAY_PREFIX).toLongOrNull() ?: return@mapNotNull null
        val sets = prefs.getStringSet(key, emptySet()).orEmpty()
        if (sets.isEmpty()) null else day to sets
      }
      .toMap()
    // a day counts toward the streak when any set was completed on it
    val streak = KhatmahMath.streak(today, byDay.mapValues { (_, sets) -> setOf(sets.size) })
    return AdhkarState(
      favorites = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
      doneToday = byDay[today].orEmpty(),
      streak = streak,
      bestStreak = maxOf(prefs.getInt(KEY_BEST_STREAK, 0), streak),
      morningReminder = reminder(AdhkarSession.MORNING),
      eveningReminder = reminder(AdhkarSession.EVENING)
    )
  }

  companion object {
    private const val PREFS_FILE = "adhkar"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_BEST_STREAK = "best_streak"
    private const val KEY_DAY_PREFIX = "day_"
    private const val KEY_MORNING_ON = "reminder_morning"
    private const val KEY_MORNING_TIME = "reminder_morning_time"
    private const val KEY_EVENING_ON = "reminder_evening"
    private const val KEY_EVENING_TIME = "reminder_evening_time"
    private const val LOG_DAYS = 400
    const val DEFAULT_MORNING = 6 * 60 + 30
    const val DEFAULT_EVENING = 17 * 60
  }
}
