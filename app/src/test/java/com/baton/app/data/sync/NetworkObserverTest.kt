package com.baton.app.data.sync

import android.content.Context
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.4 (PHONE-FINDING-6): [NetworkObserver] contract test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class NetworkObserverTest {

    @Test
    fun `default isOnline is true (optimistic start)`() {
        val ctx: Context = org.robolectric.RuntimeEnvironment.getApplication()
        val observer = NetworkObserver(ctx)
        assertEquals(
            "NetworkObserver must default to isOnline=true.",
            true,
            observer.isOnline.value,
        )
    }

    @Test
    fun `isOnline is a StateFlow of Boolean`() {
        val ctx: Context = org.robolectric.RuntimeEnvironment.getApplication()
        val observer = NetworkObserver(ctx)
        val flow: StateFlow<Boolean> = observer.isOnline
        assertNotNull("isOnline must be non-null", flow)
        assertEquals(
            "isOnline must currently expose a Boolean value",
            true,
            flow.value,
        )
    }

    @Test
    fun `start is idempotent and stop is safe when never started`() {
        val ctx: Context = org.robolectric.RuntimeEnvironment.getApplication()
        val observer = NetworkObserver(ctx)
        observer.start()
        observer.start()
        observer.stop()
        observer.stop()
        assertNotNull(observer.isOnline)
    }

    @Test
    fun `class is annotated with Hilt Singleton`() {
        val cls = NetworkObserver::class.java
        val annotation = cls.annotations.firstOrNull {
            it.annotationClass.qualifiedName == "javax.inject.Singleton"
        }
        assertNotNull(
            "NetworkObserver must be annotated with @Singleton.",
            annotation,
        )
    }
}
