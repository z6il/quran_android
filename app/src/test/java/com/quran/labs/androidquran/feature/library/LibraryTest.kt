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

  @Test
  fun ocrCommasAreNotQuotes() {
    fun w(t: String) = PdfWord(t, 0f, 0f, 0f, 0f)
    val fixed = fixCommas(listOf(w("دوائه»"), w("وقد"), w("«قال"), w("ذلك»"), w("ثم»"))).map { it.text }
    assertThat(fixed).containsExactly("دوائه،", "وقد", "«قال", "ذلك»", "ثم،").inOrder()
  }

  @Test
  fun pdfWordsRoundTrip() {
    val words = listOf(PdfWord("الحمد", 0.1f, 0.2f, 0.3f, 0.25f), PdfWord("لله", 0.31f, 0.2f, 0.4f, 0.25f))
    val back = PdfTextStore.decode(PdfTextStore.encode(words))
    assertThat(back.map { it.text }).containsExactly("الحمد", "لله").inOrder()
    assertThat(back[0].left).isWithin(0.0002f).of(0.1f)
  }

  @Test
  fun aFingerBetweenWordsTakesTheWordOnItsLine() {
    // the case seen on a scanned page: a stray word in the heading above sat closer than the next word along the line
    val words = listOf(
      PdfWord("نه", 0.525f, 0.1258f, 0.5477f, 0.1371f),
      PdfWord("في", 0.5733f, 0.1347f, 0.595f, 0.1598f),
      PdfWord("الدعا", 0.515f, 0.1319f, 0.5644f, 0.1566f)
    )
    assertThat(wordAt(words, 0.505f, 0.144f)).isEqualTo(2)
  }

  @Test
  fun theProphetsWordsAreHadithOtherQuotesAreNot() {
    val text = "قال رسول الله صلى الله عليه وسلم: «إنما الأعمال بالنيات» وفي كتاب «الأذكار» للنووي"
    val quotes = hadithQuotes(text).map { text.substring(it.first, it.last + 1) }
    assertThat(quotes).containsExactly("«إنما الأعمال بالنيات»")
    assertThat(hadithQuotes("عن النبي ﷺ قال: «الدين النصيحة»")).hasSize(1)
    assertThat(hadithQuotes("قال ابن القيم في «مدارج السالكين»")).isEmpty()
  }
}
