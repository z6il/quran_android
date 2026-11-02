package com.quran.labs.androidquran.feature.library

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.feature.home.AppIcons
import com.quran.labs.androidquran.feature.prayer.AppTopBar
import com.quran.labs.androidquran.feature.prayer.cardColor
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import java.io.File

/** A place in a book: a paragraph, page or chapter, and how far into it. */
data class BookPlace(val position: Int, val offset: Float = 0f)

/** An entry of a book's contents. */
data class ContentsEntry(val title: String, val place: BookPlace, val depth: Int)

/**
 * What a reader tells the frame around it: where the reader is, what to call that place, and
 * the book's contents. [jump] asks the reader to go somewhere; it sets it back to null.
 */
class ReaderState(start: BookPlace) {
  var place by mutableStateOf(start)
  var total by mutableIntStateOf(0)
  var label by mutableStateOf("")
  var contents by mutableStateOf<List<ContentsEntry>>(emptyList())
  var jump by mutableStateOf<BookPlace?>(null)
  var ready by mutableStateOf(false)
  var failed by mutableStateOf(false)
  /** the place as a fraction of the book, for the slider */
  val fraction: Float get() = if (total <= 1) 0f else ((place.position + place.offset) / (total - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
}

class ReaderActivity : AppCompatActivity() {
  @Inject lateinit var store: LibraryStore

  private var book by mutableStateOf<LibraryBook?>(null)
  private var state by mutableStateOf<ReaderState?>(null)
  private var textScale by mutableFloatStateOf(1f)

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)
    val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
    textScale = prefs.getFloat(KEY_SCALE, 1f)
    val id = intent.getStringExtra(EXTRA_BOOK) ?: return finish()
    lifecycleScope.launch {
      val b = store.book(id)
      if (b == null || b.file.isEmpty() || !File(b.file).exists()) {
        Toast.makeText(this@ReaderActivity, R.string.library_missing_file, Toast.LENGTH_LONG).show()
        finish()
        return@launch
      }
      store.opened(id)
      val start = if (intent.hasExtra(EXTRA_POSITION)) {
        BookPlace(intent.getIntExtra(EXTRA_POSITION, 0), intent.getFloatExtra(EXTRA_OFFSET, 0f))
      } else {
        BookPlace(b.position, b.offset)
      }
      book = b
      state = ReaderState(start)
    }

    setContentView(ComposeView(this).apply {
      setContent {
        QuranTheme {
          val b = book
          val s = state
          if (b != null && s != null) {
            Reader(b, s, textScale, onScale = { scale ->
              textScale = scale
              prefs.edit { putFloat(KEY_SCALE, scale) }
            })
          }
        }
      }
    })
  }

  override fun onPause() {
    super.onPause()
    save()
  }

