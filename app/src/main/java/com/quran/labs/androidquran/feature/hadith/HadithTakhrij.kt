package com.quran.labs.androidquran.feature.hadith

import android.content.Context
import com.quran.labs.androidquran.feature.research.HadithEntry

/** a graded hadith of the six books with the same wording as one that has no grading */
data class Parallel(val collection: String, val number: String, val grade: String) {
  val level: GradeLevel get() = GradeLevel.of(grade, collection)
}

/**
 * What can be said about a hadith whose source gives no grading. Riyad as-Salihin, Bulugh
 * al-Maram and the Forties end each hadith with where it was narrated («رواه مسلم»,
 * «متفق عليه»); for the Musnad, Darimi and the Muwatta, the six books may hold the same
 * words with a grading (a shahid). The grading then belongs to that narration, and the app
 * says so.
 */
object HadithTakhrij {
  private val MARKS = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
  private val START = Regex("(متفق عليه|رواه|أخرجه|اخرجه)")

  /** «رواه مسلم», «متفق عليه», «رواه الترمذي وقال: حديث حسن»: from the end of the text */
  fun of(entry: HadithEntry): String? {
    if (entry.collection !in WITH_TAKHRIJ) return null
    val plain = MARKS.replace(entry.text, "")
    val tail = plain.takeLast(260)
    val match = START.findAll(tail).lastOrNull() ?: return null
    return tail.substring(match.range.first)
      .replace(Regex("[()\\[\\]«»\"'.]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
      .take(90)
      .takeIf { it.length > 3 }
  }

  /** how strong the takhrij makes it: in the two Sahihs is sahih; otherwise what it says */
  fun level(takhrij: String): GradeLevel = when {
    "متفق عليه" in takhrij || "البخاري" in takhrij || "مسلم" in takhrij -> GradeLevel.SAHIH
    else -> GradeLevel.of(takhrij, "")
  }

  private val WITH_TAKHRIJ = setOf("riyadussalihin", "bulugh", "nawawi40", "qudsi40", "shahwaliullah40")

  @Volatile private var parallels: Map<String, List<Parallel>>? = null

  /** the graded parallels found for this hadith, best first; read once from the bundled list */
  fun parallels(context: Context, entry: HadithEntry): List<Parallel> {
    val map = parallels ?: synchronized(this) {
      parallels ?: load(context).also { parallels = it }
    }
    return map[entry.collection + ":" + entry.number].orEmpty()
  }

  private fun load(context: Context): Map<String, List<Parallel>> =
    runCatching {
      context.assets.open("hadith_parallels.tsv").bufferedReader().useLines { lines ->
        lines.mapNotNull { line ->
          val parts = line.split('\t')
          if (parts.size < 3) return@mapNotNull null
          val found = parts.drop(2).mapNotNull { p ->
            val f = p.split('|')
            if (f.size == 3) Parallel(f[0], f[1], f[2]) else null
          }
          (parts[0] + ":" + parts[1]) to found
        }.toMap()
      }
    }.getOrDefault(emptyMap())
}

/** how a hadith's grade is known: from its source, its takhrij, or a parallel's grading */
sealed interface KnownGrade {
  val level: GradeLevel
  data class Source(override val level: GradeLevel) : KnownGrade
  data class Takhrij(override val level: GradeLevel, val text: String) : KnownGrade
  data class Shahid(override val level: GradeLevel, val parallel: Parallel) : KnownGrade
  data object Unknown : KnownGrade { override val level = GradeLevel.UNGRADED }
}

fun knownGrade(context: Context, entry: HadithEntry): KnownGrade {
  val own = GradeLevel.of(entry.grade, entry.collection)
  if (own != GradeLevel.UNGRADED) return KnownGrade.Source(own)
  HadithTakhrij.of(entry)?.let { t ->
    val level = HadithTakhrij.level(t)
    if (level != GradeLevel.UNGRADED) return KnownGrade.Takhrij(level, t)
  }
  HadithTakhrij.parallels(context, entry).firstOrNull { it.level != GradeLevel.UNGRADED }?.let {
    return KnownGrade.Shahid(it.level, it)
  }
  return HadithTakhrij.of(entry)?.let { KnownGrade.Takhrij(GradeLevel.UNGRADED, it) } ?: KnownGrade.Unknown
}
