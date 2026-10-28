package com.quran.labs.androidquran.feature.hadith

import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.feature.research.HadithBook
import org.junit.Test

class HadithIndexTest {

  @Test
  fun gradesByStrength() {
    assertThat(GradeLevel.of("Sahih (Darussalam)", "nasai")).isEqualTo(GradeLevel.SAHIH)
    assertThat(GradeLevel.of("Hasan Sahih (Al-Albani)", "abudawud")).isEqualTo(GradeLevel.SAHIH)
    assertThat(GradeLevel.of("Hasan (Darussalam)", "tirmidhi")).isEqualTo(GradeLevel.HASAN)
    assertThat(GradeLevel.of("Da'if (Al-Albani)", "abudawud")).isEqualTo(GradeLevel.DAIF)
    // the source has a broken character in place of the apostrophe
    assertThat(GradeLevel.of("Da�if (Darussalam)", "ibnmajah")).isEqualTo(GradeLevel.DAIF)
    assertThat(GradeLevel.of("Da'if Jiddan (Al-Albani)", "abudawud")).isEqualTo(GradeLevel.VERY_WEAK)
    assertThat(GradeLevel.of("Munkar (Al-Albani)", "abudawud")).isEqualTo(GradeLevel.VERY_WEAK)
    assertThat(GradeLevel.of("Maudu� (Darussalam)", "ibnmajah")).isEqualTo(GradeLevel.MAWDU)
    assertThat(GradeLevel.of("صحيح", "abudawud")).isEqualTo(GradeLevel.SAHIH)
    // the two Sahih books need no grading
    assertThat(GradeLevel.of("", "bukhari")).isEqualTo(GradeLevel.SAHIH)
    assertThat(GradeLevel.of("", "tirmidhi")).isEqualTo(GradeLevel.UNGRADED)
  }

  @Test
  fun graderAndDetail() {
    assertThat(graderOf("Sahih in chain (Al-Albani)")).isEqualTo("Al-Albani")
    assertThat(graderOf("Sahih (None)")).isNull()
    assertThat(gradeDetailOf("Sahih in chain (Al-Albani)")).isEqualTo("Sahih in chain")
  }

  @Test
  fun narratorIsTheLastLinkBeforeTheProphet() {
    assertThat(narratorOf("حَدَّثَنَا مُسَدَّدٌ، حَدَّثَنَا يَحْيَى، عَنْ شُعْبَةَ، عَنْ قَتَادَةَ، عَنْ أَنَسٍ، عَنِ النَّبِيِّ صلى الله عليه وسلم قَالَ"))
      .isEqualTo("أنس")
    assertThat(narratorOf("حدثنا علي، حدثنا سفيان، سمع جابرا رضى الله عنه قال قال رسول الله صلى الله عليه وسلم"))
      .isEqualTo("جابر")
    // back to the nominative
    assertThat(narratorOf("حدثنا قتيبة، حدثنا الليث، عن سعيد، عن أبي هريرة، أن رسول الله صلى الله عليه وسلم قال"))
      .isEqualTo("أبو هريرة")
    assertThat(narratorOf("حدثنا يحيى، عن ابن شهاب، عن أنس بن مالك، قال كان أبو ذر يحدث أن رسول الله صلى الله عليه وسلم قال"))
      .isEqualTo("أبو ذر")
    // "his father" names nobody
    assertThat(narratorOf("حدثنا حماد، عن أبي بردة، عن أبيه، أن النبي صلى الله عليه وسلم قال")).isNull()
  }

  @Test
  fun booksFallUnderTopics() {
    fun topic(name: String) = HadithTopic.of(HadithBook("x", "1", "", name, 1))
    assertThat(topic("Prostration During Recital of Qur'an")).isEqualTo(HadithTopic.PRAYER)
    assertThat(topic("Virtues of the Night of Qadr")).isEqualTo(HadithTopic.FASTING)
    assertThat(topic("Virtues of the Qur'an")).isEqualTo(HadithTopic.KNOWLEDGE)
    assertThat(topic("The Book of Faith")).isEqualTo(HadithTopic.FAITH)
    assertThat(topic("Al-Adha Festival Sacrifice (Adaahi)")).isEqualTo(HadithTopic.FOOD)
    assertThat(topic("Distribution of Water")).isEqualTo(HadithTopic.TRADE)
    assertThat(topic("Something new")).isEqualTo(HadithTopic.OTHER)
  }
}
