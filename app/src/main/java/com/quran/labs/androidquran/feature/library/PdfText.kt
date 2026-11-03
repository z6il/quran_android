package com.quran.labs.androidquran.feature.library

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.googlecode.tesseract.android.TessBaseAPI
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.feature.hadith.HadithHighlight
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** A word on a PDF page, its box a fraction of the page's width and height. */
data class PdfWord(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
  fun contains(x: Float, y: Float, slack: Float = 0.006f) =
    x >= left - slack && x <= right + slack && y >= top - slack && y <= bottom + slack
}

/** Where a PDF's text comes from: the text stored in the file, or recognised from the page images. */
enum class TextSource {
  /** the text stored in the file */
  LAYER,
  /** Tesseract's Arabic */
  OCR,
  /** OpenITI's model for printed Arabic: Tesseract only finds the lines */
  OPENITI
}

/** How far reading a book's text has got. */
data class TextJob(val source: TextSource, val done: Int, val total: Int, val downloadingModel: Float? = null, val failed: String? = null) {
  val running: Boolean get() = failed == null && done < total
}

/**
 * Each PDF page's words, from either source, kept apart so the reader can use whichever is
 * better for a book: a scan has only OCR; a file with poor text can be read again by OCR.
 */
class PdfTextStore private constructor(private val context: Context) {
  private val helper = object : SQLiteOpenHelper(context, "pdf_text.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
        "CREATE TABLE pages(book TEXT NOT NULL, page INTEGER NOT NULL, source TEXT NOT NULL, words TEXT NOT NULL, " +
          "PRIMARY KEY(book, page, source))"
      )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
  }
  private val prefs = context.getSharedPreferences("pdf_text", Context.MODE_PRIVATE)

  private val mutableJobs = MutableStateFlow<Map<String, TextJob>>(emptyMap())
  /** the books whose text is being read, or failed to be */
  val jobs: StateFlow<Map<String, TextJob>> = mutableJobs.asStateFlow()

  internal fun report(book: String, job: TextJob?) = mutableJobs.update { if (job == null) it - book else it + (book to job) }

  /** the page's words from [source]; null when that page hasn't been read */
  suspend fun words(book: String, page: Int, source: TextSource): List<PdfWord>? = withContext(Dispatchers.IO) {
    helper.readableDatabase.rawQuery(
      "SELECT words FROM pages WHERE book = ? AND page = ? AND source = ?", arrayOf(book, page.toString(), source.name)
    ).use { if (it.moveToFirst()) decode(it.getString(0)) else null }
  }

  fun save(book: String, page: Int, source: TextSource, words: List<PdfWord>) {
    helper.writableDatabase.insertWithOnConflict("pages", null, ContentValues().apply {
      put("book", book)
      put("page", page)
      put("source", source.name)
      put("words", encode(words))
    }, SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun donePages(book: String, source: TextSource): Set<Int> =
    helper.readableDatabase.rawQuery("SELECT page FROM pages WHERE book = ? AND source = ?", arrayOf(book, source.name)).use { c ->
      buildSet { while (c.moveToNext()) add(c.getInt(0)) }
    }

  /** how many pages have words from [source] (a page read but empty doesn't count) */
  suspend fun pagesWithText(book: String, source: TextSource): Int = withContext(Dispatchers.IO) {
    helper.readableDatabase.rawQuery(
      "SELECT COUNT(*) FROM pages WHERE book = ? AND source = ? AND words != ''", arrayOf(book, source.name)
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
  }

  /** the pages where every word of [query] appears, in order */
  suspend fun search(book: String, source: TextSource, query: String): List<Int> = withContext(Dispatchers.IO) {
    val found = mutableListOf<Int>()
    helper.readableDatabase.rawQuery(
      "SELECT page, words FROM pages WHERE book = ? AND source = ? AND words != '' ORDER BY page", arrayOf(book, source.name)
    ).use { c ->
      while (c.moveToNext()) {
        coroutineContext.ensureActive()
        val text = decode(c.getString(1)).joinToString(" ") { it.text }
        if (HadithHighlight.ranges(text, query).isNotEmpty()) found += c.getInt(0)
      }
    }
    found
  }

  /** the source the reader chose for [book], or null to pick the one with more text */
  fun chosen(book: String): TextSource? = prefs.getString(book, null)?.let { runCatching { TextSource.valueOf(it) }.getOrNull() }

  fun choose(book: String, source: TextSource) = prefs.edit { putString(book, source.name) }

  fun delete(book: String) {
    helper.writableDatabase.delete("pages", "book = ?", arrayOf(book))
    prefs.edit { remove(book) }
  }

  /** reads [book]'s text from [source] in the background, starting at [from] */
  fun start(book: String, file: String, source: TextSource, from: Int = 0) {
    val request = OneTimeWorkRequestBuilder<PdfTextWorker>()
      .setInputData(workDataOf(PdfTextWorker.BOOK to book, PdfTextWorker.FILE to file, PdfTextWorker.SOURCE to source.name, PdfTextWorker.FROM to from))
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(workName(book, source), ExistingWorkPolicy.REPLACE, request)
  }

  fun stop(book: String, source: TextSource) {
    WorkManager.getInstance(context).cancelUniqueWork(workName(book, source))
    report(book, null)
  }

  private fun workName(book: String, source: TextSource) = "pdf-text:$source:$book"

  companion object {
    @Volatile
    private var instance: PdfTextStore? = null

    fun get(context: Context): PdfTextStore =
      instance ?: synchronized(this) { instance ?: PdfTextStore(context.applicationContext).also { instance = it } }

    // a word a line: text, then its box in ten-thousandths of the page
    internal fun encode(words: List<PdfWord>): String = words.joinToString("\n") { w ->
      w.text.replace('\t', ' ').replace('\n', ' ') + "\t" +
        listOf(w.left, w.top, w.right, w.bottom).joinToString("\t") { (it.coerceIn(0f, 1f) * 10000).toInt().toString() }
    }

    internal fun decode(s: String): List<PdfWord> = if (s.isEmpty()) emptyList() else s.split('\n').mapNotNull { line ->
      val p = line.split('\t')
      if (p.size < 5) null else PdfWord(p[0], p[1].toFloat() / 10000, p[2].toFloat() / 10000, p[3].toFloat() / 10000, p[4].toFloat() / 10000)
    }
  }
}

/** A file an OCR engine needs, downloaded once. */
class OcrFile(val url: String, val path: String, val bytes: Long)

/** What each OCR engine downloads: nothing is in the app until the reader asks. */
object OcrModels {
  /** Tesseract's best Arabic (Apache 2.0) */
  private val TESSERACT = OcrFile("https://github.com/tesseract-ocr/tessdata_best/raw/main/ara.traineddata", "tessdata/ara.traineddata", 12_603_724L)
  /** Tesseract's small Arabic, only to find the lines for OpenITI's model */
  private val LAYOUT = OcrFile("https://github.com/tesseract-ocr/tessdata_fast/raw/main/ara.traineddata", "layout/tessdata/ara.traineddata", 1_432_056L)
  /** OpenITI's printed Arabic-script model (Kiessling, CC0), as ONNX, 8-bit */
  private val OPENITI = OcrFile(
    "https://github.com/z6il/quran_android/releases/download/ocr-openiti-v1/openiti_arabic_q8.onnx", "openiti/openiti_arabic_q8.onnx", 4_139_900L
  )
  fun files(engine: TextSource): List<OcrFile> = when (engine) {
    TextSource.OCR -> listOf(TESSERACT)
    TextSource.OPENITI -> listOf(LAYOUT, OPENITI)
    TextSource.LAYER -> emptyList()
  }

  fun dir(context: Context) = File(context.filesDir, "ocr")

  fun file(context: Context, f: OcrFile) = File(dir(context), f.path)

  /** Tesseract's data folder for [engine]: the one holding its "tessdata" */
  fun tessData(context: Context, engine: TextSource) = if (engine == TextSource.OPENITI) File(dir(context), "layout") else dir(context)

  fun bytes(engine: TextSource) = files(engine).sumOf { it.bytes }

  fun installed(context: Context, engine: TextSource) = files(engine).all { f -> file(context, f).let { it.exists() && it.length() == f.bytes } }

  fun download(context: Context, engine: TextSource, onProgress: (Float) -> Unit) {
    val all = files(engine)
    val total = all.sumOf { it.bytes }.toFloat()
    var before = 0L
    all.forEach { f ->
      val target = file(context, f)
      if (!(target.exists() && target.length() == f.bytes)) fetch(f, target) { read -> onProgress(((before + read) / total).coerceAtMost(1f)) }
      before += f.bytes
    }
  }

  private fun fetch(f: OcrFile, target: File, onRead: (Long) -> Unit) {
    target.parentFile!!.mkdirs()
    val tmp = File(target.path + ".tmp")
    var connection = URL(f.url).openConnection() as HttpURLConnection
    try {
      var redirects = 0
      while (connection.responseCode in 300..399 && redirects++ < 5) {
        val next = connection.getHeaderField("Location")
        connection.disconnect()
        connection = URL(next).openConnection() as HttpURLConnection
      }
      if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
      var read = 0L
      connection.inputStream.use { input ->
        tmp.outputStream().use { out ->
          val buffer = ByteArray(64 * 1024)
          while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            read += n
            onRead(read)
          }
        }
      }
      if (tmp.length() != f.bytes) error("incomplete")
      if (!tmp.renameTo(target)) error("couldn't save")
    } finally {
      tmp.delete()
      connection.disconnect()
    }
  }
}

/** Reads a PDF's words page by page, from its own text or by OCR, into [PdfTextStore]. */
class PdfTextWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    init(applicationContext)
    val book = inputData.getString(BOOK) ?: return@withContext Result.failure()
    val file = File(inputData.getString(FILE) ?: return@withContext Result.failure())
    val source = TextSource.valueOf(inputData.getString(SOURCE) ?: return@withContext Result.failure())
    val from = inputData.getInt(FROM, 0)
    val store = PdfTextStore.get(applicationContext)
    val pdf = runCatching { PdfDocument(file) }.getOrElse {
      store.report(book, TextJob(source, 0, 0, failed = it.message ?: "PDF"))
      return@withContext Result.failure()
    }
    val total = pdf.pageCount
    val done = store.donePages(book, source).toMutableSet()
    // from where the reader is, then round to the start
    val order = (from until total) + (0 until from.coerceAtMost(total))
    val todo = order.filter { it !in done }
    try {
      if (todo.isEmpty()) return@withContext Result.success()
      store.report(book, TextJob(source, done.size, total))
      if (source != TextSource.LAYER) setForeground(foreground(done.size, total))
      when (source) {
        TextSource.LAYER -> readLayer(file, todo) { page, words ->
          store.save(book, page, source, words)
          done += page
          store.report(book, TextJob(source, done.size, total))
        }
        TextSource.OCR, TextSource.OPENITI -> {
          if (!OcrModels.installed(applicationContext, source)) {
            OcrModels.download(applicationContext, source) { f -> store.report(book, TextJob(source, done.size, total, downloadingModel = f)) }
          }
          // OCR is heavy: background priority from the start, so the engines' own threads have it too
          android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
          val api = TessBaseAPI()
          val openIti = if (source == TextSource.OPENITI) OpenItiOcr(applicationContext) else null
          try {
            if (!api.init(OcrModels.tessData(applicationContext, source).path, "ara", TessBaseAPI.OEM_LSTM_ONLY)) error("OCR")
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            for (page in todo) {
              coroutineContext.ensureActive()
              // OCR is heavy: the reader on screen comes first
              android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
              // printed honorifics come out jumbled: put back as their words
              val words = Honorifics.fix(if (openIti != null) openIti.recognise(api, pdf, page) else recognise(api, pdf, page))
              store.save(book, page, source, words)
              done += page
              store.report(book, TextJob(source, done.size, total))
              if (done.size % 5 == 0) setForeground(foreground(done.size, total))
            }
          } finally {
            openIti?.close()
            api.recycle()
          }
        }
      }
      store.report(book, null)
      Result.success()
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) throw e
      store.report(book, TextJob(source, done.size, total, failed = e.message ?: e.javaClass.simpleName))
      Result.failure()
    } finally {
      pdf.close()
    }
  }

  /** a page drawn at about 250 dpi, its words as Tesseract reads them, in reading order */
  private fun recognise(api: TessBaseAPI, pdf: PdfDocument, page: Int): List<PdfWord> {
    val bitmap = pdf.render(page, OCR_WIDTH)
    try {
      api.setImage(bitmap)
      api.getUTF8Text()
      val words = mutableListOf<PdfWord>()
      val it = api.resultIterator ?: return emptyList()
      try {
        it.begin()
        do {
          val text = it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim().orEmpty()
          if (text.isEmpty()) continue
          val r = it.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
          val w = bitmap.width.toFloat()
          val h = bitmap.height.toFloat()
          // ornaments and borders read as letters: unsure, or wider than any word
          if (it.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD) < MIN_CONFIDENCE || r.width() > w * 0.5f) continue
          words += PdfWord(text, r.left / w, r.top / h, r.right / w, r.bottom / h)
        } while (it.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
      } finally {
        it.delete()
      }
      api.clear()
      return fixCommas(words)
    } finally {
      bitmap.recycle()
    }
  }

  private fun foreground(done: Int, total: Int): ForegroundInfo {
    val manager = applicationContext.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL, applicationContext.getString(R.string.library_ocr_channel), NotificationManager.IMPORTANCE_LOW)
      )
    }
    val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(applicationContext.getString(R.string.library_ocr_running))
      .setProgress(total, done, false)
      .setOngoing(true)
      .setSilent(true)
      .build()
    return if (Build.VERSION.SDK_INT >= 29) {
      ForegroundInfo(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      ForegroundInfo(NOTIFICATION, notification)
    }
  }

  companion object {
    const val BOOK = "book"
    const val FILE = "file"
    const val SOURCE = "source"
    const val FROM = "from"
    private const val CHANNEL = "library_text"
    private const val NOTIFICATION = 7301
    // about 300 dpi for a book page: footnotes are small
    private const val OCR_WIDTH = 2200
    // below this it's an ornament or a smudge; vowelled words often score low, so it stays low
    private const val MIN_CONFIDENCE = 12f

    /** the text stored in the PDF, word by word with where each sits */
    internal fun readLayer(file: File, pages: List<Int>, onPage: (Int, List<PdfWord>) -> Unit) {
      PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
        for (page in pages.sorted()) {
          val stripper = WordStripper()
          stripper.startPage = page + 1
          stripper.endPage = page + 1
          runCatching { stripper.writeText(doc, StringWriter()) }
          onPage(page, stripper.finish())
        }
      }
    }

    fun init(context: Context) = PDFBoxResourceLoader.init(context.applicationContext)
  }
}

