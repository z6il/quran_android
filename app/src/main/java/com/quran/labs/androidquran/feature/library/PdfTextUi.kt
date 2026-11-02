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

/** Which text a PDF uses, and OCR to start or stop. */
@Composable
internal fun PdfTextMenu(
  expanded: Boolean,
  text: PdfTextState,
  job: TextJob?,
  pages: Int,
  onDismiss: () -> Unit,
  onChoose: (TextSource) -> Unit,
  onOcr: () -> Unit,
  onStop: () -> Unit
) {
  DropdownMenu(expanded, onDismissRequest = onDismiss) {
    Text(
      stringResource(R.string.library_text_of_pdf), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(16.dp, 8.dp)
    )
    DropdownMenuItem(
      enabled = text.layerPages > 0,
      leadingIcon = { RadioButton(selected = text.source == TextSource.LAYER, onClick = null) },
      text = { Text(stringResource(R.string.library_text_layer, text.layerPages)) },
      onClick = { onChoose(TextSource.LAYER) }
    )
    DropdownMenuItem(
      enabled = text.ocrPages > 0,
      leadingIcon = { RadioButton(selected = text.source == TextSource.OCR, onClick = null) },
      text = { Text(stringResource(R.string.library_text_ocr, text.ocrPages)) },
      onClick = { onChoose(TextSource.OCR) }
    )
    HorizontalDivider()
    if (job?.source == TextSource.OCR && job.running) {
      DropdownMenuItem(text = { Text(stringResource(R.string.library_text_stop_ocr, job.done, job.total)) }, onClick = onStop)
    } else if (!text.ocrRead) {
      DropdownMenuItem(
        text = { Text(stringResource(if (text.ocrPages > 0) R.string.library_text_resume_ocr else R.string.library_text_run_ocr)) },
        onClick = onOcr
      )
    }
    if (pages > 0 && text.layerRead && text.layerPages == 0) {
      Text(
        stringResource(R.string.library_no_layer), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 8.dp)
      )
    }
  }
}

/** Above the pages: a scanned PDF's offer of OCR, and how reading its text is going. */
@Composable
internal fun PdfTextBanner(text: PdfTextState, job: TextJob?, pages: Int, onOcr: () -> Unit, onRetry: () -> Unit) {
  val running = job != null && job.running && job.source == TextSource.OCR
  val scanned = pages > 0 && text.layerRead && text.layerPages < pages / 10 && text.ocrPages == 0
  if (!running && !scanned && job?.failed == null) return
  Surface(color = cardColor(), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      when {
        job?.failed != null -> Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            stringResource(R.string.library_ocr_failed, job.failed), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f)
          )
          TextButton(onClick = onRetry) { Text(stringResource(R.string.library_retry)) }
        }
        running && job!!.downloadingModel != null -> {
          Text(stringResource(R.string.library_ocr_model_progress, (job.downloadingModel * 100).toInt()), style = MaterialTheme.typography.bodySmall)
          LinearProgressIndicator(progress = { job.downloadingModel }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
        }
        running -> {
          Text(stringResource(R.string.library_ocr_progress, job!!.done, job.total), style = MaterialTheme.typography.bodySmall)
          LinearProgressIndicator(
            progress = { if (job.total == 0) 0f else job.done / job.total.toFloat() },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
          )
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
          Text(stringResource(R.string.library_scanned), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
          TextButton(onClick = onOcr) { Text(stringResource(R.string.library_ocr_start)) }
        }
      }
    }
  }
}

@Composable
internal fun OcrDialog(pages: Int, onStart: () -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  // about six seconds a page on a phone
  val minutes = (pages * 6 / 60).coerceAtLeast(1)
  val model = if (OcrModel.installed(context)) "" else {
    stringResource(R.string.library_ocr_model, String.format(Locale.getDefault(), "%.1f", OcrModel.BYTES / 1_000_000.0)) + " "
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.library_ocr_title)) },
    text = { Text(stringResource(R.string.library_ocr_body, model, minutes)) },
    confirmButton = { TextButton(onClick = onStart) { Text(stringResource(R.string.library_ocr_start)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } }
  )
}

/** Words chosen on a PDF page: highlight them in a colour, or copy them. */
@Composable
internal fun SelectionBar(selection: PdfSelection, onColor: (HighlightColor) -> Unit, onCopy: () -> Unit, onClose: () -> Unit) {
  Surface(color = cardColor()) {
    Column(
      Modifier.fillMaxWidth().padding(16.dp, 10.dp)
        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    ) {
      Text(
        selection.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
      )
      Spacer(Modifier.size(8.dp))
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HighlightColor.entries.forEach { color ->
          Box(Modifier.size(34.dp).clip(CircleShape).background(Color(color.argb)).clickable { onColor(color) })
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCopy) { Text(stringResource(R.string.library_copy)) }
        IconButton(onClick = onClose) { Icon(QuranIcons.Close, contentDescription = stringResource(R.string.library_cancel)) }
      }
    }
  }
}
