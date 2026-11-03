package com.quran.labs.androidquran.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.feature.hadith.HadithHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

/** What the reader knows of a PDF's text: which source it uses and how much each has. */
class PdfTextState(val book: String, val store: PdfTextStore) {
  /** the text in use; null while there's none */
  var source by mutableStateOf<TextSource?>(null)
  /** how many pages have text, by source */
  var pages by mutableStateOf<Map<TextSource, Int>>(emptyMap())
  /** the sources that have looked at every page */
  var read by mutableStateOf<Set<TextSource>>(emptySet())
  /** bumped as pages are read, so the pages on screen pick up their words */
  var version by mutableIntStateOf(0)

  fun pagesOf(source: TextSource) = pages[source] ?: 0

  suspend fun words(page: Int): List<PdfWord>? = source?.let { store.words(book, page, it) }
}

/** Words chosen on a page, from [anchor] (where the press started) to [focus]. */
data class PdfSelection(val page: Int, val anchor: Int, val focus: Int, val words: List<PdfWord>) {
  val range: IntRange get() = minOf(anchor, focus)..maxOf(anchor, focus)
  val text: String get() = range.joinToString(" ") { words[it].text }
  val boxes: List<PdfWord> get() = range.map { words[it] }
}

@Composable
internal fun PdfReader(
  file: File,
  state: ReaderState,
  text: PdfTextState,
  highlights: List<BookHighlight>,
  find: FindState,
  selection: PdfSelection?,
  onSelection: (PdfSelection?) -> Unit,
  onTapHighlight: (BookHighlight) -> Unit,
  theme: PageTheme,
  night: Boolean,
  actions: SelectionActions,
  onTap: () -> Unit,
  insets: PaddingValues
) {
  val context = LocalContext.current
  val doc by produceState<PdfDocument?>(null, file) {
    value = withContext(Dispatchers.IO) { runCatching { PdfDocument(file) }.getOrNull() }
    if (value == null) state.failed = true
    awaitDispose { value?.close() }
  }
  val pdf = doc ?: return
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.place.position.coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0)))
  var zoom by remember { mutableFloatStateOf(1f) }
  var panX by remember { mutableFloatStateOf(0f) }
  val byPage = remember(highlights) { highlights.groupBy { it.position } }
  // every page the shape of the first until measured: most books' pages are all one size
  val ratios = remember(pdf) { mutableStateListOf<Float>() }
  LaunchedEffect(pdf) {
    val first = withContext(Dispatchers.IO) { runCatching { pdf.ratio(0) }.getOrDefault(1.414f) }
    ratios.addAll(List(pdf.pageCount) { first })
    // the pages weren't there to start at the reader's place
    listState.scrollToItem(state.place.position.coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0)))
    (1 until pdf.pageCount).chunked(50).forEach { chunk ->
      val measured = withContext(Dispatchers.IO) { chunk.map { runCatching { pdf.ratio(it) }.getOrDefault(first) } }
      chunk.forEachIndexed { k, i -> if (ratios[i] != measured[k]) ratios[i] = measured[k] }
    }
  }

  LaunchedEffect(pdf) {
    state.total = pdf.pageCount
    state.ready = true
  }
  LaunchedEffect(pdf, listState) {
    snapshotFlow { listState.firstVisibleItemIndex }.collect { i ->
      state.place = BookPlace(i)
      state.label = context.getString(R.string.library_page_of, i + 1, pdf.pageCount)
    }
  }
  LaunchedEffect(state.jump) {
    val to = state.jump ?: return@LaunchedEffect
    listState.scrollToItem(to.position.coerceIn(0, pdf.pageCount - 1))
    state.jump = null
  }
  // search: the pages with every word, then from where the reader is
  LaunchedEffect(find.query, text.source, text.version) {
    val query = find.query
    val source = text.source
    if (query.isBlank() || source == null) {
      find.results = null
      return@LaunchedEffect
    }
    kotlinx.coroutines.delay(350)
    val found = text.store.search(text.book, source, query)
    val first = find.results == null
    find.results = found
    if (first) {
      find.current = found.indexOfFirst { it >= listState.firstVisibleItemIndex }.takeIf { it >= 0 } ?: 0
      found.getOrNull(find.current)?.let { listState.scrollToItem(it) }
    }
  }

  val toggleZoom = {
    zoom = if (zoom > 1f) 1f else 2f
    panX = 0f
  }
  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .background(theme.backgroundColor.darkenedForGutter())
      // two fingers zoom and pan; one finger pans sideways when zoomed, and scrolls the pages
      .pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
          do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val fingers = event.changes.count { it.pressed }
            if (fingers >= 2) {
              zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
              val max = (zoom - 1f) * size.width / 2f
              panX = (panX + event.calculatePan().x).coerceIn(-max, max)
              event.changes.forEach { it.consume() }
            } else if (zoom > 1f) {
              val max = (zoom - 1f) * size.width / 2f
              panX = (panX + event.calculatePan().x).coerceIn(-max, max)
            }
          } while (event.changes.any { it.pressed })
        }
      }
  ) {
    val width = constraints.maxWidth
    val sharp = zoom > 1.5f
    val query = find.query.takeIf { find.results != null }
    LazyColumn(
      Modifier.fillMaxSize().graphicsLayer {
        scaleX = zoom
        scaleY = zoom
        translationX = panX
      },
      state = listState,
      contentPadding = PaddingValues(top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(if (ratios.isEmpty()) 0 else pdf.pageCount) { i ->
        PdfPage(
          pdf = pdf, page = i, ratio = ratios[i], width = if (sharp) (width * 2).coerceAtMost(2600) else width,
          text = text, highlights = byPage[i].orEmpty(), query = query,
          selection = selection?.takeIf { it.page == i },
          onSelection = onSelection, onTapHighlight = onTapHighlight, onDoubleTap = toggleZoom,
          theme = theme, night = night, actions = actions, onTap = onTap
        )
      }
    }
  }
}

