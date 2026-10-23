package com.quran.mobile.feature.audiobar.ui

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.LocalQuranColors
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.audiobar.state.AudioBarState
import com.quran.mobile.feature.audiobar.state.AudioBarUiEvent

@Composable
internal fun StoppedAudioBar(
  state: AudioBarState.Stopped,
  eventSink: (AudioBarUiEvent.StoppedPlaybackEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.height(IntrinsicSize.Min)
  ) {
    FilledIconButton(
      onClick = { eventSink(AudioBarUiEvent.StoppedPlaybackEvent.Play) },
      modifier = Modifier.size(44.dp)
    ) {
      Icon(QuranIcons.PlayArrow, contentDescription = "")
    }

    TextButton(
      modifier = Modifier.weight(1f),
      onClick = { eventSink(AudioBarUiEvent.StoppedPlaybackEvent.ChangeQari) }
    ) {
      Text(
        text = stringResource(state.qariNameResource),
        color = LocalQuranColors.current.defaultTextColor,
        style = MaterialTheme.typography.titleSmall
      )
      Spacer(modifier = Modifier.weight(1f))
      Icon(QuranIcons.ExpandMore, contentDescription = "", tint =
        LocalQuranColors.current.defaultTextColor)
    }

    if (state.enableRecording) {
      IconButton(onClick = { eventSink(AudioBarUiEvent.StoppedPlaybackEvent.Record) }) {
        Icon(QuranIcons.Mic, contentDescription = "")
      }
    }
  }
}

@Preview
@Composable
private fun StoppedAudioBarPreview() {
  QuranTheme {
    StoppedAudioBar(
      state = AudioBarState.Stopped(
        qariNameResource = com.quran.labs.androidquran.common.audio.R.string.qari_dussary,
        enableRecording = false
      ),
      eventSink = {}
    )
  }
}

@Preview
@Composable
private fun StoppedAudioBarWithRecordingPreview() {
  QuranTheme {
    StoppedAudioBar(
      state = AudioBarState.Stopped(
        qariNameResource = com.quran.labs.androidquran.common.audio.R.string.qari_dussary,
        enableRecording = true
      ),
      eventSink = {}
    )
  }
}
