package com.quran.labs.androidquran.feature.research

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import org.json.JSONArray
import org.json.JSONObject

private val MapBackground = Color(0xFF061012)
private val IdeaColor = Color(0xFF26C6DA)
private val AyahColor = Color(0xFFFFC857)
private val HadithColor = Color(0xFFB388FF)

/** Builds the graph the map draws: every idea, and every verse and hadith an idea links to. */
internal suspend fun buildGraph(store: ResearchStore, text: ResearchText, untitled: String): JSONObject {
  store.refresh()
  val ideas = store.ideas.value.map { it.idea }
  val links = store.allLinks()
  val nodes = linkedMapOf<String, JSONObject>()
  fun nodeId(t: LinkTarget) = t.kind + ":" + t.ref
  ideas.forEach { idea ->
    val target = LinkTarget.Idea(idea.id)
    nodes[nodeId(target)] = JSONObject()
      .put("id", nodeId(target)).put("kind", target.kind).put("ref", target.ref)
      .put("label", idea.title.ifBlank { untitled }.let { if (it.length > 30) it.take(29) + "…" else it })
  }
  links.forEach { link ->
    val t = link.target
    val id = nodeId(t)
    if (id !in nodes) {
      val label = when (t) {
        is LinkTarget.Ayah -> text.suraName(t.sura) + " " + t.ref
        is LinkTarget.Hadith -> text.collectionName(t.collection) + " " + t.number
        is LinkTarget.Idea -> return@forEach // a link to a deleted idea
      }
      nodes[id] = JSONObject().put("id", id).put("kind", t.kind).put("ref", t.ref).put("label", label)
    }
  }
  val edges = JSONArray()
  links.forEach { link ->
    val source = nodeId(LinkTarget.Idea(link.ideaId))
    val target = nodeId(link.target)
    if (source in nodes && target in nodes) {
      edges.put(JSONObject().put("source", source).put("target", target).put("color", relationColor(link.relation).toArgb() and 0xFFFFFF))
    }
  }
  return JSONObject().put("nodes", JSONArray(nodes.values)).put("links", edges)
}

/** The 3D map of ideas and what they link to; tap a node to open it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ResearchMap(
  store: ResearchStore,
  text: ResearchText,
  onOpen: (LinkTarget) -> Unit,
  onBack: () -> Unit
) {
  val untitled = stringResource(R.string.research_untitled)
  val graph by produceState<JSONObject?>(null) { value = buildGraph(store, text, untitled) }
  var pageReady by remember { mutableStateOf(false) }
  var webView by remember { mutableStateOf<WebView?>(null) }
  val main = remember { Handler(Looper.getMainLooper()) }

  // hand the graph over once both the page and the data are ready
  LaunchedEffect(graph, pageReady, webView) {
    val g = graph ?: return@LaunchedEffect
    if (pageReady) webView?.evaluateJavascript("setGraph(${JSONObject.quote(g.toString())})", null)
  }
  DisposableEffect(Unit) { onDispose { webView?.destroy() } }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.research_map)) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(QuranIcons.ArrowBack, contentDescription = stringResource(R.string.research_back)) } },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MapBackground, titleContentColor = Color.White, navigationIconContentColor = Color.White
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
            setBackgroundColor(MapBackground.toArgb())
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
              fun open(kind: String, ref: String) {
                LinkTarget.of(kind, ref)?.let { target -> main.post { onOpen(target) } }
              }
            }, "Android")
            loadUrl("file:///android_asset/research_map/index.html")
            webView = this
          }
        }
      )
      val g = graph
      if (g != null && g.getJSONArray("links").length() == 0) {
        Text(
          stringResource(R.string.research_map_empty),
          color = Color.White.copy(alpha = 0.8f),
          textAlign = TextAlign.Center,
          modifier = Modifier.align(Alignment.Center).padding(32.dp)
        )
      }
      // what the colours mean
      Column(
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(12.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color.Black.copy(alpha = 0.45f))
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          LegendDot(IdeaColor, stringResource(R.string.research_add_idea))
          LegendDot(AyahColor, stringResource(R.string.research_add_verse))
          LegendDot(HadithColor, stringResource(R.string.research_add_hadith))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          Relation.entries.forEach { LegendDot(relationColor(it), relationLabel(it), line = true) }
        }
      }
    }
  }
}

@Composable
private fun LegendDot(color: Color, label: String, line: Boolean = false) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Box(
      Modifier
        .size(if (line) 14.dp else 10.dp, if (line) 3.dp else 10.dp)
        .clip(if (line) RoundedCornerShape(2.dp) else CircleShape)
        .background(color)
    )
    Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
  }
}
