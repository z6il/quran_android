package com.quran.labs.androidquran.feature.hadith

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.feature.research.HadithBook

/** The topics of the index; each gathers the matching books of all six collections. */
enum class HadithTopic(@StringRes val label: Int) {
  FAITH(R.string.hadith_topic_faith),
  KNOWLEDGE(R.string.hadith_topic_knowledge),
  PURIFICATION(R.string.hadith_topic_purification),
  PRAYER(R.string.hadith_topic_prayer),
  FUNERALS(R.string.hadith_topic_funerals),
  ZAKAT(R.string.hadith_topic_zakat),
  FASTING(R.string.hadith_topic_fasting),
  HAJJ(R.string.hadith_topic_hajj),
  FAMILY(R.string.hadith_topic_family),
  TRADE(R.string.hadith_topic_trade),
  INHERITANCE(R.string.hadith_topic_inheritance),
  JUDGMENT(R.string.hadith_topic_judgment),
  JIHAD(R.string.hadith_topic_jihad),
  FOOD(R.string.hadith_topic_food),
  DRESS(R.string.hadith_topic_dress),
  MANNERS(R.string.hadith_topic_manners),
  DHIKR(R.string.hadith_topic_dhikr),
  SEERAH(R.string.hadith_topic_seerah),
  END_TIMES(R.string.hadith_topic_end_times),
  OTHER(R.string.hadith_topic_other);

  companion object {
    /** checked in this order, first match wins; matched against the English book name */
    private val RULES: List<Pair<HadithTopic, List<String>>> = listOf(
      END_TIMES to listOf("day of judgment", "paradise", "hellfire", "tribulation", "fitan", "affliction", "end of the world", "mahdi", "malahim", "portents"),
      FAITH to listOf("kitab al-sunnah", "book of the sunnah", "holding fast"),
      FASTING to listOf("night of qadr", "ramadan", "ramadaan", "fasting", "siyam", "i'tikaf", "itikaf"),
      TRADE to listOf("distribution of water"),
      FOOD to listOf("sacrifice", "adha", "dahaya", "food", "meal", "drink", "ashribah", "hunting", "slaughter", "game (", "eaten"),
      PRAYER to listOf(
        "prayer", "salat", "salah", "adhan", "adhaan", "mosque", "masjid", "friday", "eid", "witr", "rain", "istisqa",
        "eclipse", "prostration", "tahajjud", "forgetfulness", "praying", "shortening", "qiblah", "tatbiq"
      ),
      FUNERALS to listOf("funeral", "jana"),
      PURIFICATION to listOf("purification", "ablution", "wudu", "bathing", "ghusl", "menstrua", "tayammum", "water", "hot baths"),
      ZAKAT to listOf("zakat", "zakah", "charity"),
      HAJJ to listOf("hajj", "pilgrim", "umrah", "'umra", "manasik", "madinah"),
      FAMILY to listOf("marriage", "nikah", "wedlock", "divorce", "talaq", "suckling", "curses", "family", "aqiqa", "treatment of women"),
      INHERITANCE to listOf("will", "wasaya", "inherit", "fara"),
      JIHAD to listOf(
        "jihad", "fighting", "expedition", "maghaazi", "booty", "spoils", "jizyah", "tribute", "government", "rulership",
        "horses", "fay'", "bay'ah"
      ),
      TRADE to listOf(
        "sale", "trade", "transaction", "business", "buyu", "shuf", "pre-emption", "hiring", "wages", "ijarah", "debt",
        "kafalah", "representation", "agriculture", "loan", "partnership", "mortgag", "pawn", "musaqah", "gift", "present",
        "lost", "luqatah", "conditions", "peacemaking", "khusoomaat", "oppression", "tricks", "endowment", "ruqba"
      ),
      JUDGMENT to listOf(
        "judg", "judicial", "aqdiyah", "punishment", "hudud", "hudood", "blood", "diyat", "witness", "oath", "vow",
        "expiation", "coercion", "rulings", "ahkaam", "slave", "manumission", "makaatib", "emancipat", "information",
        "wishes", "apostate", "thief"
      ),
      DRESS to listOf("medicine", "tibb", "patient", "dress", "cloth", "libas", "hair", "tarajjul", "signet", "adornment", "divination", "dream"),
      DHIKR to listOf("invocation", "supplication", "remembrance", "repentance", "heart", "riqaq", "zuhd", "tender", "refuge"),
      MANNERS to listOf(
        "manners", "adab", "etiquette", "behavior", "permission", "greeting", "words", "poetry", "kinship", "virtue,",
        "righteousness", "relatives", "traveling"
      ),
      KNOWLEDGE to listOf("knowledge", "qur", "tafs", "commentary", "dialects", "recitation", "parables"),
      SEERAH to listOf("prophet", "creation", "merits", "companions", "helpers", "virtues"),
      FAITH to listOf("faith", "belief", "revelation", "tawheed", "oneness", "destiny", "qadar", "divine", "hypocrite", "introduction")
    )

    /** a topic "book" of a collection filed by topic, e.g. the Musnad's "topic:PRAYER" */
    const val TOPIC_BOOK = "topic:"

    fun of(book: HadithBook): HadithTopic {
      if (book.number.startsWith(TOPIC_BOOK)) {
        return entries.firstOrNull { it.name == book.number.removePrefix(TOPIC_BOOK) } ?: OTHER
      }
      val name = book.nameEn.lowercase()
      return RULES.firstOrNull { (_, words) -> words.any { it in name } }?.first ?: OTHER
    }
  }
}

