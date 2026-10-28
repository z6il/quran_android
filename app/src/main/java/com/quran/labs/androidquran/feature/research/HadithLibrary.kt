package com.quran.labs.androidquran.feature.research

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class HadithCollection(val slug: String, val nameAr: String, val nameEn: String, val count: Int)

/** a book (kitab) of a collection, e.g. Bukhari's Book of Belief */
data class HadithBook(val collection: String, val number: String, val nameAr: String, val nameEn: String, val count: Int)

data class HadithEntry(
  val collection: String,
  val number: String,
  val chapter: String,
  /** the full Arabic text, chain of narration included */
  val text: String,
  /** the prophetic text alone, where the source separates it (Bukhari, Muslim) */
  val matn: String,
  /** e.g. "Sahih (Darussalam)"; empty where the source gives none */
  val grade: String
) {
  val target get() = LinkTarget.Hadith(collection, number)
  val displayText get() = matn.ifBlank { text }
}

sealed interface HadithLibraryState {
  data object NotInstalled : HadithLibraryState
  data class Downloading(val fraction: Float) : HadithLibraryState
  data object Installed : HadithLibraryState
  data class Failed(val message: String) : HadithLibraryState
}

/**
 * The six books of hadith (Arabic text and gradings from sunnah.com), downloaded once as a
 * prebuilt SQLite file with a full-text index over the text without diacritics.
 */