  private fun save() {
    val b = book ?: return
    val s = state ?: return
    if (!s.ready) return
    lifecycleScope.launch { store.savePosition(b.id, s.place.position, s.place.offset) }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun Reader(book: LibraryBook, state: ReaderState, scale: Float, onScale: (Float) -> Unit) {
    val scope = rememberCoroutineScope()
    var finding by remember { mutableStateOf(false) }
    var showContents by remember { mutableStateOf(false) }
    var addingMark by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    var marksVersion by remember { mutableIntStateOf(0) }
    val find = remember { FindState() }
    var highlights by remember { mutableStateOf<List<BookHighlight>>(emptyList()) }
    var highlightsVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(highlightsVersion) { highlights = store.highlights(book.id) }
    /** the paragraph being highlighted, and its text */
    var holding by remember { mutableStateOf<Pair<Int, String>?>(null) }
    // a PDF's text: from the file, or OCR; what's chosen on a page, and a highlight tapped
    val pdfText = remember { PdfTextState(book.id, PdfTextStore.get(this@ReaderActivity)) }
    var selection by remember { mutableStateOf<PdfSelection?>(null) }
    var tapped by remember { mutableStateOf<BookHighlight?>(null) }
    var textMenu by remember { mutableStateOf(false) }
    var confirmOcr by remember { mutableStateOf(false) }
    val jobs by pdfText.store.jobs.collectAsState()
    val job = jobs[book.id]
    if (book.kind == BookKind.PDF) {
      LaunchedEffect(job, state.total) { refreshPdfText(pdfText, book, state.total, job) }
      // OCR follows the reader: a page they stay on that it hasn't reached is read next
      var asked by remember { mutableIntStateOf(-1) }
      LaunchedEffect(state.place.position, job?.running, pdfText.version) {
        val running = job ?: return@LaunchedEffect
        if (running.source != TextSource.OCR || !running.running) return@LaunchedEffect
        kotlinx.coroutines.delay(3000)
        val page = state.place.position
        if (page != asked && pdfText.store.words(book.id, page, TextSource.OCR) == null) {
          asked = page
          pdfText.store.start(book.id, book.file, TextSource.OCR, page)
        }
      }
    }

    // keep the place as it changes, a moment after scrolling stops
    LaunchedEffect(state.place, state.ready) {
      if (!state.ready) return@LaunchedEffect
      kotlinx.coroutines.delay(1500)
      store.savePosition(book.id, state.place.position, state.place.offset)
    }

    Scaffold(
      topBar = {
        if (finding) {
          FindBar(find, onClose = {
            finding = false
            find.query = ""
          }, onJump = { state.jump = BookPlace(it) })
        } else {
          AppTopBar(book.title, onBack = ::finish) {
            if (book.kind == BookKind.TEXT || book.kind == BookKind.PDF && pdfText.source != null) {
              IconButton(onClick = { finding = true }) { Icon(QuranIcons.Search, contentDescription = stringResource(R.string.library_find)) }
            }
            IconButton(onClick = { showContents = true }) { Icon(AppIcons.Contents, contentDescription = stringResource(R.string.library_contents)) }
            IconButton(onClick = { addingMark = true }) { Icon(AppIcons.Bookmark, contentDescription = stringResource(R.string.library_add_bookmark)) }
            if (book.kind == BookKind.PDF) {
              Box {
                IconButton(onClick = { textMenu = true }) { Icon(AppIcons.TextSize, contentDescription = stringResource(R.string.library_text_of_pdf)) }
                PdfTextMenu(textMenu, pdfText, job, state.total, onDismiss = { textMenu = false }, onChoose = { source ->
                  textMenu = false
                  pdfText.store.choose(book.id, source)
                  pdfText.source = source
                  selection = null
                }, onOcr = {
                  textMenu = false
                  confirmOcr = true
                }, onStop = {
                  textMenu = false
                  pdfText.store.stop(book.id, TextSource.OCR)
                })
              }
            }
            if (book.kind != BookKind.PDF) {
              Box {
                IconButton(onClick = { sizeMenu = true }) { Icon(AppIcons.TextSize, contentDescription = stringResource(R.string.library_text_size)) }
                DropdownMenu(sizeMenu, onDismissRequest = { sizeMenu = false }) {
                  SCALES.forEach { s ->
                    DropdownMenuItem(
                      text = { Text("${(s * 100).toInt()}%", fontWeight = if (s == scale) FontWeight.Bold else null) },
                      onClick = {
                        sizeMenu = false
                        onScale(s)
                      }
                    )
                  }
                }
              }
            }
          }
        }
      },
      bottomBar = {
        val chosen = selection
        if (chosen != null) {
          SelectionBar(chosen, onColor = { color ->
            selection = null
            scope.launch {
              store.addHighlight(book.id, chosen.page, chosen.range.first, chosen.range.last + 1, color, chosen.text, chosen.boxes)
              highlightsVersion++
            }
          }, onCopy = {
            copy(chosen.text)
            selection = null
          }, onClose = { selection = null })
        } else {
          ReaderBottom(book, state)
        }
      },
      containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
      Box(Modifier.padding(padding).fillMaxSize()) {
        when (book.kind) {
          BookKind.TEXT -> TextReader(File(book.file), state, scale, find, highlights, onHold = { i, text -> holding = i to text })
          BookKind.PDF -> Column {
            PdfTextBanner(pdfText, job, state.total, onOcr = { confirmOcr = true }, onRetry = {
              pdfText.store.start(book.id, book.file, job?.source ?: TextSource.OCR, state.place.position)
            })
            PdfReader(
              File(book.file), state, pdfText, highlights, find, selection,
              onSelection = { selection = it }, onTapHighlight = { tapped = it }
            )
          }
          BookKind.EPUB -> EpubReader(File(book.file), state, scale)
        }
        if (state.failed) {
          Text(stringResource(R.string.library_open_failed), modifier = Modifier.align(Alignment.Center).padding(24.dp))
        } else if (!state.ready) {
          CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
      }
    }

    if (showContents) {
      ModalBottomSheet(onDismissRequest = { showContents = false }) {
        ContentsSheet(book, state, marksVersion, highlights, onGo = { place ->
          showContents = false
          state.jump = place
        }, onDeleteMark = { mark ->
          scope.launch {
            store.deleteMark(mark.id)
            marksVersion++
          }
        })
      }
    }
    tapped?.let { h ->
      ModalBottomSheet(onDismissRequest = { tapped = null }) {
        Column(Modifier.padding(20.dp, 0.dp, 20.dp, 32.dp)) {
          ExistingHighlight(h, onEdit = { old, color, note ->
            tapped = null
            scope.launch {
              store.editHighlight(old.id, color, note)
              highlightsVersion++
            }
          }, onDelete = { old ->
            tapped = null
            scope.launch {
              store.deleteHighlight(old.id)
              highlightsVersion++
            }
          })
        }
      }
    }
    if (confirmOcr) {
      OcrDialog(pages = state.total, onStart = {
        confirmOcr = false
        // asking for OCR is choosing it; the file's own text stays a tap away
        pdfText.store.choose(book.id, TextSource.OCR)
        pdfText.store.start(book.id, book.file, TextSource.OCR, state.place.position)
      }, onDismiss = { confirmOcr = false })
    }
    holding?.let { (position, text) ->
      ModalBottomSheet(onDismissRequest = { holding = null }) {
        HighlightSheet(
          text = text,
          existing = highlights.filter { it.position == position },
          scale = scale,
          onAdd = { start, end, color ->
            holding = null
            scope.launch {
              store.addHighlight(book.id, position, start, end, color, text.substring(start, end))
              highlightsVersion++
            }
          },
          onEdit = { h, color, note ->
            scope.launch {
              store.editHighlight(h.id, color, note)
              highlightsVersion++
            }
          },
          onDelete = { h ->
            scope.launch {
              store.deleteHighlight(h.id)
              highlightsVersion++
            }
          }
        )
      }
    }
    if (addingMark) {
      var note by remember { mutableStateOf("") }
      AlertDialog(
        onDismissRequest = { addingMark = false },
        title = { Text(stringResource(R.string.library_add_bookmark)) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.library_bookmark_note)) }, modifier = Modifier.fillMaxWidth())
          }
        },
        confirmButton = {
          TextButton(onClick = {
            addingMark = false
            val place = state.place
            val label = state.label
            scope.launch {
              store.addMark(book.id, place.position, place.offset, label, note.trim())
              marksVersion++
              Toast.makeText(this@ReaderActivity, R.string.library_bookmark_added, Toast.LENGTH_SHORT).show()
            }
          }) { Text(stringResource(R.string.library_save)) }
        },
        dismissButton = { TextButton(onClick = { addingMark = false }) { Text(stringResource(R.string.library_cancel)) } }
      )
    }
  }

  private fun copy(text: String) {
    getSystemService(android.content.ClipboardManager::class.java)
      ?.setPrimaryClip(android.content.ClipData.newPlainText(book?.title ?: "", text))
    Toast.makeText(this, R.string.library_copied, Toast.LENGTH_SHORT).show()
  }

  /** how much text each source has, which is in use, and the file's own text read first */
  private suspend fun refreshPdfText(text: PdfTextState, book: LibraryBook, total: Int, job: TextJob?) {
    if (total <= 0) return
    val store = text.store
    text.layerPages = store.pagesWithText(book.id, TextSource.LAYER)
    text.ocrPages = store.pagesWithText(book.id, TextSource.OCR)
    val (layerDone, ocrDone) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      store.donePages(book.id, TextSource.LAYER).size to store.donePages(book.id, TextSource.OCR).size
    }
    text.layerRead = layerDone >= total
    text.ocrRead = ocrDone >= total
    val chosen = store.chosen(book.id)?.takeIf { (if (it == TextSource.LAYER) text.layerPages else text.ocrPages) > 0 }
    // unless the reader chose, whichever has more pages of text
    text.source = chosen ?: when {
      text.layerPages > 0 && text.layerPages >= text.ocrPages -> TextSource.LAYER
      text.ocrPages > 0 -> TextSource.OCR
      else -> null
    }
    text.version++
    // a PDF added before this version: look for its own text now
    if (!text.layerRead && job == null) store.start(book.id, book.file, TextSource.LAYER)
  }

  @Composable
  private fun ReaderBottom(book: LibraryBook, state: ReaderState) {
    if (!state.ready || state.total <= 1) return
    Surface(color = cardColor()) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 8.dp).padding(bottom = navigationBarPadding())) {
        var dragging by remember { mutableStateOf<Float?>(null) }
        Slider(
          value = dragging ?: state.fraction,
          onValueChange = { dragging = it },
          onValueChangeFinished = {
            dragging?.let { f -> state.jump = BookPlace((f * (state.total - 1)).toInt()) }
            dragging = null
          }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          // a PDF's place is its page, shown on the other side
          Text(
            if (book.kind == BookKind.PDF) "" else state.label,
            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          Spacer(Modifier.width(8.dp))
          val shownAt = dragging?.let { (it * (state.total - 1)).toInt() } ?: state.place.position
          Text(
            when (book.kind) {
              BookKind.PDF -> stringResource(R.string.library_page_of, shownAt + 1, state.total)
              BookKind.EPUB -> stringResource(R.string.library_chapter_of, shownAt + 1, state.total)
              BookKind.TEXT -> "${((dragging ?: state.fraction) * 100).toInt()}%"
            },
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }

  @Composable
  private fun ContentsSheet(book: LibraryBook, state: ReaderState, marksVersion: Int, highlights: List<BookHighlight>, onGo: (BookPlace) -> Unit, onDeleteMark: (BookMark) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var marks by remember { mutableStateOf<List<BookMark>>(emptyList()) }
    LaunchedEffect(marksVersion) { marks = store.marks(book.id) }
    Column(Modifier.fillMaxWidth()) {
      PrimaryTabRow(selectedTabIndex = tab) {
        Tab(tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.library_contents)) })
        Tab(tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.library_bookmarks)) })
        if (book.kind != BookKind.EPUB) Tab(tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.library_highlights)) })
      }
      LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        if (tab == 0) {
          if (state.contents.isEmpty()) item { SheetNote(stringResource(R.string.library_no_contents)) }
          items(state.contents) { entry ->
            Text(
              entry.title,
              style = if (entry.depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
              fontWeight = if (entry.depth == 0) FontWeight.Bold else null,
              maxLines = 2, overflow = TextOverflow.Ellipsis,
              modifier = Modifier
                .fillMaxWidth()
                .clickableRow { onGo(entry.place) }
                .padding(start = (20 + 16 * entry.depth.coerceAtMost(4)).dp, end = 20.dp, top = 12.dp, bottom = 12.dp)
            )
          }
        } else if (tab == 2) {
          if (highlights.isEmpty()) item { SheetNote(stringResource(R.string.library_no_highlights)) }
          items(highlights, key = { "h" + it.id }) { h ->
            Row(Modifier.fillMaxWidth().clickableRow { onGo(BookPlace(h.position)) }.padding(20.dp, 10.dp)) {
              Box(Modifier.padding(top = 6.dp).size(10.dp).background(Color(h.color.argb), CircleShape))
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Text(h.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (h.note.isNotBlank()) Text(h.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
              }
            }
          }
        } else {
          if (marks.isEmpty()) item { SheetNote(stringResource(R.string.library_no_bookmarks)) }
          items(marks, key = { it.id }) { mark ->
            Row(Modifier.fillMaxWidth().clickableRow { onGo(BookPlace(mark.position, mark.offset)) }.padding(20.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(AppIcons.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Text(mark.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (mark.note.isNotBlank()) Text(mark.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              IconButton(onClick = { onDeleteMark(mark) }) { Icon(AppIcons.Delete, contentDescription = stringResource(R.string.library_delete_bookmark)) }
            }
          }
        }
      }
    }
  }

  @Composable
  private fun SheetNote(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
  }

  companion object {
    private const val EXTRA_BOOK = "book"
    private const val EXTRA_POSITION = "position"
    private const val EXTRA_OFFSET = "offset"
    private const val PREFS = "library"
    private const val KEY_SCALE = "text_scale"
    private val SCALES = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.75f)

    fun intent(context: Context, book: String, mark: BookMark? = null): Intent =
      Intent(context, ReaderActivity::class.java).apply {
        putExtra(EXTRA_BOOK, book)
        if (mark != null) {
          putExtra(EXTRA_POSITION, mark.position)
          putExtra(EXTRA_OFFSET, mark.offset)
        }
      }
  }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = clickable(onClick = onClick)

@Composable
private fun navigationBarPadding() = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
private fun FindBar(find: FindState, onClose: () -> Unit, onJump: (Int) -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surface) {
    Row(
      Modifier.fillMaxWidth().padding(WindowInsets.statusBars.asPaddingValues()).padding(8.dp, 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = onClose) { Icon(QuranIcons.Close, contentDescription = null) }
      OutlinedTextField(
        find.query, { find.query = it }, singleLine = true,
        placeholder = { Text(stringResource(R.string.library_find)) },
        shape = RoundedCornerShape(50),
        modifier = Modifier.weight(1f)
      )
      val results = find.results
      if (find.query.isNotBlank() && results != null) {
        Text(
          if (results.isEmpty()) stringResource(R.string.library_find_none)
          else stringResource(R.string.library_find_count, find.current + 1, results.size),
          style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp)
        )
        IconButton(enabled = results.isNotEmpty(), onClick = { find.step(-1)?.let(onJump) }) { Icon(AppIcons.ChevronRight, contentDescription = null) }
        IconButton(enabled = results.isNotEmpty(), onClick = { find.step(1)?.let(onJump) }) { Icon(AppIcons.ChevronLeft, contentDescription = null) }
      }
    }
  }
}

/** a search inside a text book: the paragraphs that match, and which one is shown */
class FindState {
  var query by mutableStateOf("")
  var results by mutableStateOf<List<Int>?>(null)
  var current by mutableIntStateOf(0)

  fun step(by: Int): Int? {
    val r = results ?: return null
    if (r.isEmpty()) return null
    current = (current + by).mod(r.size)
    return r[current]
  }
}
