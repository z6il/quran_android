package com.quran.labs.androidquran.feature.adhkar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdhkarSourcesTest {
  private fun arabic(ref: String) = AdhkarSources.arabic(ref) { if (it == 3) "آل عمران" else "سورة $it" }

  @Test
  fun collectionsInArabicWithTheirPages() {
    assertThat(arabic("Al-Bukhari, cf. Al-Asqalani, Fathul-Bari 11/113; Muslim 4/2083"))
      .isEqualTo("رواه البخاري مع الفتح ١١/١١٣، ومسلم ٤/٢٠٨٣")
    assertThat(arabic("Muslim 1/209.")).isEqualTo("رواه مسلم ١/٢٠٩")
  }

  @Test
  fun albaniBooksAreNotTheCollections() {
    // "Sahih At-Tirmidhi" is Al-Albani's book, not a second mention of At-Tirmidhi
    assertThat(arabic("At-Tirmidhi 1/78. See also Al-Albani, Sahih At- Tirmidhi 1/18"))
      .isEqualTo("رواه الترمذي ١/٧٨، وانظر تحقيق الألباني")
  }

  @Test
  fun nasaiInHisBook() {
    assertThat(arabic("An-Nasa'i, 'Amalul-Yawm wal-Laylah (Hadith no. 100), also Ibn As-Sunni (no. 121)."))
      .isEqualTo("رواه النسائي في عمل اليوم والليلة رقم ١٠٠، وابن السني رقم ١٢١")
  }

  @Test
  fun quranVersesBySuraName() {
    assertThat(arabic("Qur'an Āl-'Imran 3: 190-200; Muslim 1/530."))
      .isEqualTo("سورة آل عمران، الآية ١٩٠-٢٠٠؛ رواه مسلم ١/٥٣٠")
  }

  @Test
  fun nothingToShow() {
    assertThat(arabic("ibid.")).isNull()
  }
}