@SingleIn(AppScope::class)
class HadithLibrary @Inject constructor(
  @ApplicationContext private val context: Context
) {
  private val file = File(File(context.filesDir, "hadith"), "hadith.db")
  private val mutableState = MutableStateFlow(if (file.exists()) HadithLibraryState.Installed else HadithLibraryState.NotInstalled)
  val state: StateFlow<HadithLibraryState> = mutableState.asStateFlow()

  @Volatile
  private var db: SQLiteDatabase? = null

  private fun database(): SQLiteDatabase? {
    if (!file.exists()) return null
    db?.let { return it }
    return synchronized(this) {
      db ?: SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).also { db = it }
    }
  }

  suspend fun download() = withContext(Dispatchers.IO) {
    if (mutableState.value is HadithLibraryState.Downloading) return@withContext
    mutableState.value = HadithLibraryState.Downloading(0f)
    val dir = file.parentFile!!.apply { mkdirs() }
    val tmp = File(dir, "hadith.db.tmp")
    try {
      var connection = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
      // GitHub release downloads redirect to another host
      var redirects = 0
      while (connection.responseCode in 300..399 && redirects++ < 5) {
        val next = connection.getHeaderField("Location")
        connection.disconnect()
        connection = URL(next).openConnection() as HttpURLConnection
      }
      if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
      val total = connection.contentLengthLong.takeIf { it > 0 } ?: EXPECTED_ZIP_BYTES
      var read = 0L
      val counting = object : java.io.FilterInputStream(connection.inputStream) {
        override fun read(b: ByteArray, off: Int, len: Int): Int {
          val n = super.read(b, off, len)
          if (n > 0) {
            read += n
            mutableState.value = HadithLibraryState.Downloading((read / total.toFloat()).coerceAtMost(1f))
          }
          return n
        }
      }
      ZipInputStream(counting.buffered()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null && !entry.name.endsWith(".db")) entry = zip.nextEntry
        if (entry == null) error("no database in download")
        tmp.outputStream().use { zip.copyTo(it) }
      }
      connection.disconnect()
      synchronized(this) {
        db?.close()
        db = null
        if (!tmp.renameTo(file)) error("couldn't install")
        allBooksCache = null
      }
      mutableState.value = HadithLibraryState.Installed
    } catch (e: Exception) {
      tmp.delete()
      mutableState.value = HadithLibraryState.Failed(e.message ?: e.javaClass.simpleName)
    }
  }

  /** the installed library's version; 0 when there is none */
  suspend fun version(): Int = withContext(Dispatchers.IO) {
    val db = database() ?: return@withContext 0
    runCatching {
      db.rawQuery("SELECT value FROM properties WHERE property = 'version'", null).use { if (it.moveToFirst()) it.getString(0).toInt() else 1 }
    }.getOrDefault(1)
  }

  suspend fun collections(): List<HadithCollection> = withContext(Dispatchers.IO) {
    val db = database() ?: return@withContext emptyList()
    db.rawQuery("SELECT slug, name_ar, name_en, count FROM collections ORDER BY position", null).use { c ->
      buildList { while (c.moveToNext()) add(HadithCollection(c.getString(0), c.getString(1), c.getString(2), c.getInt(3))) }
    }
  }

  @Volatile
  private var allBooksCache: List<HadithBook>? = null

  suspend fun books(collection: String): List<HadithBook> = allBooks().filter { it.collection == collection }

  /**
   * Every book of every collection with its number of hadith, for the topic index. Counted in
   * one pass over the hadith (a join per book took seconds) and kept for the session.
   */
  suspend fun allBooks(): List<HadithBook> = withContext(Dispatchers.IO) {
    allBooksCache?.let { return@withContext it }
    val db = database() ?: return@withContext emptyList()
    val counts = HashMap<String, Int>()
    db.rawQuery("SELECT collection, book, COUNT(*) FROM hadith GROUP BY collection, book", null).use { c ->
      while (c.moveToNext()) counts[c.getString(0) + "/" + c.getString(1)] = c.getInt(2)
    }
    db.rawQuery(
      "SELECT b.collection, b.number, b.name_ar, b.name_en FROM books b " +
        "JOIN collections c ON c.slug = b.collection ORDER BY c.position, b.position",
      null
    ).use { c ->
      buildList {
        while (c.moveToNext()) {
          val collection = c.getString(0)
          val number = c.getString(1)
          add(HadithBook(collection, number, c.getString(2) ?: "", c.getString(3) ?: "", counts["$collection/$number"] ?: 0))
        }
      }
    }.also { allBooksCache = it }
  }

  /** a book's hadith in the collection's order */
  suspend fun inBook(book: HadithBook): List<HadithEntry> = withContext(Dispatchers.IO) {
    val db = database() ?: return@withContext emptyList()
    db.rawQuery(
      "SELECT collection, number, chapter_ar, text_ar, matn_ar, grade FROM hadith WHERE collection = ? AND book = ? ORDER BY id",
      arrayOf(book.collection, book.number)
    ).use { c -> buildList { while (c.moveToNext()) add(c.toEntry()) } }
  }

  /** the same hadith all day, a different one each day, from Bukhari and Muslim */
  suspend fun ofTheDay(day: Long): HadithEntry? = withContext(Dispatchers.IO) {
    val db = database() ?: return@withContext null
    // a main narration (not a variant like "1995 b") that quotes the Prophet, short enough for a card
    val where = "collection IN ('bukhari', 'muslim') AND number NOT LIKE '% %' AND LENGTH(text_ar) BETWEEN 120 AND 520 " +
      "AND text_ar LIKE '%صلى الله عليه وسلم%' AND text_ar NOT LIKE '%بِهَذَا الإِسْنَادِ%'"
    // jump to a spot picked by the day and take the first fitting one from there: no full scan
    val (first, last) = db.rawQuery("SELECT MIN(id), MAX(id) FROM hadith WHERE collection IN ('bukhari', 'muslim')", null)
      .use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else return@withContext null }
    val start = first + Math.floorMod(day * 7919L, (last - first + 1).coerceAtLeast(1))
    val sql = "SELECT collection, number, chapter_ar, text_ar, matn_ar, grade FROM hadith WHERE id >= ? AND $where ORDER BY id LIMIT 1"
    db.rawQuery(sql, arrayOf(start.toString())).use { c -> if (c.moveToFirst()) c.toEntry() else null }
      ?: db.rawQuery(sql, arrayOf(first.toString())).use { c -> if (c.moveToFirst()) c.toEntry() else null }
  }

  /** words in any order, each matched as a prefix, diacritics ignored */
  suspend fun search(query: String, collection: String? = null, limit: Int = 100): List<HadithEntry> =
    withContext(Dispatchers.IO) {
      val db = database() ?: return@withContext emptyList()
      // a number alone looks the hadith up by its number, e.g. "1" -> Bukhari 1, Muslim 1, ...
      val number = query.trim().map { c -> if (c in '٠'..'٩') '0' + (c - '٠') else c }.joinToString("")
      if (number.isNotEmpty() && number.all(Char::isDigit)) {
        val sql = "SELECT collection, number, chapter_ar, text_ar, matn_ar, grade FROM hadith WHERE number = ?" +
          (if (collection != null) " AND collection = ?" else "") + " ORDER BY id LIMIT $limit"
        val args = if (collection != null) arrayOf(number, collection) else arrayOf(number)
        return@withContext db.rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add(c.toEntry()) } }
      }
      val words = normalizeArabic(query).split(Regex("\\s+")).filter { it.length > 1 }
      if (words.isEmpty()) return@withContext emptyList()
      val match = words.joinToString(" ") { "$it*" }
      val sql = buildString {
        append("SELECT h.collection, h.number, h.chapter_ar, h.text_ar, h.matn_ar, h.grade FROM hadith_fts f ")
        append("JOIN hadith h ON h.id = f.docid WHERE f.norm MATCH ?")
        if (collection != null) append(" AND h.collection = ?")
        append(" ORDER BY h.id LIMIT $limit")
      }
      val args = if (collection != null) arrayOf(match, collection) else arrayOf(match)
      db.rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add(c.toEntry()) } }
    }

  suspend fun hadith(target: LinkTarget.Hadith): HadithEntry? = withContext(Dispatchers.IO) {
    val db = database() ?: return@withContext null
    db.rawQuery(
      "SELECT collection, number, chapter_ar, text_ar, matn_ar, grade FROM hadith WHERE collection = ? AND number = ? LIMIT 1",
      arrayOf(target.collection, target.number)
    ).use { c -> if (c.moveToFirst()) c.toEntry() else null }
  }

  private fun android.database.Cursor.toEntry() = HadithEntry(
    getString(0), getString(1), getString(2) ?: "", getString(3), getString(4) ?: "", getString(5) ?: ""
  )

  companion object {
    const val DOWNLOAD_URL = "https://github.com/z6il/quran_android/releases/download/hadith-library-v2/hadith.zip"
    private const val EXPECTED_ZIP_BYTES = 23_824_969L
    /** the library this app expects; an older one installed is offered as an update */
    const val CURRENT_VERSION = 2
    /** collections whose books are only ranges of numbers, or single short books: left out of the topic index */
    val NOT_IN_TOPICS = setOf("ahmad", "darimi", "nawawi40", "qudsi40")

    private val HARAKAT = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640\\u200f\\u200e]")

    /** the same folding the index was built with: no diacritics, one form of alif, ya, ta marbuta */
    fun normalizeArabic(text: String): String {
      var s = HARAKAT.replace(text, "")
      for ((a, b) in listOf("أ" to "ا", "إ" to "ا", "آ" to "ا", "ٱ" to "ا", "ى" to "ي", "ة" to "ه", "ؤ" to "و", "ئ" to "ي")) {
        s = s.replace(a, b)
      }
      return s.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim()
    }
  }
}
