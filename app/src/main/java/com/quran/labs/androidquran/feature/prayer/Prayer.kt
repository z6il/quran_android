package com.quran.labs.androidquran.feature.prayer

import android.icu.text.SimpleDateFormat
import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import androidx.annotation.StringRes
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.HighLatitudeRule
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.Qibla
import com.batoulapps.adhan.SunnahTimes
import com.batoulapps.adhan.data.DateComponents
import com.quran.labs.androidquran.R
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The times of a day, in order. The five prayers alert by default; the others are for reference. */
enum class PrayerName(@StringRes val label: Int, val isPrayer: Boolean) {
  FAJR(R.string.prayer_fajr, true),
  SUNRISE(R.string.prayer_sunrise, false),
  DHUHR(R.string.prayer_dhuhr, true),
  ASR(R.string.prayer_asr, true),
  MAGHRIB(R.string.prayer_maghrib, true),
  ISHA(R.string.prayer_isha, true),
  MIDNIGHT(R.string.prayer_midnight, false),
  LAST_THIRD(R.string.prayer_last_third, false)
}

/** How the times are worked out. [AUTO] picks by the country of the place. */
enum class Method(@StringRes val label: Int, @StringRes val detail: Int, val adhan: CalculationMethod?) {
  AUTO(R.string.prayer_method_auto, R.string.prayer_method_auto_detail, null),
  UMM_AL_QURA(R.string.prayer_method_umm_al_qura, R.string.prayer_method_umm_al_qura_detail, CalculationMethod.UMM_AL_QURA),
  MUSLIM_WORLD_LEAGUE(R.string.prayer_method_mwl, R.string.prayer_method_mwl_detail, CalculationMethod.MUSLIM_WORLD_LEAGUE),
  EGYPTIAN(R.string.prayer_method_egyptian, R.string.prayer_method_egyptian_detail, CalculationMethod.EGYPTIAN),
  KARACHI(R.string.prayer_method_karachi, R.string.prayer_method_karachi_detail, CalculationMethod.KARACHI),
  NORTH_AMERICA(R.string.prayer_method_isna, R.string.prayer_method_isna_detail, CalculationMethod.NORTH_AMERICA),
  MOON_SIGHTING(R.string.prayer_method_moonsighting, R.string.prayer_method_moonsighting_detail, CalculationMethod.MOON_SIGHTING_COMMITTEE),
  DUBAI(R.string.prayer_method_dubai, R.string.prayer_method_dubai_detail, CalculationMethod.DUBAI),
  KUWAIT(R.string.prayer_method_kuwait, R.string.prayer_method_kuwait_detail, CalculationMethod.KUWAIT),
  QATAR(R.string.prayer_method_qatar, R.string.prayer_method_qatar_detail, CalculationMethod.QATAR),
  SINGAPORE(R.string.prayer_method_singapore, R.string.prayer_method_singapore_detail, CalculationMethod.SINGAPORE);

  companion object {
    /** the method most used in a country, by its ISO code */
    fun forCountry(code: String?): Method = when (code?.uppercase(Locale.ROOT)) {
      "SA", "YE", "OM", "BH" -> UMM_AL_QURA
      "AE" -> DUBAI
      "KW" -> KUWAIT
      "QA" -> QATAR
      "EG", "SD", "LY", "SY", "LB", "IQ", "JO", "PS", "DZ", "MA", "TN" -> EGYPTIAN
      "PK", "IN", "BD", "AF" -> KARACHI
      "US", "CA" -> NORTH_AMERICA
      "GB", "IE" -> MOON_SIGHTING
      "SG", "MY", "ID", "BN" -> SINGAPORE
      else -> MUSLIM_WORLD_LEAGUE
    }
  }
}

enum class AsrMethod(@StringRes val label: Int, val madhab: Madhab) {
  STANDARD(R.string.prayer_asr_standard, Madhab.SHAFI),
  HANAFI(R.string.prayer_asr_hanafi, Madhab.HANAFI)
}

enum class HighLatitude(@StringRes val label: Int, val rule: HighLatitudeRule) {
  MIDDLE_OF_THE_NIGHT(R.string.prayer_high_lat_middle, HighLatitudeRule.MIDDLE_OF_THE_NIGHT),
  SEVENTH_OF_THE_NIGHT(R.string.prayer_high_lat_seventh, HighLatitudeRule.SEVENTH_OF_THE_NIGHT),
  TWILIGHT_ANGLE(R.string.prayer_high_lat_angle, HighLatitudeRule.TWILIGHT_ANGLE)
}

/** What a prayer alert sounds like. */
enum class AlertSound(@StringRes val label: Int) {
  /** an adhan recording the reader picked from their files */
  ADHAN(R.string.prayer_sound_adhan),
  /** the phone's notification sound */
  DEFAULT(R.string.prayer_sound_default),
  /** any sound from the phone's sound picker */
  PHONE(R.string.prayer_sound_phone),
  VIBRATE(R.string.prayer_sound_vibrate),
  SILENT(R.string.prayer_sound_silent)
}

