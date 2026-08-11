package com.baton.app.data.sync

import com.baton.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * M2-T7 Realtime subscription. Listens for `postgres_changes`
 * events on the `persons` and `instructions` tables and emits a
 * [Change] on a [SharedFlow] for the UI to react to.
 *
 *  - INSERT / UPDATE / DELETE on `persons` -> [Change.Persons]
 *  - INSERT / UPDATE / DELETE on `instructions` -> [Change.Instructions]
 *
 * RLS ensures we only see events for our own rows. The
 * subscription is per-table, one channel per table, kept open
 * for the lifetime of the [CoroutineScope] passed in. Pass the
 * application scope so the subscription survives Home tab
 * navigation; cancel by cancelling the scope.
 *
 * This is the cheapest M2 finding-test path: an instruction
 * created on device A emits a Realtime event; device B's
 * subscription receives it; the UI re-fetches; the new
 * instruction appears in the list within ~1s.
 *
 * M2-T6 (Room mirror) + M2-T8 (conflict resolution) are the next
 * steps in the M2 sync story. M2-T7 deliberately keeps the
 * read path on Supabase (no Room) so the change is small and
 * the multi-device e2e is fast to ship.
 */
class RealtimeSync(
    private val client: SupabaseClient,
    private val scope: CoroutineScope,
) {

    sealed class Change {
        data object Persons : Change()
        data object Instructions : Change()
    }

    private val _changes = MutableSharedFlow<Change>(
        replay = 0,
        extraBufferCapacity = 8,
    )
    val changes: SharedFlow<Change> = _changes.asSharedFlow()

    /**
     * Start the subscriptions. Idempotent — the channel names
     * are unique-per-URL, so two calls would subscribe twice.
     * Call [stop] first if you need to re-subscribe.
     */
    fun start() {
        scope.launch(Dispatchers.IO) {
            subscribeTable(
                table = "persons",
                onChange = { _changes.tryEmit(Change.Persons) },
            )
            subscribeTable(
                table = "instructions",
                onChange = { _changes.tryEmit(Change.Instructions) },
            )
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
        channel.subscribe()
        flow.onEach { action -> onChange(action) }
            .launchIn(scope)
    }
}
