package com.quran.labs.androidquran.feature.library

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.feature.hadith.HadithHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Words chosen in a text book, from [start] in paragraph [first] to [end] in paragraph [last]:
 * one paragraph, or running on over several.
 */
data class TextSelection(
  val first: Int,
  val start: Int,
  val last: Int,
  val end: Int,
  val text: String,
  /** each paragraph's part: the paragraph, and from where to where in it */
  val parts: List<Triple<Int, Int, Int>> = emptyList()
) {
  /** the part of paragraph [i] it covers, or null */
  fun rangeIn(i: Int, length: Int): IntRange? =
    if (i < first || i > last) null else (if (i == first) start else 0) until (if (i == last) end else length)

  /** each paragraph's part, to save: the paragraph, and from where to where in it */
  fun segments(lengthOf: (Int) -> Int): List<Triple<Int, Int, Int>> =
    (first..last).map { i -> Triple(i, if (i == first) start else 0, if (i == last) end else lengthOf(i)) }
}

/** where a paragraph is laid out on screen, so a selection can run on from one to the next */
private class Laid(val layout: TextLayoutResult, val coords: LayoutCoordinates)

// ---------------------------------------------------------------- classical text

@Composable
internal fun TextReader(
  file: File,
  state: ReaderState,
  style: ReaderStyle,
  theme: PageTheme,
  find: FindState,
  highlights: List<BookHighlight>,
  selection: TextSelection?,
  onSelection: (TextSelection?) -> Unit,
  actions: SelectionActions,
  onTapHighlight: (BookHighlight) -> Unit,
  onTap: () -> Unit,
  insets: PaddingValues
) {
  val context = LocalContext.current
  val blocks by produceState<List<TextBlock>?>(null, file) {
    value = withContext(Dispatchers.IO) { runCatching { OpenItiText.load(file) }.getOrNull() }
    if (value.isNullOrEmpty()) state.failed = true
  }
  val list = blocks?.takeIf { it.isNotEmpty() } ?: return
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.place.position.coerceIn(0, list.lastIndex))
  val headings = remember(list) { list.indices.filter { list[it] is TextBlock.Heading } }
  // a page label shows where a new printed page starts
  val pageStarts = remember(list) {
    var last = ""
    list.indices.filter { i ->
      val b = list[i] as? TextBlock.Paragraph ?: return@filter false
      (b.page.isNotEmpty() && b.page != last).also { last = b.page }
    }.toSet()
  }
  val volumes = remember(list) { list.any { it is TextBlock.Paragraph && it.page.isNotEmpty() && !it.page.startsWith("1:") } }
  // each paragraph's hadith, those that run on from the one before included
  val hadith = remember(list) {
    var open = false
    list.map { b ->
      if (b is TextBlock.Paragraph) hadithQuotes(b.text, open).also { open = it.openAtEnd }.ranges else emptyList<IntRange>().also { open = false }
    }
  }

  LaunchedEffect(list) {
    state.total = list.size
    state.contents = headings.map { i ->
      val h = list[i] as TextBlock.Heading
      ContentsEntry(h.title, BookPlace(i), (h.level - 1).coerceAtLeast(0))
    }
    state.ready = true
  }
  LaunchedEffect(list, listState) {
    snapshotFlow { listState.firstVisibleItemIndex }.collect { i ->
      state.place = BookPlace(i)
      state.label = placeLabel(context, list, headings, i, volumes)
      state.chapterLeft = minutesLeftInChapter(list, headings, i)
    }
  }
  LaunchedEffect(state.jump) {
    val to = state.jump ?: return@LaunchedEffect
    listState.scrollToItem(to.position.coerceIn(0, list.lastIndex))
    state.jump = null
  }
  LaunchedEffect(find.query, find.phrase, list) {
    val query = find.query
    if (query.isBlank()) {
      find.results = null
      return@LaunchedEffect
    }
    delay(350)
    val found = withContext(Dispatchers.Default) {
      list.indices.filter { i -> find.matches(list[i].text(), query).isNotEmpty() }
    }
    find.results = found
    // the first match from where the reader is
    find.current = found.indexOfFirst { it >= listState.firstVisibleItemIndex }.takeIf { it >= 0 } ?: 0
    found.getOrNull(find.current)?.let { listState.scrollToItem(it) }
  }

  val font = readerFontFamily(style.font)
  // what's searched for: a shade of the text, so it's none of the colours that mean something
  val mark = theme.textColor.copy(alpha = 0.16f)
  val query = find.query.takeIf { find.results != null }
  val byPosition = remember(highlights) { highlights.groupBy { it.position } }
  val size = 20 * style.scale
  val body = TextStyle(
    fontFamily = font, fontSize = size.sp, lineHeight = (size * style.lineSpacing).sp, color = theme.textColor,
    textDirection = TextDirection.Rtl, textAlign = if (style.justify) TextAlign.Justify else TextAlign.Start
  )
  val currentTap by rememberUpdatedState(onTap)
  val currentSelection by rememberUpdatedState(selection)
  val density = androidx.compose.ui.platform.LocalDensity.current
  // the paragraphs on screen, by index; the selection layer looks words up in them
  val laid = remember { HashMap<Int, Laid>() }
  var overlay by remember { mutableStateOf<LayoutCoordinates?>(null) }
  var anchor by remember { mutableStateOf<Pair<Int, TextRange>?>(null) }
  var dragging by remember { mutableStateOf(false) }
  var lens by remember { mutableStateOf<Offset?>(null) }
  val textOf = { i: Int -> (list.getOrNull(i) as? TextBlock.Paragraph)?.text.orEmpty() }

  /** the paragraph and letter under a point on the window, the nearest paragraph if between them */
  fun at(window: Offset): Pair<Int, Int>? {
    val shown = laid.entries.filter { it.value.coords.isAttached }
    if (shown.isEmpty()) return null
    val hit = shown.minByOrNull { (_, l) ->
      val r = l.coords.boundsInWindow()
      when {
        window.y < r.top -> r.top - window.y
        window.y > r.bottom -> window.y - r.bottom
        else -> 0f
      }
    } ?: return null
    val local = hit.value.coords.windowToLocal(window)
    return hit.key to hit.value.layout.getOffsetForPosition(local)
  }

  /** the selection from the [anchor] word to [to], whole words, across the paragraphs between */
  fun select(from: Pair<Int, TextRange>, to: Pair<Int, Int>) {
    val (ap, ar) = from
    val (fp, fo) = to
    val forward = fp > ap || fp == ap && fo >= ar.start
    val sel = if (forward) {
      val e = wordEnd(textOf(fp), fo)
      TextSelection(ap, ar.start, fp, maxOf(e, if (fp == ap) ar.end else 0), "")
    } else {
      TextSelection(fp, wordStart(textOf(fp), fo), ap, ar.end, "")
    }
    if (sel.first == sel.last && sel.end <= sel.start) return
    val parts = sel.segments { textOf(it).length }
    val text = parts.joinToString("\n") { (i, s0, e0) -> textOf(i).substring(s0.coerceAtMost(e0), e0) }
    onSelection(sel.copy(text = text, parts = parts))
  }

  /** near the top or bottom while choosing: the page scrolls on */
  fun edgeScroll(window: Offset) {
    val o = overlay ?: return
    val y = o.windowToLocal(window).y
    val edge = with(density) { 72.dp.toPx() }
    when {
      y < edge -> listState.dispatchRawDelta(-(edge - y) / 3)
      y > o.size.height - edge -> listState.dispatchRawDelta((y - (o.size.height - edge)) / 3)
    }
  }

  Box(Modifier.fillMaxSize().onGloballyPositioned { overlay = it }.lens(lens, density)) {
    LazyColumn(
      Modifier
        .fillMaxSize()
        .background(theme.backgroundColor)
        // taps between paragraphs show and hide the bars
        .pointerInput(Unit) { detectTapGestures(onTap = { if (currentSelection != null) onSelection(null) else currentTap() }) },
      state = listState,
      contentPadding = PaddingValues(
        start = style.margin.dp, end = style.margin.dp,
        top = insets.calculateTopPadding() + 16.dp, bottom = insets.calculateBottomPadding() + 48.dp
      )
    ) {
      itemsIndexed(list) { i, block ->
        when (block) {
          is TextBlock.Heading -> Text(
            block.title,
            style = TextStyle(
              fontFamily = font, fontWeight = FontWeight.Bold, textDirection = TextDirection.Rtl,
              fontSize = (when (block.level) { 1 -> 25; 2 -> 22; else -> 20 } * style.scale).sp,
              lineHeight = (when (block.level) { 1 -> 40; 2 -> 36; else -> 32 } * style.scale).sp,
              textAlign = if (block.level == 1) TextAlign.Center else TextAlign.Start,
              color = theme.textColor
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp)
          )
          is TextBlock.Paragraph -> Column(Modifier.fillMaxWidth().padding(bottom = (10 * style.lineSpacing).dp)) {
            if (i in pageStarts) {
              Text(
                pageLabel(context, block.page, volumes),
                style = TextStyle(color = theme.textColor.copy(alpha = 0.45f), fontSize = 11.sp, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
              )
            }
            Paragraph(
              index = i, text = block.text, style = body, theme = theme, query = query, mark = mark,
              highlights = byPosition[i].orEmpty(), selected = selection?.rangeIn(i, block.text.length), hadith = hadith[i],
              found = if (query == null) emptyList() else find.matches(block.text, query),
              selecting = selection != null, laid = laid,
              onHold = { range ->
                anchor = i to range
                select(i to range, i to range.start)
              },
              onHoldDrag = { window ->
                anchor?.let { a -> at(window)?.let { select(a, it) } }
                edgeScroll(window)
              },
              onClear = { onSelection(null) },
              onTapHighlight = onTapHighlight, onTap = onTap
            )
          }
        }
      }
    }

    // the handles and the bar, over the list, so they can follow a selection across paragraphs
    val o = overlay
    if (selection != null && o != null) {
      // read so the handles move as the page scrolls
      listState.firstVisibleItemIndex
      listState.firstVisibleItemScrollOffset
      val first = laid[selection.first]?.takeIf { it.coords.isAttached }
      val last = laid[selection.last]?.takeIf { it.coords.isAttached }
      val point = { l: Laid, offset: Int, end: Boolean ->
        val ly = l.layout
        val safe = offset.coerceIn(0, ly.layoutInput.text.length)
        val line = ly.getLineForOffset(if (end) (safe - 1).coerceAtLeast(0) else safe)
        o.windowToLocal(l.coords.localToWindow(Offset(ly.getHorizontalPosition(safe, true), ly.getLineBottom(line))))
      }
      val rtl = (first ?: last)?.layout?.let { it.getParagraphDirection(0) == ResolvedTextDirection.Rtl } ?: true
      /** a handle dragged: that end of the selection follows it, the other stays */
      val move = { window: Offset, isStart: Boolean ->
        dragging = true
        val sel = currentSelection
        val to = at(window)
        if (sel != null && to != null) {
          // the end that stays is the anchor: the word at it
          val fixedWord = if (isStart) {
            sel.last to TextRange(wordStart(textOf(sel.last), (sel.end - 1).coerceAtLeast(0)), sel.end)
          } else {
            sel.first to TextRange(sel.start, wordEnd(textOf(sel.first), sel.start + 1))
          }
          anchor = fixedWord
          select(fixedWord, to)
        }
        edgeScroll(window)
      }
      if (first != null) {
        SelectionHandle(
          point(first, selection.start, false), hangsLeft = !rtl, color = theme.accentColor,
          onDrag = { move(o.localToWindow(it), true) }, onDragEnd = { dragging = false }, onMove = { lens = it },
          modifier = Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft)
        )
      }
      if (last != null) {
        SelectionHandle(
          point(last, selection.end, true), hangsLeft = rtl, color = theme.accentColor,
          onDrag = { move(o.localToWindow(it), false) }, onDragEnd = { dragging = false }, onMove = { lens = it },
          modifier = Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft)
        )
      }
      if (!dragging) {
        val top = first?.let { l ->
          val ly = l.layout
          o.windowToLocal(l.coords.localToWindow(Offset(0f, ly.getLineTop(ly.getLineForOffset(selection.start.coerceIn(0, ly.layoutInput.text.length)))))).y
        } ?: 0f
        val bottom = last?.let { point(it, selection.end, true).y } ?: o.size.height.toFloat()
        SelectionToolbar(androidx.compose.ui.geometry.Rect(0f, top, o.size.width.toFloat(), bottom), actions)
      }
    }
  }
}

