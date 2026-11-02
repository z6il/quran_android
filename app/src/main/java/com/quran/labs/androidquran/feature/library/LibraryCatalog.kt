package com.quran.labs.androidquran.feature.library

import android.content.Context
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.feature.research.HadithLibrary
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The shelves, both for the catalogue and for the reader's own files. */
enum class Shelf(val key: String, val title: Int) {
  FIQH("fiqh", R.string.library_shelf_fiqh),
  USUL("usul", R.string.library_shelf_usul),
  AQIDA("aqida", R.string.library_shelf_aqida),
  HADITH("hadith", R.string.library_shelf_hadith),
  SHARH("sharh", R.string.library_shelf_sharh),
  MUSTALAH("mustalah", R.string.library_shelf_mustalah),
  TAFSIR("tafsir", R.string.library_shelf_tafsir),
  QURAN("quran", R.string.library_shelf_quran),
  SIRA("sira", R.string.library_shelf_sira),
  RAQAIQ("raqaiq", R.string.library_shelf_raqaiq),
  FATAWA("fatawa", R.string.library_shelf_fatawa),
  TARAJIM("tarajim", R.string.library_shelf_tarajim),
  IMAMS("imams", R.string.library_shelf_imams),
  OTHER("other", R.string.library_shelf_other);

  companion object {
    fun of(key: String): Shelf = entries.firstOrNull { it.key == key } ?: OTHER
  }
}

enum class Madhhab(val key: String, val title: Int) {
  HANAFI("hanafi", R.string.library_hanafi),
  MALIKI("maliki", R.string.library_maliki),
  SHAFII("shafii", R.string.library_shafii),
  HANBALI("hanbali", R.string.library_hanbali);

  companion object {
    fun of(key: String): Madhhab? = entries.firstOrNull { it.key == key }
  }
}

/** A classical book from OpenITI, downloaded as plain text when the reader wants it. */
data class CatalogBook(
  val id: String,
  val title: String,
  val author: String,
  /** the author's death, AH */
  val died: Int,
  val shelf: Shelf,
  val madhhab: Madhhab?,
  val chars: Int,
  /** the OpenITI repository, then the file's path in it */
  val path: String
) {
  val url: String
    get() = "https://raw.githubusercontent.com/OpenITI/" + path.substringBefore('/') + "/master/" + path.substringAfter('/')

  /** about a printed page's worth of letters each */
  val pages: Int get() = (chars / 1800).coerceAtLeast(1)

  /** Arabic letters take two bytes, with spaces and markup */
  val bytes: Long get() = chars * 2L

  internal val folded: String by lazy { HadithLibrary.normalizeArabic("$title $author") }
}

/** The catalogue of classical books (library_catalog.tsv), built from OpenITI's metadata. */
@SingleIn(AppScope::class)
class LibraryCatalog @Inject constructor(@ApplicationContext private val context: Context) {
  @Volatile
  private var cache: List<CatalogBook>? = null

  suspend fun all(): List<CatalogBook> = withContext(Dispatchers.IO) {
    cache ?: synchronized(this) { cache ?: load().also { cache = it } }
  }

  suspend fun byId(id: String): CatalogBook? = all().firstOrNull { it.id == id }

  suspend fun counts(): Map<Shelf, Int> = all().groupingBy { it.shelf }.eachCount()

  /** every word of [query] in the title or the author's name */
  suspend fun search(query: String, limit: Int = 150): List<CatalogBook> = withContext(Dispatchers.Default) {
    val words = HadithLibrary.normalizeArabic(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return@withContext emptyList()
    all().asSequence().filter { book -> words.all { it in book.folded } }.take(limit).toList()
  }

  private fun load(): List<CatalogBook> =
    context.assets.open("library_catalog.tsv").bufferedReader().useLines { lines ->
      lines.mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 8) return@mapNotNull null
        CatalogBook(p[0], p[1], p[2], p[3].toIntOrNull() ?: 0, Shelf.of(p[4]), Madhhab.of(p[5]), p[6].toIntOrNull() ?: 0, p[7])
      }.toList()
    }
}
