package com.quran.labs.androidquran.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.quran.labs.androidquran.R
import kotlin.math.roundToInt

/** What can be done with words the reader chose. */
interface SelectionActions {
  fun highlight(color: HighlightColor)
  fun note()
  fun copy()
  fun research()
}

private val HANDLE = 22.dp
/** the handle's reach for a finger: twice the drop, around it */
private val TOUCH = 48.dp

/**
 * A selection handle: a drop hanging from the end of the selection, its square corner at
 * [point] (the bottom of the selection's edge, in the parent's pixels). [hangsLeft]: the drop
 * sits to the left of the point, as for where an English selection starts or an Arabic one ends.
 * Placed by absolute position, so an Arabic layout doesn't mirror it. Dragging reports where
 * the finger is pointing, a little above the drop, in the parent's pixels; [onMove] too, for a
 * magnifier.
 */
@Composable
internal fun SelectionHandle(
  point: Offset,
  hangsLeft: Boolean,
  color: Color,
  onDrag: (Offset) -> Unit,
  onDragEnd: () -> Unit = {},
  onMove: (Offset?) -> Unit = {},
  /** from the parent box: AbsoluteAlignment.TopLeft, so an Arabic layout doesn't start it at the right */
  modifier: Modifier = Modifier
) {
  val density = LocalDensity.current
  val drop = with(density) { HANDLE.toPx() }
  val reach = with(density) { TOUCH.toPx() }
  val currentPoint by rememberUpdatedState(point)
  val currentDrag by rememberUpdatedState(onDrag)
  val currentMove by rememberUpdatedState(onMove)
  val shape = if (hangsLeft) {
    AbsoluteRoundedCornerShape(topLeftPercent = 50, topRightPercent = 0, bottomRightPercent = 50, bottomLeftPercent = 50)
  } else {
    AbsoluteRoundedCornerShape(topLeftPercent = 0, topRightPercent = 50, bottomRightPercent = 50, bottomLeftPercent = 50)
  }
  // the reach, with the drop in its corner at the point
  Box(
    modifier
      .absoluteOffset { IntOffset((point.x - if (hangsLeft) reach else 0f).roundToInt(), point.y.roundToInt()) }
      .size(TOUCH)
      .pointerInput(hangsLeft) {
        var at = Offset.Zero
        detectDragGestures(
          onDragStart = {
            // aim at the text just above the drop's corner
            at = currentPoint - Offset(0f, drop * 0.8f)
            currentMove(at)
          },
          onDrag = { change, delta ->
            change.consume()
            at += delta
            currentDrag(at)
            currentMove(at)
          },
          onDragEnd = {
            currentMove(null)
            onDragEnd()
          },
          onDragCancel = {
            currentMove(null)
            onDragEnd()
          }
        )
      }
  ) {
    Box(
      Modifier
        .align(if (hangsLeft) AbsoluteAlignment.TopRight else AbsoluteAlignment.TopLeft)
        .size(HANDLE)
        .clip(shape)
        .background(color)
    )
  }
}

/**
 * The floating bar over a selection: the highlight colours, then a note, copy and research.
 * [anchor] is the selection's box in the parent's pixels; the bar sits above it, or below
 * when there's no room.
 */
@Composable
internal fun SelectionToolbar(anchor: Rect, actions: SelectionActions) {
  val density = LocalDensity.current
  val gap = with(density) { 10.dp.roundToPx() }
  val below = with(density) { (HANDLE + 12.dp).roundToPx() }
  val provider = remember(anchor) {
    object : PopupPositionProvider {
      override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val centre = anchorBounds.left + anchor.center.x
        val x = (centre - popupContentSize.width / 2f).roundToInt().coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
        val above = anchorBounds.top + anchor.top.roundToInt() - popupContentSize.height - gap
        val y = if (above > popupContentSize.height) above else anchorBounds.top + anchor.bottom.roundToInt() + below
        return IntOffset(x, y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
      }
    }
  }
  Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 8.dp) {
      Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          HighlightColor.entries.forEach { c ->
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(c.argb)).clickable { actions.highlight(c) })
          }
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.3f)))
        ToolbarItem(R.string.library_highlight_note, actions::note)
        ToolbarItem(R.string.library_copy, actions::copy)
        ToolbarItem(R.string.library_to_research, actions::research)
      }
    }
  }
}

@Composable
private fun ToolbarItem(text: Int, onClick: () -> Unit) {
  Text(
    stringResource(text),
    color = MaterialTheme.colorScheme.inverseOnSurface,
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp)
  )
}

/** holds the selection's handles while one is being dragged, so the toolbar can step aside */
internal class DragState {
  var dragging by mutableStateOf(false)
}

/** a lens over [at] (in the element's pixels) while a handle is dragged, held above the finger */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun Modifier.lens(at: Offset?, density: androidx.compose.ui.unit.Density): Modifier = magnifier(
  sourceCenter = { at ?: Offset.Unspecified },
  magnifierCenter = { at?.let { it - Offset(0f, with(density) { 72.dp.toPx() }) } ?: Offset.Unspecified },
  zoom = 1.8f
)
