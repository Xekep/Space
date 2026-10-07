package com.xekep.space.ui.space

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xekep.space.R
import com.xekep.space.sim.SandboxCollisionMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollisionModePicker(mode: SandboxCollisionMode,onChange: (SandboxCollisionMode) -> Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        SandboxCollisionMode.entries.forEach { value ->
            FilterChip(mode == value,{ onChange(value) },modifier=Modifier.testTag("collision-mode-${value.name}"),
                label={ Text(stringResource(if (value == SandboxCollisionMode.Merge) R.string.collision_merge else R.string.collision_debris)) })
        }
    }
}
