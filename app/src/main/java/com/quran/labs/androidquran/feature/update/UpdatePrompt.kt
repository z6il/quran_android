package com.quran.labs.androidquran.feature.update

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.quran.labs.androidquran.R
import kotlinx.coroutines.launch
import java.io.File

/** Checks for a newer release and walks the reader through installing it. */
class UpdateController(private val activity: ComponentActivity) {
  var offer by mutableStateOf<AppRelease?>(null)
    private set
  var progress by mutableStateOf<Float?>(null)
    private set
  var checking by mutableStateOf(false)
    private set
  /** downloaded, waiting for the reader to allow installs from this app */
  private var pending: File? = null

  /** quietly, on opening the app: only says something when there is an update */
  fun checkOnStart() {
    if (!AppUpdater.dueForCheck(activity)) return
    activity.lifecycleScope.launch {
      val release = runCatching { AppUpdater.check(activity) }.getOrNull() ?: return@launch
      if (!AppUpdater.wasDismissed(activity, release.build)) offer = release
    }
  }

  /** asked for: always answers */
  fun checkNow() {
    if (checking) return
    checking = true
    activity.lifecycleScope.launch {
      val result = runCatching { AppUpdater.check(activity) }
      checking = false
      result.onSuccess { release ->
        if (release == null) Toast.makeText(activity, R.string.app_update_none, Toast.LENGTH_SHORT).show() else offer = release
      }.onFailure {
        Toast.makeText(activity, R.string.app_update_check_failed, Toast.LENGTH_LONG).show()
      }
    }
  }

  fun later() {
    offer?.let { AppUpdater.dismiss(activity, it.build) }
    offer = null
  }

  fun update() {
    val release = offer ?: return
    if (progress != null) return
    progress = 0f
    activity.lifecycleScope.launch {
      val file = runCatching { AppUpdater.download(activity, release) { p -> activity.runOnUiThread { progress = p } } }
      progress = null
      file.onSuccess { apk ->
        offer = null
        installOrAsk(apk)
      }.onFailure {
        Toast.makeText(activity, activity.getString(R.string.app_update_download_failed, it.message ?: ""), Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun installOrAsk(apk: File) {
    if (AppUpdater.canInstall(activity)) {
      AppUpdater.install(activity, apk)
    } else {
      // Android needs the reader to allow installs from this app once; we carry on when they're back
      pending = apk
      Toast.makeText(activity, R.string.app_update_allow_installs, Toast.LENGTH_LONG).show()
      AppUpdater.askPermission(activity)
    }
  }

  /** call from onResume: back from allowing installs */
  fun onResume() {
    val apk = pending ?: return
    if (AppUpdater.canInstall(activity)) {
      pending = null
      AppUpdater.install(activity, apk)
    }
  }
}

@Composable
fun UpdatePrompt(controller: UpdateController) {
  val release = controller.offer ?: return
  val progress = controller.progress
  AlertDialog(
    onDismissRequest = { if (progress == null) controller.later() },
    title = { Text(stringResource(R.string.app_update_available)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(release.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (release.notes.isNotBlank()) {
          Text(
            // the release notes are written in Markdown; plain is enough here
            readableNotes(release.notes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())
          )
        }
        if (progress != null) {
          LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
          Text(stringResource(R.string.app_update_downloading, (progress * 100).toInt()), style = MaterialTheme.typography.labelMedium)
        }
      }
    },
    confirmButton = {
      Button(onClick = controller::update, enabled = progress == null, shape = RoundedCornerShape(50)) {
        Text(stringResource(R.string.app_update_now))
      }
    },
    dismissButton = {
      if (progress == null) TextButton(onClick = controller::later) { Text(stringResource(R.string.app_update_later)) }
    }
  )
}

/** the "New in…" part of the release notes, without Markdown's marks */
private fun readableNotes(notes: String): String {
  val start = notes.indexOf("### New").takeIf { it >= 0 } ?: 0
  return notes.substring(start)
    // [text](link) -> text
    .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
    .replace("**", "").replace("### ", "").replace("`", "")
    .trim()
}
