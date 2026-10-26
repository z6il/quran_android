package com.quran.labs.androidquran.feature.research

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private val MapBackground = Color(0xFF061012)
private val CardBackground = Color(0xFF0E1F22)
private val IdeaColor = Color(0xFF26C6DA)
private val AyahColor = Color(0xFFFFC857)
private val HadithColor = Color(0xFFB388FF)

private fun kindColor(target: LinkTarget) = when (target) {
  is LinkTarget.Idea -> IdeaColor
  is LinkTarget.Ayah -> AyahColor
  is LinkTarget.Hadith -> HadithColor
}

/** brighter relation colours for the dark map, matching the page's */
private fun mapRelationColor(relation: Relation) = when (relation) {
  Relation.EXPLAINS -> Color(0xFF4DD0E1)
  Relation.SUPPORTS -> Color(0xFF69F0AE)
  Relation.RELATED -> Color(0xFF8C9EFF)
  Relation.CONTRASTS -> Color(0xFFFF5252)
}

internal data class MapNode(val id: String, val target: LinkTarget, val label: String)
internal data class MapEdge(val source: String, val target: String, val relation: Relation)

internal data class MapGraph(val nodes: List<MapNode>, val edges: List<MapEdge>) {
  val byId = nodes.associateBy { it.id }

  /** the nodes linked to [id], with how they relate */
  fun neighbours(id: String): List<Pair<MapNode, Relation>> = edges.mapNotNull { e ->
    when (id) {
      e.source -> byId[e.target]?.let { it to e.relation }
      e.target -> byId[e.source]?.let { it to e.relation }
      else -> null
    }
  }

  fun toJson(): JSONObject = JSONObject()
    .put("nodes", JSONArray(nodes.map { JSONObject().put("id", it.id).put("kind", it.target.kind).put("label", it.label) }))
    .put("links", JSONArray(edges.map { JSONObject().put("source", it.source).put("target", it.target).put("relation", it.relation.key) }))
}

private fun nodeId(t: LinkTarget) = t.kind + ":" + t.ref

/** Builds the graph the map draws: every idea, and every verse and hadith an idea links to. */
internal suspend fun buildGraph(store: ResearchStore, text: ResearchText, untitled: String): MapGraph {
  store.refresh()
  val nodes = linkedMapOf<String, MapNode>()
  store.ideas.value.forEach { summary ->
    val target = LinkTarget.Idea(summary.idea.id)
    val title = summary.idea.title.ifBlank { untitled }.let { if (it.length > 30) it.take(29) + "…" else it }
    nodes[nodeId(target)] = MapNode(nodeId(target), target, title)
  }
  val links = store.allLinks()
  links.forEach { link ->
    val t = link.target
    if (nodeId(t) !in nodes) {
      val label = when (t) {
        is LinkTarget.Ayah -> text.suraName(t.sura) + " " + t.ref
        is LinkTarget.Hadith -> text.collectionName(t.collection) + " " + t.number
        is LinkTarget.Idea -> return@forEach // a link to a deleted idea
      }
      nodes[nodeId(t)] = MapNode(nodeId(t), t, label)
    }
  }
  val edges = links.mapNotNull { link ->
    val source = nodeId(LinkTarget.Idea(link.ideaId))
    val target = nodeId(link.target)
    if (source in nodes && target in nodes) MapEdge(source, target, link.relation) else null
  }
  return MapGraph(nodes.values.toList(), edges)
}

