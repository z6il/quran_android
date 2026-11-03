package com.quran.labs.androidquran.feature.library

/**
 * The honorifics books print as one decorative glyph (ﷺ, رضي الله عنه, رحمه الله, عز وجل) come
 * out of OCR as a jumble of the letters in them: «رَضَالَهُعَنهُ», «ضَاللُعلينتقَتسلمَة»,
 * «عَزَقَجَل». Such a jumble, a word or up to three, is put back as the words it stands for.
 */
object Honorifics {
  private val MARKS = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
  /** what may trail an honorific: punctuation and closing marks */
  private val TRAIL = Regex("[\\p{P}\\p{S}0-9\\u0660-\\u0669]+$")
  private val LEAD = Regex("^[\\p{P}\\p{S}]+")

  private class Form(val pattern: Regex, val words: String, val maxWords: Int = 2, val maxLetters: Int = 16)

  private val FORMS = listOf(
    // صلى الله عليه وسلم: from ص or ض, a lam soon after, then the سل of وسلم
    Form(Regex("^[صض][^ ]{0,2}ل.*(عل|لي|عي).*س[لا]م?.*$"), "صلى الله عليه وسلم", maxWords = 3, maxLetters = 22),
    // رضي الله عنهما / عنهم / عنها / عنه
    Form(Regex("^ر?[ضع].{0,3}ل[له].*ع.*هما$"), "رضي الله عنهما"),
    Form(Regex("^ر?[ضع].{0,3}ل[له].*ع.*هم$"), "رضي الله عنهم"),
    Form(Regex("^ر?[ضع].{0,3}ل[له].*ع.*ها$"), "رضي الله عنها"),
    Form(Regex("^ر[ضع].{0,3}ل[له].*ع.{0,3}$"), "رضي الله عنه"),
    // رحمه الله / رحمهم الله
    Form(Regex("^ر[حمز]?م[هد]?م?ا?لل?ه$"), "رحمه الله", maxWords = 1, maxLetters = 10),
    // عز وجل
    Form(Regex("^ع[زنر][وقف]?[جح][ل]$"), "عز وجل", maxWords = 1, maxLetters = 6),
    Form(Regex("^سبحانهوتعالى$"), "سبحانه وتعالى", maxWords = 1, maxLetters = 13),
    Form(Regex("^تباركوتعالى$"), "تبارك وتعالى", maxWords = 1, maxLetters = 12)
  )

  fun fix(words: List<PdfWord>): List<PdfWord> {
    val out = mutableListOf<PdfWord>()
    var i = 0
    while (i < words.size) {
      val match = match(words, i)
      if (match == null) {
        out += words[i]
        i++
        continue
      }
      val (count, text) = match
      val span = words.subList(i, i + count)
      out += PdfWord(
        text, span.minOf { it.left }, span.minOf { it.top }, span.maxOf { it.right }, span.maxOf { it.bottom }
      )
      i += count
    }
    return out
  }

  /** the longest jumble starting at [i]: how many words it takes, and what it becomes */
  private fun match(words: List<PdfWord>, i: Int): Pair<Int, String>? {
    for (form in FORMS) {
      for (count in form.maxWords downTo 1) {
        if (i + count > words.size) continue
        // a word that's only punctuation isn't part of the jumble
        if (count > 1 && words[i + count - 1].text.all { !it.isLetter() }) continue
        val raw = words.subList(i, i + count).joinToString("") { it.text }
        // the words on either side of an honorific belong to the text, not to it
        val lead = LEAD.find(raw)?.value.orEmpty()
        val trail = TRAIL.find(raw)?.value.orEmpty()
        if (lead.length + trail.length >= raw.length) continue
        val core = MARKS.replace(raw.substring(lead.length, raw.length - trail.length), "")
        if (core.length > form.maxLetters || core.any { it !in 'ء'..'ي' }) continue
        // a real word that happens to fit stays: honorifics are jumbles, not words of the text
        if (count == 1 && core in WORDS) continue
        if (form.pattern.matches(core)) return count to (lead + form.words + trail)
      }
    }
    return null
  }

  /** words of the text the patterns could take for a jumble */
  private val WORDS = setOf("رضا", "رضوا", "عالم", "علم", "صلاة", "صلح", "صلى")
}
