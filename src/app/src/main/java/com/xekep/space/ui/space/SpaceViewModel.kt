package com.xekep.space.ui.space

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.xekep.space.storage.ArcadeProgress

class SpaceViewModel(application: Application) : AndroidViewModel(application) {
    private val progress = ArcadeProgress(application)
    val game = SpaceGameState(initialRecords = progress.records, saveRecord = progress::saveBestScore)
}
