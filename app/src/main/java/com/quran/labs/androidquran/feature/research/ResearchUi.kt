package com.quran.labs.androidquran.feature.research

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
internal val Graph by lazy {
  icon("Graph", "M17,15c-1.1,0 -2.08,0.5 -2.75,1.27L9.9,13.92c0.06,-0.29 0.1,-0.6 0.1,-0.92s-0.04,-0.63 -0.1,-0.92l4.35,-2.35" +
    "C14.92,10.5 15.9,11 17,11c1.93,0 3.5,-1.57 3.5,-3.5S18.93,4 17,4s-3.5,1.57 -3.5,3.5c0,0.32 0.04,0.63 0.1,0.92L9.25,10.77" +
    "C8.58,10 7.6,9.5 6.5,9.5 4.57,9.5 3,11.07 3,13s1.57,3.5 3.5,3.5c1.1,0 2.08,-0.5 2.75,-1.27l4.35,2.35c-0.06,0.29 -0.1,0.6 " +
    "-0.1,0.92 0,1.93 1.57,3.5 3.5,3.5s3.5,-1.57 3.5,-3.5 -1.57,-3.5 -3.5,-3.5z")
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
  onBack: () -> Unit
) {
  var query by rememberSaveable { mutableStateOf("") }
  var creating by rememberSaveable { mutableStateOf(false) }
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
            if (s.idea.body.isNotBlank()) {
              Text(s.idea.body, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
              CountBadge(QuranIcons.MenuBook, s.ayat)
              CountBadge(Library, s.hadith)
              CountBadge(LightBulb, s.ideas)
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
  otherIdeas: List<Idea>,
  text: ResearchText,
  hadith: suspend (LinkTarget.Hadith) -> HadithEntry?,
  onSave: (title: String, body: String, tags: List<String>) -> Unit,
  onAddLink: (LinkTarget, Relation) -> Unit,
  onRemoveLink: (IdeaLink) -> Unit,
  onPickHadith: () -> Unit,
  onOpenTarget: (LinkTarget) -> Unit,
  onDelete: () -> Unit,
  onBack: () -> Unit
) {
  var title by rememberSaveable(idea.id) { mutableStateOf(idea.title) }
  var body by rememberSaveable(idea.id) { mutableStateOf(idea.body) }
  var tags by rememberSaveable(idea.id) { mutableStateOf(idea.tags.joinToString(", ")) }
  var addingVerse by rememberSaveable { mutableStateOf(false) }
  var linkingIdea by rememberSaveable { mutableStateOf(false) }
  var confirmDelete by rememberSaveable { mutableStateOf(false) }

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
        IconButton(onClick = { confirmDelete = true }) { Icon(Delete, contentDescription = stringResource(R.string.research_delete)) }
      }
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      item {
        OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true,
          label = { Text(stringResource(R.string.research_idea_title)) }, modifier = Modifier.fillMaxWidth())
      }
      item {
        OutlinedTextField(value = body, onValueChange = { body = it }, minLines = 3,
          label = { Text(stringResource(R.string.research_idea_body)) }, modifier = Modifier.fillMaxWidth())
      }
      item {
        OutlinedTextField(value = tags, onValueChange = { tags = it }, singleLine = true,
          label = { Text(stringResource(R.string.research_idea_tags)) }, modifier = Modifier.fillMaxWidth())
      }
      item {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
          Text(stringResource(R.string.research_linked).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
          OutlinedButton(onClick = { addingVerse = true }, shape = RoundedCornerShape(8.dp)) {
            Icon(QuranIcons.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.research_add_verse))
          }
          OutlinedButton(onClick = onPickHadith, shape = RoundedCornerShape(8.dp)) {
            Icon(Library, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.research_add_hadith))
          }
          OutlinedButton(onClick = { linkingIdea = true }, enabled = otherIdeas.isNotEmpty(), shape = RoundedCornerShape(8.dp)) {
            Icon(LightBulb, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.research_add_idea))
          }
        }
      }
      if (links.isEmpty()) {
        item { Text(stringResource(R.string.research_nothing_linked), color = MaterialTheme.colorScheme.onSurfaceVariant) }
      }
      items(links, key = { it.id }) { link ->
        LinkCard(link, text, hadith, otherIdeas, onOpen = { onOpenTarget(link.target) }, onRemove = { onRemoveLink(link) })
      }
    }
  }

  if (addingVerse) AddVerseDialog(text, onAdd = { target, relation -> addingVerse = false; onAddLink(target, relation) }, onDismiss = { addingVerse = false })
  if (linkingIdea) LinkIdeaDialog(otherIdeas, onAdd = { target, relation -> linkingIdea = false; onAddLink(target, relation) }, onDismiss = { linkingIdea = false })
  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      text = { Text(stringResource(R.string.research_delete_confirm)) },
      confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.research_delete), color = MaterialTheme.colorScheme.error) } },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.research_cancel)) } }
    )
  }
}

@Composable
private fun LinkCard(
  link: IdeaLink,
  text: ResearchText,
  hadith: suspend (LinkTarget.Hadith) -> HadithEntry?,
  ideas: List<Idea>,
  onOpen: () -> Unit,
  onRemove: () -> Unit
) {
  val target = link.target
  val title = when (target) {
    is LinkTarget.Ayah -> text.suraName(target.sura) + " " + target.ref.substringAfter(':').let { "${target.sura}:$it" }
    is LinkTarget.Hadith -> text.collectionName(target.collection) + " " + target.number
    is LinkTarget.Idea -> ideas.firstOrNull { it.id == target.id }?.title ?: ""
  }
  val preview by produceState<String?>(null, target) {
    value = when (target) {
      is LinkTarget.Ayah -> text.verseText(target)
      is LinkTarget.Hadith -> hadith(target)?.displayText
      is LinkTarget.Idea -> ideas.firstOrNull { it.id == target.id }?.body
    }
  }
  val arabic = arabicFont()
  Card(
    onClick = onOpen,
    shape = CardShape,
    colors = CardDefaults.cardColors(containerColor = cardColor(), contentColor = MaterialTheme.colorScheme.onSurface),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(Modifier.padding(start = 0.dp)) {
      Box(Modifier.width(4.dp).height(96.dp).background(relationColor(link.relation)))
      Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(
            when (target) { is LinkTarget.Ayah -> QuranIcons.MenuBook; is LinkTarget.Hadith -> Library; is LinkTarget.Idea -> LightBulb },
            contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)
          )
          Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
          RelationChip(link.relation)
          IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(QuranIcons.Close, contentDescription = stringResource(R.string.research_remove_link), modifier = Modifier.size(18.dp))
          }
        }
        val p = preview
        if (!p.isNullOrBlank()) {
          Text(
            p,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = if (target is LinkTarget.Idea) MaterialTheme.typography.bodyMedium
            else TextStyle(fontFamily = arabic, fontSize = 18.sp, lineHeight = 30.sp, textDirection = TextDirection.Rtl),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else if (target is LinkTarget.Hadith && preview == null) {
          Spacer(Modifier.height(4.dp))
        }
      }
    }
  }
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
      is LinkTarget.Idea -> null
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
                is LinkTarget.Idea -> ""
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
private fun GradeChip(grade: String) {
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
