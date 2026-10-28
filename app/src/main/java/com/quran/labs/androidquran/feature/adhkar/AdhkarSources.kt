package com.quran.labs.androidquran.feature.adhkar

/**
 * The book's references are in English ("Al-Bukhari, cf. Al-Asqalani, Fathul-Bari 11/113;
 * Muslim 4/2083"). For Arabic readers this keeps what matters, the collections and their
 * volume/page, in the usual Arabic form: "رواه البخاري مع الفتح ١١/١١٣، ومسلم ٤/٢٠٨٣".
 * The English notes that explain a reward are left out; the virtue card covers those.
 */
object AdhkarSources {
  private val BOOKS = listOf(
    "Al-Bukhari|Bukhari" to "البخاري",
    "Muslim" to "مسلم",
    "Abu Dawud|Abu Dawood|AbuDawud" to "أبو داود",
    "At-\\s?Tirmidhi|Tirmidhi" to "الترمذي",
    "An-Nasa'i|Nasa'i" to "النسائي",
    "Ibn Majah|Ibn Mdjah" to "ابن ماجه",
    "Ahmad|Ahmed" to "أحمد",
    "Al-Hakim" to "الحاكم",
    "Ibn As-Sunni" to "ابن السني",
    "Al-Bayhaqi" to "البيهقي",
    "Ibn Hibban" to "ابن حبان",
    "At-Tabarani" to "الطبراني",
    "Malik" to "مالك",
    "Ad-Darimi" to "الدارمي",
    "Ibn Khuzaymah" to "ابن خزيمة",
    "Al-Baghawi" to "البغوي",
    "Ibn Abi Shaybah" to "ابن أبي شيبة",
    "'Abdur-Razzaq|Abdur-Razzaq" to "عبد الرزاق",
    "Al-Bazzar" to "البزار",
    "Abu Ya'la" to "أبو يعلى",
    "Fathul-Bari|Fathul-Bal" to FATH,
    "'Amalul-Yawm wal-Laylah" to AMAL
  )
  private const val FATH = "فتح الباري"
  private const val AMAL = "عمل اليوم والليلة"

  private val BOOK = Regex(
    "(?<![A-Za-z])(?:" + BOOKS.joinToString("|") { "(${it.first})" } + ")" +
      "\\s*((?:\\d+\\s*/\\s*\\d+(?:\\s*[-,]\\s*\\d+)*)|\\(?(?:Hadith|hadith)\\s*no\\.?\\s*\\d+\\)?|\\(?no\\.\\s*\\d+\\)?)?"
  )
  /** Al-Albani's own books carry the collections' names: "Sahih At-Tirmidhi" is not At-Tirmidhi */
  private val ALBANI_BOOK = Regex("(Sahih(?:ul)?[- ]|Sahihibn|Mukhtasar|Silsila|Irwa|Da'if)")
  private val ALBANI = Regex("Albani|Sahih At|Sahih Ibn|Sahih Abu|Sahihul|Sahih An")
  private val QURAN = Regex("(?:Qur'an|Al-Qur'an)[^0-9]*?(\\d{1,3})\\s*:\\s*(\\d+(?:\\s*-\\s*\\d+)?)")
  private val HADITH_NO = Regex("\\(?(?:Hadith|hadith)\\s*no\\.?\\s*(\\d+)\\)?|\\(?no\\.\\s*(\\d+)\\)?")

  private fun arabicDigits(s: String) = s.map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")

  fun arabic(reference: String, suraName: (Int) -> String): String? {
    val quran = QURAN.findAll(reference).map { m ->
      val sura = m.groupValues[1].toInt()
      val ayat = m.groupValues[2].replace(" ", "")
      "سورة " + (if (sura in 1..114) suraName(sura) else sura.toString()) + "، الآية " + ayat
    }.toList()

    val books = mutableListOf<Pair<String, String?>>()
    for (m in BOOK.findAll(reference)) {
      if (ALBANI_BOOK.containsMatchIn(reference.substring(maxOf(0, m.range.first - 9), m.range.first))) continue
      val index = (1..BOOKS.size).first { m.groups[it] != null } - 1
      val name = BOOKS[index].second
      val number = m.groups[BOOKS.size + 1]?.value?.let { n ->
        HADITH_NO.replace(n) { "رقم " + (it.groupValues[1].ifEmpty { it.groupValues[2] }) }
          .replace(" ", "").replace("رقم", "رقم ").replace(",", "، ")
      }
      val last = books.lastOrNull()
      when {
        // "Al-Bukhari, cf. Al-Asqalani, Fathul-Bari 11/113" -> "البخاري مع الفتح ١١/١١٣"
        name == FATH && last != null && last.first == "البخاري" && last.second == null ->
          books[books.lastIndex] = "البخاري مع الفتح" to number
        name == FATH -> books += "$FATH" to number
        // "An-Nasa'i, 'Amalul-Yawm wal-Laylah" -> "النسائي في عمل اليوم والليلة"
        name == AMAL && last != null && last.first == "النسائي" ->
          books[books.lastIndex] = "النسائي في $AMAL" to (number ?: last.second)
        name == AMAL -> books += "$AMAL" to number
        number == null && books.any { it.first.startsWith(name) } -> Unit
        else -> books += name to number
      }
    }

    val parts = books.mapIndexed { i, (name, number) ->
      (if (i == 0) "" else "و") + name + (number?.let { " $it" } ?: "")
    }
    var out = ""
    if (quran.isNotEmpty()) out += quran.joinToString("، ")
    if (parts.isNotEmpty()) out += (if (out.isEmpty()) "" else "؛ ") + "رواه " + parts.joinToString("، ")
    if (ALBANI.containsMatchIn(reference)) out += (if (out.isEmpty()) "" else "، ") + "وانظر تحقيق الألباني"
    return out.takeIf { it.isNotEmpty() }?.let(::arabicDigits)
  }
}
