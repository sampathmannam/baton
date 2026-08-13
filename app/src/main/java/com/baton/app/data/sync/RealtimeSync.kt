package com.baton.app.data.sync

import com.baton.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * M2-T7 Realtime subscription. Listens for `postgres_changes`
 * events on the `persons`, `instructions`, and `tags` tables and
 * emits a [Change] on a [SharedFlow] for the UI to react to.
 *
 *  - INSERT / UPDATE / DELETE on `persons` -> [Change.Persons]
 *  - INSERT / UPDATE / DELETE on `instructions` -> [Change.Instructions]
 *  - INSERT / UPDATE / DELETE on `tags` -> [Change.Tags]
 *
 * RLS ensures we only see events for our own rows. The
 * subscription is per-table, one channel per table, kept open
 * for the lifetime of the [CoroutineScope] passed in. Pass the
 * application scope so the subscription survives Home tab
 * navigation; cancel via [stop].
 *
 * v1.2 BATON-WIRE-002 fix: [stop] now exists and is called on
 * sign-out. Without it, the previous user's JWT stayed on the
 * WebSocket for the rest of the process lifetime, even after
 * `client.auth.signOut()`. Now sign-out closes the channels and
 * the WebSocket; the next sign-in calls [start] again with a
 * fresh subscription under the new token.
 */
class RealtimeSync(
    private val client: SupabaseClient,
    private val scope: CoroutineScope,
) {

    sealed class Change {
        data object Persons : Change()
        data object Instructions : Change()
        // M3-T7: tags table is in the supabase_realtime publication
        // (it was added at table creation by migration 0001). When any
        // tag row changes, HomeViewModel pulls the full list and
        // re-renders the tag picker.
        data object Tags : Change()
    }

    private val _changes = MutableSharedFlow<Change>(
        replay = 0,
        extraBufferCapacity = 8,
    )
    val changes: SharedFlow<Change> = _changes.asSharedFlow()

    // v1.2: track active channels + the start() job so stop() can
    // tear them down cleanly on sign-out. The previous version
    // never cancelled — the WebSocket stayed open with the
    // previous user's JWT.
    private val activeChannels = mutableListOf<io.github.jan.supabase.realtime.RealtimeChannel>()
    private val startJob = AtomicReference<Job?>(null)

    /**
     * Start the subscriptions. Idempotent — the channel names
     * are unique-per-URL, so two calls would subscribe twice.
     * Call [stop] first if you need to re-subscribe.
     */
    fun start() {
        if (startJob.get()?.isActive == true) return
        startJob.set(
            scope.launch(Dispatchers.IO) {
                subscribeTable(
                    table = "persons",
                    onChange = { _changes.tryEmit(Change.Persons) },
                )
                subscribeTable(
                    table = "instructions",
                    onChange = { _changes.tryEmit(Change.Instructions) },
                )
                // M3-T7: also subscribe to tags so the picker refreshes
                // when the user creates a tag on another device.
                subscribeTable(
                    table = "tags",
                    onChange = { _changes.tryEmit(Change.Tags) },
                )
            },
        )
    }

    /**
     * v1.2 BATON-WIRE-002 / BUG-AUTH-002: close all channels and
     * the Realtime WebSocket. Called from the sign-out path
     * (`SettingsViewModel.signOut` via a new `AuthRealtimeCloser`
     * helper or directly by the Hilt module). Idempotent.
     *
     * `unsubscribe()` and `client.realtime.close()` are suspend
     * functions in supabase-kt 3.1.1, so we launch on the
     * application scope. The launch is fire-and-forget — sign-out
     * is best-effort; the next sign-in re-creates everything.
     */
    fun stop() {
        startJob.get()?.cancel()
        startJob.set(null)
        scope.launch(Dispatchers.IO) {
            // Unsubscribe each channel; the WebSocket is closed
            // when the last channel unsubscribes (supabase-kt's
            // Realtime behaviour).
            activeChannels.forEach { channel ->
                runCatching { channel.unsubscribe() }
            }
            activeChannels.clear()
            // Also close the Realtime plugin to drop the WebSocket
            // even if a shared engine keeps it open elsewhere.
            runCatching { client.realtime.close() }
        }
    }

    private suspend fun subscribeTable(
        table: String,
        onChange: (PostgresAction) -> Unit,
    ) {
        val channelName = "baton-$table-${BuildConfig.SUPABASE_URL.hashCode()}"
        val channel = client.realtime.channel(channelName)
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            this.table = table
        }
        activeChannels.add(channel)
        channel.subscribe()
        flow.onEach { action -> onChange(action) }
            .launchIn(scope)
    }
}
