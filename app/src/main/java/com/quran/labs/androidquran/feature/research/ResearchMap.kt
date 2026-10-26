package com.quran.labs.androidquran.feature.research

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quran.labs.androidquran.R

/** The 3D map of ideas and what they link to. */
@Composable
fun ResearchMap(
  store: ResearchStore,
  text: ResearchText,
  onOpenIdea: (Long) -> Unit,
  onBack: () -> Unit
) {
  Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
    Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(stringResource(R.string.research_map))
    }
  }
}
