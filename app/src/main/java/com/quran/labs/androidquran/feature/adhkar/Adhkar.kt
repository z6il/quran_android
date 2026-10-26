package com.quran.labs.androidquran.feature.adhkar

import android.content.Context
import org.json.JSONObject
import java.util.Calendar

/** One step of a dhikr that is said in parts, e.g. subhanallah x33 then alhamdulillah x33. */
data class AdhkarPhase(val text: String, val count: Int)

/**
 * One dhikr: its Arabic text as printed in Hisn al-Muslim, how many times to say it, and
 * the book's source reference (e.g. "Al-Bukhari ... Muslim 4/2083").
 */
data class Dhikr(
  val number: String,
  val text: String,
  val count: Int,
  val reference: String,
  val phases: List<AdhkarPhase> = emptyList()
) {
  /** the phase being said after [done] repetitions, and how far into it */
  fun phaseAt(done: Int): Pair<AdhkarPhase, Int>? {
    var left = done
    for (phase in phases) {
      if (left < phase.count) return phase to left
      left -= phase.count
    }
    return null
  }
}

data class AdhkarChapter(val id: Int, val titleAr: String, val titleEn: String, val adhkar: List<Dhikr>) {
  fun title(arabic: Boolean) = if (arabic) titleAr else titleEn
}

/**
 * A set that can be completed once a day. Morning and evening adhkar share one chapter in
 * the book, so they are told apart by [AdhkarSession.MORNING] / [AdhkarSession.EVENING].
 */
data class AdhkarSet(val chapterId: Int, val session: AdhkarSession = AdhkarSession.NONE) {
  val key: String get() = chapterId.toString() + session.suffix
}

enum class AdhkarSession(val suffix: String) {
  NONE(""), MORNING("m"), EVENING("e");

  companion object {
    /** morning until noon, evening after */
    fun forHour(hour: Int) = if (hour < 12) MORNING else EVENING

    fun now(): AdhkarSession = forHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
  }
}

object AdhkarBook {
  const val MORNING_EVENING = 27
  const val SLEEP = 28
  const val WAKING = 1
  const val AFTER_PRAYER = 25

  /** chapters shown first, in this order */
  val FEATURED = listOf(MORNING_EVENING, SLEEP, WAKING, AFTER_PRAYER)

  @Volatile
  private var cached: List<AdhkarChapter>? = null

  fun chapters(context: Context): List<AdhkarChapter> {
    cached?.let { return it }
    val json = context.assets.open("adhkar.json").bufferedReader().use { it.readText() }
    return parse(json).also { cached = it }
  }

  fun chapter(context: Context, id: Int): AdhkarChapter? = chapters(context).firstOrNull { it.id == id }

  internal fun parse(json: String): List<AdhkarChapter> {
    val chapters = JSONObject(json).getJSONArray("chapters")
    return (0 until chapters.length()).map { i ->
      val c = chapters.getJSONObject(i)
      val items = c.getJSONArray("items")
      AdhkarChapter(
        id = c.getInt("id"),
        titleAr = c.getString("ar"),
        titleEn = c.getString("en"),
        adhkar = (0 until items.length()).map { j ->
          val it = items.getJSONObject(j)
          val phases = it.optJSONArray("phases")
          Dhikr(
            number = it.getString("n"),
            text = it.getString("text"),
            count = it.getInt("count"),
            reference = it.optString("ref"),
            phases = if (phases == null) emptyList() else (0 until phases.length()).map { k ->
              val p = phases.getJSONObject(k)
              AdhkarPhase(p.getString("t"), p.getInt("c"))
            }
          )
        }
      )
    }
  }
}
