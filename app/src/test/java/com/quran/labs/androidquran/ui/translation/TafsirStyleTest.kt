package com.quran.labs.androidquran.ui.translation

import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric for the Android span classes
@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class TafsirStyleTest {
  private val tabari = "قال أبو جعفر: يعني تعالى ذكره كذا.\n" +
    "٢٣١٨- حدثني المثنى قال، حدثنا أبو حذيفة قال، حدثنا شبل، عن مجاهد مثله.\n" +
    "٢٣١٩- حَدَّثَنَا بشر قال، حدثنا يزيد، عن قتادة قوله: \"ولا تقولوا\" كنا نحدث."

  @Test
  fun eachChainIsDimmedOnce() {
    val styled = TafsirStyle.style(tabari, 1, 2) as Spanned
    val shrunk = styled.getSpans(0, styled.length, RelativeSizeSpan::class.java).filter { it.sizeChange == 0.82f }
    // one per chain, not one per link
    assertThat(shrunk).hasSize(2)
    val second = shrunk.map { styled.getSpanStart(it) to styled.getSpanEnd(it) }.last()
    // the second chain ends at the colon before the narrated words
    assertThat(styled.subSequence(second.first, second.second).toString()).endsWith("قوله:")
  }

  @Test
  fun authorsVoiceIsMarked() {
    val styled = TafsirStyle.style(tabari, 1, 2) as Spanned
    val bold = styled.getSpans(0, styled.length, StyleSpan::class.java)
      .map { styled.subSequence(styled.getSpanStart(it), styled.getSpanEnd(it)).toString() }
    assertThat(bold).containsExactly("قال أبو جعفر", "يعني تعالى ذكره")
  }

  @Test
  fun readingTimeAndCut() {
    assertThat(TafsirStyle.minutes("كلمة ".repeat(400))).isEqualTo(3)
    val text = "أ".repeat(500) + ". بقية"
    assertThat(TafsirStyle.cutPoint(text, 480)).isEqualTo(500)
  }
}
