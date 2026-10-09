package com.xekep.space.ui.space

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xekep.space.R
import com.xekep.space.input.FlightControlMode
import com.xekep.space.storage.GameOptions

@Composable
fun FlightControlSettings(options: GameOptions) {
    Text(stringResource(R.string.flight_controls),style=MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FlightControlMode.entries.forEach { mode ->
            FilterChip(selected=options.flightControl == mode,onClick={ options.flightControl=mode; options.save() },
                label={ Text(stringResource(if (mode == FlightControlMode.Tilt) R.string.control_tilt else R.string.control_joystick)) },
                modifier=Modifier.weight(1f).testTag("flight-control-${mode.name}"))
        }
    }
    if (options.flightControl == FlightControlMode.Tilt) {
        Text(stringResource(R.string.tilt_sensitivity),style=MaterialTheme.typography.labelMedium)
        SpaceSlider(value=options.tiltSensitivity,onValueChange={ options.tiltSensitivity=it; options.save() },
            valueRange=.5f..1.75f,modifier=Modifier.fillMaxWidth().testTag("tilt-sensitivity"))
    }
}
