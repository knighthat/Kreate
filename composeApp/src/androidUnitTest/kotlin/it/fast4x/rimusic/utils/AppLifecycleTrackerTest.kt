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

    /**
     * Ensure both [AppLifecycleTracker.isInForeground] and [AppLifecycleTracker.isInBackground]
     * return correct value when lifecycle's state is updated
     */
    @Test
    fun testStates() {
        assertFalse( AppLifecycleTracker.isInForeground() )
        assertTrue( AppLifecycleTracker.isInBackground() )

        AppLifecycleTracker.onStart( DummyLifecycleOwner )
        assertTrue( AppLifecycleTracker.isInForeground() )
        assertFalse( AppLifecycleTracker.isInBackground() )

        AppLifecycleTracker.onStop( DummyLifecycleOwner )
        assertFalse( AppLifecycleTracker.isInForeground() )
        assertTrue( AppLifecycleTracker.isInBackground() )
    }

    @Test
    fun initialState()  {
        assertFalse( AppLifecycleTracker.isInForeground() )
    }

    @Test
    fun onForeground()  {
        assertFalse( AppLifecycleTracker.isInForeground() )
        AppLifecycleTracker.onStart( DummyLifecycleOwner )
        assertTrue( AppLifecycleTracker.isInForeground() )
    }

    @Test
    fun onBackground()  {
        assertFalse( AppLifecycleTracker.isInForeground() )
        AppLifecycleTracker.onStart( DummyLifecycleOwner )
        assertTrue( AppLifecycleTracker.isInForeground() )
        AppLifecycleTracker.onStop( DummyLifecycleOwner )
        assertFalse( AppLifecycleTracker.isInForeground() )
    }

    @Test
    fun cycleCheck() {
        repeat(5) {
            assertFalse( AppLifecycleTracker.isInForeground() )
            AppLifecycleTracker.onStart( DummyLifecycleOwner )
            assertTrue( AppLifecycleTracker.isInForeground() )
            AppLifecycleTracker.onStop( DummyLifecycleOwner )
            assertFalse( AppLifecycleTracker.isInForeground() )
        }
    }
}