/** a paragraph: held to choose words, which can run on into the next ones */
@Composable
private fun Paragraph(
  index: Int,
  text: String,
  style: TextStyle,
  theme: PageTheme,
  query: String?,
  mark: Color,
  highlights: List<BookHighlight>,
  selected: IntRange?,
  hadith: List<IntRange>,
  found: List<IntRange>,
  selecting: Boolean,
  laid: HashMap<Int, Laid>,
  onHold: (TextRange) -> Unit,
  onHoldDrag: (Offset) -> Unit,
  onClear: () -> Unit,
  onTapHighlight: (BookHighlight) -> Unit,
  onTap: () -> Unit
) {
  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
  var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
  val currentSelecting by rememberUpdatedState(selecting)
  val currentHighlights by rememberUpdatedState(highlights)
  val currentTap by rememberUpdatedState(onTap)
  val currentHoldDrag by rememberUpdatedState(onHoldDrag)
  val tint = theme.accentColor.copy(alpha = 0.3f)
  val styled = remember(text, found, highlights, selected, theme, hadith) { styled(text, found, theme, mark, highlights, selected, tint, hadith) }
  // known to the selection layer while it's laid out
  val l = layout
  val c = coords
  if (l != null && c != null) laid[index] = Laid(l, c)
  androidx.compose.runtime.DisposableEffect(index) { onDispose { laid.remove(index) } }
  Text(
    styled, style = style, onTextLayout = { layout = it },
    modifier = Modifier
      .onGloballyPositioned { coords = it }
      .pointerInput(index) {
        detectTapGestures(onTap = { at ->
          val ly = layout ?: return@detectTapGestures
          val offset = ly.getOffsetForPosition(at)
          val hit = currentHighlights.lastOrNull { offset in it.start until it.end }
          when {
            currentSelecting -> onClear()
            hit != null -> onTapHighlight(hit)
            else -> currentTap()
          }
        })
      }
      // hold on a word, then drag over the rest, here or in the paragraphs after it
      .pointerInput(index) {
        detectDragGesturesAfterLongPress(
          onDragStart = { at ->
            val ly = layout ?: return@detectDragGesturesAfterLongPress
            val word = ly.getWordBoundary(ly.getOffsetForPosition(at))
            if (word.length > 0) onHold(word)
          },
          onDrag = { change, _ ->
            val co = coords ?: return@detectDragGesturesAfterLongPress
            change.consume()
            currentHoldDrag(co.localToWindow(change.position))
          }
        )
      }
  )
}

