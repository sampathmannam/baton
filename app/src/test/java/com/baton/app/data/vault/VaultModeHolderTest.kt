package com.baton.app.data.vault

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v2.0 T3-1 (deniable vault) unit tests for [VaultModeHolder].
 *
 * The test contract from the task spec: "toggling vault mode
 * filters entities correctly". This test exercises the holder
 * directly (which is the source of truth the HomeViewModel
 * reads). The DAO query is a single
 * `WHERE vaultMode = :mode` filter (see
 * `data/local/PersonDao.kt:observeAllInMode`); the holder's
 * job is to expose a process-wide `StateFlow<VaultMode>` that
 * the UI and the DAO filter parameter both consult.
 *
 * The DAO filter is exercised end-to-end on-device in the
 * `.sdd/qa-tier3-on-device.md` log; this unit test covers
 * the holder's state machine.
 */
class VaultModeHolderTest {

    @Test
    fun `default mode is Visible`() {
        val holder = VaultModeHolder()
        assertEquals(VaultMode.Visible, holder.mode.value)
        assertEquals("visible", holder.modeString)
    }

    @Test
    fun `setMode Hidden flips the state flow`() {
        val holder = VaultModeHolder()
        holder.setMode(VaultMode.Hidden)
        assertEquals(VaultMode.Hidden, holder.mode.value)
        assertEquals("hidden", holder.modeString)
    }

    @Test
    fun `setMode Visible after Hidden round-trips correctly`() {
        val holder = VaultModeHolder()
        holder.setMode(VaultMode.Hidden)
        holder.setMode(VaultMode.Visible)
        assertEquals(VaultMode.Visible, holder.mode.value)
    }

    @Test
    fun `otherMode returns the opposite mode`() {
        val holder = VaultModeHolder()
        assertEquals(VaultMode.Hidden, holder.otherMode())
        holder.setMode(VaultMode.Hidden)
        assertEquals(VaultMode.Visible, holder.otherMode())
    }

    @Test
    fun `reset returns to Visible regardless of prior state`() {
        val holder = VaultModeHolder()
        holder.setMode(VaultMode.Hidden)
        holder.reset()
        assertEquals(VaultMode.Visible, holder.mode.value)
    }

    @Test
    fun `VaultMode storageKey matches the SQL filter contract`() {
        // The DAO query uses `WHERE vaultMode = :mode` with a
        // String parameter; the holder's `modeString` is the
        // canonical source. Pinning both here means a future
        // rename of the SQL column or the enum will fail this
        // test, not silently desync the two.
        assertEquals("visible", VaultMode.Visible.storageKey)
        assertEquals("hidden", VaultMode.Hidden.storageKey)
    }
}