@Composable
private fun PdfPage(
  pdf: PdfDocument,
  page: Int,
  ratio: Float,
  width: Int,
  text: PdfTextState,
  highlights: List<BookHighlight>,
  query: String?,
  selection: PdfSelection?,
  onSelection: (PdfSelection?) -> Unit,
  onTapHighlight: (BookHighlight) -> Unit,
  onDoubleTap: () -> Unit,
  theme: PageTheme,
  night: Boolean,
  actions: SelectionActions,
  onTap: () -> Unit
) {
  val bitmap by produceState<ImageBitmap?>(null, page, width) {
    value = withContext(Dispatchers.IO) { runCatching { pdf.render(page, width).asImageBitmap() }.getOrNull() } ?: value
  }
  val words by produceState<List<PdfWord>?>(null, page, text.source, text.version) { value = text.words(page) }
  val matches = remember(words, query) {
    val w = words
    if (query.isNullOrBlank() || w == null) emptyList() else w.filter { HadithHighlight.ranges(it.text, query).isNotEmpty() }
  }
  val currentWords by rememberUpdatedState(words)
  val currentHighlights by rememberUpdatedState(highlights)
  val currentSelection by rememberUpdatedState(selection)
  val currentTap by rememberUpdatedState(onTap)
  val selectionColor = theme.accentColor.copy(alpha = 0.3f)
  val matchColor = theme.accentColor.copy(alpha = 0.3f)
  var dragging by remember { mutableStateOf(false) }
  var lens by remember { mutableStateOf<Offset?>(null) }
  val density = androidx.compose.ui.platform.LocalDensity.current

  Box(
    Modifier
      .fillMaxWidth()
      .aspectRatio(1f / ratio.coerceIn(0.2f, 5f))
      .background(Color.White)
      .lens(lens, density)
      .pointerInput(page) {
        detectTapGestures(
          onTap = { at ->
            val x = at.x / size.width
            val y = at.y / size.height
            val hit = currentHighlights.lastOrNull { h -> h.boxes.any { it.contains(x, y) } }
            when {
              currentSelection != null -> onSelection(null)
              hit != null -> onTapHighlight(hit)
              else -> currentTap()
            }
          },
          onDoubleTap = { onDoubleTap() }
        )
      }
      // hold on a word, then drag over the rest
      .pointerInput(page) {
        var anchor = -1
        detectDragGesturesAfterLongPress(
          onDragStart = { at ->
            val w = currentWords ?: return@detectDragGesturesAfterLongPress
            anchor = wordAt(w, at.x / size.width, at.y / size.height) ?: -1
            if (anchor >= 0) onSelection(PdfSelection(page, anchor, anchor, w))
          },
          onDrag = { change, _ ->
            val w = currentWords
            if (anchor >= 0 && w != null) {
              wordAt(w, change.position.x / size.width, change.position.y / size.height)?.let { focus ->
                onSelection(PdfSelection(page, anchor, focus, w))
              }
              change.consume()
            }
          }
        )
      }
  ) {
    // night pages: the scan's paper dark and its ink light
    bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), colorFilter = if (night) NIGHT else null) }
    Canvas(Modifier.matchParentSize()) {
      highlights.forEach { h ->
        if (h.noteUid.isNotEmpty()) {
          // quoted into the research: tinted and underlined
          h.boxes.forEach { w ->
            drawWord(w, theme.accentColor.copy(alpha = 0.16f))
            drawRect(theme.accentColor, Offset(w.left * size.width, w.bottom * size.height - 2.dp.toPx()), Size((w.right - w.left) * size.width, 2.dp.toPx()))
          }
        } else {
          h.boxes.forEach { drawWord(it, Color(h.color.argb).copy(alpha = 0.4f)) }
        }
      }
      matches.forEach { drawWord(it, matchColor) }
      selection?.boxes?.forEach { drawWord(it, selectionColor) }
    }
    BoxWithConstraints(Modifier.matchParentSize()) {
      val w = constraints.maxWidth.toFloat()
      val h = constraints.maxHeight.toFloat()
      val sel = selection
      if (sel != null && sel.range.first in sel.words.indices && sel.range.last in sel.words.indices) {
        val first = sel.words[sel.range.first]
        val last = sel.words[sel.range.last]
        val arabic = first.text.any { it in '؀'..'ۿ' }
        // an Arabic selection starts at the right of its first word and ends at the left of its last
        val startPoint = if (arabic) Offset(first.right * w, first.bottom * h) else Offset(first.left * w, first.bottom * h)
        val endPoint = if (arabic) Offset(last.left * w, last.bottom * h) else Offset(last.right * w, last.bottom * h)
        val move = { at: Offset, isStart: Boolean ->
          dragging = true
          wordAt(sel.words, at.x / w, at.y / h)?.let { i ->
            val a = sel.range.first
            val b = sel.range.last
            onSelection(if (isStart) PdfSelection(page, minOf(i, b), b, sel.words) else PdfSelection(page, a, maxOf(i, a), sel.words))
          }
        }
        SelectionHandle(startPoint, hangsLeft = !arabic, color = theme.accentColor, onDrag = { move(it, true) }, onDragEnd = { dragging = false }, onMove = { lens = it }, modifier = Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft))
        SelectionHandle(endPoint, hangsLeft = arabic, color = theme.accentColor, onDrag = { move(it, false) }, onDragEnd = { dragging = false }, onMove = { lens = it }, modifier = Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft))
        if (!dragging) {
          val boxes = sel.boxes
          val anchor = androidx.compose.ui.geometry.Rect(
            boxes.minOf { it.left } * w, boxes.minOf { it.top } * h, boxes.maxOf { it.right } * w, boxes.maxOf { it.bottom } * h
          )
          SelectionToolbar(anchor, actions)
        }
      }
    }
  }
}

