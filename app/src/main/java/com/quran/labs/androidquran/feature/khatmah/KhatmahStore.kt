package com.quran.labs.androidquran.feature.khatmah

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the khatmah plan and a per-day log of pages read, in its own preferences file.
 *
 * Days are local epoch days. The log keeps [LOG_DAYS] days, enough for the streak and the
 * history strip; the plan's own set of read pages is kept separately so a long plan keeps
 * its progress after old days are pruned.
 */
@SingleIn(AppScope::class)
class KhatmahStore @Inject constructor(
  @ApplicationContext context: Context,
  private val quranInfo: QuranInfo
) {
  private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
  private val mutableProgress = MutableStateFlow(compute())

  val progress: StateFlow<KhatmahProgress> = mutableProgress.asStateFlow()

  val totalPages: Int get() = quranInfo.numberOfPages

  /** Recompute, e.g. when a screen comes back after midnight. */
  fun refresh() {
    mutableProgress.value = compute()
  }

  @Synchronized
  fun markRead(page: Int, today: Long = localEpochDay()) {
    if (page !in 1..quranInfo.numberOfPages) return
    val dayKey = KEY_DAY_PREFIX + today
    val dayPages = decodePages(prefs.getString(dayKey, null))
    val plan = plan()
    val planPages = decodePages(prefs.getString(KEY_PLAN_PAGES, null))
    val newForPlan = plan != null && plan.contains(page) && page !in planPages
    if (page in dayPages && !newForPlan) return

    prefs.edit {
      putString(dayKey, encodePages(dayPages + page))
      if (newForPlan) putString(KEY_PLAN_PAGES, encodePages(planPages + page))
      pruneOldDays(today)
    }
    val updated = compute(today)
    if (updated.bestStreak > prefs.getInt(KEY_BEST_STREAK, 0)) {
      prefs.edit { putInt(KEY_BEST_STREAK, updated.bestStreak) }
    }
    mutableProgress.value = updated
  }

  @Synchronized
  fun startPlan(days: Int, startPage: Int, today: Long = localEpochDay()) {
    val total = quranInfo.numberOfPages
    prefs.edit {
      putString(KEY_PLAN, listOf(today, days, startPage.coerceIn(1, total), total).joinToString(","))
      remove(KEY_PLAN_PAGES)
    }
    mutableProgress.value = compute(today)
  }

  @Synchronized
  fun endPlan() {
    prefs.edit {
      remove(KEY_PLAN)
      remove(KEY_PLAN_PAGES)
    }
    mutableProgress.value = compute()
  }

  private fun plan(): KhatmahPlan? {
    val parts = prefs.getString(KEY_PLAN, null)?.split(",") ?: return null
    if (parts.size != 4) return null
    return KhatmahPlan(
      startDay = parts[0].toLongOrNull() ?: return null,
      days = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null,
      startPage = parts[2].toIntOrNull() ?: return null,
      totalPages = parts[3].toIntOrNull() ?: return null
    )
  }

  private fun compute(today: Long = localEpochDay()): KhatmahProgress {
    val readByDay = prefs.all.keys
      .filter { it.startsWith(KEY_DAY_PREFIX) }
      .mapNotNull { key ->
        key.removePrefix(KEY_DAY_PREFIX).toLongOrNull()?.let { it to decodePages(prefs.getString(key, null)) }
      }
      .toMap()
    return KhatmahMath.progress(
      plan = plan(),
      today = today,
      readByDay = readByDay,
      planPagesRead = decodePages(prefs.getString(KEY_PLAN_PAGES, null)),
      bestStreakSoFar = prefs.getInt(KEY_BEST_STREAK, 0)
    )
  }

  private fun SharedPreferences.Editor.pruneOldDays(today: Long) {
    prefs.all.keys
      .filter { it.startsWith(KEY_DAY_PREFIX) }
      .filter { (it.removePrefix(KEY_DAY_PREFIX).toLongOrNull() ?: 0) < today - LOG_DAYS }
      .forEach { remove(it) }
  }

  companion object {
    private const val PREFS_FILE = "khatmah"
    private const val KEY_PLAN = "plan"
    private const val KEY_PLAN_PAGES = "plan_pages"
    private const val KEY_BEST_STREAK = "best_streak"
    private const val KEY_DAY_PREFIX = "day_"
    private const val LOG_DAYS = 400

    internal fun decodePages(value: String?): Set<Int> =
      value?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet().orEmpty()

    internal fun encodePages(pages: Set<Int>): String = pages.sorted().joinToString(",")
  }
}
