package com.baton.app.data.local.entities

/**
 * String constants for the `syncStatus` column on every Room entity
 * that mirrors a Supabase table. Centralised so a typo can't pass
 * review unnoticed.
 */
object SyncStatus {
    const val SYNCED = "SYNCED"
    const val PENDING_INSERT = "PENDING_INSERT"
    const val PENDING_UPDATE = "PENDING_UPDATE"
    const val PENDING_DELETE = "PENDING_DELETE"
}
