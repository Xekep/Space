package com.xekep.space.storage

import android.os.Build
import android.system.Os
import android.system.OsConstants
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** APK from the release AAB on a dedicated 16 KB image; no installation or data reset. */
class PageSizeCompatibilityTest {
    @Test fun libraryLoadsAndPathOperationsWorkWithSixteenKilobytePages() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pageSizeCheck") == "true")
        assertEquals(16384L,Os.sysconf(OsConstants._SC_PAGESIZE))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        // Force linker validation even though API 34+ path iteration uses the platform backend.
        System.load(context.applicationInfo.sourceDir+"!/lib/"+Build.SUPPORTED_ABIS.first()+"/libandroidx.graphics.path.so")
        val path=android.graphics.Path().apply { moveTo(0f,0f);lineTo(20f,30f);quadTo(40f,0f,60f,40f);close() }
        check(Build.VERSION.SDK_INT>=35)
        val iterator=path.pathIterator
        var count=0
        while (iterator.hasNext()) { iterator.next();count++;assertTrue(count<10) }
        assertTrue(count>=3)
    }
}
