package com.xekep.space.ui.space

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xekep.space.storage.ArcadeProgress
import com.xekep.space.storage.SessionRecovery
import com.xekep.space.storage.RecoverySnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class SpaceViewModel(application: Application) : AndroidViewModel(application) {
    private val progress = ArcadeProgress(application)
    private val recovery = SessionRecovery(application)
    val game = SpaceGameState(initialRecords=progress.records,saveRecord=progress::saveBestScore,
        initialGoals=progress.completedGoals,saveGoals=progress::saveGoals)
    private data class Write(val snapshot: RecoverySnapshot,val done: CompletableDeferred<Boolean>? = null)
    private val writes=Channel<Write>(Channel.UNLIMITED)
    init {
        game.recoveryLoading=true
        viewModelScope.launch {
            val saved=withContext(Dispatchers.IO) { recovery.load() }
            if (saved != null && !game.hasSession) game.restoreRecovery(saved)
            game.recoveryFailed=recovery.readFailed
            game.recoveryLoading=false
        }
        viewModelScope.launch {
            for (first in writes) {
                var latest=first
                val acknowledgements=mutableListOf<CompletableDeferred<Boolean>>()
                first.done?.let(acknowledgements::add)
                while (true) { val next=writes.tryReceive().getOrNull() ?: break
                    latest=next; next.done?.let(acknowledgements::add) }
                val result=withContext(Dispatchers.IO) { runCatching { recovery.save(latest.snapshot) }.isSuccess }
                game.recoverySaveFailed=!result
                if (result) game.recoveryFailed=false
                acknowledgements.forEach { it.complete(result) }
            }
        }
    }
    fun saveRecovery() { game.recoverySnapshot(System.currentTimeMillis())?.let { writes.trySend(Write(it)) } }
    suspend fun flushRecovery(): Boolean {
        val snapshot=game.recoverySnapshot(System.currentTimeMillis()) ?: return false
        val done=CompletableDeferred<Boolean>(); writes.send(Write(snapshot,done)); return done.await()
    }
}
