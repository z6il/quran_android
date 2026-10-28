package com.quran.labs.androidquran.feature.research

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.Locale

private val CardShape = RoundedCornerShape(12.dp)

private fun icon(name: String, path: String): ImageVector =
  ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()

internal val LightBulb by lazy {
  icon("Idea", "M9,21c0,0.55 0.45,1 1,1h4c0.55,0 1,-0.45 1,-1v-1L9,20v1zM12,2C8.14,2 5,5.14 5,9c0,2.38 1.19,4.47 3,5.74L8,17" +
    "c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1v-2.26c1.81,-1.27 3,-3.36 3,-5.74 0,-3.86 -3.14,-7 -7,-7z")
}
internal val Library by lazy {
  icon("Library", "M4,6L2,6v14c0,1.1 0.9,2 2,2h14v-2L4,20L4,6zM20,2L8,2c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12" +
    "c1.1,0 2,-0.9 2,-2L22,4c0,-1.1 -0.9,-2 -2,-2zM19,11L9,11L9,9h10v2zM15,15L9,15v-2h6v2zM19,7L9,7L9,5h10v2z")
}
private val Delete by lazy {
  icon("Delete", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
}
private val Plus by lazy { icon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z") }
private val Edit by lazy {
  icon("Edit", "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34" +
    "c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z")
}
internal val Quote by lazy { icon("Quote", "M6,17h3l2,-4V7H5v6h3zM14,17h3l2,-4V7h-6v6h3z") }
internal val Share by lazy {
  icon("Share", "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11" +
    "c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9" +
    "c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92" +
    "1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z")
}
private val MoreVert by lazy {
  icon("More", "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z" +
    "M12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z")
}
internal val Recenter by lazy {
  icon("Recenter", "M12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4zM5,15L3,15v4c0,1.1 0.9,2 2,2h4v-2L5,19v-4z" +
    "M5,5h4L9,3L5,3c-1.1,0 -2,0.9 -2,2v4h2L5,5zM19,3h-4v2h4v4h2L21,5c0,-1.1 -0.9,-2 -2,-2zM19,19h-4v2h4c1.1,0 2,-0.9 2,-2v-4h-2v4z")
}
internal val Graph by lazy {
  icon("Map3d", "M18.25,7.6l-5.5,-3.18c-0.46,-0.27 -1.04,-0.27 -1.5,0L5.75,7.6C5.29,7.87 5,8.36 5,8.9v6.35c0,0.54 0.29,1.03 0.75,1.3" +
    "l5.5,3.18c0.46,0.27 1.04,0.27 1.5,0l5.5,-3.18c0.46,-0.27 0.75,-0.76 0.75,-1.3V8.9C19,8.36 18.71,7.87 18.25,7.6zM7,14.96v-4.62" +
    "l4,2.32v4.61L7,14.96zM12,10.93L8,8.61l4,-2.31l4,2.31L12,10.93zM13,17.27v-4.61l4,-2.32v4.62L13,17.27z")
}

@Composable
private fun cardColor(): Color =
  MaterialTheme.colorScheme.primary.copy(alpha = 0.07f).compositeOver(MaterialTheme.colorScheme.surface)

@Composable
internal fun arabicFont(): FontFamily {
  val assets = LocalContext.current.assets
  return remember(assets) { FontFamily(Font("kitab.ttf", assets)) }
}

internal fun isArabicUi() = Locale.getDefault().language == "ar"

private fun Int.localized(): String = String.format(Locale.getDefault(), "%d", this)

@Composable
fun relationLabel(relation: Relation): String = stringResource(
  when (relation) {
    Relation.EXPLAINS -> R.string.research_relation_explains
    Relation.SUPPORTS -> R.string.research_relation_supports
    Relation.RELATED -> R.string.research_relation_related
    Relation.CONTRASTS -> R.string.research_relation_contrasts
  }
)

fun relationColor(relation: Relation): Color = when (relation) {
  Relation.EXPLAINS -> Color(0xFF00838F)
  Relation.SUPPORTS -> Color(0xFF2E7D32)
  Relation.RELATED -> Color(0xFF5C6BC0)
  Relation.CONTRASTS -> Color(0xFFC62828)
}

/** How the screens show a verse or hadith reference and fetch its text. */
interface ResearchText {
  fun suraName(sura: Int): String
  fun verseCount(sura: Int): Int
  suspend fun verseText(target: LinkTarget.Ayah): String
  fun collectionName(slug: String): String
}

@Composable
private fun RelationChip(relation: Relation) {
  Surface(shape = RoundedCornerShape(6.dp), color = relationColor(relation).copy(alpha = 0.14f)) {
    Text(
      relationLabel(relation),
      color = relationColor(relation),
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelationPicker(selected: Relation, onSelect: (Relation) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Relation.entries.forEach { r ->
      FilterChip(
        selected = r == selected,
        onClick = { onSelect(r) },
        label = { Text(relationLabel(r)) },
        shape = RoundedCornerShape(8.dp)
      )
    }
  }
}

/** "by @name" on ideas someone else shared */
@Composable
private fun AuthorLabel(author: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Icon(QuranIcons.Person, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
    Text(
      stringResource(R.string.research_by, author),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.tertiary
    )
  }
}

/** Asks for the name ideas are shared under. */
@Composable
fun AuthorNameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
  var name by rememberSaveable { mutableStateOf(current) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.research_your_name)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.research_your_name_hint), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
          value = name, onValueChange = { name = it.removePrefix("@").take(40) }, singleLine = true,
          prefix = { Text("@") }, modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text(stringResource(R.string.research_save)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.research_cancel)) } }
  )
}