/**
 * The Musnad and Darimi come without books in their edition, so each hadith is filed under the
 * topic of the closest hadith in the six books (worked out ahead, see hadith_topics.tsv).
 * Approximate, and said so where it's shown.
 */
object HadithTopicMap {
  val COLLECTIONS = setOf("darimi")

  @Volatile private var map: Map<String, Map<HadithTopic, List<String>>>? = null

  /** topic to hadith numbers, in the collection's order */
  fun of(context: android.content.Context, collection: String): Map<HadithTopic, List<String>> {
    val all = map ?: synchronized(this) { map ?: load(context).also { map = it } }
    return all[collection].orEmpty()
  }

  private fun load(context: android.content.Context): Map<String, Map<HadithTopic, List<String>>> =
    runCatching {
      val out = HashMap<String, MutableMap<HadithTopic, MutableList<String>>>()
      context.assets.open("hadith_topics.tsv").bufferedReader().useLines { lines ->
        lines.forEach { line ->
          val p = line.split('\t')
          if (p.size == 3) {
            val topic = HadithTopic.entries.firstOrNull { it.name == p[2] } ?: HadithTopic.OTHER
            out.getOrPut(p[0]) { HashMap() }.getOrPut(topic) { ArrayList() }.add(p[1])
          }
        }
      }
      out
    }.getOrDefault(emptyMap())
}

/** How strong a hadith is, read from its grading text (English or Arabic). */
enum class GradeLevel(@StringRes val label: Int, val color: Color) {
  SAHIH(R.string.hadith_grade_sahih, Color(0xFF2E7D32)),
  HASAN(R.string.hadith_grade_hasan, Color(0xFF7CB342)),
  DAIF(R.string.hadith_grade_daif, Color(0xFFEF6C00)),
  VERY_WEAK(R.string.hadith_grade_very_weak, Color(0xFFD32F2F)),
  MAWDU(R.string.hadith_grade_mawdu, Color(0xFF8E0000)),
  UNGRADED(R.string.hadith_grade_none, Color(0xFF757575));

  companion object {
    private val DAIF_WORDS = Regex("da.{0,2}if|daif|ضعيف")

    /** [collection]: Bukhari and Muslim are sahih throughout, graded or not */
    fun of(grade: String, collection: String): GradeLevel {
      val g = grade.lowercase()
      return when {
        "maudu" in g || "mawdu" in g || "موضوع" in g -> MAWDU
        "munkar" in g || "shadh" in g || "jiddan" in g || "منكر" in g || "شاذ" in g -> VERY_WEAK
        DAIF_WORDS.containsMatchIn(g) -> DAIF
        "hasan sahih" in g || "حسن صحيح" in g || "متفق عليه" in g -> SAHIH
        // "qawi" (a strong chain): counted with hasan, to be cautious
        "hasan" in g || "حسن" in g || "qawi" in g || "قوي" in g -> HASAN
        "sahih" in g || "صحيح" in g -> SAHIH
        collection == "bukhari" || collection == "muslim" -> SAHIH
        else -> UNGRADED
      }
    }
  }
}

