package com.quran.labs.androidquran.feature.prayer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Prayer and qibla settings, kept in their own preferences file. */
class PrayerStore private constructor(private val context: Context) {
  private val prefs = context.getSharedPreferences("prayer", Context.MODE_PRIVATE)
  private val mutable = MutableStateFlow(read())
  val settings: StateFlow<PrayerSettings> = mutable.asStateFlow()

  private fun read(): PrayerSettings {
    val place = if (prefs.contains(LAT)) {
      Place(
        latitude = prefs.getString(LAT, "0")!!.toDouble(),
        longitude = prefs.getString(LNG, "0")!!.toDouble(),
        name = prefs.getString(PLACE, "").orEmpty(),
        country = prefs.getString(COUNTRY, null)
      )
    } else {
      null
    }
    val defaults = PrayerSettings(place = null)
    return PrayerSettings(
      place = place,
      method = enumOr(prefs.getString(METHOD, null), defaults.method),
      asr = enumOr(prefs.getString(ASR, null), defaults.asr),
      highLatitude = enumOr(prefs.getString(HIGH_LAT, null), defaults.highLatitude),
      ramadanIsha = prefs.getBoolean(RAMADAN_ISHA, defaults.ramadanIsha),
      alerts = prefs.getStringSet(ALERTS, null)
        ?.mapNotNull { name -> PrayerName.entries.firstOrNull { it.name == name } }?.toSet()
        ?: defaults.alerts,
      hijriAdjust = prefs.getInt(HIJRI_ADJUST, 0),
      sound = enumOr(prefs.getString(SOUND, null), defaults.sound),
      soundUri = prefs.getString(SOUND_URI, null),
      soundName = prefs.getString(SOUND_NAME, null),
      remindBefore = prefs.getInt(REMIND_BEFORE, 0)
    )
  }

  fun update(change: (PrayerSettings) -> PrayerSettings) {
    val s = change(mutable.value)
    prefs.edit().apply {
      if (s.place != null) {
        putString(LAT, s.place.latitude.toString())
        putString(LNG, s.place.longitude.toString())
        putString(PLACE, s.place.name)
        putString(COUNTRY, s.place.country)
      }
      putString(METHOD, s.method.name)
      putString(ASR, s.asr.name)
      putString(HIGH_LAT, s.highLatitude.name)
      putBoolean(RAMADAN_ISHA, s.ramadanIsha)
      putStringSet(ALERTS, s.alerts.map { it.name }.toSet())
      putInt(HIJRI_ADJUST, s.hijriAdjust)
      putString(SOUND, s.sound.name)
      putString(SOUND_URI, s.soundUri)
      putString(SOUND_NAME, s.soundName)
      putInt(REMIND_BEFORE, s.remindBefore)
    }.apply()
    mutable.value = s
    PrayerAlarms.schedule(context)
  }

  companion object {
    private const val LAT = "lat"
    private const val LNG = "lng"
    private const val PLACE = "place"
    private const val COUNTRY = "country"
    private const val METHOD = "method"
    private const val ASR = "asr"
    private const val HIGH_LAT = "high_latitude"
    private const val RAMADAN_ISHA = "ramadan_isha"
    private const val ALERTS = "alerts"
    private const val HIJRI_ADJUST = "hijri_adjust"
    private const val SOUND = "sound"
    private const val SOUND_URI = "sound_uri"
    private const val SOUND_NAME = "sound_name"
    private const val REMIND_BEFORE = "remind_before"

    @Volatile private var instance: PrayerStore? = null

    fun get(context: Context): PrayerStore =
      instance ?: synchronized(this) { instance ?: PrayerStore(context.applicationContext).also { instance = it } }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
      enumValues<T>().firstOrNull { it.name == name } ?: default
  }
}
