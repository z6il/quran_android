package com.quran.labs.androidquran.ui.translation

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

/**
 * Makes a long Arabic tafsir easier to skim: the chains of narration (حدثنا فلان، عن فلان…)
 * that fill the classical tafsirs step back, smaller and fainter, so the explanation stands
 * out; the author's own voice ("قال أبو جعفر") is marked; and paragraphs get room between them.
 */
object TafsirStyle {
  private const val HARAKAT = "[\\u064B-\\u0652\\u0670\\u0640]*"

  /** [word] matched with or without its diacritics */
  private fun loose(word: String) = word.map { c -> if (c == ' ') "\\s+" else Regex.escape(c.toString()) + HARAKAT }.joinToString("")

  /** where a chain of narration starts */
  private val CHAIN_START = Regex(
    "(?<![\\p{L}])(?:" + listOf("حدثنا", "حدثني", "أخبرنا", "أخبرني", "حدثت", "أنبأنا").joinToString("|") { "(?:و$HARAKAT)?" + loose(it) } + ")"
  )
  /** the author speaking in his own voice, e.g. Tabari's "قال أبو جعفر" */
  private val AUTHOR = Regex(
    listOf("قال أبو جعفر", "يقول تعالى ذكره", "يعني تعالى ذكره", "يقول جل ثناؤه", "قال ابن كثير").joinToString("|") { loose(it) }
  )
  private const val MAX_CHAIN = 360

  fun style(text: CharSequence, accent: Int, faded: Int): CharSequence {
    val out = SpannableStringBuilder(text)
    var dimmedUntil = -1
    for (m in CHAIN_START.findAll(text)) {
      val start = m.range.first
      // the later links of a chain already dimmed ("…قال، حدثنا…") would shrink it twice
      if (start < dimmedUntil) continue
      val window = text.subSequence(start, minOf(text.length, start + MAX_CHAIN)).toString()
      // the chain runs up to where the narrated words begin: a colon or an opening quote
      val end = listOf(window.indexOf(':'), window.indexOf('«'), window.indexOf('"'), window.indexOf('\n'))
        .filter { it > 0 }.minOrNull() ?: continue
      out.setSpan(ForegroundColorSpan(faded), start, start + end + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      out.setSpan(RelativeSizeSpan(0.82f), start, start + end + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      dimmedUntil = start + end + 1
    }
    for (m in AUTHOR.findAll(text)) {
      out.setSpan(ForegroundColorSpan(accent), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      out.setSpan(StyleSpan(Typeface.BOLD), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    // half a blank line between paragraphs; inserting keeps the other spans in place
    var i = out.length - 1
    while (i > 0) {
      if (out[i] == '\n' && out[i - 1] != '\n') {
        out.insert(i, "\n")
        out.setSpan(RelativeSizeSpan(0.45f), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
      i--
    }
    return out
  }

  /** minutes to read it, at a relaxed pace */
  fun minutes(text: CharSequence): Int {
    val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
    return maxOf(1, (words + 179) / 180)
  }

  /** where to cut a preview: the end of a sentence near [from], or the next space */
  fun cutPoint(text: CharSequence, from: Int): Int {
    if (from >= text.length) return -1
    val window = text.subSequence(from, minOf(text.length, from + 220)).toString()
    val sentence = listOf(window.indexOf('.'), window.indexOf('؟'), window.indexOf('\n')).filter { it >= 0 }.minOrNull()
    if (sentence != null) return from + sentence
    return text.indexOf(' ', from).takeIf { it >= 0 } ?: -1
  }
}
