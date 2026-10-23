package com.quran.data.model.audio

import java.util.Locale

/**
 * Replacement urls for individual suras of gapless qaris, from [Qari.suraUrls].
 *
 * Audio urls are built from a per-qari format string in several places (streaming,
 * downloads), so rather than thread overrides through all of them, the formatted url is
 * looked up here. Files keep their usual local names (e.g. 017.mp3).
 */
object AudioUrlOverrides {
  @Volatile
  private var overrides: Map<String, String> = emptyMap()

  fun register(qaris: List<Qari>) {
    overrides = qaris.flatMap { qari ->
      qari.suraUrls.map { (sura, url) -> qari.url + String.format(Locale.US, "%03d.mp3", sura) to url }
    }.toMap()
  }

  fun resolve(url: String): String = overrides[url] ?: url
}