private fun wordStart(text: String, at: Int): Int {
  var i = at.coerceIn(0, text.length)
  while (i > 0 && !text[i - 1].isWhitespace()) i--
  return i
}

private fun wordEnd(text: String, at: Int): Int {
  var i = at.coerceIn(0, text.length)
  while (i < text.length && !text[i].isWhitespace()) i++
  return i
}

private fun TextBlock.text(): String = when (this) {
  is TextBlock.Heading -> title
  is TextBlock.Paragraph -> text
}

/** the reader's highlights (research quotes underlined), Quran quotes in colour, search matches, the selection */
private fun styled(
  text: String,
  found: List<IntRange>,
  theme: PageTheme,
  mark: Color,
  highlights: List<BookHighlight>,
  selection: IntRange?,
  selected: Color,
  hadith: List<IntRange>
): AnnotatedString {
  val base = HadithHighlight.annotate(text, found, mark)
  if ('﴿' !in text && hadith.isEmpty() && highlights.isEmpty() && selection == null) return base
  return buildAnnotatedString {
    append(base)
    hadith.forEach { addStyle(SpanStyle(color = theme.hadithColor), it.first, (it.last + 1).coerceAtMost(text.length)) }
    var from = 0
    while (true) {
      val start = text.indexOf('﴿', from)
      if (start < 0) break
      val end = text.indexOf('﴾', start).let { if (it < 0) text.length else it + 1 }
      addStyle(SpanStyle(color = theme.verseColor), start, end)
      from = end
    }
    highlights.forEach { h ->
      val start = h.start.coerceIn(0, text.length)
      val end = h.end.coerceIn(start, text.length)
      if (h.noteUid.isNotEmpty()) {
        // quoted into the research: underlined on a gray tint, its colours left as they are
        addStyle(SpanStyle(background = theme.textColor.copy(alpha = 0.12f), textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), start, end)
      } else {
        addStyle(SpanStyle(background = Color(h.color.argb).copy(alpha = if (theme.dark) 0.42f else 0.5f)), start, end)
      }
    }
    if (selection != null && !selection.isEmpty()) addStyle(SpanStyle(background = selected), selection.first, (selection.last + 1).coerceAtMost(text.length))
  }
}

