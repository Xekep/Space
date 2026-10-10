package com.xekep.space.storage

import android.app.ActivityManager
import android.content.Context

enum class GraphicsQuality { Auto, Full, Economy }
fun GraphicsQuality.economy(context: Context): Boolean = when (this) {
    GraphicsQuality.Full -> false
    GraphicsQuality.Economy -> true
    GraphicsQuality.Auto -> {
        val manager=context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info=ActivityManager.MemoryInfo(); manager?.getMemoryInfo(info)
        manager?.isLowRamDevice == true || info.totalMem in 1..3_221_225_472L
    }
}
