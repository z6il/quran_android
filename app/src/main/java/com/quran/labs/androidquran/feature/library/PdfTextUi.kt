package com.quran.labs.androidquran.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.feature.prayer.cardColor
import java.util.Locale

/** what each source is called in the menu */
private fun sourceTitle(source: TextSource): Int = when (source) {
  TextSource.LAYER -> R.string.library_text_layer
  TextSource.OCR -> R.string.library_text_ocr
  TextSource.OPENITI -> R.string.library_text_openiti
}

/** Which text a PDF uses: its own, or either OCR; and OCR to start or stop. */
@Composable
internal fun PdfTextMenu(
  expanded: Boolean,
  text: PdfTextState,
  job: TextJob?,
  pages: Int,
  onDismiss: () -> Unit,
  onChoose: (TextSource) -> Unit,
  onOcr: (TextSource) -> Unit,
  onStop: (TextSource) -> Unit
) {
  DropdownMenu(expanded, onDismissRequest = onDismiss) {
    Text(
      stringResource(R.string.library_text_of_pdf), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(16.dp, 8.dp)
    )
    TextSource.entries.forEach { source ->
      DropdownMenuItem(
        enabled = text.pagesOf(source) > 0,
        leadingIcon = { RadioButton(selected = text.source == source, onClick = null) },
        text = { Text(stringResource(sourceTitle(source), text.pagesOf(source))) },
        onClick = { onChoose(source) }
      )
    }
    HorizontalDivider()
    listOf(TextSource.OPENITI, TextSource.OCR).forEach { engine ->
      if (job?.source == engine && job.running) {
        DropdownMenuItem(text = { Text(stringResource(R.string.library_text_stop_ocr, job.done, job.total)) }, onClick = { onStop(engine) })
      } else if (engine !in text.read) {
        DropdownMenuItem(
          text = {
            Text(
              stringResource(
                if (engine == TextSource.OPENITI) R.string.library_run_openiti else R.string.library_run_tesseract
              )
            )
          },
          onClick = { onOcr(engine) }
        )
      }
    }
    if (pages > 0 && TextSource.LAYER in text.read && text.pagesOf(TextSource.LAYER) == 0) {
      Text(
        stringResource(R.string.library_no_layer), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 8.dp)
      )
    }
  }
}

/** Above the pages: a scanned PDF's offer of OCR, and how reading its text is going. */
@Composable
internal fun PdfTextBanner(text: PdfTextState, job: TextJob?, pages: Int, theme: PageTheme, onOcr: () -> Unit, onRetry: () -> Unit) {
  val running = job != null && job.running && job.source != TextSource.LAYER
  val ocrPages = text.pagesOf(TextSource.OCR) + text.pagesOf(TextSource.OPENITI)
  val scanned = pages > 0 && TextSource.LAYER in text.read && text.pagesOf(TextSource.LAYER) < pages / 10 && ocrPages == 0
  if (!running && !scanned && job?.failed == null) return
  // part of the bars over the page: in the page's colours
  Surface(color = theme.chromeColor, contentColor = theme.textColor, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      when {
        job?.failed != null -> Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            stringResource(R.string.library_ocr_failed, job.failed), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f)
          )
          TextButton(onClick = onRetry) { Text(stringResource(R.string.library_retry), color = theme.accentColor) }
        }
        running && job!!.downloadingModel != null -> {
          Text(stringResource(R.string.library_ocr_model_progress, (job.downloadingModel * 100).toInt()), style = MaterialTheme.typography.bodySmall)
          LinearProgressIndicator(
            progress = { job.downloadingModel }, color = theme.accentColor, trackColor = theme.textColor.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
          )
        }
        running -> {
          Text(stringResource(R.string.library_ocr_progress, job!!.done, job.total), style = MaterialTheme.typography.bodySmall)
          LinearProgressIndicator(
            progress = { if (job.total == 0) 0f else job.done / job.total.toFloat() },
            color = theme.accentColor, trackColor = theme.textColor.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
          )
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
          Text(stringResource(R.string.library_scanned), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
          TextButton(onClick = onOcr) { Text(stringResource(R.string.library_ocr_start), color = theme.accentColor) }
        }
      }
    }
  }
}

@Composable
internal fun OcrDialog(engine: TextSource, pages: Int, onStart: () -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  // seconds a page on a phone: OpenITI's model is the quicker of the two
  val minutes = (pages * (if (engine == TextSource.OPENITI) 4 else 6) / 60).coerceAtLeast(1)
  val model = if (OcrModels.installed(context, engine)) "" else {
    stringResource(R.string.library_ocr_model, String.format(Locale.getDefault(), "%.1f", OcrModels.bytes(engine) / 1_000_000.0)) + " "
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(if (engine == TextSource.OPENITI) R.string.library_run_openiti else R.string.library_run_tesseract)) },
    text = {
      Text(
        stringResource(if (engine == TextSource.OPENITI) R.string.library_openiti_about else R.string.library_tesseract_about) + "\n\n" +
          stringResource(R.string.library_ocr_body, model, minutes)
      )
    },
    confirmButton = { TextButton(onClick = onStart) { Text(stringResource(R.string.library_ocr_start)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } }
  )
}