data class Place(val latitude: Double, val longitude: Double, val name: String, val country: String?)

data class PrayerSettings(
  val place: Place?,
  val method: Method = Method.AUTO,
  val asr: AsrMethod = AsrMethod.STANDARD,
  val highLatitude: HighLatitude = HighLatitude.MIDDLE_OF_THE_NIGHT,
  /** Umm al-Qura's Isha is 120 minutes after Maghrib in Ramadan instead of 90 */
  val ramadanIsha: Boolean = true,
  val alerts: Set<PrayerName> = PrayerName.entries.filter { it.isPrayer }.toSet(),
  /** days added to the calculated Hijri date, to follow the local sighting */
  val hijriAdjust: Int = 0,
  val sound: AlertSound = AlertSound.DEFAULT,
  /** the sound for [AlertSound.ADHAN] or [AlertSound.PHONE], and a name to show for it */
  val soundUri: String? = null,
  val soundName: String? = null
) {
  /** the method in use, with [Method.AUTO] resolved */
  val effectiveMethod: Method get() = if (method == Method.AUTO) Method.forCountry(place?.country) else method
}

data class PrayerTime(val name: PrayerName, val time: Date)

object PrayerMath {
  val KAABA = Coordinates(21.4225, 39.8262)

  /** the times of the day that holds [day], in the order of [PrayerName] */
  fun day(settings: PrayerSettings, place: Place, day: Calendar): List<PrayerTime> {
    val coordinates = Coordinates(place.latitude, place.longitude)
    val components = DateComponents(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH))
    val method = settings.effectiveMethod
    val params: CalculationParameters = method.adhan!!.parameters.apply {
      madhab = settings.asr.madhab
      highLatitudeRule = settings.highLatitude.rule
      if (method == Method.UMM_AL_QURA && settings.ramadanIsha && hijriMonth(day.time, settings.hijriAdjust) == 9) {
        ishaInterval = 120
      }
    }
    val times = PrayerTimes(coordinates, components, params)
    val sunnah = SunnahTimes(times)
    return listOf(
      PrayerTime(PrayerName.FAJR, times.fajr),
      PrayerTime(PrayerName.SUNRISE, times.sunrise),
      PrayerTime(PrayerName.DHUHR, times.dhuhr),
      PrayerTime(PrayerName.ASR, times.asr),
      PrayerTime(PrayerName.MAGHRIB, times.maghrib),
      PrayerTime(PrayerName.ISHA, times.isha),
      PrayerTime(PrayerName.MIDNIGHT, sunnah.middleOfTheNight),
      PrayerTime(PrayerName.LAST_THIRD, sunnah.lastThirdOfTheNight)
    )
  }

  /** the next time after [now] among [names], looking into tomorrow if needed */
  fun next(settings: PrayerSettings, place: Place, now: Date, names: Set<PrayerName>): PrayerTime? {
    val day = Calendar.getInstance().apply { time = now; add(Calendar.DAY_OF_YEAR, -1) }
    repeat(3) {
      day(settings, place, day).sortedBy { it.time }
        .firstOrNull { it.name in names && it.time.after(now) }
        ?.let { return it }
      day.add(Calendar.DAY_OF_YEAR, 1)
    }
    return null
  }

  /** the latest time at or before [now] among [names], for "since the last prayer" */
  fun previous(settings: PrayerSettings, place: Place, now: Date, names: Set<PrayerName>): PrayerTime? {
    val day = Calendar.getInstance().apply { time = now; add(Calendar.DAY_OF_YEAR, -1) }
    return (0..1).flatMap { i ->
      day(settings, place, (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) })
    }.filter { it.name in names && !it.time.after(now) }.maxByOrNull { it.time }
  }

  /** degrees clockwise from true north */
  fun qibla(latitude: Double, longitude: Double): Double = Qibla(Coordinates(latitude, longitude)).direction

  fun kmToKaaba(latitude: Double, longitude: Double): Double {
    val r = 6371.0
    val p1 = Math.toRadians(latitude)
    val p2 = Math.toRadians(KAABA.latitude)
    val dp = p2 - p1
    val dl = Math.toRadians(KAABA.longitude - longitude)
    val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * r * asin(sqrt(a))
  }

  private fun umAlQura(date: Date, adjust: Int): IslamicCalendar =
    IslamicCalendar().apply {
      calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
      time = date
      add(android.icu.util.Calendar.DAY_OF_MONTH, adjust)
    }

  /** 1 = Muharram ... 9 = Ramadan */
  fun hijriMonth(date: Date, adjust: Int): Int = umAlQura(date, adjust).get(android.icu.util.Calendar.MONTH) + 1

  fun hijriDate(date: Date, adjust: Int, locale: Locale = Locale.getDefault()): String {
    val uLocale = ULocale.forLocale(locale).setKeywordValue("calendar", "islamic-umalqura")
    val format = SimpleDateFormat("d MMMM y", uLocale)
    return format.format(umAlQura(date, adjust).time)
  }
}
