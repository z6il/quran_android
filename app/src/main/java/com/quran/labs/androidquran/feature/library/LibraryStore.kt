package com.quran.labs.androidquran.feature.library

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.OpenableColumns
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.R
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class BookKind { TEXT, PDF, EPUB }

/** what the reader has made of a book; the catalogue's books start at [NONE] */
enum class ReadingStatus { NONE, WANT, READING, FINISHED }

/**
 * A book on the reader's shelves: downloaded from the catalogue, or a file they added.
 * [position] is a paragraph (text), a page (PDF) or a chapter (EPUB), with [offset] how far
 * through a chapter; [total] how many there are.
 */
data class LibraryBook(
  val id: String,
  val kind: BookKind,
  val title: String,
  val author: String,
  val shelf: Shelf,
  val file: String,
  val status: ReadingStatus,
  val position: Int,
  val offset: Float,
  val total: Int,
  val added: Long,
  val opened: Long
) {
  val progress: Float get() = if (total <= 0) 0f else ((position + offset) / total).coerceIn(0f, 1f)
  val imported: Boolean get() = id.startsWith(IMPORTED)

  companion object {
    const val IMPORTED = "file:"
  }
}

/** A place in a book the reader kept, with a note if they wrote one. */
data class BookMark(val id: Long, val book: String, val position: Int, val offset: Float, val label: String, val note: String, val created: Long)

/** A stretch of a paragraph the reader highlighted, from [start] to [end] in its text. */
data class BookHighlight(
  val id: Long,
  val book: String,
  val position: Int,
  val start: Int,
  val end: Int,
  val color: HighlightColor,
  val note: String,
  /** the words highlighted, kept for the list of highlights */
  val text: String,
  val created: Long,
  /** on a PDF page, the boxes it covers (fractions of the page), whichever text the page was read with */
  val boxes: List<PdfWord> = emptyList()
)

enum class HighlightColor(val argb: Long, val title: Int) {
  YELLOW(0xFFFFD54F, R.string.library_color_yellow),
  GREEN(0xFF81C784, R.string.library_color_green),
  BLUE(0xFF64B5F6, R.string.library_color_blue),
  PINK(0xFFF48FB1, R.string.library_color_pink)
}

/** Download progress (0..1), or a failure, for each catalogue book being fetched. */
sealed interface BookDownload {
  data class Running(val fraction: Float) : BookDownload
  data class Failed(val message: String) : BookDownload
}

@SingleIn(AppScope::class)
class LibraryStore @Inject constructor(@ApplicationContext private val context: Context) {
  private val helper = object : SQLiteOpenHelper(context, "library.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
        "CREATE TABLE books(id TEXT PRIMARY KEY, kind TEXT NOT NULL, title TEXT NOT NULL, author TEXT NOT NULL DEFAULT '', " +
          "shelf TEXT NOT NULL, file TEXT NOT NULL, status INTEGER NOT NULL DEFAULT 0, position INTEGER NOT NULL DEFAULT 0, " +
          "offset REAL NOT NULL DEFAULT 0, total INTEGER NOT NULL DEFAULT 0, added INTEGER NOT NULL, opened INTEGER NOT NULL DEFAULT 0)"
      )
      db.execSQL(
        "CREATE TABLE marks(id INTEGER PRIMARY KEY AUTOINCREMENT, book TEXT NOT NULL, position INTEGER NOT NULL, " +
          "offset REAL NOT NULL DEFAULT 0, label TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL)"
      )
      createHighlights(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
      if (oldVersion < 2) createHighlights(db)
      if (oldVersion == 2) db.execSQL("ALTER TABLE highlights ADD COLUMN boxes TEXT NOT NULL DEFAULT ''")
    }

    private fun createHighlights(db: SQLiteDatabase) {
      db.execSQL(
        "CREATE TABLE highlights(id INTEGER PRIMARY KEY AUTOINCREMENT, book TEXT NOT NULL, position INTEGER NOT NULL, " +
          "start INTEGER NOT NULL, \"end\" INTEGER NOT NULL, color TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', " +
          "text TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL, boxes TEXT NOT NULL DEFAULT '')"
      )
    }
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val dir = File(context.filesDir, "library")

  private val mutableBooks = MutableStateFlow<List<LibraryBook>>(emptyList())
  /** the most recently opened first */
  val books: StateFlow<List<LibraryBook>> = mutableBooks.asStateFlow()

  private val mutableDownloads = MutableStateFlow<Map<String, BookDownload>>(emptyMap())
  val downloads: StateFlow<Map<String, BookDownload>> = mutableDownloads.asStateFlow()

  suspend fun refresh() = withContext(Dispatchers.IO) {
    mutableBooks.value = helper.readableDatabase.rawQuery("SELECT * FROM books ORDER BY opened DESC, added DESC", null).use { c ->
      buildList { while (c.moveToNext()) add(c.toBook()) }
    }
  }

  suspend fun book(id: String): LibraryBook? = withContext(Dispatchers.IO) {
    helper.readableDatabase.rawQuery("SELECT * FROM books WHERE id = ?", arrayOf(id)).use { if (it.moveToFirst()) it.toBook() else null }
  }

