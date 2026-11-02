package com.quran.labs.androidquran.feature.library

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One block of a classical book: a heading or a paragraph. */
sealed interface TextBlock {
  /** [level] 1 for a book or part, deeper for its chapters */
  data class Heading(val level: Int, val title: String) : TextBlock

  /** [page]: where it starts in the printed edition, as "volume:page", or empty */
  data class Paragraph(val page: String, val text: String) : TextBlock
}

/**
 * OpenITI's texts are in its mARkdown: "### |" headings, "# " paragraphs continued by "~~"
 * lines, "PageV01P017" where a printed page ends, "@QB@ … @QE@" around Quran quotes. The app
 * keeps them as one block a line: "\u0001level\ttitle" for a heading, "page\ttext" for the rest.
 */
object OpenItiText {
  private const val HEADING = '\u0001'
  private val PAGE = Regex("PageV(\\d+)P(\\d+)")
  private val MILESTONE = Regex("\\bms\\d+\\b")
  private val QURAN_START = Regex("@QB@\\s*")
  private val QURAN_END = Regex("\\s*@QE@")
  private val OTHER_TAGS = Regex("@[A-Z]{1,6}\\d*@|#~:[^:]*:|%~%")
  private val SPACES = Regex("\\s+")

  /** downloads [url] into [into] as blocks; returns how many there are */
  fun download(url: String, into: File, expectedBytes: Long, onProgress: (Float) -> Unit): Int {
    into.parentFile!!.mkdirs()
    val tmp = File(into.path + ".tmp")
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 20_000
    connection.readTimeout = 30_000
    try {
      if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
      val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedBytes
      var read = 0L
      var shown = 0f
      val counting = object : java.io.FilterInputStream(connection.inputStream) {
        override fun read(b: ByteArray, off: Int, len: Int): Int {
          val n = super.read(b, off, len)
          if (n > 0) {
            read += n
            val f = (read / total.toFloat()).coerceAtMost(0.99f)
            if (f - shown > 0.01f) {
              shown = f
              onProgress(f)
            }
          }
          return n
        }
      }
      val count = tmp.bufferedWriter().use { out -> convert(counting.bufferedReader().lineSequence()) { out.write(it); out.write("\n") } }
      if (count == 0) error("empty")
      if (!tmp.renameTo(into)) {
        into.delete()
        if (!tmp.renameTo(into)) error("couldn't save")
      }
      return count
    } finally {
      tmp.delete()
      connection.disconnect()
    }
  }

  /** mARkdown lines to block lines; returns how many blocks */
  internal fun convert(lines: Sequence<String>, emit: (String) -> Unit): Int {
    var count = 0
    var inHeader = true
    var volume = "1"
    var lastPage = 0
    val paragraph = StringBuilder()
    var paragraphPage = ""

    fun flush() {
      val text = clean(paragraph.toString())
      if (text.isNotEmpty()) {
        emit(paragraphPage + "\t" + text)
        count++
      }
      paragraph.setLength(0)
    }

    // page markers close a page: what follows is on the next one
    fun pages(s: String): String {
      PAGE.findAll(s).lastOrNull()?.let {
        volume = it.groupValues[1].trimStart('0').ifEmpty { "1" }
        lastPage = it.groupValues[2].toIntOrNull() ?: lastPage
      }
      return PAGE.replace(s, " ")
    }

    for (raw in lines) {
      val line = raw.trimEnd()
      if (inHeader) {
        if (line.startsWith("#META#Header#End")) inHeader = false
        continue
      }
      when {
        // "### |" headings, and "# |" in older texts
        line.startsWith("### ") || line.startsWith("# |") -> {
          flush()
          val marks = line.removePrefix("###").removePrefix("#").trimStart(' ')
          val level = when {
            marks.startsWith("|") -> marks.takeWhile { it == '|' }.length
            else -> 3
          }
          val title = clean(pages(marks.trimStart('|', '$', '@', ' ')))
          if (title.isNotEmpty()) {
            emit("$HEADING$level\t$title")
            count++
          }
        }
        line.startsWith("~~") -> paragraph.append(' ').append(pages(line.removePrefix("~~")))
        line.startsWith("#") -> {
          flush()
          paragraphPage = if (lastPage > 0) "$volume:${lastPage + 1}" else ""
          paragraph.append(pages(line.trimStart('#', ' ')))
        }
        line.isBlank() -> Unit
        else -> paragraph.append(' ').append(pages(line))
      }
    }
    flush()
    return count
  }

  private fun clean(s: String): String {
    var t = MILESTONE.replace(s, " ")
    t = QURAN_START.replace(t, "﴿")
    t = QURAN_END.replace(t, "﴾")
    t = OTHER_TAGS.replace(t, " ")
    return SPACES.replace(t, " ").trim()
  }

  fun load(file: File): List<TextBlock> = file.bufferedReader().useLines { lines ->
    lines.mapNotNull { line ->
      val tab = line.indexOf('\t')
      when {
        tab < 0 -> null
        line.startsWith(HEADING) -> TextBlock.Heading(line.substring(1, tab).toIntOrNull() ?: 1, line.substring(tab + 1))
        // al-Shamela's texts set Quran quotes in braces; one character for one, so highlights still line up
        else -> TextBlock.Paragraph(line.substring(0, tab), line.substring(tab + 1).replace('{', '﴿').replace('}', '﴾'))
      }
    }.toList()
  }
}
