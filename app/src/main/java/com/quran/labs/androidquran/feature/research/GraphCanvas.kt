package com.quran.labs.androidquran.feature.research

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/*
 * The research map drawn directly with Compose: a small force layout in 3D, a camera that
 * orbits it, and nodes drawn far to near as shaded spheres with glows and labels. (It used to
 * be three.js in a WebView, which stayed blank on some phones.)
 */

private class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
  fun set(o: Vec3) { x = o.x; y = o.y; z = o.z }
}

private class SimNode(val node: MapNode, val size: Float, val delay: Int) {
  val pos = Vec3()
  val vel = Vec3()
  val links = mutableListOf<SimEdge>()
  // where it was drawn last frame, for taps
  var sx = 0f
  var sy = 0f
  var sr = 0f
  var depth = 0f
  var visible = false
}

private class SimEdge(val a: SimNode, val b: SimNode, val color: Color, val phase: Float)

private class Projected(val node: SimNode, val x: Float, val y: Float, val scale: Float, val depth: Float)

/** the layout, the camera and what's focused, kept across frames */
private class GraphState(graph: MapGraph) {
  val nodes: List<SimNode>
  val edges: List<SimEdge>
  val byId: Map<String, SimNode>
  val stars: List<Vec3>

  init {
    val random = Random(graph.nodes.size * 31 + graph.edges.size)
    val degree = mutableMapOf<String, Int>()
    graph.edges.forEach {
      degree[it.source] = (degree[it.source] ?: 0) + 1
      degree[it.target] = (degree[it.target] ?: 0) + 1
    }
    val spread = 40f + sqrt(graph.nodes.size.toFloat()) * 22f
    nodes = graph.nodes.mapIndexed { i, n ->
      val base = if (n.target is LinkTarget.Idea) 8f else 5f
      SimNode(n, base + sqrt((degree[n.id] ?: 0).toFloat()) * 1.4f, i * 4).apply {
        pos.x = (random.nextFloat() - 0.5f) * spread
        pos.y = (random.nextFloat() - 0.5f) * spread
        pos.z = (random.nextFloat() - 0.5f) * spread
      }
    }
    byId = nodes.associateBy { it.node.id }
    edges = graph.edges.mapNotNull { e ->
      val a = byId[e.source] ?: return@mapNotNull null
      val b = byId[e.target] ?: return@mapNotNull null
      SimEdge(a, b, mapRelationColor(e.relation), random.nextFloat()).also { a.links += it; b.links += it }
    }
    stars = List(260) {
      val r = 900f + random.nextFloat() * 500f
      val t = random.nextFloat() * 2f * PI.toFloat()
      val u = kotlin.math.acos(2f * random.nextFloat() - 1f)
      Vec3(r * sin(u) * cos(t), r * sin(u) * sin(t), r * cos(u))
    }
  }

  var settle = 420
  var frame = 0
  var radius = 60f

  // the camera orbits [target] at [distance]
  var yaw = 0f
  var pitch = 0.18f
  var distance = 320f
  val target = Vec3()
  var touched = false
  var autoRotate = true

  // flying to a node (or back to the whole map)
  var flyTo: Vec3? = null
  var flyDistance = 0f
  var flyThen: (() -> Unit)? = null
  var shift = 0f
  var shiftGoal = 0f

  var focused: SimNode? = null

