package com.quran.labs.androidquran.feature.hadith

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * Finds the searched words in a hadith the way the search does: tashkeel ignored, the forms
 * of alif, ya and ta marbuta folded, and each word matched at the start of a word ("رزق"
 * finds "رزقكم"). Returns ranges in the original, vowelled text, whole words.
 */
object HadithHighlight {
  private fun isMark(c: Char) =
    c in 'ؐ'..'ؚ' || c in 'ً'..'ٟ' || c == 'ٰ' || c in 'ۖ'..'ۭ' || c == 'ـ'

  private fun fold(c: Char): Char = when (c) {
    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
    'ى', 'ئ' -> 'ي'
    'ة' -> 'ه'
    'ؤ' -> 'و'
    else -> c.lowercaseChar()
  }

  /** the searched words, folded; numbers and single letters aren't highlighted */
  fun words(query: String): List<String> =
    query.trim().split(Regex("\\s+"))
      .map { w -> w.filterNot(::isMark).map(::fold).joinToString("").filter { it.isLetter() } }
      .filter { it.length > 1 }
      .distinct()

  fun ranges(text: String, query: String): List<IntRange> {
    val words = words(query)
    if (words.isEmpty()) return emptyList()
    // the text without marks, and where each of its letters sits in the original
    val folded = StringBuilder()
    val at = ArrayList<Int>(text.length)
    text.forEachIndexed { i, c ->
      if (!isMark(c)) {
        folded.append(fold(c))
        at.add(i)
      }
    }
    val plain = folded.toString()
    val found = mutableListOf<IntRange>()
    for (word in words) {
      var from = 0
      while (true) {
        val start = plain.indexOf(word, from)
        if (start < 0) break
        from = start + 1
        if (start > 0 && plain[start - 1].isLetter()) continue
        var end = start + word.length
        while (end < plain.length && plain[end].isLetter()) end++
        var originalEnd = at[end - 1] + 1
        // take the last letter's marks too
        while (originalEnd < text.length && isMark(text[originalEnd])) originalEnd++
        found += at[start] until originalEnd
      }
    }
    return found.sortedBy { it.first }
  }

  fun annotate(text: String, query: String?, color: Color): AnnotatedString {
    val ranges = if (query.isNullOrBlank()) emptyList() else ranges(text, query)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
      append(text)
      ranges.forEach { r ->
        addStyle(SpanStyle(background = color, fontWeight = FontWeight.Bold), r.first, r.last + 1)
      }
    }
  }
}
