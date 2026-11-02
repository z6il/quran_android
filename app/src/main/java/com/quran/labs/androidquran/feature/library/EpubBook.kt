package com.quran.labs.androidquran.feature.library

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * An EPUB read straight from its zip: the spine (the chapters in reading order) and the table
 * of contents, from the EPUB 3 nav page or the EPUB 2 NCX.
 */
class EpubBook private constructor(private val zip: ZipFile) : Closeable {
  class Chapter(val path: String)
  class TocEntry(val title: String, val chapter: Int, val depth: Int)

  var title: String? = null
    private set
  var author: String? = null
    private set
  val spine = mutableListOf<Chapter>()
  val toc = mutableListOf<TocEntry>()

  /** an entry of the zip, by its full path */
  fun open(path: String): InputStream? = zip.getEntry(path)?.let { zip.getInputStream(it) }

  override fun close() = zip.close()

  private fun parse() {
    val opf = parser(open("META-INF/container.xml") ?: error("not an EPUB")) { p ->
      var found: String? = null
      while (p.next() != XmlPullParser.END_DOCUMENT && found == null) {
        if (p.eventType == XmlPullParser.START_TAG && p.local() == "rootfile") found = p.attr("full-path")
      }
      found
    } ?: error("no package")
    val base = opf.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    val manifest = HashMap<String, Pair<String, String>>()
    var navPath: String? = null
    var ncxId: String? = null
    val itemRefs = mutableListOf<String>()
    parser(open(opf) ?: error("no package")) { p ->
      while (p.next() != XmlPullParser.END_DOCUMENT) {
        if (p.eventType != XmlPullParser.START_TAG) continue
        when (p.local()) {
          "title" -> if (title == null) title = p.nextText().trim().ifEmpty { null }
          "creator" -> if (author == null) author = p.nextText().trim().ifEmpty { null }
          "item" -> {
            val id = p.attr("id") ?: continue
            val href = resolve(base, p.attr("href") ?: continue)
            manifest[id] = href to p.attr("media-type").orEmpty()
            if (p.attr("properties")?.split(' ')?.contains("nav") == true) navPath = href
          }
          "spine" -> ncxId = p.attr("toc")
          "itemref" -> p.attr("idref")?.let(itemRefs::add)
        }
      }
    }
    itemRefs.mapNotNullTo(spine) { manifest[it]?.first?.let(::Chapter) }
    if (spine.isEmpty()) error("no chapters")
    val index = spine.withIndex().associate { (i, c) -> c.path to i }
    val nav = navPath
    val ncx = ncxId?.let { manifest[it]?.first }
    runCatching {
      if (nav != null) readNav(nav, index) else if (ncx != null) readNcx(ncx, index)
    }
  }

  private fun readNav(path: String, index: Map<String, Int>) {
    val base = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    parser(open(path) ?: return) { p ->
      var inToc = false
      var depth = 0
      while (p.next() != XmlPullParser.END_DOCUMENT) {
        when (p.eventType) {
          XmlPullParser.START_TAG -> when (p.local()) {
            "nav" -> inToc = p.attr("type") == "toc" || p.attr("epub:type") == "toc" || toc.isEmpty() && !inToc
            "ol" -> if (inToc) depth++
            "a" -> if (inToc) {
              val href = resolve(base, p.attr("href") ?: "").substringBefore('#')
              val text = p.nextText().trim()
              index[href]?.let { if (text.isNotEmpty()) toc += TocEntry(text, it, depth - 1) }
            }
          }
          XmlPullParser.END_TAG -> when (p.local()) {
            "ol" -> if (inToc) depth--
            "nav" -> inToc = false
          }
        }
      }
    }
  }

  private fun readNcx(path: String, index: Map<String, Int>) {
    val base = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    parser(open(path) ?: return) { p ->
      var depth = 0
      var label: String? = null
      while (p.next() != XmlPullParser.END_DOCUMENT) {
        when (p.eventType) {
          XmlPullParser.START_TAG -> when (p.local()) {
            "navPoint" -> depth++
            "text" -> label = p.nextText().trim()
            "content" -> {
              val href = resolve(base, p.attr("src") ?: "").substringBefore('#')
              val text = label
              index[href]?.let { if (!text.isNullOrEmpty()) toc += TocEntry(text, it, depth - 1) }
              label = null
            }
          }
          XmlPullParser.END_TAG -> if (p.local() == "navPoint") depth--
        }
      }
    }
  }

  companion object {
    fun open(file: File): EpubBook {
      val book = EpubBook(ZipFile(file))
      try {
        book.parse()
      } catch (e: Exception) {
        book.close()
        throw e
      }
      return book
    }

    /** [href] relative to [base], with "../" resolved and %-escapes decoded */
    fun resolve(base: String, href: String): String {
      val parts = ArrayDeque<String>()
      (base + runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)).split('/').forEach {
        when (it) {
          "", "." -> Unit
          ".." -> parts.removeLastOrNull()
          else -> parts.addLast(it)
        }
      }
      return parts.joinToString("/")
    }

    private fun <T> parser(input: InputStream, read: (XmlPullParser) -> T): T = input.use {
      val p = Xml.newPullParser()
      p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
      p.setInput(it, null)
      read(p)
    }

    private fun XmlPullParser.local(): String = name.substringAfter(':')

    private fun XmlPullParser.attr(name: String): String? = (0 until attributeCount).firstOrNull {
      getAttributeName(it) == name || getAttributeName(it).substringAfter(':') == name && !name.contains(':')
    }?.let { getAttributeValue(it) }
  }
}

/** A PDF's pages, drawn one at a time: PdfRenderer can't draw two at once. */
class PdfDocument(file: File) : Closeable {
  private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
  private val renderer = PdfRenderer(descriptor)
  val pageCount: Int get() = renderer.pageCount

  /** a page's height over its width; measuring opens the page, so a long book does it as it goes */
  @Synchronized
  fun ratio(index: Int): Float = renderer.openPage(index).use { it.height / it.width.toFloat().coerceAtLeast(1f) }

  @Synchronized
  fun render(index: Int, width: Int): Bitmap = renderer.openPage(index).use { page ->
    val height = (width * page.height / page.width.toFloat().coerceAtLeast(1f)).toInt().coerceAtLeast(1)
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
      it.eraseColor(android.graphics.Color.WHITE)
      page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    }
  }

  @Synchronized
  override fun close() {
    renderer.close()
    descriptor.close()
  }

  companion object {
    fun pageCount(file: File): Int = PdfDocument(file).use { it.pageCount }
  }
}
