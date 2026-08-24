package com.baton.app.data.supabase

/**
 * v1.9.10 (Obs-1 fix): wrapper for the singleton
 * `io.github.jan.supabase.SupabaseClient`.
 *
 * The pre-v1.9.10 design had **four** repositories each calling
 * [buildSupabaseClient] in a field initializer. Each initializer
 * created a brand-new `SupabaseClient`, so at cold start the app
 * opened four parallel Realtime WebSocket connections, four HTTP
 * pools, and four auth state observers. The logcat showed four
 * "SupabaseClient created!" lines and four interleaved
 * "Trying again in 7s" retry loops on every transient network
 * blip — visible from the v1.9.8 audit's on-device capture as
 * `Supabase-Realtime: Error while trying to connect to realtime
 * websocket. Trying again in 7s` appearing once per client
 * (35 lines over 178 seconds in the refuted-finding evidence).
 *
 * v1.9.10 collapses this into a single Hilt-provided
 * [BatonSupabase] singleton. The underlying `SupabaseClient`
 * is built **lazily** on first access via [buildSupabaseClient]
 * so the KSP1 processor that backs Hilt never has to resolve
 * the `io.github.jan.supabase.SupabaseClient` AAR type at
 * `@Provides`-signature analysis time — see the [SupabaseModule]
 * class docstring for the original `error.NonExistentClass`
 * KSP failure mode that motivates the wrapper.
 *
 * **Why a plain Kotlin class, not a `data class` with a
 * `SupabaseClient` field.** A `data class` would also work
 * (KSP1 can resolve the `BatonSupabase` type), but it would
 * generate `equals` / `hashCode` / `copy` / `toString` on a
 * value that holds a live `SupabaseClient` — those would
 * cause subtle bugs in any consumer that puts a
 * [BatonSupabase] into a Hilt `MapKey` or `Set`. A plain
 * class is safer.
 *
 * Consumers should inject [BatonSupabase] and read [client]
 * to get the underlying `SupabaseClient`. The plugin handles
 * (`client.postgrest`, `client.auth`, etc.) are Kotlin
 * extension properties defined in the supabase-kt library —
 * import them in the consumer as needed.
 */
class BatonSupabase internal constructor(
    @PublishedApi internal val builder: () -> io.github.jan.supabase.SupabaseClient,
) {
    /**
     * The single [SupabaseClient] used by the whole app.
     * Built lazily on first access so the Hilt @Provides
     * never has to resolve the AAR type. Most consumers
     * should read this once and cache the plugin they need
     * (`client.postgrest`, `client.auth`, etc.).
     */
    val client: io.github.jan.supabase.SupabaseClient by lazy(builder)

    /**
     * **Internal** constructor used by [SupabaseModule.provideBatonSupabase].
     * Takes a builder lambda so the KSP processor never sees the
     * `io.github.jan.supabase.SupabaseClient` type in any
     * `@Provides` signature.
     */
    companion object {
        internal fun create(builder: () -> io.github.jan.supabase.SupabaseClient): BatonSupabase =
            BatonSupabase(builder)
    }
}
