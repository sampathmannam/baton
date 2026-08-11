package com.baton.app.data.supabase

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import javax.inject.Singleton

/**
 * Hilt module that provides the singleton [HttpClient] used by the Supabase
 * client.
 *
 * **Why OkHttp and not the Android engine:** the M2-T7 Realtime
 * plugin needs WebSockets; the Android engine does not advertise
 * the `WebSocketCapability` and throws at install time. OkHttp
 * supports WebSockets out of the box and is already a transitive
 * dep (ModelManager uses it to download the GGUF model). The
 * [WebSockets] plugin is installed explicitly so the Ktor client
 * reports the capability regardless of which engine is underneath.
 *
 * **Why no [SupabaseClient] binding here:** Hilt's KSP1 processor cannot
 * resolve KMP AAR classes (e.g. `io.github.jan.supabase.SupabaseClient`) at
 * `@Provides`-signature analysis time — KSP only loads Kotlin sources, not
 * AAR bytecode, for symbol resolution. Putting the type in a `@Provides`
 * parameter list surfaces as `error.NonExistentClass`. The [SupabaseClient]
 * is built lazily inside the consumers (see [SupabasePersonRepository]),
 * where the type only needs to be resolvable by the Kotlin compiler — not
 * by Hilt's KSP processor. The result: one client per app, no Hilt graph
 * conflict.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(WebSockets)
    }
}
