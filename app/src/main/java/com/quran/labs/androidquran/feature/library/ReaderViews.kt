package com.quran.labs.androidquran.feature.library

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.feature.hadith.HadithHighlight
import com.quran.labs.androidquran.feature.research.arabicFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

// ---------------------------------------------------------------- classical text

@Composable
internal fun TextReader(
  file: File,
  state: ReaderState,
  scale: Float,
  find: FindState,
  highlights: List<BookHighlight>,
  onHold: (position: Int, text: String) -> Unit
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
    }
  }
  LaunchedEffect(state.jump) {
    val to = state.jump ?: return@LaunchedEffect
    listState.scrollToItem(to.position.coerceIn(0, list.lastIndex))
    state.jump = null
  }
  LaunchedEffect(find.query, list) {
    val query = find.query
    if (query.isBlank()) {
      find.results = null
      return@LaunchedEffect
    }
    delay(350)
    val found = withContext(Dispatchers.Default) {
      list.indices.filter { i -> HadithHighlight.ranges(list[i].text(), query).isNotEmpty() }
    }
    find.results = found
    // the first match from where the reader is
    find.current = found.indexOfFirst { it >= listState.firstVisibleItemIndex }.takeIf { it >= 0 } ?: 0
    found.getOrNull(find.current)?.let { listState.scrollToItem(it) }
  }

  val arabic = arabicFont()
  val quran = MaterialTheme.colorScheme.primary
  val mark = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
  val query = find.query.takeIf { find.results != null }
  val byPosition = remember(highlights) { highlights.groupBy { it.position } }
  LazyColumn(state = listState, contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 40.dp)) {
    itemsIndexed(list) { i, block ->
      when (block) {
        is TextBlock.Heading -> Text(
          block.title,
          style = TextStyle(
            fontFamily = arabic, fontWeight = FontWeight.Bold, textDirection = TextDirection.Rtl,
            fontSize = (when (block.level) { 1 -> 25; 2 -> 22; else -> 20 } * scale).sp,
            lineHeight = (when (block.level) { 1 -> 40; 2 -> 36; else -> 32 } * scale).sp,
            textAlign = if (block.level == 1) TextAlign.Center else TextAlign.Start,
            color = MaterialTheme.colorScheme.primary
          ),
          modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp)
        )
        is TextBlock.Paragraph -> Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
          if (i in pageStarts) {
            Text(
              pageLabel(context, block.page, volumes),
              style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), textAlign = TextAlign.Center
            )
          }
          Text(
            styled(block.text, query, quran, mark, byPosition[i].orEmpty()),
            // hold to highlight
            modifier = Modifier.pointerInput(block) { detectTapGestures(onLongPress = { onHold(i, block.text) }) },
            style = TextStyle(
              fontFamily = arabic, fontSize = (20 * scale).sp, lineHeight = (37 * scale).sp,
              textDirection = TextDirection.Rtl, textAlign = TextAlign.Justify
            )
          )
        }
      }
    }
  }
}

private fun TextBlock.text(): String = when (this) {
  is TextBlock.Heading -> title
  is TextBlock.Paragraph -> text
}

/** the reader's highlights, Quran quotes in colour, and what's searched for marked */
private fun styled(text: String, query: String?, quran: Color, mark: Color, highlights: List<BookHighlight>): AnnotatedString {
  val base = HadithHighlight.annotate(text, query, mark)
  if ('﴿' !in text && highlights.isEmpty()) return base
  return buildAnnotatedString {
    append(base)
    highlights.forEach { h ->
      val start = h.start.coerceIn(0, text.length)
      val end = h.end.coerceIn(start, text.length)
      addStyle(SpanStyle(background = Color(h.color.argb).copy(alpha = 0.45f)), start, end)
    }
    var from = 0
    while (true) {
      val start = text.indexOf('﴿', from)
      if (start < 0) break
      val end = text.indexOf('﴾', start).let { if (it < 0) text.length else it + 1 }
      addStyle(SpanStyle(color = quran), start, end)
      from = end
    }
  }
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

// ---------------------------------------------------------------- PDF

@Composable
internal fun PdfReader(file: File, state: ReaderState) {
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

  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surfaceVariant)
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
      .pointerInput(Unit) {
        detectTapGestures(onDoubleTap = {
          zoom = if (zoom > 1f) 1f else 2f
          panX = 0f
        })
      }
  ) {
    val width = constraints.maxWidth
    val sharp = zoom > 1.5f
    LazyColumn(
      Modifier.fillMaxSize().graphicsLayer {
        scaleX = zoom
        scaleY = zoom
        translationX = panX
      },
      state = listState,
      contentPadding = PaddingValues(vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(pdf.pageCount) { i ->
        val target = if (sharp) (width * 2).coerceAtMost(2600) else width
        val bitmap by produceState<ImageBitmap?>(null, i, target) {
          value = withContext(Dispatchers.IO) { runCatching { pdf.render(i, target).asImageBitmap() }.getOrNull() } ?: value
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1f / pdf.ratios[i].coerceIn(0.2f, 5f)).background(Color.White)) {
          bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
      }
    }
  }
}

// ---------------------------------------------------------------- EPUB

