package com.quran.labs.androidquran.feature.hadith

import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.feature.research.HadithEntry
import org.junit.Test

class HadithTakhrijTest {
  private fun riyad(text: String) = HadithEntry("riyadussalihin", "1", "", text, "", "")

  @Test
  fun readsWhereItWasNarrated() {
    val t = HadithTakhrij.of(riyad("قال رسول الله صلى الله عليه وسلم : \" لا يستر عبد عبدا \" ( ( رواه مسلم ) ) ."))
    assertThat(t).isEqualTo("رواه مسلم")
    assertThat(HadithTakhrij.level(t!!)).isEqualTo(GradeLevel.SAHIH)
  }

  @Test
  fun agreedUponAndOtherGradings() {
    assertThat(HadithTakhrij.level("متفق عليه")).isEqualTo(GradeLevel.SAHIH)
    assertThat(HadithTakhrij.level("رواه الترمذي وقال حديث حسن")).isEqualTo(GradeLevel.HASAN)
    // where it was narrated says nothing of its strength
    assertThat(HadithTakhrij.level("رواه أبو داود")).isEqualTo(GradeLevel.UNGRADED)
  }

  @Test
  fun onlyForBooksThatGiveIt() {
    val musnad = HadithEntry("ahmad", "1", "", "حدثنا وكيع رواه مسلم", "", "")
    assertThat(HadithTakhrij.of(musnad)).isNull()
  }
}