/** What a shared file holds, before it's added. */
@Composable
fun ImportDialog(bundle: ResearchBundle, onImport: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(LightBulb, contentDescription = null) },
    title = { Text(stringResource(R.string.research_import)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          if (bundle.author.isBlank()) stringResource(R.string.research_import_confirm, bundle.ideas.size)
          else stringResource(R.string.research_import_confirm_by, bundle.ideas.size, bundle.author)
        )
        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          bundle.ideas.forEach { idea ->
            Text(
              "• " + idea.title.ifBlank { stringResource(R.string.research_untitled) } +
                (if (idea.links.isNotEmpty()) "  (${idea.links.size})" else ""),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onImport) { Text(stringResource(R.string.research_add)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.research_cancel)) } }
  )
}

@Composable
private fun CountBadge(icon: ImageVector, count: Int) {
  if (count <= 0) return
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    Text(count.localized(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
  }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
  @OptIn(ExperimentalMaterial3Api::class)
  TopAppBar(
    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    navigationIcon = { IconButton(onClick = onBack) { Icon(QuranIcons.ArrowBack, contentDescription = stringResource(R.string.research_back)) } },
    actions = { actions() },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
  )
}

/** The list of ideas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdeasScreen(
  ideas: List<IdeaSummary>,
  onOpen: (Long) -> Unit,
  onCreate: (String) -> Unit,
  onLibrary: () -> Unit,
  onMap: () -> Unit,
  onShareAll: () -> Unit,
  onImport: () -> Unit,
  onName: () -> Unit,
  onBack: () -> Unit
) {
  var query by rememberSaveable { mutableStateOf("") }
  var creating by rememberSaveable { mutableStateOf(false) }
  var menu by remember { mutableStateOf(false) }
  val shown = remember(ideas, query) {
    val q = query.trim()
    if (q.isEmpty()) ideas
    else ideas.filter { s -> s.idea.title.contains(q, true) || s.idea.tags.any { it.contains(q, true) } || s.idea.body.contains(q, true) }
  }
  Scaffold(
    topBar = {
      TopBar(stringResource(R.string.research_title), onBack) {
        IconButton(onClick = onMap) { Icon(Graph, contentDescription = stringResource(R.string.research_map)) }
        IconButton(onClick = onLibrary) { Icon(Library, contentDescription = stringResource(R.string.research_hadith_library)) }
        Box {
          IconButton(onClick = { menu = true }) { Icon(MoreVert, contentDescription = stringResource(R.string.research_more)) }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.research_share_all)) },
              leadingIcon = { Icon(Share, contentDescription = null) },
              enabled = ideas.isNotEmpty(),
              onClick = { menu = false; onShareAll() }
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.research_import)) },
              leadingIcon = { Icon(Library, contentDescription = null) },
              onClick = { menu = false; onImport() }
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.research_your_name)) },
              leadingIcon = { Icon(QuranIcons.Person, contentDescription = null) },
              onClick = { menu = false; onName() }
            )
          }
        }
      }
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = { creating = true },
        icon = { Icon(Plus, contentDescription = null) },
        text = { Text(stringResource(R.string.research_new_idea)) }
      )
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (ideas.isNotEmpty()) {
        item {
          OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(QuranIcons.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.research_search)) },
            shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
          )
        }
      } else {
        item {
          Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(LightBulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text(
              stringResource(R.string.research_empty),
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 24.dp)
            )
          }
        }
      }
      items(shown, key = { it.idea.id }) { s ->
        Card(
          onClick = { onOpen(s.idea.id) },
          shape = CardShape,
          colors = CardDefaults.cardColors(containerColor = cardColor(), contentColor = MaterialTheme.colorScheme.onSurface),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.idea.title.ifBlank { stringResource(R.string.research_untitled) }, style = MaterialTheme.typography.titleMedium)
            if (s.idea.author.isNotBlank()) AuthorLabel(s.idea.author)
            if (s.idea.body.isNotBlank()) {
              Text(s.idea.body, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
              CountBadge(QuranIcons.MenuBook, s.ayat)
              CountBadge(Library, s.hadith)
              CountBadge(LightBulb, s.ideas)
              CountBadge(Quote, s.notes)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                s.idea.tags.take(4).forEach { tag ->
                  Text("#$tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        }
      }
    }
  }
  if (creating) {
    var title by rememberSaveable { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { creating = false },
      title = { Text(stringResource(R.string.research_new_idea)) },
      text = {
        OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true,
          label = { Text(stringResource(R.string.research_idea_title)) }, modifier = Modifier.fillMaxWidth())
      },
      confirmButton = {
        TextButton(enabled = title.isNotBlank(), onClick = { creating = false; onCreate(title) }) { Text(stringResource(R.string.research_add)) }
      },
      dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.research_cancel)) } }
    )
  }
}

/** One idea: its title, notes and tags, and everything linked to it. */
@OptIn(ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun IdeaScreen(
  idea: Idea,
  links: List<IdeaLink>,
  notes: Map<String, NoteSource>,
  otherIdeas: List<Idea>,
  text: ResearchText,
  hadith: suspend (LinkTarget.Hadith) -> HadithEntry?,
  onSave: (title: String, body: String, tags: List<String>) -> Unit,
  onAddLink: (LinkTarget, Relation, parentId: Long?) -> Unit,
  onAddNote: (text: String, source: String, Relation, parentId: Long?) -> Unit,
  onEditNote: (uid: String, text: String, source: String) -> Unit,
  onRemoveLink: (IdeaLink) -> Unit,
  onEditLink: (IdeaLink, Relation, String) -> Unit,
  onPickHadith: (parentId: Long?) -> Unit,
  onOpenTarget: (LinkTarget) -> Unit,
  onShare: () -> Unit,
  onDelete: () -> Unit,
  onBack: () -> Unit
) {
  var title by rememberSaveable(idea.id) { mutableStateOf(idea.title) }
  var body by rememberSaveable(idea.id) { mutableStateOf(idea.body) }
  var tags by rememberSaveable(idea.id) { mutableStateOf(idea.tags.joinToString(", ")) }
  var addingVerse by rememberSaveable { mutableStateOf(false) }
  var linkingIdea by rememberSaveable { mutableStateOf(false) }
  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  var editingLink by rememberSaveable { mutableStateOf<Long?>(null) }
  var addingNote by rememberSaveable { mutableStateOf(false) }
  // the link a verse, hadith, note or idea is being added under; null for the idea itself
  var addingUnder by rememberSaveable { mutableStateOf<Long?>(null) }
  val tree = remember(links) { linkTree(links) }
  var editingNote by rememberSaveable { mutableStateOf<String?>(null) }
  // a new idea opens on its details; one with content opens on its sources
  var editingDetails by rememberSaveable(idea.id) { mutableStateOf(idea.title.isBlank() && idea.body.isBlank()) }
  var addMenu by remember { mutableStateOf(false) }
  // only one kind of source, as a flat list; null for the whole tree
  var kindFilter by rememberSaveable { mutableStateOf<String?>(null) }
  var collapsedIds by rememberSaveable(idea.id) { mutableStateOf("") }
  val collapsed = remember(collapsedIds) { collapsedIds.split(',').mapNotNull { it.toLongOrNull() }.toSet() }
  fun setCollapsed(ids: Set<Long>) {
    collapsedIds = ids.joinToString(",")
  }
  val childCount = remember(links) { links.mapNotNull { it.parentId }.groupingBy { it }.eachCount() }
  val parents = remember(childCount) { childCount.keys }
  val hasBranches = parents.isNotEmpty()
  val counts = remember(links) {
    listOf(LinkTarget.Ayah.KIND, LinkTarget.Hadith.KIND, LinkTarget.Note.KIND, LinkTarget.Idea.KIND)
      .map { kind -> kind to links.count { it.target.kind == kind } }
      .filter { it.second > 0 }
  }
  val shown = remember(tree, collapsedIds, kindFilter) {
    val filter = kindFilter
    if (filter != null) {
      tree.filter { it.first.target.kind == filter }
    } else {
      // skip everything inside a collapsed branch
      val hidden = collapsed
      var hideDeeperThan = Int.MAX_VALUE
      tree.filter { (link, depth) ->
        if (depth > hideDeeperThan) return@filter false
        hideDeeperThan = if (link.id in hidden) depth else Int.MAX_VALUE
        true
      }
    }
  }

  // save as the reader types, once they pause
  LaunchedEffect(idea.id) {
    snapshotFlow { Triple(title, body, tags) }
      .drop(1)
      .distinctUntilChanged()
      .debounce(600)
      .collect { (t, b, g) -> onSave(t, b, parseTags(g)) }
  }

  Scaffold(
    topBar = {
      TopBar(title.ifBlank { stringResource(R.string.research_untitled) }, onBack = {
        onSave(title, body, parseTags(tags))
        onBack()
      }) {
        IconButton(onClick = {
          onSave(title, body, parseTags(tags))
          onShare()
        }) { Icon(Share, contentDescription = stringResource(R.string.research_share)) }
        IconButton(onClick = { confirmDelete = true }) { Icon(Delete, contentDescription = stringResource(R.string.research_delete)) }
      }
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (idea.author.isNotBlank()) item { AuthorLabel(idea.author) }
      item {
        AnimatedContent(editingDetails, label = "details") { editing ->
          if (editing) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true,
                label = { Text(stringResource(R.string.research_idea_title)) }, modifier = Modifier.fillMaxWidth())
              OutlinedTextField(value = body, onValueChange = { body = it }, minLines = 3,
                label = { Text(stringResource(R.string.research_idea_body)) }, modifier = Modifier.fillMaxWidth())
              OutlinedTextField(value = tags, onValueChange = { tags = it }, singleLine = true,
                label = { Text(stringResource(R.string.research_idea_tags)) }, modifier = Modifier.fillMaxWidth())
              TextButton(onClick = { onSave(title, body, parseTags(tags)); editingDetails = false }, modifier = Modifier.align(Alignment.End)) {
                Icon(QuranIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.research_done))
              }
            }
          } else {
            IdeaSummaryCard(title, body, parseTags(tags), onEdit = { editingDetails = true })
          }
        }
      }
      item {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
          Text(
            stringResource(R.string.research_linked).uppercase(Locale.getDefault()) + if (links.isNotEmpty()) "  ·  " + links.size.localized() else "",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
          )
          if (hasBranches && kindFilter == null) {
            TextButton(onClick = { setCollapsed(if (collapsed.isEmpty()) parents else emptySet()) }) {
              Text(stringResource(if (collapsed.isEmpty()) R.string.research_collapse_all else R.string.research_expand_all))
            }
          }
          Box {
            Button(onClick = { addMenu = true }, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
              Icon(Plus, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
              Text(stringResource(R.string.research_add))
            }
            AddMenu(addMenu, canAddIdea = otherIdeas.isNotEmpty(), onDismiss = { addMenu = false }) { kind ->
              addMenu = false
              addingUnder = null
              when (kind) {
                LinkTarget.Ayah.KIND -> addingVerse = true
                LinkTarget.Hadith.KIND -> onPickHadith(null)
                LinkTarget.Idea.KIND -> linkingIdea = true
                else -> addingNote = true
              }
            }
          }
        }
      }
      if (links.isEmpty()) {
        item { Text(stringResource(R.string.research_nothing_linked), color = MaterialTheme.colorScheme.onSurfaceVariant) }
      } else if (counts.size > 1) {
        item {
          androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              FilterChip(selected = kindFilter == null, onClick = { kindFilter = null },
                label = { Text(stringResource(R.string.research_all) + " " + links.size.localized()) }, shape = RoundedCornerShape(50))
            }
            items(counts) { (kind, count) ->
              FilterChip(
                selected = kindFilter == kind,
                onClick = { kindFilter = if (kindFilter == kind) null else kind },
                leadingIcon = { Icon(kindIcon(kind), contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(stringResource(kindLabel(kind)) + " " + count.localized()) },
                shape = RoundedCornerShape(50)
              )
            }
          }
        }
      }
      items(shown, key = { it.first.id }) { (link, depth) ->
        val target = link.target
        val under = childCount[link.id] ?: 0
        LinkCard(
          link, text, hadith, otherIdeas,
          depth = if (kindFilter == null) depth else 0,
          under = if (kindFilter == null) under else 0,
          isCollapsed = link.id in collapsed,
          onToggle = { setCollapsed(if (link.id in collapsed) collapsed - link.id else collapsed + link.id) },
          canAddIdea = otherIdeas.isNotEmpty(),
          onAddUnder = { kind ->
            addingUnder = link.id
            // show what's about to be added under it
            setCollapsed(collapsed - link.id)
            when (kind) {
              LinkTarget.Ayah.KIND -> addingVerse = true
              LinkTarget.Hadith.KIND -> onPickHadith(link.id)
              LinkTarget.Idea.KIND -> linkingIdea = true
              else -> addingNote = true
            }
          },
          note = (target as? LinkTarget.Note)?.let { notes[it.uid] },
          // a note is edited here; everything else opens
          onOpen = { if (target is LinkTarget.Note) editingNote = target.uid else onOpenTarget(target) },
          onComment = { editingLink = link.id },
          onRemove = { onRemoveLink(link) },
          modifier = Modifier.animateItem()
        )
      }
    }
  }

  if (addingVerse) {
    AddVerseDialog(text, onAdd = { target, relation -> addingVerse = false; onAddLink(target, relation, addingUnder) }, onDismiss = { addingVerse = false })
  }
  if (linkingIdea) {
    LinkIdeaDialog(otherIdeas, onAdd = { target, relation -> linkingIdea = false; onAddLink(target, relation, addingUnder) }, onDismiss = { linkingIdea = false })
  }
  if (addingNote) {
    NoteDialog(null, onSave = { t, s, r -> addingNote = false; onAddNote(t, s, r, addingUnder) }, onDismiss = { addingNote = false })
  }
  editingNote?.let { uid ->
    NoteDialog(notes[uid], onSave = { t, s, _ -> editingNote = null; onEditNote(uid, t, s) }, onDismiss = { editingNote = null })
  }
  links.firstOrNull { it.id == editingLink }?.let { link ->
    LinkNoteDialog(
      link,
      onSave = { relation, note -> editingLink = null; onEditLink(link, relation, note) },
      onDismiss = { editingLink = null }
    )
  }
  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      text = { Text(stringResource(R.string.research_delete_confirm)) },
      confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.research_delete), color = MaterialTheme.colorScheme.error) } },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.research_cancel)) } }
    )
  }
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
  LinkTarget.Ayah.KIND -> QuranIcons.MenuBook
  LinkTarget.Hadith.KIND -> Library
  LinkTarget.Idea.KIND -> LightBulb
  else -> Quote
}