/** what attributes words to the Prophet ﷺ just before a quote */
private val PROPHET = Regex("(صلى الله عليه وسلم|ﷺ|عليه الصلاة والسلام|عليه السلام|رسول الله|النبي|قال رسول|قال النبي)[^«»]{0,40}$")

/** a paragraph's hadith: where they are, and whether one is still open at its end, running on into the next */
internal class HadithSpans(val ranges: List<IntRange>, val openAtEnd: Boolean)

/**
 * the quotes that are the Prophet's words ﷺ: «…», "…" or “…” attributed to him in the eighty
 * letters before them. A book's title, or a scholar's words, stays as it is. [openAtStart]: the
 * paragraph before ended inside one, as a hadith does that runs over a printed page.
 */
internal fun hadithQuotes(text: String, openAtStart: Boolean = false): HadithSpans {
  val out = mutableListOf<IntRange>()
  var i = 0
  if (openAtStart) {
    val close = closing(text, 0, null)
    if (close < 0) return HadithSpans(listOf(text.indices), true)
    out += 0..close
    i = close + 1
  }
  while (i < text.length) {
    val c = text[i]
    val opens = c == '«' || c == '“' || c == '"'
    if (!opens) {
      i++
      continue
    }
    val close = closing(text, i + 1, c)
    val prophetic = PROPHET.containsMatchIn(text.substring(maxOf(0, i - 80), i))
    if (close < 0) {
      // open to the paragraph's end
      if (prophetic) return HadithSpans(out + listOf(i until text.length), true)
      break
    }
    if (prophetic) out += i..close
    i = close + 1
  }
  return HadithSpans(out, false)
}

/** where the quote opened by [open] (or any, for one carried over) closes, or -1 */
private fun closing(text: String, from: Int, open: Char?): Int {
  val close = when (open) {
    '«' -> "»"
    '“' -> "”"
    '"' -> "\""
    else -> "»”\""
  }
  for (k in from until text.length) if (text[k] in close) return k
  return -1
}

private fun pageLabel(context: Context, page: String, volumes: Boolean): String {
  val volume = page.substringBefore(':')
  val number = page.substringAfter(':')
  return if (volumes) context.getString(R.string.library_volume_page, volume, number) else context.getString(R.string.library_page, number)
}

/** the chapter the reader is in, and the printed page */
private fun placeLabel(context: Context, list: List<TextBlock>, headings: List<Int>, at: Int, volumes: Boolean): String {
  val heading = headings.lastOrNull { it <= at }?.let { (list[it] as TextBlock.Heading).title }
  val page = (at downTo maxOf(0, at - 60)).firstNotNullOfOrNull { (list[it] as? TextBlock.Paragraph)?.page?.takeIf(String::isNotEmpty) }
  return listOfNotNull(heading?.take(60), page?.let { pageLabel(context, it, volumes) }).joinToString(" · ")
}

/** minutes to the next heading, at a reading pace for classical Arabic (about 150 words a minute) */
private fun minutesLeftInChapter(list: List<TextBlock>, headings: List<Int>, at: Int): Int {
  val next = headings.firstOrNull { it > at } ?: list.size
  var words = 0
  for (i in at until next) words += (list[i] as? TextBlock.Paragraph)?.text?.count { it == ' ' }?.plus(1) ?: 0
  return (words / 150).coerceAtLeast(if (words > 0) 1 else 0)
}

// ---------------------------------------------------------------- EPUB

/** what was chosen in an EPUB chapter: its text, and where it sits in the chapter's text */
data class EpubSelection(val chapter: Int, val start: Int, val end: Int, val text: String)

enum class EpubAction { HIGHLIGHT, NOTE, RESEARCH }

