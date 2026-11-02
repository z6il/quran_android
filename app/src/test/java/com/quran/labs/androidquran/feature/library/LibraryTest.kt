package com.quran.labs.androidquran.feature.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryTest {
  private fun convert(text: String): List<String> {
    val out = mutableListOf<String>()
    OpenItiText.convert(text.lines().asSequence()) { out += it }
    return out
  }

  @Test
  fun readsHeadingsParagraphsAndPages() {
    val lines = convert(
      """
      ######OpenITI#
      #META# 020.BookTITLE	:: المغني
      #META#Header#End#

      # كتاب المغني PageV01P001
      # | مقدمة
      # الحمد لله ms0001 بارىء البريات
      ~~وغافر الخطيئات @QB@ يعلم ما بين أيديهم @QE@ البقرة 255
      ### || باب الطهارة
      # قال PageV01P017 ثم
      # والماء الطاهر
      """.trimIndent()
    )
    assertThat(lines).containsExactly(
      "\t" + "كتاب المغني",
      "\u00011\tمقدمة",
      "1:2\tالحمد لله بارىء البريات وغافر الخطيئات ﴿يعلم ما بين أيديهم﴾ البقرة 255",
      "\u00012\tباب الطهارة",
      "1:2\tقال ثم",
      // a page marker ends its page: what follows is on the next
      "1:18\tوالماء الطاهر"
    ).inOrder()
  }

  @Test
  fun skipsTheHeader() {
    assertThat(convert("#META# 010.AuthorNAME\t:: x\n# not text yet")).isEmpty()
  }

  @Test
  fun highlightsCoverWholeWords() {
    val text = "قال رسول الله صلى الله عليه وسلم"
    // from inside "رسول" to inside "صلى"
    val (start, end) = wholeWords(text, 6, 16)
    assertThat(text.substring(start, end)).isEqualTo("رسول الله صلى")
    assertThat(wholeWords(text, 3, 5).let { text.substring(it.first, it.second) }).isEqualTo("رسول")
  }

  @Test
  fun resolvesEpubPaths() {
    assertThat(EpubBook.resolve("OEBPS/text/", "../images/a%20b.png")).isEqualTo("OEBPS/images/a b.png")
    assertThat(EpubBook.resolve("", "ch1.xhtml")).isEqualTo("ch1.xhtml")
  }
}
