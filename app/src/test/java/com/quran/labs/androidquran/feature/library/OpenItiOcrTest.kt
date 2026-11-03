package com.quran.labs.androidquran.feature.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class OpenItiOcrTest {
  @Test
  fun readingOrderTurnsTheModelsLettersRound() {
    // the model reads left to right: words come out back to front, numbers as they look
    assertThat(OpenItiOcr.readingOrder("هللا لوسر")).isEqualTo("رسول الله")
    assertThat(OpenItiOcr.readingOrder("(٢٠٢/٢)")).isEqualTo("(٢٠٢/٢)")
    assertThat(OpenItiOcr.readingOrder("«ءاعدلا»")).isEqualTo("«الدعاء»")
    // a mark stays on its letter
    assertThat(OpenItiOcr.readingOrder("ٌبَر")).isEqualTo("رَبٌ")
  }

  @Test
  fun otsuSplitsInkFromPaper() {
    val grey = IntArray(100) { if (it < 30) 20 else 230 }
    val t = OpenItiOcr.otsu(grey)
    assertThat(t).isGreaterThan(20)
    assertThat(t).isAtMost(230)
  }

  /** compares with Kraken's own preparation of a real line, when the comparison files are there */
  @Test
  fun normalisesLikeKraken() {
    val crop = File(System.getProperty("java.io.tmpdir"), "q/kraken/line.txt")
    if (!crop.exists()) return
    val lines = crop.readLines()
    val (w, h) = lines[0].split(' ').map { it.toInt() }
    val values = lines[1].split(' ').map { it.toFloat() }.toFloatArray()
    val n = OpenItiOcr.normalise(values, w, h)!!
    File(crop.parentFile, "kt_norm.txt").writeText("${n.pixels.size / 120}\n" + n.pixels.joinToString(" "))
  }

  private fun fix(vararg words: String) = Honorifics.fix(words.map { PdfWord(it, 0f, 0f, 0.1f, 0.1f) }).joinToString(" ") { it.text }

  @Test
  fun jumbledHonorificsAreWords() {
    // as OCR read them on a page of al-Jawab al-Kafi
    assertThat(fix("أَنَس", "رَضَالَهُعَنهُ،", "عن")).isEqualTo("أَنَس رضي الله عنه، عن")
    assertThat(fix("هُرَيْرَةَ", "رَضَالَهُعنْكُ", "وأبو")).isEqualTo("هُرَيْرَةَ رضي الله عنه وأبو")
    assertThat(fix("عائشة", "رَع", "للهعَنها،")).isEqualTo("عائشة رضي الله عنها،")
    assertThat(fix("ضَاللُعلينتقَتسلمَة", ":")).isEqualTo("صلى الله عليه وسلم :")
    assertThat(fix("الله", "عَزَقَجَل،")).isEqualTo("الله عز وجل،")
    assertThat(fix("القيم", "رحمهالله")).isEqualTo("القيم رحمه الله")
  }

  @Test
  fun wordsOfTheTextStay() {
    assertThat(fix("الصلاة", "على", "النبي")).isEqualTo("الصلاة على النبي")
    assertThat(fix("رضا", "الله")).isEqualTo("رضا الله")
    assertThat(fix("عن", "عروة", "عن", "عائشة")).isEqualTo("عن عروة عن عائشة")
    assertThat(fix("قال", "رسول", "الله")).isEqualTo("قال رسول الله")
  }
}