  /** fetches [book] from OpenITI in the background; [downloads] follows it */
  fun download(book: CatalogBook) {
    if (mutableDownloads.value[book.id] is BookDownload.Running) return
    mutableDownloads.update { it + (book.id to BookDownload.Running(0f)) }
    scope.launch {
      val file = File(dir, "text/" + book.id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".txt")
      try {
        val total = OpenItiText.download(book.url, file, book.bytes) { f ->
          mutableDownloads.update { it + (book.id to BookDownload.Running(f)) }
        }
        helper.writableDatabase.insertWithOnConflict("books", null, ContentValues().apply {
          put("id", book.id)
          put("kind", BookKind.TEXT.name)
          put("title", book.title)
          put("author", book.author)
          put("shelf", book.shelf.key)
          put("file", file.path)
          put("total", total)
          put("added", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_IGNORE)
        // downloaded again after the file was lost: keep its place and marks
        helper.writableDatabase.execSQL("UPDATE books SET file = ?, total = ? WHERE id = ?", arrayOf(file.path, total, book.id))
        mutableDownloads.update { it - book.id }
        refresh()
      } catch (e: Exception) {
        file.delete()
        mutableDownloads.update { it + (book.id to BookDownload.Failed(e.message ?: e.javaClass.simpleName)) }
      }
    }
  }

  /** copies a PDF or EPUB the reader picked into the app, so it stays readable; null when it isn't one */
  suspend fun import(uri: Uri, shelf: Shelf): LibraryBook? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
      if (it.moveToFirst()) it.getString(0) else null
    } ?: uri.lastPathSegment ?: "book"
    val type = resolver.getType(uri).orEmpty()
    val kind = when {
      type == "application/pdf" || name.endsWith(".pdf", true) -> BookKind.PDF
      type == "application/epub+zip" || name.endsWith(".epub", true) -> BookKind.EPUB
      else -> return@withContext null
    }
    val id = LibraryBook.IMPORTED + UUID.randomUUID()
    val file = File(dir, "files/" + id.removePrefix(LibraryBook.IMPORTED) + if (kind == BookKind.PDF) ".pdf" else ".epub")
    file.parentFile!!.mkdirs()
    resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return@withContext null
    var title = name.substringBeforeLast('.').replace('_', ' ').trim()
    var author = ""
    var total = 0
    runCatching {
      if (kind == BookKind.EPUB) {
        EpubBook.open(file).use { epub ->
          epub.title?.let { title = it }
          author = epub.author.orEmpty()
          total = epub.spine.size
        }
      } else {
        total = PdfDocument.pageCount(file)
      }
    }.onFailure {
      file.delete()
      return@withContext null
    }
    val now = System.currentTimeMillis()
    helper.writableDatabase.insert("books", null, ContentValues().apply {
      put("id", id)
      put("kind", kind.name)
      put("title", title)
      put("author", author)
      put("shelf", shelf.key)
      put("file", file.path)
      put("total", total)
      put("added", now)
    })
    refresh()
    if (kind == BookKind.PDF) PdfTextStore.get(context).start(id, file.path, TextSource.LAYER)
    book(id)
  }

  suspend fun savePosition(id: String, position: Int, offset: Float = 0f) = withContext(Dispatchers.IO) {
    helper.writableDatabase.execSQL("UPDATE books SET position = ?, offset = ? WHERE id = ?", arrayOf<Any>(position, offset, id))
    mutableBooks.update { list -> list.map { if (it.id == id) it.copy(position = position, offset = offset) else it } }
  }

  /** opening a book puts it first, and marks it as being read unless it was finished */
  suspend fun opened(id: String) = withContext(Dispatchers.IO) {
    helper.writableDatabase.execSQL(
      "UPDATE books SET opened = ?, status = CASE WHEN status = ? THEN status ELSE ? END WHERE id = ?",
      arrayOf<Any>(System.currentTimeMillis(), ReadingStatus.FINISHED.ordinal, ReadingStatus.READING.ordinal, id)
    )
    refresh()
  }

  suspend fun setStatus(id: String, status: ReadingStatus) = set(id, "status", status.ordinal)

  suspend fun setShelf(id: String, shelf: Shelf) = set(id, "shelf", shelf.key)

  suspend fun rename(id: String, title: String) = set(id, "title", title)

  private suspend fun set(id: String, column: String, value: Any) = withContext(Dispatchers.IO) {
    helper.writableDatabase.execSQL("UPDATE books SET $column = ? WHERE id = ?", arrayOf(value, id))
    refresh()
  }

  /** a catalogue book the reader hasn't downloaded yet can still be put on a shelf to read later */
  suspend fun want(book: CatalogBook) = withContext(Dispatchers.IO) {
    helper.writableDatabase.insertWithOnConflict("books", null, ContentValues().apply {
      put("id", book.id)
      put("kind", BookKind.TEXT.name)
      put("title", book.title)
      put("author", book.author)
      put("shelf", book.shelf.key)
      put("file", "")
      put("status", ReadingStatus.WANT.ordinal)
      put("added", System.currentTimeMillis())
    }, SQLiteDatabase.CONFLICT_IGNORE)
    refresh()
  }

