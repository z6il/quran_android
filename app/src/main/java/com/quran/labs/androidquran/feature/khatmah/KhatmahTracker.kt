package com.quran.labs.androidquran.feature.khatmah

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach

/**
 * Counts a page as read once it has stayed on screen for [DWELL_MS], so flipping past
 * pages doesn't count. Bound while the reading screen is in the foreground.
 */
class KhatmahTracker @Inject constructor(
  private val khatmahStore: KhatmahStore
) {
  private val scope = MainScope()
  private var job: Job? = null

  @OptIn(ExperimentalCoroutinesApi::class)
  fun bind(pageFlow: Flow<Int>) {
    job?.cancel()
    job = pageFlow
      .distinctUntilChanged()
      .mapLatest { page ->
        delay(DWELL_MS)
        page
      }
      .onEach { khatmahStore.markRead(it) }
      .launchIn(scope)
  }

  fun unbind() {
    job?.cancel()
    job = null
  }

  companion object {
    const val DWELL_MS = 5_000L
  }
}