private fun kindLabel(kind: String): Int = when (kind) {
  LinkTarget.Ayah.KIND -> R.string.research_map_verses
  LinkTarget.Hadith.KIND -> R.string.research_map_hadith
  LinkTarget.Idea.KIND -> R.string.research_map_ideas
  else -> R.string.research_map_notes
}

/** verse, hadith, note or idea */
@Composable
private fun AddMenu(expanded: Boolean, canAddIdea: Boolean, onDismiss: () -> Unit, onPick: (kind: String) -> Unit) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    listOf(
      LinkTarget.Ayah.KIND to R.string.research_add_verse,
      LinkTarget.Hadith.KIND to R.string.research_add_hadith,
      LinkTarget.Note.KIND to R.string.research_add_note,
      LinkTarget.Idea.KIND to R.string.research_add_idea
    ).forEach { (kind, label) ->
      DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(kindIcon(kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        enabled = kind != LinkTarget.Idea.KIND || canAddIdea,
        onClick = { onPick(kind) }
      )
    }
  }
}

/** The idea's title, description and tags, folded; editing opens the fields. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdeaSummaryCard(title: String, body: String, tags: List<String>, onEdit: () -> Unit) {
  Surface(onClick = onEdit, shape = RoundedCornerShape(16.dp), color = cardColor(), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          title.ifBlank { stringResource(R.string.research_untitled) },
          style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
        )
        Icon(Edit, contentDescription = stringResource(R.string.research_edit), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
      }
      if (body.isNotBlank()) {
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
      }
      if (tags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          tags.forEach { tag ->
            Text(
              "#$tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LinkCard(
  link: IdeaLink,
  text: ResearchText,
  hadith: suspend (LinkTarget.Hadith) -> HadithEntry?,
  ideas: List<Idea>,
  note: NoteSource?,
  depth: Int,
  under: Int,
  isCollapsed: Boolean,
  onToggle: () -> Unit,
  canAddIdea: Boolean,
  onAddUnder: (kind: String) -> Unit,
  onOpen: () -> Unit,
  onComment: () -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier
) {
  val target = link.target
  val title = when (target) {
    is LinkTarget.Ayah -> text.suraName(target.sura) + " " + target.ref.substringAfter(':').let { "${target.sura}:$it" }
    is LinkTarget.Hadith -> text.collectionName(target.collection) + " " + target.number
    is LinkTarget.Idea -> ideas.firstOrNull { it.id == target.id }?.title ?: ""
    is LinkTarget.Note -> note?.source?.takeIf { it.isNotBlank() } ?: stringResource(R.string.research_add_note)
  }
  val preview by produceState<String?>(null, target, note) {
    value = when (target) {
      is LinkTarget.Ayah -> text.verseText(target)
      is LinkTarget.Hadith -> hadith(target)?.displayText
      is LinkTarget.Idea -> ideas.firstOrNull { it.id == target.id }?.body
      is LinkTarget.Note -> note?.text
    }
  }
  val arabic = arabicFont()
  var menu by remember { mutableStateOf(false) }
  var addMenu by remember { mutableStateOf(false) }
  val accent = relationColor(link.relation)
  // links under links step in, with an elbow back to their parent
  Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
    if (depth > 0) {
      Spacer(Modifier.width(16.dp * (depth - 1)))
      Box(Modifier.width(16.dp).fillMaxHeight()) {
        Box(Modifier.padding(start = 6.dp).width(2.dp).fillMaxHeight(0.5f).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
        Box(
          Modifier.padding(start = 6.dp).align(Alignment.CenterStart).width(10.dp).height(2.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        )
      }
    }
    Card(
      onClick = onOpen,
      shape = CardShape,
      colors = CardDefaults.cardColors(containerColor = cardColor(), contentColor = MaterialTheme.colorScheme.onSurface),
      modifier = Modifier.weight(1f)
    ) {
      Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 6.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
              Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
              contentAlignment = Alignment.Center
            ) { Icon(kindIcon(target.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            RelationChip(link.relation)
            Box {
              IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                Icon(MoreVert, contentDescription = stringResource(R.string.research_more), modifier = Modifier.size(20.dp))
              }
              DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.research_comment)) },
                  leadingIcon = { Icon(QuranIcons.Chat, contentDescription = null) },
                  onClick = { menu = false; onComment() }
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.research_add_under)) },
                  leadingIcon = { Icon(Plus, contentDescription = null) },
                  onClick = { menu = false; addMenu = true }
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.research_remove_link), color = MaterialTheme.colorScheme.error) },
                  leadingIcon = { Icon(QuranIcons.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                  onClick = { menu = false; onRemove() }
                )
              }
              AddMenu(addMenu, canAddIdea, onDismiss = { addMenu = false }) { kind -> addMenu = false; onAddUnder(kind) }
            }
          }
          val p = preview
          if (!p.isNullOrBlank()) {
            Text(
              p,
              maxLines = if (target is LinkTarget.Note) 4 else 2,
              overflow = TextOverflow.Ellipsis,
              style = if (target is LinkTarget.Idea) MaterialTheme.typography.bodyMedium
              else if (target is LinkTarget.Note) MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
              else TextStyle(fontFamily = arabic, fontSize = 17.sp, lineHeight = 28.sp, textDirection = TextDirection.Rtl),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(end = 12.dp)
            )
          }
          if (link.note.isNotBlank()) {
            // the reader's own comment on why this is here
            Row(
              Modifier
                .padding(end = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.08f))
                .clickable(onClick = onComment)
                .padding(8.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(QuranIcons.Chat, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp).padding(top = 2.dp))
              Text(link.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
          }
          if (under > 0) {
            Row(
              Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onToggle)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isCollapsed) 0.14f else 0.07f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                QuranIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp).rotate(if (isCollapsed) -90f else 0f)
              )
              Spacer(Modifier.width(4.dp))
              Text(
                stringResource(if (isCollapsed) R.string.research_show_under else R.string.research_hide_under, under),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }
      }
    }
  }
}

/** A note source: what someone said or found, and where it's from. [note] null to add one. */
@Composable
private fun NoteDialog(note: NoteSource?, onSave: (text: String, source: String, Relation) -> Unit, onDismiss: () -> Unit) {
  var text by rememberSaveable { mutableStateOf(note?.text.orEmpty()) }
  var source by rememberSaveable { mutableStateOf(note?.source.orEmpty()) }
  var relation by rememberSaveable { mutableStateOf(Relation.SUPPORTS) }
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(Quote, contentDescription = null) },
    title = { Text(stringResource(R.string.research_add_note)) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = text, onValueChange = { text = it }, minLines = 4,
          label = { Text(stringResource(R.string.research_note_text)) },
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = source, onValueChange = { source = it }, singleLine = true,
          label = { Text(stringResource(R.string.research_note_source)) },
          placeholder = { Text(stringResource(R.string.research_note_source_hint)) },
          modifier = Modifier.fillMaxWidth()
        )
        if (note == null) {
          Text(stringResource(R.string.research_relation_question), style = MaterialTheme.typography.labelLarge)
          RelationPicker(relation) { relation = it }
        }
      }
    },
    confirmButton = {
      TextButton(enabled = text.isNotBlank(), onClick = { onSave(text, source, relation) }) {
        Text(stringResource(if (note == null) R.string.research_add else R.string.research_save))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.research_cancel)) } }
  )
}

