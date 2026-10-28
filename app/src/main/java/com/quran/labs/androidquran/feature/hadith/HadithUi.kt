package com.quran.labs.androidquran.feature.hadith

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.quran.labs.androidquran.feature.research.HadithBook
import com.quran.labs.androidquran.feature.research.HadithCollection
import com.quran.labs.androidquran.feature.research.HadithEntry
import com.quran.labs.androidquran.feature.research.HadithLibraryState
import com.quran.labs.androidquran.feature.research.LinkTarget
import com.quran.labs.androidquran.feature.research.arabicFont
import com.quran.labs.androidquran.feature.research.isArabicUi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import java.util.Locale

private val CardShape = RoundedCornerShape(16.dp)

private fun Int.localized(): String = String.format(Locale.getDefault(), "%,d", this)

/**
 * The home screen's search and where it was scrolled, kept by the activity so that opening a
 * hadith and coming back returns to the same results.
 */
class HadithSearchState {
  var query by mutableStateOf("")
  var collection by mutableStateOf<String?>(null)
  var grade by mutableStateOf<GradeLevel?>(null)
  var results by mutableStateOf<List<HadithEntry>?>(null)
  /** what [results] were found for, so returning doesn't search again */
  internal var searched: Pair<String, String?>? = null
  val list = LazyListState()
}

/** names of the books, in the reader's language */
fun interface CollectionNames {
  fun name(slug: String): String
}