  fun step() {
    if (settle <= 0) return
    settle--
    val k = 0.02f + 0.98f * (settle / 420f)
    for (i in nodes.indices) {
      val a = nodes[i]
      for (j in i + 1 until nodes.size) {
        val b = nodes[j]
        val dx = a.pos.x - b.pos.x
        val dy = a.pos.y - b.pos.y
        val dz = a.pos.z - b.pos.z
        val d2 = max(dx * dx + dy * dy + dz * dz, 100f)
        val f = 140f / d2 * k
        a.vel.x += dx * f; a.vel.y += dy * f; a.vel.z += dz * f
        b.vel.x -= dx * f; b.vel.y -= dy * f; b.vel.z -= dz * f
      }
    }
    for (e in edges) {
      val dx = e.b.pos.x - e.a.pos.x
      val dy = e.b.pos.y - e.a.pos.y
      val dz = e.b.pos.z - e.a.pos.z
      val d = sqrt(dx * dx + dy * dy + dz * dz).takeIf { it > 0f } ?: 1f
      val f = (d - 48f) * 0.02f * k / d
      e.a.vel.x += dx * f; e.a.vel.y += dy * f; e.a.vel.z += dz * f
      e.b.vel.x -= dx * f; e.b.vel.y -= dy * f; e.b.vel.z -= dz * f
    }
    var cx = 0f
    var cy = 0f
    var cz = 0f
    nodes.forEach { cx += it.pos.x; cy += it.pos.y; cz += it.pos.z }
    if (nodes.isNotEmpty()) { cx /= nodes.size; cy /= nodes.size; cz /= nodes.size }
    radius = 0f
    for (n in nodes) {
      n.vel.x += -n.pos.x * 0.025f * k
      n.vel.y += -n.pos.y * 0.025f * k
      n.vel.z += -n.pos.z * 0.025f * k
      n.vel.x *= 0.82f; n.vel.y *= 0.82f; n.vel.z *= 0.82f
      val speed = sqrt(n.vel.x * n.vel.x + n.vel.y * n.vel.y + n.vel.z * n.vel.z)
      if (speed > 4f) { val s = 4f / speed; n.vel.x *= s; n.vel.y *= s; n.vel.z *= s }
      n.pos.x += n.vel.x - cx
      n.pos.y += n.vel.y - cy
      n.pos.z += n.vel.z - cz
      radius = max(radius, sqrt(n.pos.x * n.pos.x + n.pos.y * n.pos.y + n.pos.z * n.pos.z) + n.size * 3f)
    }
  }

  fun fitDistance(aspect: Float): Float {
    val fit = radius / sin(FOV / 2f) * (if (aspect < 1f) 1f / aspect else 1f) * 0.95f + 20f
    return fit.coerceIn(MIN_DISTANCE, MAX_DISTANCE)
  }

  fun focus(n: SimNode) {
    focused = n
    autoRotate = false
    touched = true
    var reach = n.size * 4f
    n.links.forEach { e ->
      val dx = e.a.pos.x - e.b.pos.x
      val dy = e.a.pos.y - e.b.pos.y
      val dz = e.a.pos.z - e.b.pos.z
      reach = max(reach, sqrt(dx * dx + dy * dy + dz * dz) + 12f)
    }
    flyTo = n.pos
    flyDistance = max(90f, reach * 2.4f)
    flyThen = null
    shiftGoal = 0.2f
  }

  fun clearFocus() {
    focused = null
    shiftGoal = 0f
  }

  fun reset(aspect: Float) {
    clearFocus()
    flyTo = Vec3()
    flyDistance = fitDistance(aspect)
    flyThen = { autoRotate = true; touched = false }
  }

  /** moves the camera along for one frame */
  fun animate(aspect: Float) {
    frame++
    if (autoRotate) yaw += 0.0035f
    if (!touched && settle > 0) distance += (fitDistance(aspect) - distance) * 0.08f
    flyTo?.let { goal ->
      target.x += (goal.x - target.x) * 0.1f
      target.y += (goal.y - target.y) * 0.1f
      target.z += (goal.z - target.z) * 0.1f
      distance += (flyDistance - distance) * 0.1f
      val dx = goal.x - target.x
      val dy = goal.y - target.y
      val dz = goal.z - target.z
      if (dx * dx + dy * dy + dz * dz < 0.25f && kotlin.math.abs(distance - flyDistance) < 1f) {
        flyThen?.invoke()
        flyTo = null
      }
    }
    shift += (shiftGoal - shift) * 0.1f
  }

  companion object {
    val FOV = (55.0 * PI / 180.0).toFloat()
    const val MIN_DISTANCE = 30f
    const val MAX_DISTANCE = 900f
  }
}

private val KIND_COLORS = mapOf(
  LinkTarget.Idea.KIND to Color(0xFF26C6DA),
  LinkTarget.Ayah.KIND to Color(0xFFFFC857),
  LinkTarget.Hadith.KIND to Color(0xFFB388FF),
  LinkTarget.Note.KIND to Color(0xFFFF8A65)
)

/** brighter relation colours for the dark map */
internal fun mapRelationColor(relation: Relation) = when (relation) {
  Relation.EXPLAINS -> Color(0xFF4DD0E1)
  Relation.SUPPORTS -> Color(0xFF69F0AE)
  Relation.RELATED -> Color(0xFF8C9EFF)
  Relation.CONTRASTS -> Color(0xFFFF5252)
}

/**
 * The map itself. Drag to turn, pinch to zoom, tap a node to fly to it ([onSelect] with its
 * id) or empty space to let go (null). Changing [resetKey] flies back to the whole map.
 */