private val NIGHT = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
  androidx.compose.ui.graphics.ColorMatrix(
    floatArrayOf(
      -0.85f, 0f, 0f, 0f, 235f,
      0f, -0.85f, 0f, 0f, 232f,
      0f, 0f, -0.85f, 0f, 225f,
      0f, 0f, 0f, 1f, 0f
    )
  )
)

/** around the pages: a little darker than the page theme, so a page stands out */
private fun Color.darkenedForGutter(): Color = Color(red * 0.9f, green * 0.9f, blue * 0.9f, 1f)

private fun DrawScope.drawWord(w: PdfWord, color: Color) {
  drawRect(
    color,
    topLeft = Offset(w.left * size.width, w.top * size.height),
    size = Size((w.right - w.left) * size.width, (w.bottom - w.top) * size.height)
  )
}

/** the word under the finger; between words, the nearest on the same line; else the nearest close by */
internal fun wordAt(words: List<PdfWord>, x: Float, y: Float): Int? {
  words.indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }?.let { return it }
  val onLine = words.indices.filter { y >= words[it].top - 0.004f && y <= words[it].bottom + 0.004f }
  onLine.minByOrNull { i -> distanceAcross(words[i], x) }?.let { i ->
    if (distanceAcross(words[i], x) < 0.08f) return i
  }
  val nearest = words.indices.minByOrNull { i ->
    val w = words[i]
    hypot((w.left + w.right) / 2 - x, ((w.top + w.bottom) / 2 - y) * 2)
  } ?: return null
  val w = words[nearest]
  return nearest.takeIf { hypot((w.left + w.right) / 2 - x, ((w.top + w.bottom) / 2 - y) * 2) < 0.05f }
}

private fun distanceAcross(w: PdfWord, x: Float): Float = when {
  x < w.left -> w.left - x
  x > w.right -> x - w.right
  else -> 0f
}