/** The topic index, today's hadith, favourites and search. */
@OptIn(FlowPreview::class)
@Composable
fun HadithHome(
  state: HadithLibraryState,
  collections: List<HadithCollection>,
  names: CollectionNames,
  favoriteCount: Int,
  favorites: Set<LinkTarget.Hadith>,
  ofTheDay: HadithEntry?,
  topics: List<Pair<HadithTopic, List<HadithBook>>>,
  libraryOutdated: Boolean,
  searchState: HadithSearchState,
  search: suspend (String, String?) -> List<HadithEntry>,
  onDownload: () -> Unit,
  onTopic: (HadithTopic) -> Unit,
  onCollection: (HadithCollection) -> Unit,
  onFavorites: () -> Unit,
  onOpen: (HadithEntry) -> Unit,
  onToggleFavorite: (HadithEntry) -> Unit
) {
  var query by searchState::query
  var collection by searchState::collection
  var gradeFilter by searchState::grade
  val context = androidx.compose.ui.platform.LocalContext.current
  val results = searchState.results
  LaunchedEffect(state, collection) {
    snapshotFlow { query }.debounce(300).collect { q ->
      val key = q to collection
      if (key == searchState.searched) return@collect
      searchState.results = if (q.isBlank()) null else search(q, collection)
      searchState.searched = key
    }
  }
  Scaffold(
    topBar = {
      AppTopBar(stringResource(R.string.hadith_title), onBack = null) {
        IconButton(onClick = onFavorites) { Icon(AppIcons.Star, contentDescription = stringResource(R.string.hadith_favorites)) }
      }
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    if (state !is HadithLibraryState.Installed) {
      Download(state, onDownload, Modifier.padding(padding))
      return@Scaffold
    }
    LazyColumn(
      Modifier.padding(padding),
      state = searchState.list,
      contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item {
        OutlinedTextField(
          query, { query = it }, singleLine = true,
          leadingIcon = { Icon(QuranIcons.Search, contentDescription = null) },
          trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(QuranIcons.Close, contentDescription = stringResource(R.string.hadith_clear)) }
          },
          placeholder = { Text(stringResource(R.string.research_search_hadith)) },
          shape = RoundedCornerShape(50),
          modifier = Modifier.fillMaxWidth()
        )
      }
      if (query.isNotBlank()) {
        val r = results?.let { all ->
          val level = gradeFilter
          if (level == null) all else all.filter { knownGrade(context, it).level == level }
        }
        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              FilterChip(selected = collection == null, onClick = { collection = null },
                label = { Text(stringResource(R.string.research_all)) }, shape = RoundedCornerShape(50))
            }
            items(collections) { c ->
              FilterChip(selected = collection == c.slug, onClick = { collection = c.slug },
                label = { Text(names.name(c.slug)) }, shape = RoundedCornerShape(50))
            }
          }
        }
        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf<GradeLevel?>(null) + GradeLevel.entries.filter { it != GradeLevel.UNGRADED }) { level ->
              FilterChip(
                selected = gradeFilter == level,
                onClick = { gradeFilter = level },
                label = { Text(stringResource(level?.label ?: R.string.hadith_any_grade)) },
                leadingIcon = level?.let { l -> { Box(Modifier.size(10.dp).clip(CircleShape).background(l.color)) } },
                shape = RoundedCornerShape(50)
              )
            }
          }
        }
        if (r != null && r.isEmpty()) {
          item { Text(stringResource(R.string.research_no_hadith), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        itemsIndexed(r.orEmpty(), key = { i, h -> "${h.collection}:${h.number}:$i" }) { _, h ->
          HadithCard(h, names, h.target in favorites, highlight = query, onOpen = { onOpen(h) }, onToggleFavorite = { onToggleFavorite(h) })
        }
        return@LazyColumn
      }
      if (libraryOutdated) {
        item {
          // Musnad Ahmad, the Muwatta and the rest came in a newer library
          Surface(onClick = onDownload, shape = CardShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
              Badge { Icon(AppIcons.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
              Spacer(Modifier.width(14.dp))
              Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.hadith_more_books), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.hadith_more_books_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
      if (ofTheDay != null) {
        item { DailyHadith(ofTheDay, names) { onOpen(ofTheDay) } }
      }
      item {
        Surface(onClick = onFavorites, shape = CardShape, color = cardColor(), modifier = Modifier.fillMaxWidth()) {
          Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge { Icon(AppIcons.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
              Text(stringResource(R.string.hadith_favorites), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text(
                if (favoriteCount == 0) stringResource(R.string.hadith_favorites_none_short)
                else stringResource(R.string.hadith_favorites_count, favoriteCount),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      if (topics.isNotEmpty()) {
        item { SectionTitle(stringResource(R.string.hadith_topics)) }
        items(topics.chunked(2)) { pair ->
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { (topic, books) -> TopicTile(topic, books, Modifier.weight(1f)) { onTopic(topic) } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
          }
        }
      }
      item { SectionTitle(stringResource(R.string.hadith_books)) }
      items(collections, key = { it.slug }) { c -> CollectionCard(c) { onCollection(c) } }
    }
  }
}

@Composable
private fun Badge(content: @Composable () -> Unit) {
  Box(
    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
    contentAlignment = Alignment.Center
  ) { content() }
}

@Composable
private fun TopicTile(topic: HadithTopic, books: List<HadithBook>, modifier: Modifier, onClick: () -> Unit) {
  Surface(onClick = onClick, shape = CardShape, color = cardColor(), modifier = modifier) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(5.dp, 34.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)))
      Spacer(Modifier.width(10.dp))
      Column {
        Text(
          stringResource(topic.label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
          maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
          stringResource(R.string.hadith_count, books.sumOf { it.count }.localized()),
          style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun DailyHadith(entry: HadithEntry, names: CollectionNames, onOpen: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Surface(onClick = onOpen, shape = RoundedCornerShape(24.dp), color = colors.primary, contentColor = colors.onPrimary, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(stringResource(R.string.hadith_of_the_day), style = MaterialTheme.typography.labelLarge, color = colors.onPrimary.copy(alpha = 0.8f))
      Text(
        entry.displayText, maxLines = 6, overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = arabicFont(), fontSize = 20.sp, lineHeight = 34.sp, textDirection = TextDirection.Rtl),
        modifier = Modifier.fillMaxWidth()
      )
      Text(
        names.name(entry.collection) + " · " + entry.number,
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.onPrimary.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 5.dp)
      )
    }
  }
}

@Composable
private fun CollectionCard(collection: HadithCollection, onClick: () -> Unit) {
  val arabic = isArabicUi()
  Surface(onClick = onClick, shape = CardShape, color = cardColor(), modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Badge { Icon(AppIcons.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          collection.nameAr,
          style = TextStyle(fontFamily = arabicFont(), fontSize = 20.sp, fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface
        )
        if (!arabic) Text(collection.nameEn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text(
        stringResource(R.string.hadith_count, collection.count.localized()),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
          .padding(horizontal = 10.dp, vertical = 4.dp)
      )
    }
  }
}

/** the grade as a coloured pill: green sahih, light green hasan, orange da'if, red very weak, dark red fabricated */
@Composable
fun GradeBadge(entry: HadithEntry) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val known = remember(entry) { knownGrade(context, entry) }
  val level = known.level
  // a grade the source gives is filled; one taken from a parallel is only outlined
  val shahid = known is KnownGrade.Shahid
  Row(
    Modifier.clip(RoundedCornerShape(50))
      .background(level.color.copy(alpha = if (shahid) 0.05f else 0.14f))
      .then(if (shahid) Modifier.border(1.dp, level.color.copy(alpha = 0.5f), RoundedCornerShape(50)) else Modifier)
      .padding(horizontal = 10.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(level.color))
    Spacer(Modifier.width(6.dp))
    Text(
      if (shahid) stringResource(R.string.hadith_has_shahid, stringResource(level.label)) else stringResource(level.label),
      style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = level.color
    )
  }
}

@Composable
private fun NarratorLine(entry: HadithEntry) {
  val narrator = remember(entry.text) { narratorOf(entry.text) } ?: return
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(QuranIcons.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
    Spacer(Modifier.width(4.dp))
    Text(
      stringResource(R.string.hadith_narrator, narrator),
      style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
fun HadithCard(
  entry: HadithEntry,
  names: CollectionNames,
  favorite: Boolean,
  showSource: Boolean = true,
  /** the search, to mark its words and show the part of the hadith where they are */
  highlight: String? = null,
  onOpen: () -> Unit,
  onToggleFavorite: () -> Unit
) {
  val mark = searchMark()
  val preview = remember(entry, highlight) { searchPreview(entry, highlight, mark) }
  Surface(onClick = onOpen, shape = CardShape, color = cardColor(), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          if (showSource) names.name(entry.collection) + " · " + entry.number else "#" + entry.number,
          style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        GradeBadge(entry)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleFavorite) {
          Icon(
            if (favorite) AppIcons.Star else AppIcons.StarBorder,
            contentDescription = stringResource(if (favorite) R.string.hadith_unfavorite else R.string.hadith_favorite),
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      NarratorLine(entry)
      Text(
        preview, maxLines = 5, overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = arabicFont(), fontSize = 18.sp, lineHeight = 31.sp, textDirection = TextDirection.Rtl),
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp)
      )
    }
  }
}

@Composable
private fun Download(state: HadithLibraryState, onDownload: () -> Unit, modifier: Modifier) {
  Column(
    modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center
    ) { Icon(AppIcons.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) }
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.research_library_intro, 17), textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    when (state) {
      is HadithLibraryState.Downloading -> {
        LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.research_downloading, (state.fraction * 100).toInt()))
      }
      is HadithLibraryState.Failed -> {
        Text(stringResource(R.string.research_download_failed, state.message), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onDownload, shape = RoundedCornerShape(50)) { Text(stringResource(R.string.research_retry)) }
      }
      else -> Button(onClick = onDownload, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(stringResource(R.string.research_download))
      }
    }
  }
}

@Composable
private fun BookRow(book: HadithBook, index: Int, onClick: () -> Unit) {
  val arabic = isArabicUi()
  Surface(onClick = onClick, shape = CardShape, color = cardColor(), modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
      ) {
        Text(index.localized(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
      }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(
          book.nameAr.ifBlank { book.nameEn },
          style = TextStyle(fontFamily = arabicFont(), fontSize = 18.sp, fontWeight = FontWeight.Bold),
          maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (!arabic && book.nameEn.isNotBlank()) {
          Text(book.nameEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      Spacer(Modifier.width(8.dp))
      Text(book.count.localized(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

/** A topic of the index: its books in each of the six collections. */
@Composable
fun TopicScreen(topic: HadithTopic, books: List<HadithBook>, names: CollectionNames, onBook: (HadithBook) -> Unit, onBack: () -> Unit) {
  val byCollection = remember(books) { books.groupBy { it.collection }.toList() }
  Scaffold(topBar = { AppTopBar(stringResource(topic.label), onBack) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      byCollection.forEach { (collection, list) ->
        item(key = "h:$collection") { SectionTitle(names.name(collection)) }
        itemsIndexed(list, key = { _, b -> "${b.collection}:${b.number}" }) { i, book -> BookRow(book, i + 1) { onBook(book) } }
      }
    }
  }
}

/** A collection's books (kitab), in order. */
@Composable
fun CollectionScreen(collection: HadithCollection, names: CollectionNames, books: suspend () -> List<HadithBook>, onBook: (HadithBook) -> Unit, onBack: () -> Unit) {
  val list by produceState<List<HadithBook>?>(null, collection) { value = books() }
  Scaffold(topBar = { AppTopBar(names.name(collection.slug), onBack) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
    if (list == null) {
      LinearProgressIndicator(Modifier.padding(padding).fillMaxWidth())
      return@Scaffold
    }
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      itemsIndexed(list.orEmpty(), key = { _, b -> b.number }) { i, book -> BookRow(book, i + 1) { onBook(book) } }
    }
  }
}

/** A book's hadith. */
@Composable
fun BookScreen(
  book: HadithBook,
  names: CollectionNames,
  favorites: Set<LinkTarget.Hadith>,
  hadith: suspend () -> List<HadithEntry>,
  onOpen: (HadithEntry) -> Unit,
  onToggleFavorite: (HadithEntry) -> Unit,
  onBack: () -> Unit
) {
  val list by produceState<List<HadithEntry>?>(null, book) { value = hadith() }
  Scaffold(
    topBar = { AppTopBar(if (isArabicUi()) book.nameAr.ifBlank { book.nameEn } else book.nameEn.ifBlank { book.nameAr }, onBack) },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    if (list == null) {
      LinearProgressIndicator(Modifier.padding(padding).fillMaxWidth())
      return@Scaffold
    }
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      item { SectionTitle(names.name(book.collection)) }
      if (book.number.startsWith(HadithTopic.TOPIC_BOOK)) {
        item {
          Text(stringResource(R.string.hadith_topic_approximate), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
      }
      itemsIndexed(list.orEmpty(), key = { i, h -> "${h.number}:$i" }) { _, h ->
        HadithCard(h, names, h.target in favorites, showSource = false, onOpen = { onOpen(h) }, onToggleFavorite = { onToggleFavorite(h) })
      }
    }
  }
}

/** The starred hadith, newest first. */
@Composable
fun FavoritesScreen(
  favorites: List<LinkTarget.Hadith>,
  names: CollectionNames,
  load: suspend (LinkTarget.Hadith) -> HadithEntry?,
  onOpen: (HadithEntry) -> Unit,
  onToggleFavorite: (HadithEntry) -> Unit,
  onBack: () -> Unit
) {
  // keep removed ones on screen until leaving, so a slip can be undone with another tap
  val shown by produceState<List<HadithEntry>?>(null, favorites.size) {
    val current = value.orEmpty()
    val loaded = favorites.mapNotNull { t -> current.firstOrNull { it.target == t } ?: load(t) }
    value = (loaded + current.filter { e -> loaded.none { it.target == e.target } })
  }
  val starred = favorites.toSet()
  Scaffold(topBar = { AppTopBar(stringResource(R.string.hadith_favorites), onBack) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
    val list = shown
    if (list != null && list.isEmpty()) {
      Column(
        Modifier.padding(padding).fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(AppIcons.StarBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.hadith_favorites_none), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      return@Scaffold
    }
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      items(list.orEmpty(), key = { it.target.ref }) { h ->
        HadithCard(h, names, h.target in starred, onOpen = { onOpen(h) }, onToggleFavorite = { onToggleFavorite(h) })
      }
    }
  }
}

/** the colour behind searched words */
@Composable
private fun searchMark(): androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)

/**
 * A card's text: the hadith with the searched words marked. When they come later than the
 * card shows, it starts a little before the first one, so the match is always in view.
 */
private fun searchPreview(entry: HadithEntry, query: String?, mark: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString {
  if (query.isNullOrBlank()) return androidx.compose.ui.text.AnnotatedString(entry.displayText)
  // the words may be in the chain of narration rather than the prophetic text
  val source = if (HadithHighlight.ranges(entry.displayText, query).isNotEmpty()) entry.displayText else entry.text
  val first = HadithHighlight.ranges(source, query).firstOrNull()?.first ?: return HadithHighlight.annotate(source, query, mark)
  if (first < 160) return HadithHighlight.annotate(source, query, mark)
  val start = source.lastIndexOf(' ', first - 60).coerceAtLeast(0)
  return HadithHighlight.annotate("… " + source.substring(start).trimStart(), query, mark)
}

/** One hadith in full: text, grade, narrator. */
@Composable
fun HadithDetailScreen(
  entry: HadithEntry,
  onOpenParallel: (Parallel) -> Unit,
  /** the search that led here, to mark its words */
  highlight: String?,
  names: CollectionNames,
  favorite: Boolean,
  onToggleFavorite: () -> Unit,
  onCopy: () -> Unit,
  onShare: () -> Unit,
  onResearch: () -> Unit,
  onBack: () -> Unit
) {
  val arabic = arabicFont()
  val mark = searchMark()
  val marked = remember(entry, highlight) { HadithHighlight.annotate(entry.text, highlight, mark) }
  val matches = remember(entry, highlight) { if (highlight.isNullOrBlank()) emptyList() else HadithHighlight.ranges(entry.text, highlight) }
  val scroll = rememberScrollState()
  var textTop by remember { mutableStateOf(0f) }
  var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
  var current by remember(entry, highlight) { mutableStateOf(0) }
  val density = androidx.compose.ui.platform.LocalDensity.current
  // bring a match into view, a little below the top
  suspend fun goTo(i: Int) {
    val l = layout ?: return
    val range = matches.getOrNull(i) ?: return
    val y = textTop + l.getLineTop(l.getLineForOffset(range.first)) - with(density) { 120.dp.toPx() }
    scroll.animateScrollTo(y.toInt().coerceAtLeast(0))
  }
  LaunchedEffect(layout, current) { if (matches.isNotEmpty()) goTo(current) }
  Scaffold(
    topBar = {
      AppTopBar(names.name(entry.collection) + " · " + entry.number, onBack) {
        IconButton(onClick = onToggleFavorite) {
          Icon(
            if (favorite) AppIcons.Star else AppIcons.StarBorder,
            contentDescription = stringResource(if (favorite) R.string.hadith_unfavorite else R.string.hadith_favorite),
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
          )
        }
        IconButton(onClick = onCopy) { Icon(AppIcons.Copy, contentDescription = stringResource(R.string.hadith_copy)) }
        IconButton(onClick = onShare) { Icon(AppIcons.Share, contentDescription = stringResource(R.string.hadith_share)) }
      }
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    Column(
      Modifier.padding(padding).verticalScroll(scroll).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      if (matches.isNotEmpty()) {
        // how many times the search appears, and a way through them
        Surface(shape = RoundedCornerShape(50), color = mark, modifier = Modifier.fillMaxWidth()) {
          Row(Modifier.heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(QuranIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
              stringResource(R.string.hadith_matches, current + 1, matches.size, highlight.orEmpty().trim()),
              style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            if (matches.size > 1) {
              IconButton(onClick = { current = (current - 1 + matches.size) % matches.size }) {
                Icon(AppIcons.ChevronLeft, contentDescription = stringResource(R.string.hadith_match_previous))
              }
              IconButton(onClick = { current = (current + 1) % matches.size }) {
                Icon(AppIcons.ChevronRight, contentDescription = stringResource(R.string.hadith_match_next))
              }
            }
          }
        }
      }
      if (entry.chapter.isNotBlank()) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
          Text(
            entry.chapter,
            style = TextStyle(fontFamily = arabic, fontSize = 17.sp, lineHeight = 28.sp, textDirection = TextDirection.Rtl, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(14.dp)
          )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GradeCard(entry, names, onOpenParallel, Modifier.weight(1f))
        remember(entry.text) { narratorOf(entry.text) }?.let { NarratorCard(it, Modifier.weight(1f)) }
      }
      Surface(
        shape = CardShape, color = cardColor(),
        modifier = Modifier.fillMaxWidth().onGloballyPositioned { textTop = it.positionInParent().y + with(density) { 18.dp.toPx() } }
      ) {
        Text(
          marked,
          style = TextStyle(fontFamily = arabic, fontSize = 23.sp, lineHeight = 42.sp, textDirection = TextDirection.Rtl),
          onTextLayout = { layout = it },
          modifier = Modifier.padding(18.dp)
        )
      }
      OutlinedButton(onClick = onResearch, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Icon(AppIcons.Bulb, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.research_add_to_idea))
      }
    }
  }
}

@Composable
private fun GradeCard(entry: HadithEntry, names: CollectionNames, onOpenParallel: (Parallel) -> Unit, modifier: Modifier) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val known = remember(entry) { knownGrade(context, entry) }
  val parallels = remember(entry) { HadithTakhrij.parallels(context, entry) }
  val level = known.level
  val grader = graderOf(entry.grade)
  val detail = gradeDetailOf(entry.grade)
  Surface(shape = CardShape, color = level.color.copy(alpha = 0.11f), modifier = modifier) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(stringResource(R.string.hadith_grade), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(level.color))
        Spacer(Modifier.width(6.dp))
        Text(
          if (known is KnownGrade.Shahid) stringResource(R.string.hadith_has_shahid, stringResource(level.label)) else stringResource(level.label),
          style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = level.color
        )
      }
      when (known) {
        is KnownGrade.Takhrij -> Text(known.text, style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Rtl),
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        is KnownGrade.Shahid -> Text(stringResource(R.string.hadith_shahid_note), style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Unit
      }
      // the same words elsewhere, each with its own grading; tap to read it
      parallels.forEach { p ->
        Text(
          "↳ " + names.name(p.collection) + " " + p.number + " · " + stringResource(p.level.label),
          style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = p.level.color,
          modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onOpenParallel(p) }.padding(vertical = 3.dp)
        )
      }
      val note = when {
        known !is KnownGrade.Source && known !is KnownGrade.Unknown -> null
        grader != null -> stringResource(R.string.hadith_graded_by, graderName(grader)) +
          (if (detail.isNotBlank() && detail.split(' ').size > 1) " · $detail" else "")
        entry.collection == "bukhari" || entry.collection == "muslim" -> stringResource(R.string.hadith_grade_sahihain)
        entry.grade.isBlank() -> stringResource(R.string.hadith_grade_not_in_source)
        else -> null
      }
      if (note != null) {
        Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun NarratorCard(narrator: String, modifier: Modifier) {
  Surface(shape = CardShape, color = cardColor(), modifier = modifier) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(stringResource(R.string.hadith_narrator_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(QuranIcons.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(narrator, style = TextStyle(fontFamily = arabicFont(), fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 2)
      }
    }
  }
}

@Composable
private fun graderName(grader: String): String = when (grader.lowercase()) {
  "al-albani" -> stringResource(R.string.hadith_grader_albani)
  "darussalam" -> stringResource(R.string.hadith_grader_darussalam)
  else -> grader
}