@Composable
internal fun GraphCanvas(
  graph: MapGraph,
  shown: (LinkTarget) -> Boolean,
  selectedId: String?,
  resetKey: Int,
  onSelect: (String?) -> Unit,
  modifier: Modifier = Modifier
) {
  val state = remember(graph) { GraphState(graph) }
  val measurer = rememberTextMeasurer()
  val labels = remember(graph, measurer) {
    graph.nodes.associate { n ->
      val idea = n.target is LinkTarget.Idea
      n.id to measurer.measure(
        n.label,
        TextStyle(color = Color.White, fontSize = if (idea) 12.sp else 10.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
  val currentShown by rememberUpdatedState(shown)
  val currentOnSelect by rememberUpdatedState(onSelect)
  var tick by remember { mutableLongStateOf(0L) }
  var aspect by remember { mutableLongStateOf(0L) } // width/height * 1000, set while drawing

  LaunchedEffect(state) {
    while (true) {
      withFrameNanos { tick = it }
      state.step()
      state.animate(if (aspect == 0L) 0.5f else aspect / 1000f)
    }
  }
  // the card picks a node too (a connection, or a re-selection after the graph reloads)
  LaunchedEffect(state, selectedId) {
    val node = selectedId?.let { state.byId[it] }
    if (node == null) state.clearFocus() else if (state.focused != node) state.focus(node)
  }
  LaunchedEffect(state, resetKey) {
    if (resetKey > 0) state.reset(if (aspect == 0L) 0.5f else aspect / 1000f)
  }

  Canvas(
    modifier
      .pointerInput(state) {
        detectTransformGestures { _, pan, zoom, _ ->
          state.touched = true
          state.autoRotate = false
          state.flyTo = null
          state.yaw -= pan.x * 0.006f
          state.pitch = (state.pitch + pan.y * 0.006f).coerceIn(-1.35f, 1.35f)
          state.distance = (state.distance / zoom).coerceIn(GraphState.MIN_DISTANCE, GraphState.MAX_DISTANCE)
        }
      }
      .pointerInput(state) {
        detectTapGestures { tap ->
          // the nearest node under the finger, front ones first
          val hit = state.nodes
            .filter { it.visible && currentShown(it.node.target) }
            .filter { n ->
              val dx = tap.x - n.sx
              val dy = tap.y - n.sy
              val reach = max(n.sr * 1.6f, 22.dp.toPx())
              dx * dx + dy * dy <= reach * reach
            }
            .minByOrNull { it.depth }
          if (hit != null) {
            state.focus(hit)
            currentOnSelect(hit.node.id)
          } else if (state.focused != null) {
            state.clearFocus()
            currentOnSelect(null)
          }
        }
      }
  ) {
    tick // redraw every frame
    aspect = (size.width / size.height * 1000f).toLong()
    drawGraph(state, labels, currentShown)
  }
}

private fun DrawScope.drawGraph(state: GraphState, labels: Map<String, TextLayoutResult>, shown: (LinkTarget) -> Boolean) {
  val w = size.width
  val h = size.height
  if (w <= 0f || h <= 0f) return
  val focal = (h / 2f) / tan(GraphState.FOV / 2f)
  val cx = w / 2f
  val cy = h / 2f - h * 0.2f * state.shift
  val cosYaw = cos(state.yaw)
  val sinYaw = sin(state.yaw)
  val cosPitch = cos(state.pitch)
  val sinPitch = sin(state.pitch)

  fun project(p: Vec3, relativeToTarget: Boolean = true): Triple<Float, Float, Float>? {
    val qx = if (relativeToTarget) p.x - state.target.x else p.x
    val qy = if (relativeToTarget) p.y - state.target.y else p.y
    val qz = if (relativeToTarget) p.z - state.target.z else p.z
    val x1 = qx * cosYaw + qz * sinYaw
    val z1 = -qx * sinYaw + qz * cosYaw
    val y2 = qy * cosPitch - z1 * sinPitch
    val z2 = qy * sinPitch + z1 * cosPitch
    val depth = state.distance - z2
    if (depth < 2f) return null
    val s = focal / depth
    return Triple(cx + x1 * s, cy - y2 * s, depth)
  }

  // stars, far behind everything
  for (star in state.stars) {
    val p = project(star, relativeToTarget = false) ?: continue
    drawCircle(Color(0x665F8B8F), radius = 1.6f, center = Offset(p.first, p.second))
  }

  val focused = state.focused
  val near = mutableSetOf<SimNode>()
  if (focused != null) {
    near += focused
    focused.links.forEach { near += it.a; near += it.b }
  }
  fun fog(depth: Float) = exp(-(0.0007f * depth).pow(2)).coerceIn(0.25f, 1f)

  // where each node lands this frame
  val projected = mutableMapOf<SimNode, Projected>()
  for (n in state.nodes) {
    n.visible = false
    if (!shown(n.node.target)) continue
    val p = project(n.pos) ?: continue
    val grow = ((state.frame - n.delay) / 25f).coerceIn(0f, 1f)
    val ease = 1f + 2.7f * (grow - 1f).pow(3) + 1.7f * (grow - 1f).pow(2)
    if (ease <= 0.01f) continue
    val scale = focal / p.third
    val breathe = if (n == focused) 1.1f + sin(state.frame * 0.08f) * 0.06f else 1f
    projected[n] = Projected(n, p.first, p.second, scale * ease * breathe, p.third)
    n.sx = p.first
    n.sy = p.second
    n.sr = n.size * scale * ease * 1.35f
    n.depth = p.third
    n.visible = true
  }

  // links, then a pulse running along each from the idea out
  for (e in state.edges) {
    val a = projected[e.a] ?: continue
    val b = projected[e.b] ?: continue
    val mine = focused != null && (e.a == focused || e.b == focused)
    val alpha = when {
      focused == null -> 0.75f
      mine -> 1f
      else -> 0.07f
    } * fog((a.depth + b.depth) / 2f)
    val width = (if (mine) 3.6f else 1.8f) * (a.scale + b.scale) / 2f
    drawLine(
      e.color.copy(alpha = alpha),
      Offset(a.x, a.y), Offset(b.x, b.y),
      strokeWidth = width.coerceIn(1.5f, 14f),
      cap = StrokeCap.Round
    )
    if (focused == null || mine) {
      val t = ((state.frame * 0.006f + e.phase) % 1f)
      val px = a.x + (b.x - a.x) * t
      val py = a.y + (b.y - a.y) * t
      val pr = (if (mine) 9f else 6f) * ((a.scale + b.scale) / 2f) * sin(t * PI.toFloat())
      if (pr > 0.5f) {
        drawCircle(
          Brush.radialGradient(listOf(e.color.copy(alpha = alpha), Color.Transparent), Offset(px, py), pr),
          radius = pr, center = Offset(px, py)
        )
      }
    }
  }

  // nodes far to near, each a shaded sphere in a glow, with its label above
  for (p in projected.values.sortedByDescending { it.depth }) {
    val n = p.node
    val color = KIND_COLORS[n.node.target.kind] ?: Color.White
    val lit = focused == null || n in near
    val alpha = (if (lit) 1f else 0.18f) * fog(p.depth)
    val r = n.size * p.scale * 1.35f
    val center = Offset(p.x, p.y)
    val glow = r * (if (n == focused) 4f else 2.6f)
    drawCircle(
      Brush.radialGradient(listOf(color.copy(alpha = 0.45f * alpha), Color.Transparent), center, glow),
      radius = glow, center = center
    )
    drawCircle(
      Brush.radialGradient(
        listOf(lerp(color, Color.White, 0.55f), color, lerp(color, Color.Black, 0.35f)),
        center = Offset(p.x - r * 0.35f, p.y - r * 0.35f),
        radius = r * 1.5f
      ),
      radius = r, center = center, alpha = alpha
    )
    if (n == focused) {
      drawCircle(color.copy(alpha = 0.9f), radius = r * 1.45f, center = center, style = Stroke(width = 2.5f))
    }
    labels[n.node.id]?.let { label ->
      val labelScale = (p.scale * 1.5f).coerceIn(0.6f, 1.05f)
      val lw = label.size.width * labelScale
      val lh = label.size.height * labelScale
      val padX = 10f * labelScale
      val padY = 5f * labelScale
      val left = p.x - lw / 2f - padX
      val top = p.y - r - lh - padY * 2f - 8f
      val labelAlpha = if (lit) fog(p.depth) else 0.15f
      drawRoundRect(
        Color(0xC8061012).copy(alpha = 0.8f * labelAlpha),
        topLeft = Offset(left, top),
        size = Size(lw + padX * 2f, lh + padY * 2f),
        cornerRadius = CornerRadius(12f * labelScale)
      )
      drawRoundRect(
        color.copy(alpha = labelAlpha),
        topLeft = Offset(left, top),
        size = Size(lw + padX * 2f, lh + padY * 2f),
        cornerRadius = CornerRadius(12f * labelScale),
        style = Stroke(width = 1.5f)
      )
      val pivot = Offset(left + padX, top + padY)
      withTransform({ scale(labelScale, labelScale, pivot) }) {
        drawText(label, topLeft = pivot, alpha = labelAlpha)
      }
    }
  }
}

