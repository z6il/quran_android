package com.quran.labs.androidquran.feature.research

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.ui.PagerActivity
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Ideas linked to verses, hadith and each other, and the hadith library. */
class ResearchActivity : AppCompatActivity() {
  @Inject lateinit var store: ResearchStore
  @Inject lateinit var library: HadithLibrary
  @Inject lateinit var quranInfo: QuranInfo
  @Inject lateinit var quranDisplayData: QuranDisplayData
  @Inject lateinit var arabicDatabaseUtils: ArabicDatabaseUtils

  private sealed interface Screen {
    data object Ideas : Screen
    data class IdeaDetail(val id: Long) : Screen
    /** [forIdea]: picking a hadith to link to that idea */
    data class Library(val forIdea: Long?) : Screen
    data class HadithDetail(val entry: HadithEntry, val forIdea: Long?) : Screen
    data class PickIdea(val target: LinkTarget) : Screen
    data object Map : Screen
  }

  private val stack = mutableStateListOf<Screen>(Screen.Ideas)
  private val prefs by lazy { getSharedPreferences("research", MODE_PRIVATE) }
  private var authorName by mutableStateOf("")
  /** asking for the reader's name, then doing this */
  private var askingName by mutableStateOf<(() -> Unit)?>(null)
  private var pendingImport by mutableStateOf<ResearchBundle?>(null)
  private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::readFile) }
  private var collections by mutableStateOf<List<HadithCollection>>(emptyList())

  private val text = object : ResearchText {
    override fun suraName(sura: Int) = quranDisplayData.getSuraName(this@ResearchActivity, sura, false)
    override fun verseCount(sura: Int) = quranInfo.getNumberOfAyahs(sura)
    // the full verses (getAyahTextForSuraAyahs only gives the first few words, for bookmarks)
    override suspend fun verseText(target: LinkTarget.Ayah): String = withContext(Dispatchers.IO) {
      runCatching {
        arabicDatabaseUtils.getVerses(SuraAyah(target.sura, target.ayah), SuraAyah(target.sura, target.endAyah))
          .blockingGet()
          .joinToString(" ") { it.text }
      }.getOrDefault("")
    }
    override fun collectionName(slug: String): String {
      val c = collections.firstOrNull { it.slug == slug } ?: return slug
      return if (isArabicUi()) c.nameAr else c.nameEn
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)
    authorName = prefs.getString(PREF_AUTHOR, "").orEmpty()

    val target = LinkTarget.of(intent.getStringExtra(EXTRA_KIND).orEmpty(), intent.getStringExtra(EXTRA_REF).orEmpty())
    if (target != null && savedInstanceState == null) {
      stack.clear()
      stack.add(Screen.PickIdea(target))
    }
    if (savedInstanceState == null) readShared(intent)
    onBackPressedDispatcher.addCallback(this) { back() }
    lifecycleScope.launch {
      store.refresh()
      collections = library.collections()
    }

    setContentView(ComposeView(this).apply {
      setContent {
        QuranTheme {
          val libraryState by library.state.collectAsState()
          LaunchedEffect(libraryState) {
            if (libraryState is HadithLibraryState.Installed) collections = library.collections()
          }
          AnimatedContent(
            targetState = stack.last(),
            transitionSpec = {
              (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
            },
            label = "research"
          ) { screen -> Render(screen, libraryState) }
          askingName?.let { then ->
            AuthorNameDialog(
              current = authorName,
              onSave = { name ->
                authorName = name
                prefs.edit().putString(PREF_AUTHOR, name).apply()
                askingName = null
                then()
              },
              onDismiss = { askingName = null }
            )
          }
          pendingImport?.let { bundle ->
            ImportDialog(
              bundle,
              onImport = {
                pendingImport = null
                lifecycleScope.launch {
                  val count = store.import(bundle, authorName)
                  Toast.makeText(this@ResearchActivity, getString(R.string.research_imported, count), Toast.LENGTH_SHORT).show()
                  stack.clear()
                  stack.add(Screen.Ideas)
                }
              },
              onDismiss = { pendingImport = null }
            )
          }
        }
      }
    })
  }

  @Composable
  private fun Render(screen: Screen, libraryState: HadithLibraryState) {
    val scope = rememberCoroutineScope()
    val ideas by store.ideas.collectAsState()
    when (screen) {
      Screen.Ideas -> IdeasScreen(
        ideas = ideas,
        onOpen = { stack.add(Screen.IdeaDetail(it)) },
        onCreate = { title -> scope.launch { stack.add(Screen.IdeaDetail(store.createIdea(title))) } },
        onLibrary = { stack.add(Screen.Library(null)) },
        onMap = { stack.add(Screen.Map) },
        onShareAll = { withName { share(null) } },
        onImport = { pickFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        onName = { askingName = {} },
        onBack = { back() }
      )
      is Screen.IdeaDetail -> {
        val idea by produceState<Idea?>(null, screen.id) { value = store.idea(screen.id) }
        val links by produceState(emptyList<IdeaLink>(), screen.id, ideas) { value = store.links(screen.id) }
        val notes by produceState(emptyMap<String, NoteSource>(), screen.id, ideas) { value = store.notes() }
        val current = idea ?: return
        IdeaScreen(
          idea = current,
          links = links,
          notes = notes,
          otherIdeas = ideas.map { it.idea }.filter { it.id != current.id },
          text = text,
          hadith = { library.hadith(it) },
          onSave = { t, b, tags -> scope.launch { store.updateIdea(current.id, t, b, tags) } },
          onAddLink = { target, relation -> scope.launch { store.addLink(current.id, target, relation) } },
          onAddNote = { t, s, relation -> scope.launch { store.addNote(current.id, t, s, relation) } },
          onEditNote = { uid, t, s -> scope.launch { store.updateNote(current.id, uid, t, s) } },
          onRemoveLink = { link -> scope.launch { store.removeLink(link) } },
          onEditLink = { link, relation, note -> scope.launch { store.updateLink(link, relation, note) } },
          onPickHadith = { stack.add(Screen.Library(current.id)) },
          onOpenTarget = { openTarget(it) },
          onShare = { withName { share(setOf(current.id)) } },
          onDelete = { scope.launch { store.deleteIdea(current.id); back() } },
          onBack = { back() }
        )
      }
      is Screen.Library -> HadithLibraryScreen(
        state = libraryState,
        collections = collections,
        search = { q, c -> library.search(q, c) },
        text = text,
        onDownload = { lifecycleScope.launch { library.download() } },
        onOpen = { stack.add(Screen.HadithDetail(it, screen.forIdea)) },
        onBack = { back() }
      )
      is Screen.HadithDetail -> HadithScreen(
        entry = screen.entry,
        text = text,
        askRelation = screen.forIdea != null,
        onAddToIdea = { relation ->
          if (screen.forIdea != null) {
            // came from an idea: link it there and go back to that idea
            scope.launch {
              store.addLink(screen.forIdea, screen.entry.target, relation)
              while (stack.size > 1 && stack.last() != Screen.IdeaDetail(screen.forIdea)) stack.removeAt(stack.lastIndex)
            }
          } else {
            stack.add(Screen.PickIdea(screen.entry.target))
          }
        },
        onBack = { back() }
      )
      is Screen.PickIdea -> PickIdeaScreen(
        target = screen.target,
        ideas = ideas,
        text = text,
        hadith = { library.hadith(it) },
        onPick = { ideaId, newTitle, relation ->
          scope.launch {
            val id = ideaId ?: store.createIdea(newTitle.orEmpty())
            store.addLink(id, screen.target, relation)
            stack.clear()
            stack.add(Screen.Ideas)
            stack.add(Screen.IdeaDetail(id))
          }
        },
        onBack = { back() }
      )
      Screen.Map -> ResearchMap(
        store = store,
        text = text,
        hadith = { library.hadith(it) },
        onOpen = { openTarget(it) },
        onBack = { back() }
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    readShared(intent)
  }

  /** a research file opened from a file manager or shared from another app */
  private fun readShared(intent: Intent) {
    val uri = when (intent.action) {
      Intent.ACTION_VIEW -> intent.data
      Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
      else -> null
    } ?: return
    readFile(uri)
  }

  private fun readFile(uri: Uri) {
    lifecycleScope.launch {
      val bundle = withContext(Dispatchers.IO) {
        runCatching { contentResolver.openInputStream(uri)?.use { it.reader().readText() } }.getOrNull()
      }?.let(ResearchBundle::parse)
      if (bundle == null || bundle.ideas.isEmpty()) {
        Toast.makeText(this@ResearchActivity, R.string.research_import_invalid, Toast.LENGTH_LONG).show()
      } else {
        pendingImport = bundle
      }
    }
  }

  /** runs [then] once the reader has a name to share under */
  private fun withName(then: () -> Unit) {
    if (authorName.isBlank()) askingName = then else then()
  }

  private fun share(ideaIds: Set<Long>?) {
    lifecycleScope.launch {
      val bundle = store.export(ideaIds, authorName)
      val file = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = (if (bundle.ideas.size == 1) bundle.ideas.first().title else getString(R.string.research_title))
          .replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40).ifBlank { "research" }
        File(dir, "$name.json").apply { writeText(bundle.toJson().toString(2)) }
      }
      val uri = FileProvider.getUriForFile(this@ResearchActivity, getString(R.string.file_authority), file)
      val send = Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, getString(R.string.research_share_text, bundle.ideas.size, authorName))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      startActivity(Intent.createChooser(send, getString(R.string.research_share)))
    }
  }

  private fun back() {
    if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish()
  }

  private fun openTarget(target: LinkTarget) {
    when (target) {
      is LinkTarget.Ayah -> startActivity(
        Intent(this, PagerActivity::class.java)
          .putExtra("page", quranInfo.getPageFromSuraAyah(target.sura, target.ayah))
          .putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, target.sura)
          .putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, target.ayah)
      )
      is LinkTarget.Hadith -> lifecycleScope.launch {
        library.hadith(target)?.let { stack.add(Screen.HadithDetail(it, null)) }
      }
      is LinkTarget.Idea -> stack.add(Screen.IdeaDetail(target.id))
      // a note lives in the ideas that cite it
      is LinkTarget.Note -> lifecycleScope.launch {
        store.ideasLinking(target).firstOrNull()?.let { stack.add(Screen.IdeaDetail(it.id)) }
      }
    }
  }

  companion object {
    private const val EXTRA_KIND = "kind"
    private const val EXTRA_REF = "ref"
    private const val PREF_AUTHOR = "author"

    fun intent(context: Context): Intent = Intent(context, ResearchActivity::class.java)

    /** opens straight to choosing an idea for [target], e.g. from a verse's menu */
    fun addToIdeaIntent(context: Context, target: LinkTarget): Intent =
      intent(context).putExtra(EXTRA_KIND, target.kind).putExtra(EXTRA_REF, target.ref)
  }
}
