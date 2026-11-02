package com.quran.labs.androidquran.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.launch
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

/** kept across screens, so going back returns to the same search */
class LibrarySearchState {
  var query by mutableStateOf("")
  val list = LazyListState()
}

private fun megabytes(bytes: Long): String = String.format(Locale.getDefault(), "%.1f", bytes / 1_000_000.0)

@Composable
internal fun LibraryHome(
  catalog: List<CatalogBook>,
  mine: List<LibraryBook>,
  search: LibrarySearchState,
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
  val counts = remember(catalog) { catalog.groupingBy { it.shelf }.eachCount() }
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
      contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
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
          modifier = Modifier.fillMaxWidth()
        )
      }
      if (query.isNotBlank()) {
        val words = HadithLibrary.normalizeArabic(query).split(' ').filter { it.isNotBlank() }
        val files = mine.filter { b -> b.imported && words.all { it in HadithLibrary.normalizeArabic(b.title + " " + b.author) } }
        items(files, key = { "f" + it.id }) { MineRow(it, onClick = { onBook(it.id) }) }
        val found = results
        if (found != null && found.isEmpty() && files.isEmpty()) {
          item { Empty(stringResource(R.string.library_no_results)) }
        }
        items(found.orEmpty(), key = { it.id }) { CatalogRow(it, byId[it.id], onClick = { onBook(it.id) }) }
        return@LazyColumn
      }
      if (reading.isNotEmpty()) {
        item { SectionTitle(stringResource(R.string.library_continue)) }
        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(reading, key = { it.id }) { ContinueCard(it, onClick = { onRead(it) }) }
          }
        }
      }
      item { SectionTitle(stringResource(R.string.library_my_shelves)) }
      item {
        val tiles = listOf(
          Triple(MineFilter.READING, AppIcons.Library, mine.count(MineFilter.READING::matches)),
          Triple(MineFilter.WANT, AppIcons.Bookmark, mine.count(MineFilter.WANT::matches)),
          Triple(MineFilter.FINISHED, QuranIcons.Check, mine.count(MineFilter.FINISHED::matches)),
          Triple(MineFilter.FILES, AppIcons.Book, mine.count(MineFilter.FILES::matches))
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              pair.forEach { (filter, icon, count) ->
                StatusTile(filter, icon, count, Modifier.weight(1f), onClick = { onMine(filter) })
              }
            }
          }
        }
      }
      item {
        Spacer(Modifier.height(4.dp))
        SectionTitle(stringResource(R.string.library_catalog))
        // the catalogue takes a moment to read the first time
        if (catalog.isEmpty()) {
          LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        } else {
          Text(
            stringResource(R.string.library_catalog_note, catalog.size),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      items(Shelf.entries.filter { (counts[it] ?: 0) > 0 }, key = { "s" + it.key }) { shelf ->
        Surface(onClick = { onShelf(shelf) }, shape = RoundedCornerShape(16.dp), color = cardColor(), modifier = Modifier.fillMaxWidth()) {
          Row(Modifier.padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(shelf.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
              stringResource(R.string.library_books_count, counts[shelf] ?: 0),
              style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(QuranIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun StatusTile(filter: MineFilter, icon: ImageVector, count: Int, modifier: Modifier, onClick: () -> Unit) {
  Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = cardColor(), modifier = modifier) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
      }
      Spacer(Modifier.width(10.dp))
      Column {
        Text(stringResource(filter.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(count.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun ContinueCard(book: LibraryBook, onClick: () -> Unit) {
  Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = cardColor(), modifier = Modifier.width(220.dp)) {
    Column(Modifier.padding(16.dp)) {
      Text(
        book.title, style = TextStyle(fontFamily = arabicFont(), fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
        maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis
      )
      Text(book.author, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(12.dp))
      LinearProgressIndicator(progress = { book.progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
      Spacer(Modifier.height(4.dp))
      Text(
        stringResource(R.string.library_progress, (book.progress * 100).toInt()),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun Empty(text: String) {
  Text(
    text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
  )
}

/** a catalogue book, with how far the reader got if it's theirs */
@Composable
private fun CatalogRow(book: CatalogBook, mine: LibraryBook?, onClick: () -> Unit) {
  Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = cardColor(), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp, 12.dp)) {
      Text(
        book.title, style = TextStyle(fontFamily = arabicFont(), fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
        maxLines = 2, overflow = TextOverflow.Ellipsis
      )
      Text(
        book.author + " · " + stringResource(R.string.library_died, book.died),
        // the names are Arabic: they read from the right even in an English layout
        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, overflow = TextOverflow.Ellipsis
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          stringResource(R.string.library_pages, book.pages) + (book.madhhab?.let { " · " + stringResource(it.title) } ?: ""),
          style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
        )
        if (mine != null && mine.status != ReadingStatus.NONE) StatusLabel(mine)
      }
    }
  }
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
private fun MineRow(book: LibraryBook, onClick: () -> Unit) {
  Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = cardColor(), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp, 12.dp)) {
      Text(
        book.title, style = TextStyle(fontFamily = arabicFont(), fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
        maxLines = 2, overflow = TextOverflow.Ellipsis
      )
      val detail = listOfNotNull(
        book.author.takeIf { it.isNotBlank() },
        stringResource(book.shelf.title),
        if (book.imported) book.kind.name else null
      ).joinToString(" · ")
      Text(detail, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (book.file.isNotEmpty() && book.progress > 0f) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { book.progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
      }
      Row(Modifier.padding(top = 6.dp)) {
        Spacer(Modifier.weight(1f))
        StatusLabel(book)
      }
    }
  }
}

@Composable
internal fun ShelfScreen(shelf: Shelf, books: List<CatalogBook>, mine: Map<String, LibraryBook>, onBook: (String) -> Unit, onBack: () -> Unit) {
  var madhhab by rememberSaveable { mutableStateOf<Madhhab?>(null) }
  val shown = if (madhhab == null) books else books.filter { it.madhhab == madhhab }
  Scaffold(topBar = { AppTopBar(stringResource(shelf.title), onBack = onBack) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
    LazyColumn(
      Modifier.padding(padding),
      contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      if (books.any { it.madhhab != null }) {
        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              FilterChip(selected = madhhab == null, onClick = { madhhab = null }, label = { Text(stringResource(R.string.library_all)) }, shape = RoundedCornerShape(50))
            }
            items(Madhhab.entries) { m ->
              FilterChip(selected = madhhab == m, onClick = { madhhab = m }, label = { Text(stringResource(m.title)) }, shape = RoundedCornerShape(50))
            }
          }
        }
      }
      items(shown, key = { it.id }) { CatalogRow(it, mine[it.id], onClick = { onBook(it.id) }) }
    }
  }
}

@Composable
internal fun MineScreen(initial: MineFilter, books: List<LibraryBook>, onBook: (String) -> Unit, onAddFile: () -> Unit, onBack: () -> Unit) {
  var filter by rememberSaveable { mutableStateOf(initial) }
  val shown = books.filter(filter::matches)
  Scaffold(
    topBar = { AppTopBar(stringResource(R.string.library_my_shelves), onBack = onBack) },
    floatingActionButton = {
      if (filter == MineFilter.FILES) {
        ExtendedFloatingActionButton(onClick = onAddFile, icon = { Icon(AppIcons.Plus, contentDescription = null) }, text = { Text(stringResource(R.string.library_add_file)) })
      }
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    LazyColumn(
      Modifier.padding(padding),
      contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(MineFilter.entries) { f ->
            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(stringResource(f.title)) }, shape = RoundedCornerShape(50))
          }
        }
      }
      if (shown.isEmpty()) item { Empty(stringResource(R.string.library_empty_mine)) }
      items(shown, key = { it.id }) { MineRow(it, onClick = { onBook(it.id) }) }
    }
  }
}

@Composable
internal fun BookScreen(
  catalog: CatalogBook?,
  book: LibraryBook?,
  download: BookDownload?,
  store: LibraryStore,
  onDownload: (CatalogBook) -> Unit,
  onRead: (LibraryBook, BookMark?) -> Unit,
  onDeleted: () -> Unit,
  onBack: () -> Unit
) {
  val scope = rememberCoroutineScope()
  val title = book?.title ?: catalog?.title.orEmpty()
  var confirmDelete by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf(false) }
  var choosingShelf by remember { mutableStateOf(false) }
  val marks by produceState(emptyList<BookMark>(), book?.id, book?.opened) { value = book?.let { store.marks(it.id) }.orEmpty() }
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
      Text(title, style = TextStyle(fontFamily = arabicFont(), fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp))
      val author = book?.author?.takeIf { it.isNotBlank() } ?: catalog?.author
      if (!author.isNullOrBlank()) {
        Text(
          author + (catalog?.let { " · " + stringResource(R.string.library_died, it.died) } ?: ""),
          style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
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
    AlertDialog(
      onDismissRequest = { renaming = false },
      title = { Text(stringResource(R.string.library_rename)) },
      text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
      confirmButton = {
        TextButton(enabled = text.isNotBlank(), onClick = {
          renaming = false
          scope.launch { store.rename(book.id, text.trim()) }
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