/** who graded it, from "Sahih (Al-Albani)"; null where none is named */
fun graderOf(grade: String): String? {
  val inside = grade.substringAfterLast('(', "").substringBefore(')').trim()
  return inside.takeIf { it.isNotEmpty() && !it.equals("none", ignoreCase = true) }
}

/** the qualifier the grading adds, e.g. "in chain" or "li ghairih", without the grader */
fun gradeDetailOf(grade: String): String = grade.substringBeforeLast('(').trim()

private val HARAKAT = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640\\u200f\\u200e]")
private val RIDA = Regex("(?:عن|سمع|سمعت|أن)\\s+([^،:\"]{2,40}?)\\s+رض[يى]\\s+الله\\s+عن")
private val LINK = Regex("(?:عن|سمع|سمعت)\\s+([^،:\"]{2,40}?)\\s*،")
/** "أن" joins clauses too, so after it only a name that starts like one */
private val ANNA = Regex("أن\\s+((?:أبا|أبي|عبد|ابن|أم)\\s[^،:\"]{2,36}?)\\s*،")
/** "كان أبو ذر يحدث أن رسول الله ..." */
private val TELLS = Regex("كان\\s+(أبو\\s+\\S+|ابن\\s+\\S+|\\S+)\\s+(?:يحدث|يقول)")
/** links that aren't a named companion, and followers who often end a chain */
private val NOT_NARRATOR = setOf(
  "أبيه", "جده", "أمه", "عمه", "رجل", "رجلا", "أبيها", "خالته", "رجل من", "امرأة", "المرأة", "أبي",
  "الزهري", "ابن شهاب", "نافع", "أيوب", "سالم", "قتادة", "عطاء", "عكرمة", "مجاهد", "عروة", "محمد بن المنكدر", "همام بن منبه"
)
/** words that show the match caught a phrase of the text, not a name */
private val NOT_NAME_WORDS = setOf("قال", "حدثنا", "الأرض", "الآخر", "مكة", "المدينة", "الفيل", "يوم", "الصلاة")

/**
 * The companion who narrates it: the last link of the chain before the Prophet ﷺ is named.
 * Worked out from the text, so it's left out where the chain doesn't read clearly.
 */
fun narratorOf(text: String): String? {
  val t = HARAKAT.replace(text, "").replace(Regex("\\s+"), " ")
  val cut = listOf(t.indexOf("رسول الله"), t.indexOf("النبي")).filter { it >= 0 }.minOrNull() ?: t.length
  val before = t.substring(0, cut)
  val match = RIDA.findAll(before).lastOrNull()
    ?: (LINK.findAll(before) + ANNA.findAll(before) + TELLS.findAll(before)).maxByOrNull { it.range.first }
    ?: return null
  // "أبي عمرو هو ذكوان عن عائشة" -> the last link; "عمه عبد الله بن زيد" -> the name
  var name = match.groupValues[1].substringAfterLast(" عن ").trim()
    .removePrefix("عمه ").removePrefix("أبيه ").removePrefix("جده ").trim()
  val words = name.split(' ')
  if (words.any { it in NOT_NAME_WORDS }) return null
  if (name in NOT_NARRATOR || words.size > 6 || "قال" in words || "حدثنا" in words) return null
  // back to the nominative: "أبي هريرة" / "أبا هريرة" -> "أبو هريرة", "أنسا" -> "أنس"
  name = when {
    words.size > 1 && words[1] != "بن" && (words[0] == "أبي" || words[0] == "أبا") -> "أبو " + words.drop(1).joinToString(" ")
    words[0].length > 3 && words[0].endsWith("ا") && !words[0].endsWith("اا") -> (listOf(words[0].dropLast(1)) + words.drop(1)).joinToString(" ")
    else -> name
  }
  return name
}
