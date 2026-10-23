package com.quran.labs.androidquran.feature.khatmah

import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A plan to read pages [startPage]..[totalPages] over [days] days, starting on [startDay]
 * (a local epoch day, see [localEpochDay]).
 */
data class KhatmahPlan(
  val startDay: Long,
  val days: Int,
  val startPage: Int,
  val totalPages: Int
) {
  val pageCount: Int get() = totalPages - startPage + 1
  val lastDay: Long get() = startDay + days - 1

  fun contains(page: Int) = page in startPage..totalPages
}

data class KhatmahProgress(
  val plan: KhatmahPlan?,
  /** 1-based day of the plan, clamped to the plan's length. */
  val dayNumber: Int,
  /** days left in the plan, today included; 0 once the plan's last day has passed. */
  val daysLeft: Int,
  val pagesRead: Int,
  val todayPages: Int,
  /** pages to read today to stay on plan, worked out from where the day started. */
  val dailyTarget: Int,
  /**
   * negative: pages short of an even pace as it stood when today began; positive: pages past
   * an even pace through the end of today; 0 in between, i.e. on track.
   */
  val aheadBy: Int,
  val streak: Int,
  val bestStreak: Int,
  /** pages read on each of the last [HISTORY_DAYS] days, oldest first, today last. */
  val history: List<Int>
) {
  val isComplete: Boolean get() = plan != null && pagesRead >= plan.pageCount
  val todayDone: Boolean get() = plan != null && todayPages >= dailyTarget

  companion object {
    const val HISTORY_DAYS = 35
  }
}

/** Days since 1970-01-01 in the device's time zone, so a "day" ends at local midnight. */
fun localEpochDay(millis: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Long {
  return Math.floorDiv(millis + zone.getOffset(millis), 86_400_000L)
}

object KhatmahMath {

  /**
   * @param readByDay pages read on each local epoch day (every page counts toward the streak)
   * @param planPagesRead distinct pages of the plan read since it started
   */
  fun progress(
    plan: KhatmahPlan?,
    today: Long,
    readByDay: Map<Long, Set<Int>>,
    planPagesRead: Set<Int>,
    bestStreakSoFar: Int
  ): KhatmahProgress {
    val streak = streak(today, readByDay)
    val history = (KhatmahProgress.HISTORY_DAYS - 1 downTo 0).map { readByDay[today - it]?.size ?: 0 }
    val todayAll = readByDay[today].orEmpty()
    if (plan == null) {
      return KhatmahProgress(
        plan = null, dayNumber = 0, daysLeft = 0, pagesRead = 0,
        todayPages = todayAll.size, dailyTarget = 0, aheadBy = 0,
        streak = streak, bestStreak = maxOf(bestStreakSoFar, streak), history = history
      )
    }

    val read = planPagesRead.count { plan.contains(it) }
    // pages of the plan first read today: today's reading minus what was already done before
    val todayInPlan = todayAll.count { plan.contains(it) }
    val readBeforeToday = (read - todayInPlan).coerceAtLeast(0)
    val dayNumber = (today - plan.startDay + 1).coerceIn(1, plan.days.toLong()).toInt()
    val daysLeft = (plan.lastDay - today + 1).coerceIn(0, plan.days.toLong()).toInt()
    val remainingAtDayStart = (plan.pageCount - readBeforeToday).coerceAtLeast(0)
    val dailyTarget = when {
      remainingAtDayStart == 0 -> 0
      daysLeft <= 0 -> remainingAtDayStart
      else -> ceil(remainingAtDayStart / daysLeft.toDouble()).toInt()
    }
    // behind is judged against where an even pace stood when today began, so a new day
    // doesn't start out behind; ahead only once past where it will be tonight
    val evenPaceBeforeToday = (plan.pageCount * (dayNumber - 1) / plan.days.toDouble()).roundToInt()
    val evenPaceByTonight = (plan.pageCount * dayNumber / plan.days.toDouble()).roundToInt()
    val aheadBy = when {
      read < evenPaceBeforeToday -> read - evenPaceBeforeToday
      read > evenPaceByTonight -> read - evenPaceByTonight
      else -> 0
    }
    return KhatmahProgress(
      plan = plan,
      dayNumber = dayNumber,
      daysLeft = daysLeft,
      pagesRead = read,
      todayPages = todayInPlan,
      dailyTarget = dailyTarget,
      aheadBy = aheadBy,
      streak = streak,
      bestStreak = maxOf(bestStreakSoFar, streak),
      history = history
    )
  }

  /**
   * Consecutive days with at least one page read, ending today. A streak stays alive
   * through today until midnight, so with nothing read yet today it counts from yesterday.
   */
  fun streak(today: Long, readByDay: Map<Long, Set<Int>>): Int {
    var day = if (readByDay[today].isNullOrEmpty()) today - 1 else today
    var count = 0
    while (!readByDay[day].isNullOrEmpty()) {
      count++
      day--
    }
    return count
  }
}