/**
 * Tesseract often reads the Arabic comma «،» as a closing quote «»»: one that closes nothing
 * is a comma.
 */
internal fun fixCommas(words: List<PdfWord>): List<PdfWord> {
  var open = false
  return words.map { w ->
    if ('«' !in w.text && '»' !in w.text) return@map w
    val text = buildString {
      w.text.forEach { c ->
        when {
          c == '«' -> { open = true; append(c) }
          c == '»' && open -> { open = false; append(c) }
          c == '»' -> append('،')
          else -> append(c)
        }
      }
    }
    w.copy(text = text)
  }
}

/**
 * PDFBox's text, kept as words with their boxes. Its words come left to right along a line;
 * an Arabic line is turned round so they read in order.
 */
private class WordStripper : PDFTextStripper() {
  private val words = mutableListOf<PdfWord>()
  private val line = mutableListOf<PdfWord>()

  init {
    sortByPosition = true
  }

  override fun writeString(text: String, positions: MutableList<TextPosition>) {
    if (positions.isEmpty() || text.isBlank()) return
    // the letters as they sit on the page, split at spaces into words
    val words = mutableListOf<MutableList<TextPosition>>(mutableListOf())
    positions.forEach { p -> if (p.unicode.isNullOrBlank()) words += mutableListOf<TextPosition>() else words.last() += p }
    words.filter { it.isNotEmpty() }.forEach { add(it) }
  }