/** The reader's comment on a link, and how it relates. */
@Composable
private fun LinkNoteDialog(link: IdeaLink, onSave: (Relation, String) -> Unit, onDismiss: () -> Unit) {
  var note by rememberSaveable(link.id) { mutableStateOf(link.note) }
  var relation by rememberSaveable(link.id) { mutableStateOf(link.relation) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.research_comment)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = note, onValueChange = { note = it }, minLines = 3,
          placeholder = { Text(stringResource(R.string.research_comment_hint)) },
          modifier = Modifier.fillMaxWidth()
        )
        Text(stringResource(R.string.research_relation_question), style = MaterialTheme.typography.labelLarge)
        RelationPicker(relation) { relation = it }
      }
    },
    confirmButton = { TextButton(onClick = { onSave(relation, note) }) { Text(stringResource(R.string.research_save)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.research_cancel)) } }
  )
}

@Composable
private fun AddVerseDialog(text: ResearchText, onAdd: (LinkTarget.Ayah, Relation) -> Unit, onDismiss: () -> Unit) {
  var sura by rememberSaveable { mutableStateOf("") }
  var from by rememberSaveable { mutableStateOf("") }
  var to by rememberSaveable { mutableStateOf("") }
  var relation by rememberSaveable { mutableStateOf(Relation.RELATED) }
  val s = sura.toIntOrNull()
  val a = from.toIntOrNull()
  val b = to.toIntOrNull() ?: a
  val valid = s != null && s in 1..114 && a != null && b != null && a in 1..text.verseCount(s) && b in a..text.verseCount(s)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.research_add_verse)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)
        OutlinedTextField(sura, { sura = it.filter(Char::isDigit).take(3) }, singleLine = true, keyboardOptions = numbers,
          label = { Text(stringResource(R.string.research_sura)) },
          supportingText = { if (s != null && s in 1..114) Text(text.suraName(s)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(from, { from = it.filter(Char::isDigit).take(3) }, singleLine = true, keyboardOptions = numbers,
            label = { Text(stringResource(R.string.research_from_ayah)) }, modifier = Modifier.weight(1f))
          OutlinedTextField(to, { to = it.filter(Char::isDigit).take(3) }, singleLine = true, keyboardOptions = numbers,
            label = { Text(stringResource(R.string.research_to_ayah)) }, modifier = Modifier.weight(1f))
        }
        if (sura.isNotEmpty() && from.isNotEmpty() && !valid) {
          Text(stringResource(R.string.research_invalid_verse), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.research_relation_question), style = MaterialTheme.typography.labelLarge)
        RelationPicker(relation) { relation = it }
      }
    },
    confirmButton = { TextButton(enabled = valid, onClick = { onAdd(LinkTarget.Ayah(s!!, a!!, b!!), relation) }) { Text(stringResource(R.string.research_add)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.research_cancel)) } }
  )
}

