package com.quran.labs.androidquran.feature.hadith

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.feature.home.AppTab
import com.quran.labs.androidquran.feature.home.WithBottomBar
import com.quran.labs.androidquran.feature.research.HadithBook
import com.quran.labs.androidquran.feature.research.HadithCollection
import com.quran.labs.androidquran.feature.research.HadithEntry
import com.quran.labs.androidquran.feature.research.HadithLibrary
import com.quran.labs.androidquran.feature.research.HadithLibraryState
import com.quran.labs.androidquran.feature.research.ResearchActivity
import com.quran.labs.androidquran.feature.research.isArabicUi
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar

/** Read the six books: browse by book, search, keep favourites. */
class HadithActivity : AppCompatActivity() {
  @Inject lateinit var library: HadithLibrary
  @Inject lateinit var favorites: HadithFavorites

  private sealed interface Screen {
    data object Home : Screen
    data class Topic(val topic: HadithTopic) : Screen
    data class Collection(val collection: HadithCollection) : Screen
    data class Book(val book: HadithBook) : Screen
    /** [highlight]: the search it was found with, if any */
    data class Detail(val entry: HadithEntry, val highlight: String? = null) : Screen
    data object Favorites : Screen
  }

  private val stack = mutableStateListOf<Screen>(Screen.Home)
  private var collections by mutableStateOf<List<HadithCollection>>(emptyList())
  private var ofTheDay by mutableStateOf<HadithEntry?>(null)
  private var topics by mutableStateOf<List<Pair<HadithTopic, List<HadithBook>>>>(emptyList())
  private val searchState = HadithSearchState()

  private val names = CollectionNames { slug ->
    val c = collections.firstOrNull { it.slug == slug }
    if (c == null) slug else if (isArabicUi()) c.nameAr else c.nameEn
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)
    onBackPressedDispatcher.addCallback(this) { back() }
    lifecycleScope.launch { favorites.refresh() }
    // today's hadith shows at once when it was already picked today
    ofTheDay = cachedOfTheDay()
    // opened to search for something, e.g. from elsewhere in the app
    if (savedInstanceState == null) intent.getStringExtra(EXTRA_QUERY)?.let { searchState.query = it }

    setContentView(ComposeView(this).apply {
      setContent {
        QuranTheme {
          val state by library.state.collectAsState()
          LaunchedEffect(state) {
            if (state is HadithLibraryState.Installed) {
              collections = library.collections()
              // today's hadith first: it's at the top of the page
              if (ofTheDay == null) {
                ofTheDay = library.ofTheDay(today().toLong())?.also(::cacheOfTheDay)
              }
              val byTopic = library.allBooks().groupBy(HadithTopic::of)
              topics = HadithTopic.entries.mapNotNull { t -> byTopic[t]?.let { t to it } }
            }
          }
          WithBottomBar(this@HadithActivity, AppTab.HADITH, showBar = stack.size == 1) {
            AnimatedContent(
              targetState = stack.last(),
              transitionSpec = {
                (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
              },
              label = "hadith"
            ) { screen -> Render(screen, state) }
          }
        }
      }
    })
  }