@Composable
internal fun EpubReader(
  file: File,
  state: ReaderState,
  style: ReaderStyle,
  theme: PageTheme,
  highlights: List<BookHighlight>,
  onSelection: (EpubAction, EpubSelection) -> Unit,
  onTapHighlight: (Long) -> Unit,
  onTap: () -> Unit,
  insets: PaddingValues
) {
  val context = LocalContext.current
  val opened by produceState<EpubBook?>(null, file) {
    value = withContext(Dispatchers.IO) { runCatching { EpubBook.open(file) }.getOrNull() }
    if (value == null) state.failed = true
    awaitDispose { value?.close() }
  }
  val epub = opened ?: return
  var chapter by remember { mutableIntStateOf(state.place.position.coerceIn(0, epub.spine.lastIndex)) }
  // CSS pixels are the same as dp
  val top = insets.calculateTopPadding().value
  val bottom = insets.calculateBottomPadding().value
  var page by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
  val density = androidx.compose.ui.platform.LocalDensity.current
  val pageW = with(density) { page.width.toDp().value }
  val pageH = with(density) { page.height.toDp().value }
  val css = remember(theme, style, top, bottom, pageW, pageH) { epubCss(theme, style, top, bottom, pageW, pageH) }
  val view = remember(epub) { EpubView(context, epub).also { it.pendingOffset = state.place.offset } }
  view.css = css
  view.onChapter = { chapter = it }
  view.onScrolled = { f ->
    state.place = BookPlace(view.chapter, f)
    state.label = chapterLabel(context, epub, view.chapter, f)
  }
  view.paged = style.bookPages
  view.onPaged = { page, pages ->
    state.label = chapterTitle(context, epub, view.chapter) + " \u00B7 " + context.getString(R.string.library_page_of, page + 1, pages)
  }
  view.onSelection = onSelection
  view.onMark = onTapHighlight
  view.onTap = onTap
  view.nextLabel = context.getString(R.string.library_next_chapter)
  view.marks = highlights.filter { it.position == chapter }

  LaunchedEffect(epub) {
    state.total = epub.spine.size
    state.contents = epub.toc.map { ContentsEntry(it.title, BookPlace(it.chapter), it.depth.coerceAtLeast(0)) }
    state.label = chapterLabel(context, epub, chapter, state.place.offset)
    state.ready = true
  }
  LaunchedEffect(state.jump) {
    val to = state.jump ?: return@LaunchedEffect
    view.pendingOffset = to.offset
    chapter = to.position.coerceIn(0, epub.spine.lastIndex)
    view.show(chapter, force = true)
    state.jump = null
  }
  LaunchedEffect(chapter) {
    state.place = BookPlace(chapter, view.pendingOffset)
    state.label = chapterLabel(context, epub, chapter, view.pendingOffset)
  }
  // a new highlight, or one changed: repaint this chapter's
  LaunchedEffect(highlights, chapter) { view.paintMarks() }
  // a new look: the chapter again, where the reader was
  LaunchedEffect(css) { view.restyle() }

  AndroidView(
    factory = { view.web },
    update = { web ->
      web.settings.textZoom = (style.scale * 100).toInt()
      web.setBackgroundColor(theme.backgroundColor.toArgb())
      view.show(chapter)
    },
    modifier = Modifier.fillMaxSize().background(theme.backgroundColor)
      .onSizeChanged { page = it }
  )
}

private fun chapterTitle(context: Context, epub: EpubBook, chapter: Int): String =
  "\u2068" + (epub.toc.lastOrNull { it.chapter <= chapter }?.title ?: context.getString(R.string.library_chapter_of, chapter + 1, epub.spine.size)).take(60) + "\u2069"

private fun chapterLabel(context: Context, epub: EpubBook, chapter: Int, offset: Float): String {
  val title = epub.toc.lastOrNull { it.chapter <= chapter }?.title
    ?: context.getString(R.string.library_chapter_of, chapter + 1, epub.spine.size)
  // isolated, so an Arabic title and the percentage keep their order
  return "\u2068" + title.take(60) + "\u2069 · " + (offset * 100).toInt() + "%"
}

private fun hex(color: Color) = String.format("#%06X", color.toArgb() and 0xFFFFFF)

private fun rgba(color: Color, alpha: Float) =
  "rgba(${(color.red * 255).toInt()},${(color.green * 255).toInt()},${(color.blue * 255).toInt()},$alpha)"