@Composable
private fun LinkIdeaDialog(ideas: List<Idea>, onAdd: (LinkTarget.Idea, Relation) -> Unit, onDismiss: () -> Unit) {
  var chosen by rememberSaveable { mutableStateOf<Long?>(null) }
  var relation by rememberSaveable { mutableStateOf(Relation.RELATED) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.research_add_idea)) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ideas.forEach { idea ->
          Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { chosen = idea.id }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = chosen == idea.id, onClick = { chosen = idea.id })
            Text(idea.title.ifBlank { stringResource(R.string.research_untitled) })
          }
        }
        Text(stringResource(R.string.research_relation_question), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        RelationPicker(relation) { relation = it }
      }
    },
    confirmButton = { TextButton(enabled = chosen != null, onClick = { onAdd(LinkTarget.Idea(chosen!!), relation) }) { Text(stringResource(R.string.research_add)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.research_cancel)) } }
  )
}

/** Choose an idea (or start one) for a verse or hadith, and how it relates. */
@Composable
fun PickIdeaScreen(
  target: LinkTarget,
  ideas: List<IdeaSummary>,
  text: ResearchText,
  hadith: suspend (LinkTarget.Hadith) -> HadithEntry?,
  onPick: (ideaId: Long?, newTitle: String?, relation: Relation) -> Unit,
  onBack: () -> Unit
) {
  var chosen by rememberSaveable { mutableStateOf<Long?>(ideas.firstOrNull()?.idea?.id) }
  var newTitle by rememberSaveable { mutableStateOf("") }
  var relation by rememberSaveable { mutableStateOf(Relation.RELATED) }
  val preview by produceState<String?>(null, target) {
    value = when (target) {
      is LinkTarget.Ayah -> text.verseText(target)
      is LinkTarget.Hadith -> hadith(target)?.displayText
      is LinkTarget.Idea, is LinkTarget.Note -> null
    }
  }
  val arabic = arabicFont()
  Scaffold(topBar = { TopBar(stringResource(R.string.research_add_to_idea), onBack) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      item {
        Surface(shape = CardShape, color = cardColor(), modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              when (target) {
                is LinkTarget.Ayah -> text.suraName(target.sura) + " " + target.ref
                is LinkTarget.Hadith -> text.collectionName(target.collection) + " " + target.number
                is LinkTarget.Idea, is LinkTarget.Note -> ""
              },
              style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary
            )
            preview?.let {
              Text(it, maxLines = 4, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = arabic, fontSize = 19.sp, lineHeight = 32.sp, textDirection = TextDirection.Rtl))
            }
          }
        }
      }
      item {
        Text(stringResource(R.string.research_relation_question), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        RelationPicker(relation) { relation = it }
      }
      item { Text(stringResource(R.string.research_choose_idea), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp)) }
      items(ideas, key = { it.idea.id }) { s ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { chosen = s.idea.id }, verticalAlignment = Alignment.CenterVertically) {
          RadioButton(selected = chosen == s.idea.id, onClick = { chosen = s.idea.id })
          Text(s.idea.title.ifBlank { stringResource(R.string.research_untitled) })
        }
      }
      item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          RadioButton(selected = chosen == null, onClick = { chosen = null })
          OutlinedTextField(newTitle, { newTitle = it; chosen = null }, singleLine = true,
            placeholder = { Text(stringResource(R.string.research_new_idea)) }, modifier = Modifier.weight(1f))
        }
      }
      item {
        Button(
          enabled = chosen != null || newTitle.isNotBlank(),
          onClick = { onPick(chosen, newTitle.takeIf { chosen == null }, relation) },
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text(stringResource(R.string.research_add)) }
      }
    }
  }
}