  /**
   * A word's text is rebuilt from where its letters are, not the order the file lists them:
   * many PDFs store Arabic back to front, or as presentation forms. Arabic reads from the
   * right; a mark goes with the letter it sits over.
   */
  private fun add(letters: List<TextPosition>) {
    val pageWidth = letters[0].pageWidth.coerceAtLeast(1f)
    val pageHeight = letters[0].pageHeight.coerceAtLeast(1f)
    var left = Float.MAX_VALUE
    var right = 0f
    var top = Float.MAX_VALUE
    var bottom = 0f
    letters.forEach { p ->
      left = minOf(left, p.xDirAdj)
      right = maxOf(right, p.xDirAdj + p.widthDirAdj)
      // the height PDFBox gives is often short of the letters' tops
      top = minOf(top, p.yDirAdj - p.heightDir * 1.4f)
      bottom = maxOf(bottom, p.yDirAdj + p.heightDir * 0.35f)
    }
    val text = letters.joinToString("") { it.unicode }
    val arabic = text.any { it in '؀'..'ۿ' || it in 'ﭐ'..'﻿' }
    val ordered = if (!arabic) text else {
      val bases = mutableListOf<Pair<TextPosition, StringBuilder>>()
      letters.forEach { p ->
        if (p.unicode.all { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }) {
          // the letter it overlaps the most
          val mid = p.xDirAdj + p.widthDirAdj / 2
          bases.minByOrNull { (b, _) -> kotlin.math.abs(b.xDirAdj + b.widthDirAdj / 2 - mid) }?.second?.append(p.unicode)
        } else {
          bases += p to StringBuilder(p.unicode)
        }
      }
      bases.sortedByDescending { (b, _) -> b.xDirAdj + b.widthDirAdj / 2 }.joinToString("") { it.second }
    }
    line += PdfWord(ordered, left / pageWidth, top / pageHeight, right / pageWidth, bottom / pageHeight)
  }

  override fun writeLineSeparator() {
    flushLine()
    super.writeLineSeparator()
  }

  private fun flushLine() {
    if (line.isEmpty()) return
    val arabic = line.sumOf { w -> w.text.count { it in '؀'..'ۿ' || it in 'ﭐ'..'﻿' } }
    val latin = line.sumOf { w -> w.text.count { it in 'A'..'Z' || it in 'a'..'z' } }
    words += if (arabic > latin) line.sortedByDescending { it.left } else line.sortedBy { it.left }
    line.clear()
  }

  fun finish(): List<PdfWord> {
    flushLine()
    return words.map { it.copy(text = java.text.Normalizer.normalize(it.text, java.text.Normalizer.Form.NFKC)) }
  }
}