private fun epubCss(theme: PageTheme, style: ReaderStyle, top: Float, bottom: Float, width: Float, height: Float): String = buildString {
  append("@font-face{font-family:'AppArabic';src:url('https://${EpubView.HOST}/${EpubView.APP}/kitab.ttf');}")
  append("html,body{background:${hex(theme.backgroundColor)} !important;color:${hex(theme.textColor)} !important;}")
  val face = if (style.font == ReaderFont.KITAB) "'AppArabic',serif" else "serif"
  append(
    "body{margin:0;padding:${(top + 16).toInt()}px ${style.margin}px ${(bottom + 72).toInt()}px;line-height:${style.lineSpacing};" +
      "font-family:$face;overflow-wrap:break-word;${if (style.justify) "text-align:justify;" else ""}}"
  )
  append("p{margin:0 0 0.6em 0;}")
  append("h1,h2,h3,h4{color:${hex(theme.textColor)} !important;line-height:1.5;}")
  append("a{color:${hex(theme.accentColor)} !important;}")
  append("img,svg,video,table{max-width:100% !important;height:auto !important;}")
  // a book's own colours would be unreadable on a dark page
  if (theme.dark) append("*{color:inherit !important;background-color:transparent !important;border-color:${hex(theme.textColor)}33 !important;}")
  HighlightColor.entries.forEach { c ->
    append("mark.hl-${c.name}{background:${rgba(Color(c.argb), if (theme.dark) 0.42f else 0.5f)} !important;color:inherit !important;}")
  }
  append("mark.research{background:${rgba(theme.textColor, 0.12f)} !important;color:inherit !important;border-bottom:2px solid ${hex(theme.textColor)}99;}")
  append("a.__next{display:block;margin:48px auto 0;padding:14px 18px;text-align:center;border-radius:24px;")
  append("border:1px solid ${hex(theme.accentColor)}66;text-decoration:none;font-weight:bold;}")
  if (style.bookPages && width > 0 && height > 0) append(bookCss(theme, style, top, bottom, width, height))
}

/**
 * A chapter set as a book's pages: columns a screen wide, turned sideways; paragraphs indented
 * with no space between, headings centred over an ornament, and the page's edges shaded as
 * paper is where it meets the spine.
 */
private fun bookCss(theme: PageTheme, style: ReaderStyle, top: Float, bottom: Float, width: Float, height: Float): String = buildString {
  val above = (top + 28).toInt()
  val below = (bottom + 44).toInt()
  val m = style.margin + 6
  val edge = if (theme.dark) "rgba(255,255,255,0.035)" else "rgba(60,40,10,0.07)"
  val w = width.toInt()
  val h = height.toInt()
  append("html{height:${h}px !important;overflow:hidden !important;")
  append("background-image:linear-gradient(to right,$edge,rgba(0,0,0,0) 4%,rgba(0,0,0,0) 96%,$edge) !important;background-attachment:fixed !important;}")
  // a column is exactly a page, and the margins are inside it: a page turn is one column across
  append("body{margin:${above}px 0 ${below}px 0 !important;padding:0 !important;height:${h - above - below}px !important;")
  append("column-width:${w}px;column-gap:0;column-fill:auto;overflow:visible !important;background:transparent !important;}")
  // above the paragraphs' own margins
  append("html body>*{margin-left:${m}px !important;margin-right:${m}px !important;box-sizing:border-box;}")
  append("p{text-indent:1.4em;margin:0 0 0.15em 0 !important;}")
  append("h1+p,h2+p,h3+p,h4+p,p:first-child{text-indent:0;}")
  append("h1,h2,h3{text-align:center !important;margin:0.9em 0 0.8em !important;break-after:avoid;}")
  append("h1::after,h2::after{content:'\u2741';display:block;font-size:0.6em;opacity:0.55;margin-top:0.35em;}")
  append("img,svg,figure{max-height:${h - above - below - 16}px !important;break-inside:avoid;object-fit:contain;}")
  append("a.__next{display:none !important;}")
}

/** what runs in a chapter: choosing words, and painting highlights. The book's own scripts are taken out. */
private const val SCRIPT = """
(function(){
function texts(){var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null);var a=[],n;while((n=w.nextNode()))a.push(n);return a;}
function at(node,off){var r=document.createRange();r.setStart(document.body,0);r.setEnd(node,off);return r.toString().length;}
window.__sel=function(){var s=window.getSelection();if(!s||s.rangeCount===0||s.isCollapsed)return null;var r=s.getRangeAt(0);
return JSON.stringify({text:s.toString(),start:at(r.startContainer,r.startOffset),end:at(r.endContainer,r.endOffset)});};
window.__clear=function(){var m=document.querySelectorAll('mark[data-id]');for(var i=0;i<m.length;i++){var e=m[i],p=e.parentNode;while(e.firstChild)p.insertBefore(e.firstChild,e);p.removeChild(e);p.normalize();}};
window.__mark=function(id,start,end,cls){var a=texts(),t=0;for(var i=0;i<a.length;i++){var n=a[i],L=n.length,s=Math.max(start,t),e=Math.min(end,t+L);
if(s<e){var r=document.createRange();r.setStart(n,s-t);r.setEnd(n,e-t);var m=document.createElement('mark');m.className=cls;m.setAttribute('data-id',id);try{r.surroundContents(m);}catch(x){}}t+=L;}};
window.__unselect=function(){var s=window.getSelection();if(s)s.removeAllRanges();};
function W(){var c=parseFloat(getComputedStyle(document.body).columnWidth);return c>0?c:window.innerWidth;}
function root(){return document.scrollingElement||document.documentElement;}
function rtl(){return getComputedStyle(document.body).direction==='rtl';}
window.__pages=function(){return Math.max(1,Math.round(root().scrollWidth/W()));};
window.__cur=function(){return Math.round(Math.abs(root().scrollLeft)/W());};
window.__go=function(p,smooth){var n=__pages();p=Math.max(0,Math.min(n-1,p));window.scrollTo({left:p*W()*(rtl()?-1:1),top:0,behavior:smooth?'smooth':'auto'});Reader.paged(p,n);};
window.__turn=function(d){var p=__cur()+d;if(p<0){Reader.edge(-1);return;}if(p>=__pages()){Reader.edge(1);return;}__go(p,true);};
window.__goFraction=function(f){__go(f>=0.999?__pages()-1:Math.round(f*(__pages()-1)),false);};
if(window.__isPaged){var sx=0,sy=0;
document.addEventListener('touchstart',function(e){var t=e.touches[0];sx=t.clientX;sy=t.clientY;},{passive:true});
document.addEventListener('touchend',function(e){var t=e.changedTouches[0],dx=t.clientX-sx,dy=t.clientY-sy,s=window.getSelection();
if(s&&!s.isCollapsed)return;if(Math.abs(dx)>40&&Math.abs(dx)>Math.abs(dy)*1.5){var forward=rtl()?dx>0:dx<0;__turn(forward?1:-1);}},{passive:true});}
document.addEventListener('click',function(e){var m=e.target.closest?e.target.closest('mark[data-id]'):null;
if(m){e.preventDefault();Reader.mark(m.getAttribute('data-id'));return;}
if(e.target.closest&&e.target.closest('a'))return;var s=window.getSelection();if(s&&!s.isCollapsed)return;
if(window.__isPaged){var x=e.clientX,w=W();if(x<w*0.28){__turn(rtl()?1:-1);return;}if(x>w*0.72){__turn(rtl()?-1:1);return;}}
Reader.tap();},true);
})();
"""

