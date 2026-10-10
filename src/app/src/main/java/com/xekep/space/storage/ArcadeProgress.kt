package com.xekep.space.storage

import android.content.Context
import com.xekep.space.ui.space.ArcadeDifficulty

class ArcadeProgress(context: Context) {
    private val preferences = context.getSharedPreferences("space_arcade", Context.MODE_PRIVATE)

    val bestScore: Double
        get() = preferences.getString("best_score", "0")?.toDoubleOrNull() ?: 0.0

    fun saveBestScore(score: Double) {
        if (score > bestScore) {
            preferences.edit().putString("best_score", score.toString()).apply()
        }
    }

    val records: Map<ArcadeDifficulty, Double>
        get() = ArcadeDifficulty.entries.associateWith { preferences.getString("v4_${it.name}", "0")?.toDoubleOrNull() ?: 0.0 }

    fun saveBestScore(difficulty: ArcadeDifficulty, score: Double) {
        if (score > (records[difficulty] ?: 0.0)) preferences.edit().putString("v4_${difficulty.name}", score.toString()).apply()
    }
    val completedGoals: Set<com.xekep.space.ui.space.ArcadeGoal>
        get() = preferences.getStringSet("goals_v1",emptySet()).orEmpty().mapNotNull { name ->
            runCatching { com.xekep.space.ui.space.ArcadeGoal.valueOf(name) }.getOrNull()
        }.toSet()
    fun saveGoals(goals: Set<com.xekep.space.ui.space.ArcadeGoal>) {
        preferences.edit().putStringSet("goals_v1",goals.map { it.name }.toSet()).apply()
    }
}