@Composable
internal fun EpubReader(file: File, state: ReaderState, scale: Float) {
  val context = LocalContext.current
  val opened by produceState<EpubBook?>(null, file) {
    value = withContext(Dispatchers.IO) { runCatching { EpubBook.open(file) }.getOrNull() }
    if (value == null) state.failed = true
    awaitDispose { value?.close() }
  }
  val epub = opened ?: return
  var chapter by remember { mutableIntStateOf(state.place.position.coerceIn(0, epub.spine.lastIndex)) }
  val colors = MaterialTheme.colorScheme
  val dark = colors.surface.luminance() < 0.5f
  val css = remember(colors, dark) { epubCss(colors.surface.toArgb(), colors.onSurface.toArgb(), colors.primary.toArgb(), dark) }
  val view = remember(epub) { EpubView(context, epub).also { it.pendingOffset = state.place.offset } }
  view.css = css
  view.onChapter = { chapter = it }
  view.onScrolled = { f ->
    state.place = BookPlace(view.chapter, f)
    state.label = chapterLabel(context, epub, view.chapter, f)
  }

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

  Column(Modifier.fillMaxSize()) {
    AndroidView(
      factory = { view.web },
      update = { web ->
        web.settings.textZoom = (scale * 100).toInt()
        view.show(chapter)
      },
      modifier = Modifier.weight(1f).fillMaxWidth()
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      TextButton(enabled = chapter > 0, onClick = {
        view.pendingOffset = 0f
        chapter--
      }) { Text(stringResource(R.string.library_previous_chapter)) }
      Spacer(Modifier.weight(1f))
      TextButton(enabled = chapter < epub.spine.lastIndex, onClick = {
        view.pendingOffset = 0f
        chapter++
      }) { Text(stringResource(R.string.library_next_chapter)) }
    }
  }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private fun chapterLabel(context: Context, epub: EpubBook, chapter: Int, offset: Float): String {
  val title = epub.toc.lastOrNull { it.chapter <= chapter }?.title
    ?: context.getString(R.string.library_chapter_of, chapter + 1, epub.spine.size)
  // isolated, so an Arabic title and the percentage keep their order
  return "⁨" + title.take(60) + "⁩ · " + (offset * 100).toInt() + "%"
}

private fun hex(color: Int) = String.format("#%06X", color and 0xFFFFFF)

private fun epubCss(background: Int, text: Int, link: Int, dark: Boolean): String = buildString {
  append("@font-face{font-family:'AppArabic';src:url('https://${EpubView.HOST}/${EpubView.APP}/kitab.ttf');}")
  append("html,body{background:${hex(background)} !important;color:${hex(text)} !important;}")
  append("body{margin:0;padding:16px 18px 56px;line-height:1.85;font-family:'AppArabic',serif;overflow-wrap:break-word;}")
  append("a{color:${hex(link)} !important;}")
  append("img,svg,video,table{max-width:100% !important;height:auto !important;}")
  // a book's own colours would be unreadable on a dark page
  if (dark) append("*{color:inherit !important;background-color:transparent !important;border-color:${hex(text)}33 !important;}")
}

/**
 * The WebView an EPUB's chapters show in. It serves the book's files itself and nothing else:
 * no network, no file access, no scripts.
 */
@SuppressLint("SetJavaScriptEnabled")
private class EpubView(private val context: Context, private val epub: EpubBook) {
  var css = ""
  var chapter = -1
    private set
  var pendingOffset = 0f
  var onChapter: (Int) -> Unit = {}
  var onScrolled: (Float) -> Unit = {}

  val web: WebView = WebView(context).apply {
    settings.javaScriptEnabled = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.builtInZoomControls = false
    webViewClient = Client()
    setOnScrollChangeListener { _, _, y, _, _ ->
      val height = contentHeight * resources.displayMetrics.density - this.height
      onScrolled(if (height <= 0f) 0f else (y / height).coerceIn(0f, 1f))
    }
  }

  fun show(index: Int, force: Boolean = false) {
    if (index == chapter && !force) return
    chapter = index
    web.loadUrl("https://$HOST/" + Uri.encode(epub.spine[index].path, "/"))
  }

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
        val html = input.use { it.readBytes().toString(Charsets.UTF_8) }
        val style = "<style type=\"text/css\">$css</style>"
        val head = HEAD.find(html)
        val page = if (head != null) html.substring(0, head.range.last + 1) + style + html.substring(head.range.last + 1) else style + html
        return WebResourceResponse(type, "UTF-8", ByteArrayInputStream(page.toByteArray(Charsets.UTF_8)))
      }
      return WebResourceResponse(type, null, input)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
      val url = request.url
      if (url.host == HOST) {
        val path = url.path.orEmpty().removePrefix("/")
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
      val offset = pendingOffset
      pendingOffset = 0f
      if (offset > 0f) {
        view.postDelayed({
          val height = view.contentHeight * view.resources.displayMetrics.density - view.height
          if (height > 0) view.scrollTo(0, (height * offset).toInt())
        }, 150)
      }
    }
  }

  private fun empty() = WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

  companion object {
    const val HOST = "epub.local"
    /** the app's own files the page may use: its Arabic font */
    const val APP = "__app"
    private val HEAD = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)

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
