package com.quran.labs.androidquran.feature.research

import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.ui.translation.TafsirStyle
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.view.QuoteActionMode

/** an installed translation or tafsir to quote from */
data class TafsirChoice(val file: String, val name: String, val arabic: Boolean)

/**
 * Quote a tafsir from inside the research: pick the verse and the tafsir, read it, select the
 * passage (or take it all). It becomes a note with its reference, highlighted in the tafsir.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TafsirQuoteScreen(
  tafsirs: List<TafsirChoice>,
  text: ResearchText,
  load: suspend (file: String, sura: Int, ayah: Int) -> String?,
  onQuote: (quote: String, choice: TafsirChoice, sura: Int, ayah: Int, relation: Relation) -> Unit,
  onGetTafsirs: () -> Unit,
  onBack: () -> Unit
) {
  var sura by rememberSaveable { mutableStateOf<Int?>(1) }
  var ayah by rememberSaveable { mutableStateOf("1") }
  var chosen by rememberSaveable { mutableStateOf(tafsirs.firstOrNull { it.arabic }?.file ?: tafsirs.firstOrNull()?.file) }
  var relation by rememberSaveable { mutableStateOf(Relation.EXPLAINS) }
  var body by rememberSaveable { mutableStateOf<String?>(null) }
  var loading by rememberSaveable { mutableStateOf(false) }

  val suraNumber = sura
  val ayahNumber = ayah.toIntOrNull()?.takeIf { suraNumber != null && it in 1..text.verseCount(suraNumber) }
  val choice = tafsirs.firstOrNull { it.file == chosen }

  // the installed list arrives after the screen opens: start on the first Arabic tafsir
  LaunchedEffect(tafsirs) {
    if (tafsirs.none { it.file == chosen }) chosen = (tafsirs.firstOrNull { it.arabic } ?: tafsirs.firstOrNull())?.file
  }

  LaunchedEffect(chosen, suraNumber, ayahNumber) {
    body = null
    if (choice != null && suraNumber != null && ayahNumber != null) {
      loading = true
      body = load(choice.file, suraNumber, ayahNumber)
      loading = false
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.research_add_tafsir)) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(QuranIcons.ArrowBack, contentDescription = stringResource(R.string.research_back)) } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    },
    containerColor = MaterialTheme.colorScheme.surface
  ) { padding ->
    Column(
      Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      if (tafsirs.isEmpty()) {
        Text(stringResource(R.string.research_no_tafsir), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onGetTafsirs, shape = RoundedCornerShape(50)) { Text(stringResource(R.string.research_get_tafsir)) }
        return@Column
      }

      Text(stringResource(R.string.research_which_verse), style = MaterialTheme.typography.labelLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        SuraPicker(sura, text, onPick = { sura = it; ayah = "1" }, modifier = Modifier.weight(1f))
        OutlinedTextField(
          ayah, { ayah = it.filter(Char::isDigit).take(3) }, singleLine = true,
          label = { Text(stringResource(R.string.research_ayah)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.width(100.dp)
        )
      }

      Text(stringResource(R.string.research_which_tafsir), style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tafsirs.forEach { t ->
          FilterChip(selected = chosen == t.file, onClick = { chosen = t.file }, label = { Text(t.name) }, shape = RoundedCornerShape(50))
        }
      }

      Text(stringResource(R.string.research_relation_question), style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Relation.entries.forEach { r ->
          FilterChip(selected = relation == r, onClick = { relation = r }, label = { Text(relationLabel(r)) }, shape = RoundedCornerShape(50))
        }
      }

      if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
      val passage = body
      if (passage != null && choice != null && suraNumber != null && ayahNumber != null) {
        Text(stringResource(R.string.research_select_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
          onClick = { onQuote(passage.trim(), choice, suraNumber, ayahNumber, relation) },
          shape = RoundedCornerShape(50),
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(Quote, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.research_quote_all), fontWeight = FontWeight.Bold)
        }
        val accent = MaterialTheme.colorScheme.primary.toArgb()
        val faded = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb()
        val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f), modifier = Modifier.fillMaxWidth()) {
          // a plain TextView: Compose text can't hand over what was selected
          AndroidView(
            factory = { context ->
              TextView(context).apply {
                setTextIsSelectable(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                setLineSpacing(0f, 1.2f)
                setTextColor(textColor)
                if (choice.arabic) {
                  typeface = TypefaceManager.getTafseerTypeface(context)
                  layoutDirection = View.LAYOUT_DIRECTION_RTL
                  textDirection = View.TEXT_DIRECTION_RTL
                }
              }
            },
            update = { view ->
              // the same easier reading as the tafsir itself: chains of narration step back
              view.text = if (choice.arabic) TafsirStyle.style(passage, accent, faded) else passage
              view.customSelectionActionModeCallback = QuoteActionMode(view) { selected ->
                onQuote(selected, choice, suraNumber, ayahNumber, relation)
              }
            },
            modifier = Modifier.padding(16.dp).fillMaxWidth()
          )
        }
      } else if (!loading && suraNumber != null && ayahNumber != null && choice != null) {
        Text(stringResource(R.string.research_no_tafsir_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
