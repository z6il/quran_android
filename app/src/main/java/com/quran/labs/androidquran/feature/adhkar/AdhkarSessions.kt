package com.quran.labs.androidquran.feature.adhkar

import android.content.Context
import com.quran.labs.androidquran.feature.prayer.PrayerMath
import com.quran.labs.androidquran.feature.prayer.PrayerName
import com.quran.labs.androidquran.feature.prayer.PrayerStore
import java.util.Calendar

/**
 * Hisn al-Muslim prints the morning and evening adhkar as one chapter: some in their morning
 * wording with a note for the evening («وإذا أمسى قال…», «أصبحنا[أمسينا]»), some for one time
 * only («إذا أصبح», «إذا أمسى»). Each session gets its own list here, every dhikr in that
 * session's own words.
 */
object AdhkarSessions {
  /** said in the morning only */
  private val MORNING_ONLY = setOf("93", "94", "95")

  /** said in the evening only */
  private val EVENING_ONLY = setOf("97")

  /** the wording of the morning and of the evening, where the book gives one with a note */
  private val WORDS: Map<String, Pair<String, String>> = mapOf(
    "77" to Pair(
      "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ،\n" +
        "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ،\n" +
        "لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ،\n" +
        "رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيْرَ مَا بَعْدَهُ،\n" +
        "وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ،\n" +
        "رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ،\n" +
        "رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ"
      ,
      "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ،\n" +
        "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ،\n" +
        "لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ،\n" +
        "رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيْرَ مَا بَعْدَهَا،\n" +
        "وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا،\n" +
        "رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ،\n" +
        "رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ"
      ),
    "78" to Pair(
      "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا،\nوَبِكَ نَحْيَا، وَبِكَ نَمُوتُ،\nوَإِلَيْكَ النُّشُورُ"
      ,
      "اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا،\nوَبِكَ نَحْيَا، وَبِكَ نَمُوتُ،\nوَإِلَيْكَ الْمَصِيرُ"
      ),
    "80" to Pair(
      "اللَّهُمَّ إِنِّي أَصْبَحْتُ أُشْهِدُكَ،\nوَأُشْهِدُ حَمَلَةَ عَرْشِكَ، وَمَلَائِكَتَكَ، وَجَمِيعَ خَلْقِكَ،\n" +
        "أَنَّكَ أَنْتَ اللَّهُ لَا إِلَهَ إِلَّا أَنْتَ وَحْدَكَ لَا شَرِيكَ لَكَ،\nوَأَنَّ مُحَمَّدًا عَبْدُكَ وَرَسُولُكَ"
      ,
      "اللَّهُمَّ إِنِّي أَمْسَيْتُ أُشْهِدُكَ،\nوَأُشْهِدُ حَمَلَةَ عَرْشِكَ، وَمَلَائِكَتَكَ، وَجَمِيعَ خَلْقِكَ،\n" +
        "أَنَّكَ أَنْتَ اللَّهُ لَا إِلَهَ إِلَّا أَنْتَ وَحْدَكَ لَا شَرِيكَ لَكَ،\nوَأَنَّ مُحَمَّدًا عَبْدُكَ وَرَسُولُكَ"
      ),
    "81" to Pair(
      "اللَّهُمَّ مَا أَصْبَحَ بِي مِنْ نِعْمَةٍ أَوْ بِأَحَدٍ مِنْ خَلْقِكَ،\nفَمِنْكَ وَحْدَكَ لَا شَرِيكَ لَكَ،\nفَلَكَ الْحَمْدُ وَلَكَ الشُّكْرُ"
      ,
      "اللَّهُمَّ مَا أَمْسَى بِي مِنْ نِعْمَةٍ أَوْ بِأَحَدٍ مِنْ خَلْقِكَ،\nفَمِنْكَ وَحْدَكَ لَا شَرِيكَ لَكَ،\nفَلَكَ الْحَمْدُ وَلَكَ الشُّكْرُ"
      ),
    "89" to Pair(
      "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ رَبِّ الْعَالَمِينَ،\nاللَّهُمَّ إِنِّي أَسْأَلُكَ خَيْرَ هَذَا الْيَوْمِ:\n" +
        "فَتْحَهُ، وَنَصْرَهُ، وَنُورَهُ، وَبَرَكَتَهُ، وَهُدَاهُ،\nوَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِيهِ وَشَرِّ مَا بَعْدَهُ"
      ,
      "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ رَبِّ الْعَالَمِينَ،\nاللَّهُمَّ إِنِّي أَسْأَلُكَ خَيْرَ هَذِهِ اللَّيْلَةِ:\n" +
        "فَتْحَهَا، وَنَصْرَهَا، وَنُورَهَا، وَبَرَكَتَهَا، وَهُدَاهَا،\nوَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِيهَا وَشَرِّ مَا بَعْدَهَا"
      ),
    "90" to Pair(
      "أَصْبَحْنَا عَلَى فِطْرَةِ الْإِسْلَامِ، وَعَلَى كَلِمَةِ الْإِخْلَاصِ،\nوَعَلَى دِينِ نَبِيِّنَا مُحَمَّدٍ ﷺ،\n" +
        "وَعَلَى مِلَّةِ أَبِينَا إِبْرَاهِيمَ، حَنِيفًا مُسْلِمًا،\nوَمَا كَانَ مِنَ الْمُشْرِكِينَ"
      ,
      "أَمْسَيْنَا عَلَى فِطْرَةِ الْإِسْلَامِ، وَعَلَى كَلِمَةِ الْإِخْلَاصِ،\nوَعَلَى دِينِ نَبِيِّنَا مُحَمَّدٍ ﷺ،\n" +
        "وَعَلَى مِلَّةِ أَبِينَا إِبْرَاهِيمَ، حَنِيفًا مُسْلِمًا،\nوَمَا كَانَ مِنَ الْمُشْرِكِينَ"
      )
  )

