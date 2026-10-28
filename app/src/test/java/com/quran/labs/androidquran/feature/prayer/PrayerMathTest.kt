package com.quran.labs.androidquran.feature.prayer

import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

// Robolectric for the Umm al-Qura calendar, which decides Ramadan's Isha
@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class PrayerMathTest {
  private val riyadh = Place(24.7136, 46.6753, "Riyadh", "SA")

  private fun minuteOfDay(date: java.util.Date, zone: TimeZone): Int =
    Calendar.getInstance(zone).apply { time = date }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

  @Test
  fun riyadhTimesFollowUmmAlQura() {
    val zone = TimeZone.getTimeZone("Asia/Riyadh")
    val settings = PrayerSettings(place = riyadh)
    assertThat(settings.effectiveMethod).isEqualTo(Method.UMM_AL_QURA)
    val day = Calendar.getInstance(zone).apply { set(2026, Calendar.OCTOBER, 28, 12, 0) }
    val times = PrayerMath.day(settings, riyadh, day).associate { it.name to minuteOfDay(it.time, zone) }
    // published Umm al-Qura times for Riyadh, 28 Oct 2026, within two minutes
    assertThat(times.getValue(PrayerName.FAJR)).isIn(4 * 60 + 37..4 * 60 + 41)
    assertThat(times.getValue(PrayerName.DHUHR)).isIn(11 * 60 + 35..11 * 60 + 39)
    assertThat(times.getValue(PrayerName.MAGHRIB)).isIn(17 * 60 + 14..17 * 60 + 18)
    // Isha is 90 minutes after Maghrib outside Ramadan
    assertThat(times.getValue(PrayerName.ISHA) - times.getValue(PrayerName.MAGHRIB)).isEqualTo(90)
  }

  @Test
  fun nextPrayerRollsIntoTomorrow() {
    val zone = TimeZone.getTimeZone("Asia/Riyadh")
    val settings = PrayerSettings(place = riyadh)
    val lateNight = Calendar.getInstance(zone).apply { set(2026, Calendar.OCTOBER, 28, 23, 30) }.time
    val next = PrayerMath.next(settings, riyadh, lateNight, setOf(PrayerName.FAJR, PrayerName.DHUHR))
    assertThat(next!!.name).isEqualTo(PrayerName.FAJR)
    assertThat(next.time.after(lateNight)).isTrue()
  }

  @Test
  fun reminderComesBeforeThePrayer() {
    val zone = TimeZone.getTimeZone("Asia/Riyadh")
    val settings = PrayerSettings(place = riyadh)
    val morning = Calendar.getInstance(zone).apply { set(2026, Calendar.OCTOBER, 28, 11, 0) }.time
    val dhuhr = setOf(PrayerName.DHUHR)
    val reminder = PrayerMath.nextEvent(settings, riyadh, morning, dhuhr, before = 10)!!
    assertThat(reminder.before).isTrue()
    assertThat(reminder.prayer.name).isEqualTo(PrayerName.DHUHR)
    assertThat(reminder.prayer.time.time - reminder.at).isEqualTo(10 * 60_000L)
    // once the reminder has passed, the prayer itself is next
    val after = java.util.Date(reminder.at + 1000)
    val alert = PrayerMath.nextEvent(settings, riyadh, after, dhuhr, before = 10)!!
    assertThat(alert.before).isFalse()
    assertThat(alert.at).isEqualTo(reminder.prayer.time.time)
    // no reminder asked for
    assertThat(PrayerMath.nextEvent(settings, riyadh, morning, dhuhr, before = 0)!!.before).isFalse()
  }

  @Test
  fun qiblaFromRiyadhIsSouthWest() {
    val direction = PrayerMath.qibla(riyadh.latitude, riyadh.longitude)
    assertThat(direction).isWithin(1.0).of(244.0)
    assertThat(PrayerMath.kmToKaaba(riyadh.latitude, riyadh.longitude)).isWithin(15.0).of(790.0)
  }

  @Test
  fun methodByCountry() {
    assertThat(Method.forCountry("eg")).isEqualTo(Method.EGYPTIAN)
    assertThat(Method.forCountry("PK")).isEqualTo(Method.KARACHI)
    assertThat(Method.forCountry(null)).isEqualTo(Method.MUSLIM_WORLD_LEAGUE)
  }
}