/**
 * The WebView an EPUB's chapters show in. It serves the book's files itself and nothing else:
 * no network, no file access, and none of the book's scripts, only the reader's own.
 */
@SuppressLint("SetJavaScriptEnabled")
private class EpubView(private val context: Context, private val epub: EpubBook) {
  var css = ""
  var chapter = -1
    private set
  var pendingOffset = 0f
  var onChapter: (Int) -> Unit = {}
  var onScrolled: (Float) -> Unit = {}
  var onSelection: (EpubAction, EpubSelection) -> Unit = { _, _ -> }
  var onMark: (Long) -> Unit = {}
  var onTap: () -> Unit = {}
  var nextLabel = ""
  var marks: List<BookHighlight> = emptyList()
  /** laid out in pages turned sideways */
  var paged = false
  /** the page and how many, in book pages */
  var onPaged: (Int, Int) -> Unit = { _, _ -> }
  private var lastOffset = 0f
  private var styled = ""

  val web: WebView = BookWebView(context).apply {
    settings.javaScriptEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.builtInZoomControls = false
    settings.useWideViewPort = false
    settings.loadWithOverviewMode = false
    webViewClient = Client()
    addJavascriptInterface(Bridge(), "Reader")
    setOnScrollChangeListener { _, _, y, _, _ ->
      if (paged) return@setOnScrollChangeListener
      val height = contentHeight * resources.displayMetrics.density - this.height
      lastOffset = if (height <= 0f) 0f else (y / height).coerceIn(0f, 1f)
      onScrolled(lastOffset)
    }
  }

  fun show(index: Int, force: Boolean = false) {
    if (index == chapter && !force) return
    chapter = index
    styled = css
    web.loadUrl("https://$HOST/" + Uri.encode(epub.spine[index].path, "/"))
  }

  /** the chapter again in the new style, at the same place */
  fun restyle() {
    if (chapter < 0 || styled == css) return
    pendingOffset = lastOffset
    show(chapter, force = true)
  }

  fun paintMarks() {
    val calls = marks.joinToString("") { h ->
      val cls = if (h.noteUid.isNotEmpty()) "research" else "hl-${h.color.name}"
      "__mark('${h.id}',${h.start},${h.end},'$cls');"
    }
    web.evaluateJavascript("if(window.__clear){__clear();$calls}", null)
  }

  private inner class Bridge {
    @JavascriptInterface
    fun mark(id: String) {
      id.toLongOrNull()?.let { web.post { onMark(it) } }
    }

    @JavascriptInterface
    fun tap() {
      web.post { onTap() }
    }

    @JavascriptInterface
    fun paged(page: Int, pages: Int) {
      web.post {
        lastOffset = if (pages <= 1) 0f else page / (pages - 1).toFloat()
        // the place first, then the label with the page number
        onScrolled(lastOffset)
        onPaged(page, pages)
      }
    }

    /** turned past a chapter's first or last page: on to the next chapter, or back to the end of the one before */
    @JavascriptInterface
    fun edge(direction: Int) {
      web.post {
        if (direction > 0 && chapter < epub.spine.lastIndex) {
          pendingOffset = 0f
          onChapter(chapter + 1)
        } else if (direction < 0 && chapter > 0) {
          pendingOffset = 1f
          onChapter(chapter - 1)
        }
      }
    }
  }

