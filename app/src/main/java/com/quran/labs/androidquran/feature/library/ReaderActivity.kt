package com.quran.labs.androidquran.feature.library

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.feature.home.AppIcons
import com.quran.labs.androidquran.feature.research.BookQuotePlace
import com.quran.labs.androidquran.feature.research.ResearchActivity
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
  /** minutes to the end of the chapter, where the book knows */
  var chapterLeft by mutableIntStateOf(-1)
  /** the place as a fraction of the book, for the slider */
  val fraction: Float get() = if (total <= 1) 0f else ((place.position + place.offset) / (total - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
}

/** a passage to save: where it is, and its words */
private data class Passage(
  val position: Int,
  val start: Int,
  val end: Int,
  val text: String,
  val boxes: List<PdfWord> = emptyList(),
  /** a passage over several paragraphs: each one's part, the paragraph and from where to where */
  val parts: List<Triple<Int, Int, Int>> = emptyList()
)

class ReaderActivity : AppCompatActivity() {
  @Inject lateinit var store: LibraryStore

  private var book by mutableStateOf<LibraryBook?>(null)
  private var state by mutableStateOf<ReaderState?>(null)
  private var style by mutableStateOf(ReaderStyle())
  /** bumped on return from the research, where a quote may have become a highlight */
  private var highlightsVersion by mutableIntStateOf(0)

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)
    style = ReaderStyle.load(this)
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
          if (b != null && s != null) Reader(b, s)
        }
      }
    })
  }

  override fun onResume() {
    super.onResume()
    highlightsVersion++
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

  /** the bars over the page, and the system's: shown together, hidden together */
  private fun showSystemBars(show: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (show) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun Reader(book: LibraryBook, state: ReaderState) {
    val scope = rememberCoroutineScope()
    val theme = style.pageTheme(isSystemInDarkTheme())
    var chrome by remember { mutableStateOf(true) }
    var finding by remember { mutableStateOf(false) }
    var showContents by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var addingMark by remember { mutableStateOf(false) }
    var marksVersion by remember { mutableIntStateOf(0) }
    var marks by remember { mutableStateOf<List<BookMark>>(emptyList()) }
    LaunchedEffect(marksVersion) { marks = store.marks(book.id) }
    val find = remember { FindState() }
    var highlights by remember { mutableStateOf<List<BookHighlight>>(emptyList()) }
    LaunchedEffect(highlightsVersion) { highlights = store.highlights(book.id) }
    var tapped by remember { mutableStateOf<BookHighlight?>(null) }
    /** a passage the reader is writing a note for, before it's saved */
    var noting by remember { mutableStateOf<Passage?>(null) }
    var textSelection by remember { mutableStateOf<TextSelection?>(null) }
    var pdfSelection by remember { mutableStateOf<PdfSelection?>(null) }
    // a PDF's text: from the file, or OCR
    val pdfText = remember { PdfTextState(book.id, PdfTextStore.get(this@ReaderActivity)) }
    var textMenu by remember { mutableStateOf(false) }
    var confirmOcr by remember { mutableStateOf<TextSource?>(null) }
    val jobs by pdfText.store.jobs.collectAsState()
    val job = jobs[book.id]
    if (book.kind == BookKind.PDF) {
      LaunchedEffect(job, state.total) { refreshPdfText(pdfText, book, state.total, job) }
      // OCR follows the reader: a page they stay on that it hasn't reached is read next
      var asked by remember { mutableIntStateOf(-1) }
      LaunchedEffect(state.place.position, job?.running, pdfText.version) {
        val running = job ?: return@LaunchedEffect
        if (running.source == TextSource.LAYER || !running.running) return@LaunchedEffect
        kotlinx.coroutines.delay(3000)
        val page = state.place.position
        if (page != asked && pdfText.store.words(book.id, page, running.source) == null) {
          asked = page
          pdfText.store.start(book.id, book.file, running.source, page)
        }
      }
    }
    LaunchedEffect(chrome) { showSystemBars(chrome) }
    // the status bar's icons light on a dark page
    LaunchedEffect(theme) {
      WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !theme.dark
        isAppearanceLightNavigationBars = !theme.dark
      }
    }
    LaunchedEffect(style.keepScreenOn) {
      if (style.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    // keep the place as it changes, a moment after scrolling stops
    LaunchedEffect(state.place, state.ready) {
      if (!state.ready) return@LaunchedEffect
      kotlinx.coroutines.delay(1500)
      store.savePosition(book.id, state.place.position, state.place.offset)
    }

    val bookmarked = marks.firstOrNull { it.position == state.place.position }
    val save = { p: Passage, color: HighlightColor, note: String ->
      scope.launch {
        if (p.parts.size > 1) {
          // one highlight in each paragraph; the note goes with the first
          val lines = p.text.split('\n')
          p.parts.forEachIndexed { k, (position, start, end) ->
            store.addHighlight(book.id, position, start, end, color, lines.getOrElse(k) { "" }, note = if (k == 0) note else "")
          }
        } else {
          store.addHighlight(book.id, p.position, p.start, p.end, color, p.text, p.boxes, note = note)
        }
        highlightsVersion++
      }
      Unit
    }
    val toResearch = { p: Passage ->
      val source = listOf(book.title, book.author.takeIf { it.isNotBlank() }, state.label.takeIf { it.isNotBlank() })
        .filterNotNull().joinToString(" · ")
      startActivity(
        ResearchActivity.quoteBookIntent(
          this@ReaderActivity, p.text, source,
          BookQuotePlace(book.id, p.position, p.start, p.end, PdfTextStore.encode(p.boxes), p.parts.joinToString(";") { (a, b, c) -> "$a,$b,$c" })
        )
      )
    }
    /** what's chosen now, in whichever kind of book */
    val chosen: () -> Passage? = {
      textSelection?.let { Passage(it.first, it.start, it.parts.firstOrNull()?.third ?: it.end, it.text, parts = it.parts) }
        ?: pdfSelection?.let { Passage(it.page, it.range.first, it.range.last + 1, it.text, it.boxes) }
    }
    val clear = {
      textSelection = null
      pdfSelection = null
    }
    val actions = object : SelectionActions {
      override fun highlight(color: HighlightColor) {
        chosen()?.let { save(it, color, "") }
        clear()
      }

      override fun note() {
        noting = chosen()
        clear()
      }

      override fun copy() {
        chosen()?.let { copy(it.text) }
        clear()
      }

      override fun research() {
        chosen()?.let(toResearch)
        clear()
      }
    }
    val tapHighlight = { h: BookHighlight ->
      if (h.noteUid.isNotEmpty()) startActivity(ResearchActivity.openNoteIntent(this@ReaderActivity, h.noteUid)) else tapped = h
    }
    val toggle = { chrome = !chrome }
    val insets = PaddingValues(
      top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
      bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )

    Box(Modifier.fillMaxSize().background(theme.backgroundColor)) {
      when (book.kind) {
        BookKind.TEXT -> TextReader(
          File(book.file), state, style, theme, find, highlights, textSelection,
          onSelection = { textSelection = it }, actions = actions, onTapHighlight = tapHighlight, onTap = toggle, insets = insets
        )
        BookKind.PDF -> PdfReader(
          File(book.file), state, pdfText, highlights, find, pdfSelection,
          onSelection = { pdfSelection = it }, onTapHighlight = tapHighlight,
          theme = theme, night = theme.dark && style.pdfNight, actions = actions, onTap = toggle, insets = insets
        )
        BookKind.EPUB -> EpubReader(
          File(book.file), state, style, theme, highlights,
          onSelection = { action, sel ->
            val p = Passage(sel.chapter, sel.start, sel.end, sel.text)
            when (action) {
              EpubAction.HIGHLIGHT -> save(p, HighlightColor.YELLOW, "")
              EpubAction.NOTE -> noting = p
              EpubAction.RESEARCH -> toResearch(p)
            }
          },
          onTapHighlight = { id -> highlights.firstOrNull { it.id == id }?.let(tapHighlight) },
          onTap = toggle, insets = insets
        )
      }
      if (state.failed) {
        Text(stringResource(R.string.library_open_failed), color = theme.textColor, modifier = Modifier.align(Alignment.Center).padding(24.dp))
      } else if (!state.ready) {
        CircularProgressIndicator(Modifier.align(Alignment.Center), color = theme.accentColor)
      }

      // with the bars away, only a quiet note of where the reader is
      if (!chrome && state.ready && !finding) {
        Text(
          when (book.kind) {
            BookKind.PDF -> "${state.place.position + 1} / ${state.total}"
            else -> "${(state.fraction * 100).toInt()}%"
          },
          style = MaterialTheme.typography.labelSmall, color = theme.textColor.copy(alpha = 0.5f),
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = insets.calculateBottomPadding() + 6.dp)
        )
      }

      Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
        AnimatedVisibility(chrome || finding, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
          Column {
            if (finding) {
              FindBar(find, theme, onClose = {
                finding = false
                find.query = ""
              }, onJump = { state.jump = BookPlace(it) })
            } else {
              TopChrome(
                book, theme, bookmarked != null, canFind = book.kind == BookKind.TEXT || book.kind == BookKind.PDF && pdfText.source != null,
                onBack = ::finish, onFind = { finding = true }, onContents = { showContents = true },
                onBookmark = {
                  if (bookmarked != null) scope.launch {
                    store.deleteMark(bookmarked.id)
                    marksVersion++
                  } else addingMark = true
                },
                onAppearance = { showAppearance = true },
                pdfMenu = if (book.kind != BookKind.PDF) null else {
                  {
                    Box {
                      IconButton(onClick = { textMenu = true }) { Icon(AppIcons.TextSize, contentDescription = stringResource(R.string.library_text_of_pdf), tint = theme.textColor) }
                      PdfTextMenu(textMenu, pdfText, job, state.total, onDismiss = { textMenu = false }, onChoose = { source ->
                        textMenu = false
                        pdfText.store.choose(book.id, source)
                        pdfText.source = source
                        pdfSelection = null
                      }, onOcr = { engine ->
                        textMenu = false
                        confirmOcr = engine
                      }, onStop = { source ->
                        textMenu = false
                        pdfText.store.stop(book.id, source)
                      })
                    }
                  }
                }
              )
            }
            if (book.kind == BookKind.PDF) {
              PdfTextBanner(pdfText, job, state.total, theme, onOcr = { confirmOcr = TextSource.OPENITI }, onRetry = {
                pdfText.store.start(book.id, book.file, job?.source ?: TextSource.OPENITI, state.place.position)
              })
            }
          }
        }
      }
      Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        AnimatedVisibility(chrome && state.ready, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
          BottomChrome(book, state, theme)
        }
      }
    }

    if (showAppearance) {
      ModalBottomSheet(onDismissRequest = { showAppearance = false }) {
        AppearanceSheet(style, book.kind, isSystemInDarkTheme()) { changed ->
          style = changed
          changed.save(this@ReaderActivity)
        }
      }
    }
    if (showContents) {
      ModalBottomSheet(onDismissRequest = { showContents = false }) {
        ContentsSheet(book, state, marks, highlights, onGo = { place ->
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
        Column(Modifier.padding(20.dp, 0.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
          TextButton(onClick = {
            tapped = null
            toResearch(Passage(h.position, h.start, h.end, h.text, h.boxes))
          }) { Text(stringResource(R.string.library_to_research)) }
        }
      }
    }
    noting?.let { p ->
      var note by remember { mutableStateOf("") }
      var color by remember { mutableStateOf(HighlightColor.YELLOW) }
      AlertDialog(
        onDismissRequest = { noting = null },
        title = { Text(stringResource(R.string.library_highlight_note)) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(p.text, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              HighlightColor.entries.forEach { c ->
                Box(
                  Modifier.size(if (c == color) 34.dp else 28.dp).background(Color(c.argb), CircleShape).clickable { color = c }
                )
              }
            }
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.library_highlight_note)) }, modifier = Modifier.fillMaxWidth())
          }
        },
        confirmButton = {
          TextButton(onClick = {
            noting = null
            save(p, color, note.trim())
          }) { Text(stringResource(R.string.library_save)) }
        },
        dismissButton = { TextButton(onClick = { noting = null }) { Text(stringResource(R.string.library_cancel)) } }
      )
    }
    confirmOcr?.let { engine ->
      OcrDialog(engine = engine, pages = state.total, onStart = {
        confirmOcr = null
        // asking for OCR is choosing it; the other texts stay a tap away
        pdfText.store.choose(book.id, engine)
        pdfText.store.start(book.id, book.file, engine, state.place.position)
      }, onDismiss = { confirmOcr = null })
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

  @Composable
  private fun TopChrome(
    book: LibraryBook,
    theme: PageTheme,
    bookmarked: Boolean,
    canFind: Boolean,
    onBack: () -> Unit,
    onFind: () -> Unit,
    onContents: () -> Unit,
    onBookmark: () -> Unit,
    onAppearance: () -> Unit,
    pdfMenu: (@Composable () -> Unit)?
  ) {
    Surface(color = theme.chromeColor, contentColor = theme.textColor, shadowElevation = 2.dp) {
      Row(
        Modifier.fillMaxWidth().padding(WindowInsets.statusBars.asPaddingValues()).height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBack) { Icon(QuranIcons.ArrowBack, contentDescription = stringResource(R.string.research_back)) }
        Text(
          book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
        )
        if (canFind) IconButton(onClick = onFind) { Icon(QuranIcons.Search, contentDescription = stringResource(R.string.library_find)) }
        IconButton(onClick = onContents) { Icon(AppIcons.Contents, contentDescription = stringResource(R.string.library_contents)) }
        pdfMenu?.invoke()
        IconButton(onClick = onBookmark) {
          Icon(
            if (bookmarked) AppIcons.Bookmark else QuranIcons.BookmarkBorder,
            contentDescription = stringResource(R.string.library_add_bookmark),
            tint = if (bookmarked) theme.accentColor else theme.textColor
          )
        }
        IconButton(onClick = onAppearance) { Text("Aa", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
      }
    }
  }

  @Composable
  private fun BottomChrome(book: LibraryBook, state: ReaderState, theme: PageTheme) {
    if (state.total <= 0) return
    Surface(color = theme.chromeColor, contentColor = theme.textColor, shadowElevation = 2.dp) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 8.dp).padding(WindowInsets.navigationBars.asPaddingValues())) {
        var dragging by remember { mutableStateOf<Float?>(null) }
        val shownAt = dragging?.let { (it * (state.total - 1)).toInt() } ?: state.place.position
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            if (book.kind == BookKind.PDF) stringResource(R.string.library_page_of, shownAt + 1, state.total) else state.label,
            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
          )
          Spacer(Modifier.width(8.dp))
          Text(
            when {
              book.kind == BookKind.TEXT && state.chapterLeft > 0 && dragging == null -> stringResource(R.string.library_minutes_left, state.chapterLeft)
              book.kind == BookKind.EPUB -> stringResource(R.string.library_chapter_of, shownAt + 1, state.total)
              book.kind == BookKind.TEXT -> "${((dragging ?: state.fraction) * 100).toInt()}%"
              else -> "${((dragging ?: state.fraction) * 100).toInt()}%"
            },
            style = MaterialTheme.typography.labelMedium, color = theme.textColor.copy(alpha = 0.7f)
          )
        }
        if (state.total > 1) {
          Slider(
            value = dragging ?: state.fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
              dragging?.let { f -> state.jump = BookPlace((f * (state.total - 1)).toInt()) }
              dragging = null
            },
            colors = SliderDefaults.colors(thumbColor = theme.accentColor, activeTrackColor = theme.accentColor, inactiveTrackColor = theme.textColor.copy(alpha = 0.2f))
          )
        }
        if (book.kind == BookKind.EPUB) {
          Row {
            TextButton(enabled = state.place.position > 0, onClick = { state.jump = BookPlace(state.place.position - 1) }) {
              Text(stringResource(R.string.library_previous_chapter), color = theme.accentColor)
            }
            Spacer(Modifier.weight(1f))
            TextButton(enabled = state.place.position < state.total - 1, onClick = { state.jump = BookPlace(state.place.position + 1) }) {
              Text(stringResource(R.string.library_next_chapter), color = theme.accentColor)
            }
          }
        }
      }
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
    val pages = TextSource.entries.associateWith { store.pagesWithText(book.id, it) }
    val read = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      TextSource.entries.filter { store.donePages(book.id, it).size >= total }.toSet()
    }
    text.pages = pages
    text.read = read
    val chosen = store.chosen(book.id)?.takeIf { (pages[it] ?: 0) > 0 }
    // unless the reader chose, whichever has the most pages of text, the file's own first
    text.source = chosen ?: TextSource.entries.filter { (pages[it] ?: 0) > 0 }.maxByOrNull { (pages[it] ?: 0) * 10 + if (it == TextSource.LAYER) 1 else 0 }
    text.version++
    // a PDF added before this version: look for its own text now
    if (TextSource.LAYER !in read && job == null) store.start(book.id, book.file, TextSource.LAYER)
  }

  @Composable
  private fun ContentsSheet(
    book: LibraryBook,
    state: ReaderState,
    marks: List<BookMark>,
    highlights: List<BookHighlight>,
    onGo: (BookPlace) -> Unit,
    onDeleteMark: (BookMark) -> Unit
  ) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
      PrimaryTabRow(selectedTabIndex = tab) {
        Tab(tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.library_contents)) })
        Tab(tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.library_bookmarks)) })
        Tab(tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.library_highlights)) })
      }
      LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        if (tab == 0) {
          if (state.contents.isEmpty()) item { SheetNote(stringResource(R.string.library_no_contents)) }
          items(state.contents) { entry ->
            val here = state.contents.lastOrNull { it.place.position <= state.place.position } == entry
            Text(
              entry.title,
              style = if (entry.depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
              fontWeight = if (entry.depth == 0 || here) FontWeight.Bold else null,
              color = if (here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
              maxLines = 2, overflow = TextOverflow.Ellipsis,
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onGo(entry.place) }
                .padding(start = (20 + 16 * entry.depth.coerceAtMost(4)).dp, end = 20.dp, top = 12.dp, bottom = 12.dp)
            )
          }
        } else if (tab == 2) {
          if (highlights.isEmpty()) item { SheetNote(stringResource(R.string.library_no_highlights)) }
          items(highlights, key = { "h" + it.id }) { h ->
            Row(Modifier.fillMaxWidth().clickable { onGo(BookPlace(h.position)) }.padding(20.dp, 10.dp)) {
              Box(
                Modifier.padding(top = 6.dp).size(10.dp)
                  .background(if (h.noteUid.isNotEmpty()) MaterialTheme.colorScheme.primary else Color(h.color.argb), RoundedCornerShape(if (h.noteUid.isNotEmpty()) 2.dp else 5.dp))
              )
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Text(h.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (h.note.isNotBlank()) Text(h.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (h.noteUid.isNotEmpty()) Text(stringResource(R.string.library_in_research), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
              }
            }
          }
        } else {
          if (marks.isEmpty()) item { SheetNote(stringResource(R.string.library_no_bookmarks)) }
          items(marks, key = { it.id }) { mark ->
            Row(Modifier.fillMaxWidth().clickable { onGo(BookPlace(mark.position, mark.offset)) }.padding(20.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun FindBar(find: FindState, theme: PageTheme, onClose: () -> Unit, onJump: (Int) -> Unit) {
  Surface(color = theme.chromeColor, contentColor = theme.textColor, shadowElevation = 2.dp) {
    Column(Modifier.padding(WindowInsets.statusBars.asPaddingValues())) {
    Row(
      Modifier.fillMaxWidth().padding(8.dp, 6.dp),
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
    // with more than one word: all of them together, or any one
    if (find.query.trim().contains(' ')) {
      Row(Modifier.padding(start = 56.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.FilterChip(
          selected = find.phrase, onClick = { find.phrase = true }, shape = RoundedCornerShape(50),
          label = { Text(stringResource(R.string.library_find_phrase)) }
        )
        androidx.compose.material3.FilterChip(
          selected = !find.phrase, onClick = { find.phrase = false }, shape = RoundedCornerShape(50),
          label = { Text(stringResource(R.string.library_find_any)) }
        )
      }
    }
    }
  }
}

/** a search inside a book: the paragraphs or pages that match, and which one is shown */
class FindState {
  var query by mutableStateOf("")
  /** the words together and in order, or any one of them */
  var phrase by mutableStateOf(true)

  /** where in [text] the search finds what's asked for */
  fun matches(text: String, query: String = this.query): List<IntRange> =
    if (query.isBlank()) emptyList()
    else if (phrase) com.quran.labs.androidquran.feature.hadith.HadithHighlight.phraseRanges(text, query)
    else com.quran.labs.androidquran.feature.hadith.HadithHighlight.ranges(text, query)
  var results by mutableStateOf<List<Int>?>(null)
  var current by mutableIntStateOf(0)

  fun step(by: Int): Int? {
    val r = results ?: return null
    if (r.isEmpty()) return null
    current = (current + by).mod(r.size)
    return r[current]
  }
}
