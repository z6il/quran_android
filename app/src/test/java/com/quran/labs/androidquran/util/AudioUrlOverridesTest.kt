package com.quran.labs.androidquran.util

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.audio.AudioUrlOverrides
import com.quran.data.model.audio.Qari
import org.junit.After
import org.junit.Test

class AudioUrlOverridesTest {

  private val qari = Qari(
    id = 1,
    nameResource = 0,
    url = "https://example.com/qari/",
    path = "qari",
    hasGaplessAlternative = false,
    db = "qari",
    suraUrls = mapOf(17 to "https://mirror.example.com/017.mp3")
  )

  @After
  fun tearDown() {
    AudioUrlOverrides.register(emptyList())
  }

  @Test
  fun overriddenSuraResolvesToReplacement() {
    AudioUrlOverrides.register(listOf(qari))
    assertThat(AudioUrlOverrides.resolve("https://example.com/qari/017.mp3"))
      .isEqualTo("https://mirror.example.com/017.mp3")
  }

  @Test
  fun otherSurasAndLocalPathsAreUnchanged() {
    AudioUrlOverrides.register(listOf(qari))
    assertThat(AudioUrlOverrides.resolve("https://example.com/qari/018.mp3"))
      .isEqualTo("https://example.com/qari/018.mp3")
    assertThat(AudioUrlOverrides.resolve("/sdcard/quran/audio/qari/017.mp3"))
      .isEqualTo("/sdcard/quran/audio/qari/017.mp3")
  }
}