/** Download, search and read the six books. */
@OptIn(ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun HadithLibraryScreen(
  state: HadithLibraryState,
  collections: List<HadithCollection>,
  search: suspend (String, String?) -> List<HadithEntry>,
  text: ResearchText,
  onDownload: () -> Unit,
  onOpen: (HadithEntry) -> Unit,
  onBack: () -> Unit
) {
  var query by rememberSaveable { mutableStateOf("") }
  var collection by rememberSaveable { mutableStateOf<String?>(null) }
  var results by remember { mutableStateOf<List<HadithEntry>?>(null) }
  LaunchedEffect(state, collection) {
    snapshotFlow { query }.debounce(300).collect { q -> results = if (q.isBlank()) null else search(q, collection) }
  }
  val arabic = arabicFont()
  Scaffold(topBar = { TopBar(stringResource(R.string.research_hadith_library), onBack) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
    when (state) {
      is HadithLibraryState.Installed -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
          OutlinedTextField(query, { query = it }, singleLine = true,
            leadingIcon = { Icon(QuranIcons.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.research_search_hadith)) },
            shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
        }
        item {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = collection == null, onClick = { collection = null }, label = { Text(stringResource(R.string.research_all)) }, shape = RoundedCornerShape(8.dp))
            collections.forEach { c ->
              FilterChip(selected = collection == c.slug, onClick = { collection = c.slug },
                label = { Text(if (isArabicUi()) c.nameAr else c.nameEn) }, shape = RoundedCornerShape(8.dp))
            }
          }
        }
        val r = results
        if (r != null && r.isEmpty()) {
          item { Text(stringResource(R.string.research_no_hadith), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        // numbers can repeat within a book in sunnah.com's numbering, so key by position too
        itemsIndexed(r.orEmpty(), key = { i, h -> "${h.collection}:${h.number}:$i" }) { _, h ->
          Card(onClick = { onOpen(h) }, shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = cardColor(), contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text.collectionName(h.collection) + " " + h.number, style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                GradeChip(h.grade)
              }
              Text(h.displayText, maxLines = 4, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = arabic, fontSize = 18.sp, lineHeight = 30.sp, textDirection = TextDirection.Rtl))
            }
          }
        }
      }
      else -> Column(
        Modifier.padding(padding).fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(Library, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.research_library_intro, 13), textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        when (state) {
          is HadithLibraryState.Downloading -> {
            LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.research_downloading, (state.fraction * 100).toInt()))
          }
          is HadithLibraryState.Failed -> {
            Text(stringResource(R.string.research_download_failed, state.message), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDownload, shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.research_retry)) }
          }
          else -> Button(onClick = onDownload, shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.research_download)) }
        }
      }
    }
  }
}

