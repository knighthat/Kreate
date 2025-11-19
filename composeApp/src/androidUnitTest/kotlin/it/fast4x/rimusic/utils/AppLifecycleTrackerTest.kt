package it.fast4x.rimusic.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLifecycleTrackerTest {
    private object DummyLifecycleOwner : LifecycleOwner {
        override val lifecycle: Lifecycle
            get() = throw UnsupportedOperationException("Not needed for this test")
    }

    @BeforeTest
    fun setup() {
        AppLifecycleTracker.onStop(DummyLifecycleOwner)
    }

    @Test
    fun initialState() {
        assertFalse(AppLifecycleTracker.appRunningInForeground)
    }

    @Test
    fun onForeground() {
        assertFalse(AppLifecycleTracker.appRunningInForeground)
        AppLifecycleTracker.onStart(DummyLifecycleOwner)
        assertTrue(AppLifecycleTracker.appRunningInForeground)
    }

    @Test
    fun onBackground() {
        assertFalse(AppLifecycleTracker.appRunningInForeground)
        AppLifecycleTracker.onStart(DummyLifecycleOwner)
        assertTrue(AppLifecycleTracker.appRunningInForeground)
        AppLifecycleTracker.onStop(DummyLifecycleOwner)
        assertFalse(AppLifecycleTracker.appRunningInForeground)
    }

    @Test
    fun cycleCheck() {
        repeat(5) {
            assertFalse(AppLifecycleTracker.appRunningInForeground)
            AppLifecycleTracker.onStart(DummyLifecycleOwner)
            assertTrue(AppLifecycleTracker.appRunningInForeground)
            AppLifecycleTracker.onStop(DummyLifecycleOwner)
            assertFalse(AppLifecycleTracker.appRunningInForeground)
        }
    }
}