  @Composable
  private fun Render(screen: Screen, state: HadithLibraryState) {
    val starred by favorites.favorites.collectAsState()
    val starredSet = remember(starred) { starred.toSet() }
    val toggle = { e: HadithEntry -> lifecycleScope.launch { favorites.toggle(e.target) }; Unit }
    when (screen) {
      Screen.Home -> HadithHome(
        state = state,
        collections = collections,
        names = names,
        favoriteCount = starred.size,
        favorites = starredSet,
        ofTheDay = ofTheDay,
        topics = topics,
        searchState = searchState,
        search = { q, c -> library.search(q, c) },
        onDownload = { lifecycleScope.launch { library.download() } },
        onTopic = { stack.add(Screen.Topic(it)) },
        onCollection = { stack.add(Screen.Collection(it)) },
        onFavorites = { stack.add(Screen.Favorites) },
        onOpen = { stack.add(Screen.Detail(it, searchState.query.takeIf { q -> q.isNotBlank() })) },
        onToggleFavorite = toggle
      )
      is Screen.Topic -> TopicScreen(
        topic = screen.topic,
        books = topics.firstOrNull { it.first == screen.topic }?.second.orEmpty(),
        names = names,
        onBook = { stack.add(Screen.Book(it)) },
        onBack = ::back
      )
      is Screen.Collection -> CollectionScreen(
        collection = screen.collection,
        names = names,
        books = { library.books(screen.collection.slug) },
        onBook = { stack.add(Screen.Book(it)) },
        onBack = ::back
      )
      is Screen.Book -> BookScreen(
        book = screen.book,
        names = names,
        favorites = starredSet,
        hadith = { library.inBook(screen.book) },
        onOpen = { stack.add(Screen.Detail(it)) },
        onToggleFavorite = toggle,
        onBack = ::back
      )
      is Screen.Detail -> HadithDetailScreen(
        entry = screen.entry,
        highlight = screen.highlight,
        names = names,
        favorite = screen.entry.target in starredSet,
        onToggleFavorite = { toggle(screen.entry) },
        onCopy = { copy(screen.entry) },
        onShare = { share(screen.entry) },
        onResearch = { startActivity(ResearchActivity.addToIdeaIntent(this, screen.entry.target)) },
        onBack = ::back
      )
      Screen.Favorites -> FavoritesScreen(
        favorites = starred,
        names = names,
        load = { library.hadith(it) },
        onOpen = { stack.add(Screen.Detail(it)) },
        onToggleFavorite = toggle,
        onBack = ::back
      )
    }
  }

  private val dailyPrefs by lazy { getSharedPreferences("hadith_of_the_day", MODE_PRIVATE) }

  /** the local date as yyyymmdd, so the hadith changes at midnight where the reader is */
  private fun today(): Int = Calendar.getInstance().let {
    it.get(Calendar.YEAR) * 10_000 + (it.get(Calendar.MONTH) + 1) * 100 + it.get(Calendar.DAY_OF_MONTH)
  }

  private fun cachedOfTheDay(): HadithEntry? {
    if (dailyPrefs.getInt(DAY, 0) != today()) return null
    return runCatching {
      val json = JSONObject(dailyPrefs.getString(ENTRY, null) ?: return null)
      HadithEntry(
        json.getString("collection"), json.getString("number"), json.optString("chapter"),
        json.getString("text"), json.optString("matn"), json.optString("grade")
      )
    }.getOrNull()
  }

  private fun cacheOfTheDay(entry: HadithEntry) {
    val json = JSONObject()
      .put("collection", entry.collection).put("number", entry.number).put("chapter", entry.chapter)
      .put("text", entry.text).put("matn", entry.matn).put("grade", entry.grade)
    dailyPrefs.edit().putInt(DAY, today()).putString(ENTRY, json.toString()).apply()
  }

  private fun shareText(entry: HadithEntry): String = buildString {
    append(entry.text.trim())
    append("\n\n")
    append(names.name(entry.collection)).append(" ").append(entry.number)
    if (entry.grade.isNotBlank()) append(" — ").append(entry.grade)
  }

  private fun copy(entry: HadithEntry) {
    getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(names.name(entry.collection), shareText(entry)))
    Toast.makeText(this, R.string.hadith_copied, Toast.LENGTH_SHORT).show()
  }

  private fun share(entry: HadithEntry) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(entry))
    startActivity(Intent.createChooser(send, getString(R.string.hadith_share)))
  }

  companion object {
    private const val DAY = "day"
    private const val ENTRY = "entry"
    const val EXTRA_QUERY = "query"
  }

  private fun back() {
    if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish()
  }
}