  /** the system's selection bar, with the reader's own actions first */
  private inner class BookWebView(context: Context) : WebView(context) {
    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? =
      super.startActionMode(Wrapped(callback), type)

    private inner class Wrapped(private val inner: ActionMode.Callback) : ActionMode.Callback2() {
      override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val shown = inner.onCreateActionMode(mode, menu)
        menu.add(Menu.NONE, ID_HIGHLIGHT, 0, R.string.library_highlight)
        menu.add(Menu.NONE, ID_NOTE, 1, R.string.library_highlight_note)
        menu.add(Menu.NONE, ID_RESEARCH, 2, R.string.library_to_research)
        return shown
      }

      override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = inner.onPrepareActionMode(mode, menu)

      override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val action = when (item.itemId) {
          ID_HIGHLIGHT -> EpubAction.HIGHLIGHT
          ID_NOTE -> EpubAction.NOTE
          ID_RESEARCH -> EpubAction.RESEARCH
          else -> return inner.onActionItemClicked(mode, item)
        }
        evaluateJavascript("__sel()") { result ->
          parseSelection(result)?.let { onSelection(action, it) }
          evaluateJavascript("__unselect()", null)
        }
        mode.finish()
        return true
      }

      override fun onDestroyActionMode(mode: ActionMode) = inner.onDestroyActionMode(mode)

      override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
        if (inner is ActionMode.Callback2) inner.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect)
      }
    }
  }

  private fun parseSelection(result: String?): EpubSelection? = runCatching {
    val json = JSONTokener(result ?: return null).nextValue() as? String ?: return null
    val o = JSONObject(json)
    val text = o.getString("text").trim()
    if (text.isEmpty()) null else EpubSelection(chapter, o.getInt("start"), o.getInt("end"), text)
  }.getOrNull()

  private inner class Client : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
      val url = request.url
      if (url.host != HOST) return empty()
      val path = url.path.orEmpty().removePrefix("/")
      if (path.startsWith("$APP/")) {
        return runCatching { WebResourceResponse("font/ttf", null, context.assets.open(path.removePrefix("$APP/"))) }.getOrElse { empty() }
      }
      val input = runCatching { epub.open(path) }.getOrNull() ?: return empty()
      val type = mime(path)
      if (type == "text/html" || type == "application/xhtml+xml") {
        var html = input.use { it.readBytes().toString(Charsets.UTF_8) }
        // the book's scripts don't run; only the reader's
        html = SCRIPTS.replace(html, "")
        html = HANDLERS.replace(html, "")
        // the screen's width, or the WebView lays a page out wide and shrinks it
        val style = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no\"/>" +
          "<style type=\"text/css\">$css</style>"
        val head = HEAD.find(html)
        html = if (head != null) html.substring(0, head.range.last + 1) + style + html.substring(head.range.last + 1) else style + html
        val next = if (chapter < epub.spine.lastIndex) "<a class=\"__next\" href=\"https://$HOST/$NEXT\">${escape(nextLabel)} ›</a>" else ""
        // commented CDATA: XHTML needs it, HTML ignores it
        val script = "<script type=\"text/javascript\">//<![CDATA[\nwindow.__isPaged=$paged;\n$SCRIPT\n//]]></script>"
        val end = BODY_END.findAll(html).lastOrNull()
        html = if (end != null) html.substring(0, end.range.first) + next + script + html.substring(end.range.first) else html + next + script
        return WebResourceResponse(type, "UTF-8", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
      }
      return WebResourceResponse(type, null, input)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
      val url = request.url
      if (url.host == HOST) {
        val path = url.path.orEmpty().removePrefix("/")
        if (path == NEXT) {
          pendingOffset = 0f
          onChapter((chapter + 1).coerceAtMost(epub.spine.lastIndex))
          return true
        }
        val index = epub.spine.indexOfFirst { it.path == path }
        if (index < 0 || index == chapter) return false
        pendingOffset = 0f
        onChapter(index)
        return true
      }
      if (url.scheme == "http" || url.scheme == "https") {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
      }
      return true
    }

    override fun onPageFinished(view: WebView, url: String) {
      paintMarks()
      val offset = pendingOffset
      pendingOffset = 0f
      if (paged) {
        // the columns are laid out a moment after the page loads
        view.postDelayed({ view.evaluateJavascript("__goFraction($offset)", null) }, 200)
        return
      }
      if (offset > 0f) {
        view.postDelayed({
          val height = view.contentHeight * view.resources.displayMetrics.density - view.height
          if (height > 0) view.scrollTo(0, (height * offset).toInt())
        }, 150)
      }
    }
  }

  private fun empty() = WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

  private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")

  companion object {
    const val HOST = "epub.local"
    /** the app's own files the page may use: its Arabic font */
    const val APP = "__app"
    private const val NEXT = "__next"
    private const val ID_HIGHLIGHT = 0x5101
    private const val ID_NOTE = 0x5102
    private const val ID_RESEARCH = 0x5103
    private val HEAD = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)
    private val BODY_END = Regex("</body>", RegexOption.IGNORE_CASE)
    private val SCRIPTS = Regex("<script[\\s\\S]*?</script>|<script[^>]*/>", RegexOption.IGNORE_CASE)
    private val HANDLERS = Regex("\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*')", RegexOption.IGNORE_CASE)

    fun mime(path: String): String = when (path.substringAfterLast('.').lowercase()) {
      "xhtml", "xht" -> "application/xhtml+xml"
      "html", "htm" -> "text/html"
      "css" -> "text/css"
      "jpg", "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      "gif" -> "image/gif"
      "svg" -> "image/svg+xml"
      "webp" -> "image/webp"
      "ttf" -> "font/ttf"
      "otf" -> "font/otf"
      "woff" -> "font/woff"
      "woff2" -> "font/woff2"
      else -> "application/octet-stream"
    }
  }
}
