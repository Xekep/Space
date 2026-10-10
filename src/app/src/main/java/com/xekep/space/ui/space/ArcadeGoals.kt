package com.xekep.space.ui.space

import com.xekep.space.R

enum class ArcadeGoal(val label: Int, val description: Int) {
    Intercept(R.string.goal_intercept,R.string.goal_intercept_help),
    Giant(R.string.goal_giant,R.string.goal_giant_help),
    Convoy(R.string.goal_convoy,R.string.goal_convoy_help),
    Twenty(R.string.goal_twenty,R.string.goal_twenty_help);
    fun earned(run: ArcadeSession): Boolean = when (this) {
        Intercept -> run.destroyed > 0
        Giant -> run.challenge?.let { it.rewarded && !it.failed } == true
        Convoy -> run.convoy?.status == ConvoyStatus.Delivered
        Twenty -> run.campaignCleared
    }
}
