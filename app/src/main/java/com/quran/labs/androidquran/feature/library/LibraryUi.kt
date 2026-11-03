package com.quran.labs.androidquran.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.feature.home.AppIcons
import com.quran.labs.androidquran.feature.prayer.AppTopBar
import com.quran.labs.androidquran.feature.prayer.SectionTitle
import com.quran.labs.androidquran.feature.prayer.cardColor
import com.quran.labs.androidquran.feature.research.HadithLibrary
import com.quran.labs.androidquran.feature.research.arabicFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** which of the reader's books to list */
enum class MineFilter(val title: Int) {
  ALL(R.string.library_all_mine),
  READING(R.string.library_status_reading),
  WANT(R.string.library_status_want),
  FINISHED(R.string.library_status_finished),
  FILES(R.string.library_my_files);

  fun matches(book: LibraryBook): Boolean = when (this) {
    ALL -> true
    READING -> book.status == ReadingStatus.READING
    WANT -> book.status == ReadingStatus.WANT
    FINISHED -> book.status == ReadingStatus.FINISHED
    FILES -> book.imported
  }
}

/** how the reader's books are ordered */
enum class MineOrder(val title: Int) {
  RECENT(R.string.library_order_recent),
  TITLE(R.string.library_order_title),
  PROGRESS(R.string.library_order_progress)
}

/** kept across screens, so going back returns to the same search */
class LibrarySearchState {
  var query by mutableStateOf("")
  val list = LazyListState()
}

private fun megabytes(bytes: Long): String = String.format(Locale.getDefault(), "%.1f", bytes / 1_000_000.0)

/** a style for Arabic names in any layout: they read from the right */
@Composable
private fun names() = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content)

