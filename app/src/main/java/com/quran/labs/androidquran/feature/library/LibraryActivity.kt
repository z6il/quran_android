package com.quran.labs.androidquran.feature.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch

/** The classical books to download, the reader's own PDF and EPUB files, and their shelves. */
class LibraryActivity : AppCompatActivity() {
  @Inject lateinit var catalog: LibraryCatalog
  @Inject lateinit var store: LibraryStore

  internal sealed interface Screen {
    data object Home : Screen
    data class OnShelf(val shelf: Shelf) : Screen
    /** the reader's books, [filter] null for all */
    data class Mine(val filter: MineFilter) : Screen
    data class Book(val id: String) : Screen
  }

  private val stack = mutableStateListOf<Screen>(Screen.Home)
  private var catalogBooks by mutableStateOf<List<CatalogBook>>(emptyList())
  /** a file picked to add, waiting for its shelf */
  private var picked by mutableStateOf<Uri?>(null)
  /** a file being copied in: a big PDF takes a few seconds */
  private var adding by mutableStateOf(false)
  internal val search = LibrarySearchState()

  private val pick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) picked = uri }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)
    onBackPressedDispatcher.addCallback(this) { back() }
    lifecycleScope.launch {
      store.refresh()
      catalogBooks = catalog.all()
    }
    intent.getStringExtra(EXTRA_BOOK)?.let { if (savedInstanceState == null) stack += Screen.Book(it) }

    setContentView(ComposeView(this).apply {
      setContent {
        QuranTheme {
          AnimatedContent(
            targetState = stack.last(),
            transitionSpec = {
              (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
            },
            label = "library"
          ) { screen -> Render(screen) }
          if (adding) {
            androidx.compose.material3.AlertDialog(
              onDismissRequest = {},
              confirmButton = {},
              text = {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                  androidx.compose.material3.CircularProgressIndicator(androidx.compose.ui.Modifier.size(24.dp))
                  androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.size(16.dp))
                  androidx.compose.material3.Text(getString(R.string.library_adding))
                }
              }
            )
          }
          picked?.let { uri ->
            ShelfPicker(
              current = Shelf.OTHER,
              onPick = { shelf ->
                picked = null
                addFile(uri, shelf)
              },
              onDismiss = { picked = null }
            )
          }
        }
      }
    })
  }

  override fun onResume() {
    super.onResume()
    // progress and status change while reading
    lifecycleScope.launch { store.refresh() }
  }

  @Composable
  private fun Render(screen: Screen) {
    val mine by store.books.collectAsState()
    val downloads by store.downloads.collectAsState()
    val byId = mine.associateBy { it.id }
    when (screen) {
      Screen.Home -> LibraryHome(
        catalog = catalogBooks,
        mine = mine,
        search = search,
        onSearch = { q -> catalog.search(q) },
        onShelf = { stack += Screen.OnShelf(it) },
        onMine = { stack += Screen.Mine(it) },
        onBook = { stack += Screen.Book(it) },
        onRead = ::read,
        onAddFile = ::pickFile,
        onBack = ::finish
      )
      is Screen.OnShelf -> ShelfScreen(
        shelf = screen.shelf,
        books = catalogBooks.filter { it.shelf == screen.shelf },
        mine = byId,
        onBook = { stack += Screen.Book(it) },
        onBack = ::back
      )
      is Screen.Mine -> MineScreen(
        initial = screen.filter,
        books = mine,
        onBook = { stack += Screen.Book(it) },
        onAddFile = ::pickFile,
        onBack = ::back
      )
      is Screen.Book -> BookScreen(
        catalog = catalogBooks.firstOrNull { it.id == screen.id },
        book = byId[screen.id],
        download = downloads[screen.id],
        store = store,
        onDownload = { c -> store.download(c) },
        onRead = { b, mark -> read(b, mark) },
        onDeleted = ::back,
        onBack = ::back
      )
    }
  }

  private fun read(book: LibraryBook, mark: BookMark? = null) {
    startActivity(ReaderActivity.intent(this, book.id, mark))
  }

  private fun pickFile() {
    pick.launch(arrayOf("application/pdf", "application/epub+zip"))
  }

  private fun addFile(uri: Uri, shelf: Shelf) {
    lifecycleScope.launch {
      adding = true
      val book = try {
        store.import(uri, shelf)
      } finally {
        adding = false
      }
      if (book == null) {
        Toast.makeText(this@LibraryActivity, R.string.library_add_failed, Toast.LENGTH_LONG).show()
      } else {
        Toast.makeText(this@LibraryActivity, getString(R.string.library_added, getString(shelf.title)), Toast.LENGTH_SHORT).show()
        stack += Screen.Book(book.id)
      }
    }
  }

  private fun back() {
    if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish()
  }

  companion object {
    private const val EXTRA_BOOK = "book"

    fun intent(context: Context, book: String? = null): Intent =
      Intent(context, LibraryActivity::class.java).apply { book?.let { putExtra(EXTRA_BOOK, it) } }
  }
}