  /** removes the book and its file; its marks go too */
  suspend fun delete(id: String) = withContext(Dispatchers.IO) {
    book(id)?.file?.takeIf { it.isNotEmpty() }?.let { File(it).delete() }
    helper.writableDatabase.delete("books", "id = ?", arrayOf(id))
    helper.writableDatabase.delete("marks", "book = ?", arrayOf(id))
    helper.writableDatabase.delete("highlights", "book = ?", arrayOf(id))
    PdfTextStore.get(context).delete(id)
    refresh()
  }

  suspend fun marks(book: String): List<BookMark> = withContext(Dispatchers.IO) {
    helper.readableDatabase.rawQuery("SELECT * FROM marks WHERE book = ? ORDER BY position, offset", arrayOf(book)).use { c ->
      buildList {
        while (c.moveToNext()) {
          add(BookMark(c.getLong(0), c.getString(1), c.getInt(2), c.getFloat(3), c.getString(4), c.getString(5), c.getLong(6)))
        }
      }
    }
  }

  suspend fun addMark(book: String, position: Int, offset: Float, label: String, note: String) = withContext(Dispatchers.IO) {
    helper.writableDatabase.insert("marks", null, ContentValues().apply {
      put("book", book)
      put("position", position)
      put("offset", offset)
      put("label", label)
      put("note", note)
      put("created", System.currentTimeMillis())
    })
    Unit
  }

  suspend fun editMark(id: Long, note: String) = withContext(Dispatchers.IO) {
    helper.writableDatabase.execSQL("UPDATE marks SET note = ? WHERE id = ?", arrayOf<Any>(note, id))
  }

  suspend fun deleteMark(id: Long) = withContext(Dispatchers.IO) {
    helper.writableDatabase.delete("marks", "id = ?", arrayOf(id.toString()))
    Unit
  }

  /** in reading order */
  suspend fun highlights(book: String): List<BookHighlight> = withContext(Dispatchers.IO) {
    helper.readableDatabase.rawQuery(
      "SELECT id, book, position, start, \"end\", color, note, text, created, boxes FROM highlights WHERE book = ? ORDER BY position, start",
      arrayOf(book)
    ).use { c ->
      buildList {
        while (c.moveToNext()) {
          val color = HighlightColor.entries.firstOrNull { it.name == c.getString(5) } ?: HighlightColor.YELLOW
          add(
            BookHighlight(
              c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3), c.getInt(4), color, c.getString(6), c.getString(7), c.getLong(8),
              PdfTextStore.decode(c.getString(9))
            )
          )
        }
      }
    }
  }

  /** a new highlight over others in the paragraph replaces where they overlap */
  suspend fun addHighlight(
    book: String,
    position: Int,
    start: Int,
    end: Int,
    color: HighlightColor,
    text: String,
    boxes: List<PdfWord> = emptyList()
  ) = withContext(Dispatchers.IO) {
    val db = helper.writableDatabase
    // on a PDF page, where words are counted depends on the text used: boxes say where it is
    if (boxes.isEmpty()) {
      db.delete(
        "highlights", "book = ? AND position = ? AND start < ? AND \"end\" > ?",
        arrayOf(book, position.toString(), end.toString(), start.toString())
      )
    }
    db.insert("highlights", null, ContentValues().apply {
      put("book", book)
      put("position", position)
      put("start", start)
      put("end", end)
      put("color", color.name)
      put("text", text)
      put("created", System.currentTimeMillis())
      put("boxes", PdfTextStore.encode(boxes))
    })
    Unit
  }

  suspend fun editHighlight(id: Long, color: HighlightColor, note: String) = withContext(Dispatchers.IO) {
    helper.writableDatabase.execSQL("UPDATE highlights SET color = ?, note = ? WHERE id = ?", arrayOf<Any>(color.name, note, id))
  }

  suspend fun deleteHighlight(id: Long) = withContext(Dispatchers.IO) {
    helper.writableDatabase.delete("highlights", "id = ?", arrayOf(id.toString()))
    Unit
  }

  private fun Cursor.toBook() = LibraryBook(
    id = getString(getColumnIndexOrThrow("id")),
    kind = BookKind.valueOf(getString(getColumnIndexOrThrow("kind"))),
    title = getString(getColumnIndexOrThrow("title")),
    author = getString(getColumnIndexOrThrow("author")),
    shelf = Shelf.of(getString(getColumnIndexOrThrow("shelf"))),
    file = getString(getColumnIndexOrThrow("file")),
    status = ReadingStatus.entries[getInt(getColumnIndexOrThrow("status"))],
    position = getInt(getColumnIndexOrThrow("position")),
    offset = getFloat(getColumnIndexOrThrow("offset")),
    total = getInt(getColumnIndexOrThrow("total")),
    added = getLong(getColumnIndexOrThrow("added")),
    opened = getLong(getColumnIndexOrThrow("opened"))
  )
}