/** The 3D map of ideas and what they link to: tap a node to fly to it and see what it is. */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ResearchMap(
  store: ResearchStore,
  text: ResearchText,
  hadith: suspend (LinkTarget.Hadith) -> HadithEntry?,
  onOpen: (LinkTarget) -> Unit,
  onBack: () -> Unit
) {
  val untitled = stringResource(R.string.research_untitled)
  val ideas by store.ideas.collectAsState()
  val graph by produceState<MapGraph?>(null, ideas) { value = buildGraph(store, text, untitled) }
  var pageReady by remember { mutableStateOf(false) }
  var webView by remember { mutableStateOf<WebView?>(null) }
  var selectedId by remember { mutableStateOf<String?>(null) }
  var showVerses by remember { mutableStateOf(true) }
  var showHadith by remember { mutableStateOf(true) }
  var hint by remember { mutableStateOf(true) }
  val main = remember { Handler(Looper.getMainLooper()) }

  fun js(code: String) = webView?.evaluateJavascript(code, null)
  fun select(id: String) {
    selectedId = id
    js("focusNode(${JSONObject.quote(id)})")
  }

  // hand the graph over once both the page and the data are ready
  LaunchedEffect(graph, pageReady, webView) {
    val g = graph ?: return@LaunchedEffect
    if (pageReady) {
      js("setGraph(${JSONObject.quote(g.toJson().toString())})")
      js("setFilter('{\"ayah\":$showVerses,\"hadith\":$showHadith}')")
      selectedId?.let { if (it in g.byId) select(it) else selectedId = null }
    }
  }
  LaunchedEffect(showVerses, showHadith, pageReady) {
    if (pageReady) js("setFilter('{\"ayah\":$showVerses,\"hadith\":$showHadith}')")
  }
  LaunchedEffect(Unit) {
    delay(6000)
    hint = false
  }
  DisposableEffect(Unit) { onDispose { webView?.destroy() } }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.research_map), fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(QuranIcons.ArrowBack, contentDescription = stringResource(R.string.research_back)) } },
        actions = {
          IconButton(onClick = {
            selectedId = null
            js("resetView()")
          }) { Icon(Recenter, contentDescription = stringResource(R.string.research_map_reset)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MapBackground,
          titleContentColor = Color.White,
          navigationIconContentColor = Color.White,
          actionIconContentColor = Color.White
        )
      )
    },
    containerColor = MapBackground
  ) { padding ->
    Box(Modifier.padding(padding).fillMaxSize().background(MapBackground)) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
          WebView(context).apply {
            setBackgroundColor(android.graphics.Color.rgb(6, 16, 18))
            settings.javaScriptEnabled = true
            webChromeClient = object : WebChromeClient() {
              override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d("ResearchMap", "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
              }
            }
            addJavascriptInterface(object {
              @JavascriptInterface
              fun ready() = main.post { pageReady = true }

              @JavascriptInterface
              fun select(id: String) = main.post {
                selectedId = id
                hint = false
              }

              @JavascriptInterface
              fun deselect() = main.post { selectedId = null }
            }, "Android")
            loadUrl("file:///android_asset/research_map/index.html")
            webView = this
          }
        }
      )

      val g = graph
      // what's on the map, and switches to hide verses or hadith
      if (g != null) {
        Row(
          Modifier.align(Alignment.TopCenter).padding(top = 8.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          KindChip(IdeaColor, stringResource(R.string.research_map_ideas), g.nodes.count { it.target is LinkTarget.Idea }, on = true, onClick = null)
          KindChip(AyahColor, stringResource(R.string.research_map_verses), g.nodes.count { it.target is LinkTarget.Ayah }, showVerses) {
            showVerses = !showVerses
          }
          KindChip(HadithColor, stringResource(R.string.research_map_hadith), g.nodes.count { it.target is LinkTarget.Hadith }, showHadith) {
            showHadith = !showHadith
          }
        }
        if (g.edges.isEmpty()) {
          Text(
            stringResource(R.string.research_map_empty),
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center).padding(32.dp)
          )
        }
      }

      Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
        AnimatedVisibility(hint && selectedId == null && g != null && g.edges.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
          Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
              stringResource(R.string.research_map_hint),
              color = Color.White,
              style = MaterialTheme.typography.labelLarge,
              modifier = Modifier
                .padding(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
            )
          }
        }
        val node = selectedId?.let { g?.byId?.get(it) }
        AnimatedContent(
          targetState = node,
          transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { it / 2 } + fadeOut())
          },
          label = "selected"
        ) { shown ->
          if (shown != null && g != null) {
            NodeCard(
              node = shown,
              neighbours = g.neighbours(shown.id),
              store = store,
              text = text,
              hadith = hadith,
              onNeighbour = { select(it.id) },
              onOpen = { onOpen(shown.target) },
              onClose = {
                selectedId = null
                js("clearFocus()")
              }
            )
          } else if (g != null && g.edges.isNotEmpty()) {
            RelationLegend()
          }
        }
      }
    }
  }
}