@Composable
internal fun LibraryHome(
  catalog: List<CatalogBook>,
  mine: List<LibraryBook>,
  search: LibrarySearchState,
  coverVersion: Int,
  onSearch: suspend (String) -> List<CatalogBook>,
  onShelf: (Shelf) -> Unit,
  onMine: (MineFilter) -> Unit,
  onBook: (String) -> Unit,
  onRead: (LibraryBook) -> Unit,
  onAddFile: () -> Unit,
  onBack: () -> Unit
) {
  val query = search.query
  val results by produceState<List<CatalogBook>?>(null, query, catalog) { value = if (query.isBlank()) null else onSearch(query) }
  val byShelf = remember(catalog) { catalog.groupBy { it.shelf } }
  val reading = mine.filter { it.status == ReadingStatus.READING && it.file.isNotEmpty() }
  val byId = mine.associateBy { it.id }
  Scaffold(
    topBar = { AppTopBar(stringResource(R.string.library_title), onBack = onBack) },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = onAddFile,
        icon = { Icon(AppIcons.Plus, contentDescription = null) },
        text = { Text(stringResource(R.string.library_add_file)) }
      )
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    LazyColumn(
      Modifier.padding(padding),
      state = search.list,
      contentPadding = PaddingValues(bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        OutlinedTextField(
          query, { search.query = it }, singleLine = true,
          leadingIcon = { Icon(QuranIcons.Search, contentDescription = null) },
          trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { search.query = "" }) { Icon(QuranIcons.Close, contentDescription = null) }
          },
          placeholder = { Text(stringResource(R.string.library_search)) },
          shape = RoundedCornerShape(50),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
      }
      if (query.isNotBlank()) {
        val words = HadithLibrary.normalizeArabic(query).split(' ').filter { it.isNotBlank() }
        val files = mine.filter { b -> b.imported && words.all { it in HadithLibrary.normalizeArabic(b.title + " " + b.author) } }
        items(files, key = { "f" + it.id }) { BookRow(it.id, it.title, it.author, it.shelf, subtitle = it.kind.name, mine = it, coverVersion, onClick = { onBook(it.id) }) }
        val found = results
        if (found != null && found.isEmpty() && files.isEmpty()) item { Empty(stringResource(R.string.library_no_results)) }
        items(found.orEmpty(), key = { it.id }) { CatalogRow(it, byId[it.id], coverVersion, onClick = { onBook(it.id) }) }
        return@LazyColumn
      }
      // the book being read, large
      reading.firstOrNull()?.let { book ->
        item { HeroBook(book, coverVersion, onRead = { onRead(book) }, onOpen = { onBook(book.id) }) }
      }
      if (reading.size > 1) {
        item { Box(Modifier.padding(horizontal = 16.dp)) { SectionTitle(stringResource(R.string.library_continue)) } }
        item {
          LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(reading.drop(1), key = { it.id }) { book -> CoverTile(book.id, book.title, book.author, book.shelf, book, coverVersion) { onRead(book) } }
          }
        }
      }
      item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.weight(1f)) { SectionTitle(stringResource(R.string.library_my_shelves)) }
          TextButton(onClick = { onMine(MineFilter.ALL) }) { Text(stringResource(R.string.library_see_all)) }
        }
      }
      item {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(listOf(MineFilter.READING, MineFilter.WANT, MineFilter.FINISHED, MineFilter.FILES)) { filter ->
            FilterChip(
              selected = false, onClick = { onMine(filter) }, shape = RoundedCornerShape(50),
              label = { Text(stringResource(filter.title) + "  " + mine.count(filter::matches)) }
            )
          }
        }
      }
      val shelf = mine.filter { it.status != ReadingStatus.NONE || it.imported }.take(12)
      if (shelf.isNotEmpty()) {
        item {
          LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(shelf, key = { "m" + it.id }) { book -> CoverTile(book.id, book.title, book.author, book.shelf, book, coverVersion) { onBook(book.id) } }
          }
        }
      } else {
        item { Empty(stringResource(R.string.library_empty_mine), Modifier.padding(horizontal = 16.dp)) }
      }
      item {
        Column(Modifier.padding(horizontal = 16.dp)) {
          SectionTitle(stringResource(R.string.library_catalog))
          // the catalogue takes a moment to read the first time
          if (catalog.isEmpty()) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
          } else {
            Text(stringResource(R.string.library_catalog_note, catalog.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      items(Shelf.entries.filter { !byShelf[it].isNullOrEmpty() }, key = { "s" + it.key }) { shelf ->
        ShelfCard(shelf, byShelf[shelf].orEmpty(), coverVersion, onClick = { onShelf(shelf) })
      }
    }
  }
}

/** the book the reader is in: its cover, how far they are, and the way back in */
@Composable
private fun HeroBook(book: LibraryBook, coverVersion: Int, onRead: () -> Unit, onOpen: () -> Unit) {
  Surface(onClick = onOpen, shape = RoundedCornerShape(24.dp), color = cardColor(), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      BookCover(book.id, book.title, book.author, book.shelf, 96.dp, Modifier.width(96.dp), coverVersion)
      Spacer(Modifier.width(16.dp))
      Column(Modifier.weight(1f)) {
        Text(stringResource(R.string.library_continue), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
          book.title, style = TextStyle(fontFamily = arabicFont(), fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
          maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (book.author.isNotBlank()) Text(book.author, style = names(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(progress = { book.progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            stringResource(R.string.library_progress, (book.progress * 100).toInt()),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
          )
          Button(onClick = onRead, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
            Text(stringResource(R.string.library_read))
          }
        }
      }
    }
  }
}

/** a cover with its title under it, and how far the reader got */
@Composable
private fun CoverTile(id: String, title: String, author: String, shelf: Shelf?, mine: LibraryBook?, coverVersion: Int, width: Int = 104, onClick: () -> Unit) {
  Column(Modifier.width(width.dp).clickable(onClick = onClick)) {
    BookCover(id, title, author, shelf, width.dp, Modifier.fillMaxWidth(), coverVersion)
    if (mine != null && mine.file.isNotEmpty() && mine.progress > 0f) {
      Spacer(Modifier.height(6.dp))
      LinearProgressIndicator(progress = { mine.progress }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)))
    }
    Spacer(Modifier.height(6.dp))
    Text(
      title, style = TextStyle(fontFamily = arabicFont(), fontSize = 14.sp, lineHeight = 20.sp, textDirection = TextDirection.Content),
      maxLines = 2, overflow = TextOverflow.Ellipsis
    )
  }
}

/** a shelf of the catalogue: three of its books fanned out, its name and how many */
@Composable
private fun ShelfCard(shelf: Shelf, books: List<CatalogBook>, coverVersion: Int, onClick: () -> Unit) {
  Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = cardColor(), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.width(92.dp).height(70.dp)) {
        books.take(3).reversed().forEachIndexed { k, b ->
          BookCover(b.id, b.title, "", shelf, 44.dp, Modifier.width(46.dp).offset(x = (k * 22).dp, y = (if (k == 1) 0 else 4).dp), coverVersion)
        }
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(stringResource(shelf.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
          stringResource(R.string.library_books_count, books.size),
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Icon(QuranIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
  }
}

@Composable
private fun Empty(text: String, modifier: Modifier = Modifier) {
  Text(
    text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.fillMaxWidth().padding(vertical = 24.dp)
  )
}

/** a book in a list: a small cover, its title and author, and where the reader is with it */
@Composable
private fun BookRow(
  id: String,
  title: String,
  author: String,
  shelf: Shelf?,
  subtitle: String,
  mine: LibraryBook?,
  coverVersion: Int,
  onClick: () -> Unit
) {
  Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = cardColor(), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      BookCover(id, title, "", shelf, 52.dp, Modifier.width(52.dp), coverVersion)
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(
          title, style = TextStyle(fontFamily = arabicFont(), fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
          maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (author.isNotBlank()) Text(author, style = names(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
          if (mine != null) StatusLabel(mine)
        }
        if (mine != null && mine.file.isNotEmpty() && mine.progress > 0f) {
          Spacer(Modifier.height(6.dp))
          LinearProgressIndicator(progress = { mine.progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
        }
      }
    }
  }
}

@Composable
private fun CatalogRow(book: CatalogBook, mine: LibraryBook?, coverVersion: Int, onClick: () -> Unit) {
  BookRow(
    book.id, book.title, book.author + " · " + stringResource(R.string.library_died, book.died), book.shelf,
    stringResource(R.string.library_pages, book.pages) + (book.madhhab?.let { " · " + stringResource(it.title) } ?: ""),
    mine, coverVersion, onClick
  )
}

@Composable
private fun StatusLabel(book: LibraryBook) {
  val text = when (book.status) {
    ReadingStatus.READING -> stringResource(R.string.library_progress, (book.progress * 100).toInt())
    ReadingStatus.WANT -> stringResource(R.string.library_status_want)
    ReadingStatus.FINISHED -> stringResource(R.string.library_status_finished)
    ReadingStatus.NONE -> return
  }
  Text(
    text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(8.dp, 2.dp)
  )
}

@Composable
internal fun ShelfScreen(shelf: Shelf, books: List<CatalogBook>, mine: Map<String, LibraryBook>, coverVersion: Int, onBook: (String) -> Unit, onBack: () -> Unit) {
  var madhhab by rememberSaveable { mutableStateOf<Madhhab?>(null) }
  val shown = if (madhhab == null) books else books.filter { it.madhhab == madhhab }
  Scaffold(topBar = { AppTopBar(stringResource(shelf.title), onBack = onBack) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
    LazyColumn(
      Modifier.padding(padding),
      contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      if (books.any { it.madhhab != null }) {
        item {
          LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              FilterChip(selected = madhhab == null, onClick = { madhhab = null }, label = { Text(stringResource(R.string.library_all)) }, shape = RoundedCornerShape(50))
            }
            items(Madhhab.entries) { m ->
              FilterChip(selected = madhhab == m, onClick = { madhhab = m }, label = { Text(stringResource(m.title)) }, shape = RoundedCornerShape(50))
            }
          }
        }
      }
      items(shown, key = { it.id }) { CatalogRow(it, mine[it.id], coverVersion, onClick = { onBook(it.id) }) }
    }
  }
}

/** the reader's books as a wall of covers */
@Composable
internal fun MineScreen(initial: MineFilter, books: List<LibraryBook>, coverVersion: Int, onBook: (String) -> Unit, onAddFile: () -> Unit, onBack: () -> Unit) {
  var filter by rememberSaveable { mutableStateOf(initial) }
  var order by rememberSaveable { mutableStateOf(MineOrder.RECENT) }
  var ordering by remember { mutableStateOf(false) }
  val shown = books.filter(filter::matches).let { list ->
    when (order) {
      MineOrder.RECENT -> list
      MineOrder.TITLE -> list.sortedBy { HadithLibrary.normalizeArabic(it.title) }
      MineOrder.PROGRESS -> list.sortedByDescending { it.progress }
    }
  }
  Scaffold(
    topBar = {
      AppTopBar(stringResource(R.string.library_my_shelves), onBack = onBack) {
        Box {
          TextButton(onClick = { ordering = true }) { Text(stringResource(order.title)) }
          DropdownMenu(ordering, onDismissRequest = { ordering = false }) {
            MineOrder.entries.forEach { o ->
              DropdownMenuItem(text = { Text(stringResource(o.title), fontWeight = if (o == order) FontWeight.Bold else null) }, onClick = {
                order = o
                ordering = false
              })
            }
          }
        }
      }
    },
    floatingActionButton = {
      if (filter == MineFilter.FILES) {
        ExtendedFloatingActionButton(onClick = onAddFile, icon = { Icon(AppIcons.Plus, contentDescription = null) }, text = { Text(stringResource(R.string.library_add_file)) })
      }
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    LazyVerticalGrid(
      GridCells.Adaptive(108.dp),
      Modifier.padding(padding),
      contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
      item(span = { GridItemSpan(maxLineSpan) }) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(MineFilter.entries) { f ->
            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(stringResource(f.title)) }, shape = RoundedCornerShape(50))
          }
        }
      }
      if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Empty(stringResource(R.string.library_empty_mine)) }
      items(shown, key = { it.id }) { book -> CoverTile(book.id, book.title, book.author, book.shelf, book, coverVersion, width = 108) { onBook(book.id) } }
    }
  }
}

/** what can be done to a book's cover */
internal class CoverActions(
  val pickPicture: () -> Unit,
  val fromPage: (Int) -> Unit,
  val reset: () -> Unit
)

@Composable
internal fun BookScreen(
  catalog: CatalogBook?,
  book: LibraryBook?,
  download: BookDownload?,
  store: LibraryStore,
  catalogue: LibraryCatalog,
  coverVersion: Int,
  cover: CoverActions,
  onDownload: (CatalogBook) -> Unit,
  onRead: (LibraryBook, BookMark?) -> Unit,
  onOpenCatalog: (String) -> Unit,
  onDeleted: () -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val title = book?.title ?: catalog?.title.orEmpty()
  var confirmDelete by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf(false) }
  var choosingShelf by remember { mutableStateOf(false) }
  var coverMenu by remember { mutableStateOf(false) }
  var matchDismissed by remember { mutableStateOf(false) }
  val marks by produceState(emptyList<BookMark>(), book?.id, book?.opened) { value = book?.let { store.marks(it.id) }.orEmpty() }
  // a file the reader added may be a book of the catalogue: by its first pages, or its title
  val match by produceState<CatalogBook?>(null, book?.id, book?.title) {
    val b = book ?: return@produceState
    if (!b.imported) return@produceState
    val text = withContext(Dispatchers.IO) {
      if (b.kind == BookKind.PDF) {
        val texts = PdfTextStore.get(context)
        val pages = mutableListOf<String>()
        for (page in 0 until 10) {
          for (s in TextSource.entries) {
            val words = texts.words(b.id, page, s)
            if (!words.isNullOrEmpty()) {
              pages += words.joinToString(" ") { it.text }
              break
            }
          }
        }
        pages.joinToString(" ")
      } else ""
    }
    value = catalogue.recognise(b.title + " " + text)
  }
  Scaffold(
    topBar = {
      AppTopBar("", onBack = onBack) {
        if (book?.imported == true) {
          IconButton(onClick = { renaming = true }) { Icon(AppIcons.Edit, contentDescription = stringResource(R.string.library_rename)) }
        }
        if (book != null) {
          IconButton(onClick = { confirmDelete = true }) { Icon(AppIcons.Delete, contentDescription = stringResource(R.string.library_delete)) }
        }
      }
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    Column(
      Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp, 4.dp, 20.dp, 32.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(verticalAlignment = Alignment.Top) {
        Box {
          BookCover(
            book?.id ?: catalog?.id.orEmpty(), title, book?.author ?: catalog?.author.orEmpty(), book?.shelf ?: catalog?.shelf, 120.dp,
            Modifier.width(120.dp).clickable(enabled = book?.imported == true) { coverMenu = true }, coverVersion
          )
          DropdownMenu(coverMenu, onDismissRequest = { coverMenu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.library_cover_picture)) }, onClick = {
              coverMenu = false
              cover.pickPicture()
            })
            if (book?.kind == BookKind.PDF) {
              DropdownMenuItem(text = { Text(stringResource(R.string.library_cover_first_page)) }, onClick = {
                coverMenu = false
                cover.fromPage(0)
              })
              DropdownMenuItem(text = { Text(stringResource(R.string.library_cover_this_page)) }, onClick = {
                coverMenu = false
                cover.fromPage(book.position)
              })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.library_cover_drawn)) }, onClick = {
              coverMenu = false
              cover.reset()
            })
          }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(title, style = TextStyle(fontFamily = arabicFont(), fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp))
          val author = book?.author?.takeIf { it.isNotBlank() } ?: catalog?.author
          if (!author.isNullOrBlank()) {
            Text(
              author + (catalog?.let { " · " + stringResource(R.string.library_died, it.died) } ?: ""),
              style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          val shelf = book?.shelf ?: catalog?.shelf
          val facts = listOfNotNull(
            shelf?.let { stringResource(it.title) },
            catalog?.madhhab?.let { stringResource(it.title) },
            catalog?.let { stringResource(R.string.library_pages, it.pages) },
            if (book?.imported == true) book.kind.name else null
          )
          Text(facts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
          if (book?.imported == true) {
            Text(stringResource(R.string.library_cover_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      // recognised: the catalogue's title and author, and its clean text
      val m = match
      if (m != null && book != null && !matchDismissed && HadithLibrary.normalizeArabic(book.title) != HadithLibrary.normalizeArabic(m.title)) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.library_looks_like), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(m.title, style = TextStyle(fontFamily = arabicFont(), fontSize = 18.sp, fontWeight = FontWeight.Bold))
            Text(m.author + " · " + stringResource(R.string.library_died, m.died), style = names(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TextButton(onClick = {
                scope.launch {
                  store.rename(book.id, m.title)
                  store.setAuthor(book.id, m.author)
                  store.setShelf(book.id, m.shelf)
                }
              }) { Text(stringResource(R.string.library_use_title)) }
              TextButton(onClick = { onOpenCatalog(m.id) }) { Text(stringResource(R.string.library_open_classical)) }
              Spacer(Modifier.weight(1f))
              IconButton(onClick = { matchDismissed = true }) { Icon(QuranIcons.Close, contentDescription = stringResource(R.string.library_cancel)) }
            }
          }
        }
      }

      // read, or get it first
      val readable = book != null && book.file.isNotEmpty() && java.io.File(book.file).exists()
      when {
        readable -> Button(onClick = { onRead(book!!, null) }, modifier = Modifier.fillMaxWidth()) {
          Text(
            if (book!!.progress > 0f) stringResource(R.string.library_continue_at, (book.progress * 100).toInt())
            else stringResource(R.string.library_read)
          )
        }
        download is BookDownload.Running -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          LinearProgressIndicator(progress = { download.fraction }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
          Text(stringResource(R.string.library_downloading, (download.fraction * 100).toInt()), style = MaterialTheme.typography.bodySmall)
        }
        catalog != null -> {
          if (download is BookDownload.Failed) {
            Text(stringResource(R.string.library_download_failed, download.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
          }
          Button(onClick = { onDownload(catalog) }, modifier = Modifier.fillMaxWidth()) {
            Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.library_download, megabytes(catalog.bytes)))
          }
        }
        book != null -> Text(stringResource(R.string.library_missing_file), color = MaterialTheme.colorScheme.error)
      }

      // where it stands
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ReadingStatus.WANT, ReadingStatus.READING, ReadingStatus.FINISHED).forEach { status ->
          val selected = book?.status == status
          FilterChip(
            selected = selected,
            onClick = {
              scope.launch {
                if (book == null && catalog != null) store.want(catalog)
                val id = book?.id ?: catalog?.id ?: return@launch
                store.setStatus(id, if (selected) ReadingStatus.NONE else status)
              }
            },
            label = { Text(stringResource(statusTitle(status))) },
            shape = RoundedCornerShape(50)
          )
        }
      }
      if (book?.imported == true) {
        OutlinedButton(onClick = { choosingShelf = true }) {
          Text(stringResource(R.string.library_shelf) + ": " + stringResource(book.shelf.title))
        }
      }

      if (book != null && marks.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        SectionTitle(stringResource(R.string.library_bookmarks))
        marks.forEach { mark ->
          Surface(onClick = { if (readable) onRead(book, mark) }, shape = RoundedCornerShape(16.dp), color = cardColor(), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(AppIcons.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(10.dp))
              Column(Modifier.weight(1f)) {
                Text(mark.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (mark.note.isNotBlank()) Text(mark.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
      if (catalog != null) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.library_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }

  if (confirmDelete && book != null) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      text = { Text(stringResource(R.string.library_delete_confirm, book.title)) },
      confirmButton = {
        TextButton(onClick = {
          confirmDelete = false
          scope.launch {
            store.delete(book.id)
            // a catalogue book stays to download again; the reader's own file is gone
            if (book.imported) onDeleted()
          }
        }) { Text(stringResource(R.string.library_remove)) }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.library_cancel)) } }
    )
  }
  if (renaming && book != null) {
    var text by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    AlertDialog(
      onDismissRequest = { renaming = false },
      title = { Text(stringResource(R.string.library_rename)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
          OutlinedTextField(author, { author = it }, singleLine = true, label = { Text(stringResource(R.string.library_author)) }, modifier = Modifier.fillMaxWidth())
        }
      },
      confirmButton = {
        TextButton(enabled = text.isNotBlank(), onClick = {
          renaming = false
          scope.launch {
            store.rename(book.id, text.trim())
            store.setAuthor(book.id, author.trim())
          }
        }) { Text(stringResource(R.string.library_save)) }
      },
      dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.library_cancel)) } }
    )
  }
  if (choosingShelf && book != null) {
    ShelfPicker(book.shelf, onPick = { shelf ->
      choosingShelf = false
      scope.launch { store.setShelf(book.id, shelf) }
    }, onDismiss = { choosingShelf = false })
  }
}

private fun statusTitle(status: ReadingStatus): Int = when (status) {
  ReadingStatus.NONE -> R.string.library_status_none
  ReadingStatus.WANT -> R.string.library_status_want
  ReadingStatus.READING -> R.string.library_status_reading
  ReadingStatus.FINISHED -> R.string.library_status_finished
}

@Composable
internal fun ShelfPicker(current: Shelf, onPick: (Shelf) -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.library_shelf)) },
    text = {
      LazyColumn {
        items(Shelf.entries) { shelf ->
          Surface(
            onClick = { onPick(shelf) },
            shape = RoundedCornerShape(12.dp),
            color = if (shelf == current) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(stringResource(shelf.title), modifier = Modifier.padding(12.dp, 12.dp), style = MaterialTheme.typography.bodyLarge)
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } }
  )
}
