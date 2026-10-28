package com.quran.labs.androidquran.feature.research

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import java.util.Locale

/**
 * Pick a surah by its name. The field shows the chosen surah; tapping it opens the list of
 * all 114, with a search box that matches a name or a number ("baq", "بقر", "2").
 */
@Composable
fun SuraPicker(sura: Int?, text: ResearchText, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
  var open by rememberSaveable { mutableStateOf(false) }
  Box(modifier) {
    OutlinedTextField(
      value = sura?.let { label(it, text) }.orEmpty(),
      onValueChange = {},
      readOnly = true,
      singleLine = true,
      label = { Text(stringResource(R.string.research_sura)) },
      trailingIcon = { Icon(QuranIcons.ExpandMore, contentDescription = null) },
      modifier = Modifier.fillMaxWidth()
    )
    // the whole field opens the list, not only its text
    Box(Modifier.matchParentSize().clip(RoundedCornerShape(4.dp)).clickable { open = true })
  }
  if (open) {
    SuraListDialog(text, onPick = { onPick(it); open = false }, onDismiss = { open = false })
  }
}

@Composable
private fun SuraListDialog(text: ResearchText, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
  var query by remember { mutableStateOf("") }
  val folded = fold(query)
  val matches = remember(folded) {
    (1..114).filter { s -> folded.isEmpty() || s.toString().startsWith(folded) || fold(text.suraName(s)).contains(folded) }
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.research_sura)) },
    text = {
      Column {
        OutlinedTextField(
          query, { query = it }, singleLine = true,
          leadingIcon = { Icon(QuranIcons.Search, contentDescription = null) },
          placeholder = { Text(stringResource(R.string.research_sura_search)) },
          shape = RoundedCornerShape(50),
          modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(Modifier.padding(top = 8.dp).height(360.dp)) {
          items(matches) { s ->
            Text(
              label(s, text),
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(s) }
                .padding(horizontal = 8.dp, vertical = 12.dp)
            )
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.research_cancel)) } }
  )
}

private fun label(sura: Int, text: ResearchText) = String.format(Locale.getDefault(), "%d. %s", sura, text.suraName(sura))

/** lower case, no "Surah"/"سورة", no diacritics or apostrophes, so "Al-Baqarah" matches "baq" */
private fun fold(s: String): String =
  HadithLibrary.normalizeArabic(s).lowercase(Locale.ROOT)
    .replace("surah", "").replace("سوره", "")
    .replace(Regex("[āīūʿʾ'`\\-\\s]")) { m -> when (m.value) { "ā" -> "a"; "ī" -> "i"; "ū" -> "u"; else -> "" } }