@Composable
private fun KindChip(color: Color, label: String, count: Int, on: Boolean, onClick: (() -> Unit)?) {
  Row(
    Modifier
      .clip(RoundedCornerShape(50))
      .background(if (on) color.copy(alpha = 0.22f) else Color.Transparent)
      .border(BorderStroke(1.5.dp, if (on) color else Color.White.copy(alpha = 0.25f)), RoundedCornerShape(50))
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) color else Color.White.copy(alpha = 0.3f)))
    Text(label, color = if (on) Color.White else Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelLarge)
    Text(
      count.toString(),
      color = if (on) color else Color.White.copy(alpha = 0.4f),
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelationLegend() {
  FlowRow(
    Modifier.fillMaxWidth().padding(12.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(Color.Black.copy(alpha = 0.45f))
      .padding(horizontal = 14.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Relation.entries.forEach { relation ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(18.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(mapRelationColor(relation)))
        Text(relationLabel(relation), color = Color.White, style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

/** What the tapped node is: its text, what it's connected to, and a way to open it. */
@Composable
private fun NodeCard(
  node: MapNode,
  neighbours: List<Pair<MapNode, Relation>>,
  store: ResearchStore,
  text: ResearchText,
  hadith: suspend (LinkTarget.Hadith) -> HadithEntry?,
  onNeighbour: (MapNode) -> Unit,
  onOpen: () -> Unit,
  onClose: () -> Unit
) {
  val color = kindColor(node.target)
  // the verse, the hadith and its grade, or the idea's notes
  val body by produceState<Pair<String, String>?>(null, node.id) {
    value = when (val t = node.target) {
      is LinkTarget.Ayah -> text.verseText(t) to ""
      is LinkTarget.Hadith -> hadith(t)?.let { it.displayText to it.grade } ?: ("" to "")
      is LinkTarget.Idea -> (store.idea(t.id)?.body ?: "") to ""
    }
  }
  Surface(
    modifier = Modifier.fillMaxWidth().padding(12.dp),
    shape = RoundedCornerShape(20.dp),
    color = CardBackground,
    border = BorderStroke(1.5.dp, color.copy(alpha = 0.6f)),
    shadowElevation = 12.dp
  ) {
    Column(Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
          stringResource(
            when (node.target) {
              is LinkTarget.Idea -> R.string.research_add_idea
              is LinkTarget.Ayah -> R.string.research_add_verse
              is LinkTarget.Hadith -> R.string.research_add_hadith
            }
          ).uppercase(),
          color = color,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.5.sp,
          modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
          Icon(QuranIcons.Close, contentDescription = stringResource(R.string.research_close), tint = Color.White.copy(alpha = 0.7f))
        }
      }
      Text(
        node.label,
        color = Color.White,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      val (main, grade) = body ?: ("" to "")
      if (main.isNotBlank()) {
        val arabic = node.target !is LinkTarget.Idea
        Text(
          main,
          color = Color.White.copy(alpha = 0.9f),
          fontFamily = if (arabic) arabicFont() else null,
          fontSize = if (arabic) 20.sp else 15.sp,
          lineHeight = if (arabic) 34.sp else 22.sp,
          style = MaterialTheme.typography.bodyLarge.copy(textDirection = if (arabic) TextDirection.Rtl else TextDirection.Content),
          modifier = Modifier.padding(top = 10.dp).fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState())
        )
      }
      if (grade.isNotBlank()) {
        Text(grade, color = HadithColor, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
      }
      if (neighbours.isNotEmpty()) {
        Text(
          stringResource(R.string.research_map_connections),
          color = Color.White.copy(alpha = 0.6f),
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          neighbours.forEach { (other, relation) ->
            Row(
              Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .clickable { onNeighbour(other) }
                .padding(end = 12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(Modifier.width(4.dp).height(40.dp).background(mapRelationColor(relation)))
              Spacer(Modifier.width(10.dp))
              Column(Modifier.padding(vertical = 4.dp)) {
                Text(other.label, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(relationLabel(relation), color = mapRelationColor(relation), style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      }
      Button(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = MapBackground)
      ) {
        Text(
          stringResource(if (node.target is LinkTarget.Ayah) R.string.research_open_in_quran else R.string.research_open),
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}
