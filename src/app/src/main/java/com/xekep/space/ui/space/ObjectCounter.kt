package com.xekep.space.ui.space

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.res.stringResource
import com.xekep.space.R

@Composable
fun ObjectCounter(game: SpaceGameState) {
    val count by remember(game) { derivedStateOf { game.spawnCount } }
    val maximum = game.spawnLimit
    val label = stringResource(R.string.object_count, count, maximum)
    Text("$count/$maximum", Modifier.testTag("object-counter").semantics { contentDescription = label },
        style = MaterialTheme.typography.labelSmall,
        color = if (count >= maximum) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}