@Composable
internal fun GradeChip(grade: String) {
  if (grade.isBlank()) return
  val g = grade.lowercase()
  val color = when {
    "da'if" in g || "daif" in g || "da`if" in g || "mawdu" in g || "munkar" in g -> Color(0xFFC62828)
    "hasan" in g || "sahih" in g -> Color(0xFF2E7D32)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
    Text(grade.substringBefore(';').substringBefore('('), color = color, style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
  }
}

/**
 * One hadith in full, with its grading. [askRelation]: adding straight into an idea, so ask
 * how it relates here (otherwise the idea picker asks).
 */
@Composable
fun HadithScreen(
  entry: HadithEntry,
  text: ResearchText,
  askRelation: Boolean,
  onAddToIdea: (Relation) -> Unit,
  onBack: () -> Unit
) {
  val arabic = arabicFont()
  var relation by rememberSaveable { mutableStateOf(Relation.RELATED) }
  Scaffold(topBar = { TopBar(text.collectionName(entry.collection) + " " + entry.number, onBack) }, containerColor = MaterialTheme.colorScheme.surface) { padding ->
    Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (entry.chapter.isNotBlank()) {
        Text(entry.chapter, style = TextStyle(fontFamily = arabic, fontSize = 17.sp, textDirection = TextDirection.Rtl),
          color = MaterialTheme.colorScheme.primary)
      }
      Text(entry.text, style = TextStyle(fontFamily = arabic, fontSize = 22.sp, lineHeight = 40.sp, textDirection = TextDirection.Rtl))
      Surface(shape = RoundedCornerShape(8.dp), color = cardColor(), modifier = Modifier.fillMaxWidth()) {
        Text(entry.grade.ifBlank { stringResource(R.string.research_no_grade) }, modifier = Modifier.padding(12.dp),
          style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
      }
      if (askRelation) {
        Text(stringResource(R.string.research_relation_question), style = MaterialTheme.typography.labelLarge)
        RelationPicker(relation) { relation = it }
      }
      Button(onClick = { onAddToIdea(relation) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Icon(LightBulb, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.research_add_to_idea))
      }
    }
  }
}