  /** a line that only says how many times, or when: «(ثلاث مرات إذا أصبح)»; the counter shows it */
  private val COUNT_NOTE = Regex("""\n?\s*\((?:[^()]*\s)?(?:مر[ةه]|مرات|مَرَّةٍ|مرّة|مِائَةَ|مائة|يصْبِح|يمسي|أصبح|أمسى)[^()]*\)\s*""")

  /** [chapter] as said at [session]: its own adhkar, in its own words */
  fun chapter(chapter: AdhkarChapter, session: AdhkarSession): AdhkarChapter {
    if (chapter.id != AdhkarBook.MORNING_EVENING || session == AdhkarSession.NONE) return chapter
    val morning = session == AdhkarSession.MORNING
    val adhkar = chapter.adhkar
      .filter { if (morning) it.number !in EVENING_ONLY else it.number !in MORNING_ONLY }
      .map { d ->
        val words = WORDS[d.number]
        val text = words?.let { if (morning) it.first else it.second } ?: d.text
        d.copy(text = COUNT_NOTE.replace(text, "").trim())
      }
    return chapter.copy(adhkar = adhkar)
  }

  /**
   * which set it's time for: the morning adhkar from Fajr until Asr, the evening ones from
   * Asr until the night is over; by the clock (before noon) when the app doesn't know where
   * the reader is
   */
  fun now(context: Context, now: Calendar = Calendar.getInstance()): AdhkarSession {
    val settings = PrayerStore.get(context).settings.value
    val place = settings.place ?: return AdhkarSession.forHour(now.get(Calendar.HOUR_OF_DAY))
    val times = runCatching { PrayerMath.day(settings, place, now) }.getOrNull()
      ?: return AdhkarSession.forHour(now.get(Calendar.HOUR_OF_DAY))
    val fajr = times.firstOrNull { it.name == PrayerName.FAJR }?.time?.time
    val asr = times.firstOrNull { it.name == PrayerName.ASR }?.time?.time
    if (fajr == null || asr == null) return AdhkarSession.forHour(now.get(Calendar.HOUR_OF_DAY))
    val t = now.timeInMillis
    return if (t in fajr until asr) AdhkarSession.MORNING else AdhkarSession.EVENING
  }
}
