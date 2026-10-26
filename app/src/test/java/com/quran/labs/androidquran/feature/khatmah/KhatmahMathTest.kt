package com.quran.labs.androidquran.feature.khatmah

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.TimeZone

class KhatmahMathTest {
  private val start = 20_000L
  private val plan = KhatmahPlan(startDay = start, days = 30, startPage = 1, totalPages = 604)

  private fun pages(range: IntRange) = range.toSet()

  @Test
  fun firstDayTargetSpreadsTheWholeQuran() {
    val p = KhatmahMath.progress(plan, start, emptyMap(), emptySet(), 0)
    assertThat(p.dayNumber).isEqualTo(1)
    assertThat(p.daysLeft).isEqualTo(30)
    assertThat(p.dailyTarget).isEqualTo(21) // ceil(604 / 30)
    assertThat(p.todayDone).isFalse()
    assertThat(p.aheadBy).isEqualTo(0) // a new plan is on track, not behind
  }

  @Test
  fun readingTodayDoesNotMoveTodaysTarget() {
    val today = pages(1..10)
    val p = KhatmahMath.progress(plan, start, mapOf(start to today), today, 0)
    assertThat(p.dailyTarget).isEqualTo(21)
    assertThat(p.todayPages).isEqualTo(10)
    assertThat(p.pagesRead).isEqualTo(10)
    assertThat(p.aheadBy).isEqualTo(0) // under today's share is still on track
  }

  @Test
  fun readingPastTodaysShareIsAhead() {
    val today = pages(1..30)
    val p = KhatmahMath.progress(plan, start, mapOf(start to today), today, 0)
    assertThat(p.aheadBy).isEqualTo(10) // even pace by tonight is 20
  }

  @Test
  fun missedDaysSpreadOverTheDaysLeft() {
    // nothing read on days 1-10; day 11 has 20 days left for all 604 pages
    val p = KhatmahMath.progress(plan, start + 10, emptyMap(), emptySet(), 0)
    assertThat(p.dayNumber).isEqualTo(11)
    assertThat(p.daysLeft).isEqualTo(20)
    assertThat(p.dailyTarget).isEqualTo(31) // ceil(604 / 20)
    assertThat(p.aheadBy).isLessThan(0)
  }

  @Test
  fun startingMidQuranOnlyCountsTheRest() {
    val mid = plan.copy(startPage = 302)
    val read = pages(1..50) + pages(302..311)
    val p = KhatmahMath.progress(mid, start, mapOf(start to read), read, 0)
    assertThat(mid.pageCount).isEqualTo(303)
    assertThat(p.pagesRead).isEqualTo(10)
    assertThat(p.todayPages).isEqualTo(10)
  }

  @Test
  fun lastDayPassedAsksForEverythingLeft() {
    val read = pages(1..600)
    val p = KhatmahMath.progress(plan, start + 40, emptyMap(), read, 0)
    assertThat(p.daysLeft).isEqualTo(0)
    assertThat(p.dayNumber).isEqualTo(30)
    assertThat(p.dailyTarget).isEqualTo(4)
  }

  @Test
  fun completePlan() {
    val p = KhatmahMath.progress(plan, start + 5, emptyMap(), pages(1..604), 0)
    assertThat(p.isComplete).isTrue()
    assertThat(p.dailyTarget).isEqualTo(0)
  }

  @Test
  fun streakCountsConsecutiveDaysEndingToday() {
    val log = mapOf(start to setOf(1), start + 1 to setOf(2), start + 2 to setOf(3))
    assertThat(KhatmahMath.streak(start + 2, log)).isEqualTo(3)
  }

  @Test
  fun streakSurvivesUntilTheEndOfToday() {
    val log = mapOf(start to setOf(1), start + 1 to setOf(2))
    assertThat(KhatmahMath.streak(start + 2, log)).isEqualTo(2)
  }

  @Test
  fun streakBreaksAfterAMissedDay() {
    val log = mapOf(start to setOf(1), start + 2 to setOf(3))
    assertThat(KhatmahMath.streak(start + 3, log)).isEqualTo(1)
    assertThat(KhatmahMath.streak(start + 4, log)).isEqualTo(0)
  }

  @Test
  fun bestStreakKeepsTheHigherValue() {
    val log = mapOf(start to setOf(1))
    assertThat(KhatmahMath.progress(null, start, log, emptySet(), 9).bestStreak).isEqualTo(9)
    assertThat(KhatmahMath.progress(null, start, log, emptySet(), 0).bestStreak).isEqualTo(1)
  }

  @Test
  fun historyEndsWithToday() {
    val log = mapOf(start to setOf(1, 2, 3), start - 1 to setOf(4))
    val h = KhatmahMath.progress(null, start, log, emptySet(), 0).history
    assertThat(h).hasSize(KhatmahProgress.HISTORY_DAYS)
    assertThat(h.last()).isEqualTo(3)
    assertThat(h[h.size - 2]).isEqualTo(1)
  }

  @Test
  fun localDayChangesAtLocalMidnight() {
    val riyadh = TimeZone.getTimeZone("Asia/Riyadh") // UTC+3, no DST
    val utc2130 = 1_700_000_000_000L - (1_700_000_000_000L % 86_400_000L) + 21 * 3_600_000L + 30 * 60_000L
    // 21:30 UTC is 00:30 the next day in Riyadh
    assertThat(localEpochDay(utc2130, riyadh)).isEqualTo(localEpochDay(utc2130, TimeZone.getTimeZone("UTC")) + 1)
  }

  @Test
  fun pausedPlanStopsTheClock() {
    val paused = plan.paused(start + 4) // paused on day 5
    val p = KhatmahMath.progress(paused, start + 9, emptyMap(), emptySet(), 0)
    assertThat(p.isPaused).isTrue()
    assertThat(p.dayNumber).isEqualTo(5)
    assertThat(p.daysLeft).isEqualTo(26)
    assertThat(p.dailyTarget).isEqualTo(0)
    assertThat(p.todayDone).isFalse()
  }

  @Test
  fun resumingShiftsThePlanByTheDaysPaused() {
    val resumed = plan.paused(start + 4).resumed(start + 9)
    assertThat(resumed.isPaused).isFalse()
    assertThat(resumed.startDay).isEqualTo(start + 5)
    val p = KhatmahMath.progress(resumed, start + 9, emptyMap(), emptySet(), 0)
    assertThat(p.dayNumber).isEqualTo(5) // picks up on the day it was paused
    assertThat(p.daysLeft).isEqualTo(26)
  }

  @Test
  fun planRoundTripsWithAndWithoutPause() {
    assertThat(KhatmahStore.decodePlan(KhatmahStore.encodePlan(plan))).isEqualTo(plan)
    val paused = plan.paused(start + 3)
    assertThat(KhatmahStore.decodePlan(KhatmahStore.encodePlan(paused))).isEqualTo(paused)
    // plans saved before pause existed have four fields
    assertThat(KhatmahStore.decodePlan("20000,30,1,604")).isEqualTo(plan)
  }

  @Test
  fun pagesRoundTrip() {
    assertThat(KhatmahStore.decodePages(KhatmahStore.encodePages(setOf(3, 1, 604)))).containsExactly(1, 3, 604)
    assertThat(KhatmahStore.decodePages(null)).isEmpty()
  }
}
