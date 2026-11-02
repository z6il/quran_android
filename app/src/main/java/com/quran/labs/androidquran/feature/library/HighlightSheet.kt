package com.quran.labs.androidquran.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.feature.home.AppIcons
import com.quran.labs.androidquran.feature.prayer.cardColor
import com.quran.labs.androidquran.feature.research.arabicFont

/**
 * A paragraph to highlight: the reader drags over the words or lines they want, then picks a
 * colour. With nothing chosen the whole paragraph is highlighted. Below, the highlights it
 * already has, each with its note.
 */
@Composable
internal fun HighlightSheet(
  text: String,
  existing: List<BookHighlight>,
  scale: Float,
  onAdd: (start: Int, end: Int, color: HighlightColor) -> Unit,
  onEdit: (BookHighlight, HighlightColor, String) -> Unit,
  onDelete: (BookHighlight) -> Unit
) {
  val field = rememberTextFieldState(text)
  Column(
    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp, 0.dp, 20.dp, 32.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(stringResource(R.string.library_highlight), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.library_highlight_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(shape = RoundedCornerShape(16.dp), color = cardColor(), modifier = Modifier.fillMaxWidth()) {
      BasicTextField(
        state = field,
        readOnly = true,
        lineLimits = TextFieldLineLimits.MultiLine(),
        textStyle = TextStyle(
          fontFamily = arabicFont(), fontSize = (19 * scale).sp, lineHeight = (34 * scale).sp,
          textDirection = TextDirection.Rtl, color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(14.dp)
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      HighlightColor.entries.forEach { color ->
        ColorDot(color, selected = false, size = 40) {
          val selection = field.selection
          val (start, end) = if (selection.collapsed) 0 to text.length else wholeWords(text, selection.min, selection.max)
          if (end > start) onAdd(start, end, color)
        }
      }
    }

    if (existing.isNotEmpty()) {
      Spacer(Modifier.size(4.dp))
      Text(stringResource(R.string.library_highlight_existing), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      existing.forEach { h -> ExistingHighlight(h, onEdit, onDelete) }
    }
  }
}

@Composable
internal fun ExistingHighlight(h: BookHighlight, onEdit: (BookHighlight, HighlightColor, String) -> Unit, onDelete: (BookHighlight) -> Unit) {
  var note by remember(h.id) { mutableStateOf(h.note) }
  var color by remember(h.id) { mutableStateOf(h.color) }
  Surface(shape = RoundedCornerShape(16.dp), color = cardColor(), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Text(
          h.text,
          style = TextStyle(fontFamily = arabicFont(), fontSize = 17.sp, lineHeight = 29.sp, textDirection = TextDirection.Rtl, background = Color(color.argb).copy(alpha = 0.4f)),
          maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onDelete(h) }) { Icon(AppIcons.Delete, contentDescription = stringResource(R.string.library_highlight_delete)) }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HighlightColor.entries.forEach { c -> ColorDot(c, selected = c == color, size = 28) { color = c } }
      }
      OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.library_highlight_note)) }, modifier = Modifier.fillMaxWidth())
      if (note != h.note || color != h.color) {
        Row {
          Spacer(Modifier.weight(1f))
          TextButton(onClick = { onEdit(h, color, note.trim()) }) { Text(stringResource(R.string.library_save)) }
        }
      }
    }
  }
}

@Composable
private fun ColorDot(color: HighlightColor, selected: Boolean, size: Int, onClick: () -> Unit) {
  Box(
    Modifier
      .size(size.dp)
      .clip(CircleShape)
      .background(Color(color.argb))
      .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
      .clickable(onClick = onClick)
  )
}

/** a selection widened to whole words, so a highlight never cuts one in half */
internal fun wholeWords(text: String, start: Int, end: Int): Pair<Int, Int> {
  var s = start.coerceIn(0, text.length)
  var e = end.coerceIn(s, text.length)
  // spaces at either end first, so they don't pull in the word beyond
  while (s < e && text[s].isWhitespace()) s++
  while (e > s && text[e - 1].isWhitespace()) e--
  if (s == e) return s to e
  while (s > 0 && !text[s - 1].isWhitespace()) s--
  while (e < text.length && !text[e].isWhitespace()) e++
  return s to e
}
