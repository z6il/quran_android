package com.quran.labs.androidquran.feature.hadith

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HadithHighlightTest {
  private fun marked(text: String, query: String) = HadithHighlight.ranges(text, query).map { text.substring(it) }

  @Test
  fun ignoresTashkeelAndMarksTheWholeWord() {
    // "رزق" finds the vowelled word, marks and suffix included
    assertThat(marked("وَاللَّهُ يَرْزُقُكُمْ رِزْقًا حَسَنًا", "رزق")).containsExactly("رِزْقًا")
    assertThat(marked("إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ", "الاعمال")).containsExactly("الأَعْمَالُ")
  }

  @Test
  fun matchesOnlyAtTheStartOfAWord() {
    // the search matches word starts, so "زق" inside "يرزقكم" is no match
    assertThat(marked("يَرْزُقُكُمْ", "زق")).isEmpty()
  }

  @Test
  fun everyWordOfTheSearchInOrder() {
    assertThat(marked("قال رسول الله صلى الله عليه وسلم الصبر ضياء", "ضياء الصبر"))
      .containsExactly("الصبر", "ضياء").inOrder()
  }

  @Test
  fun numbersAndSingleLettersAreNotMarked() {
    assertThat(HadithHighlight.words("15 و")).isEmpty()
  }
}
