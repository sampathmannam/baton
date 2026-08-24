package com.kaavalan.note.data.user

import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

/**
 * v1.8.0 (PROD-READINESS-P2-#3): the user-bootstrap
 * helper. On a fresh install (or a v14->v15 upgrade)
 * the [users] table is empty; this helper inserts the
 * single device-owner row on the first observation.
 *
 * The v1.8.0 trade-off is "exactly one user, the
 * device owner, role = SENIOR_OFFICER" — a
 * multi-officer pilot sets up the additional users
 * at provisioning time (a future Settings → Provisioning
 * tab; out of scope for v1.8.0).
 */
@Singleton
class UserBootstrap @Inject constructor(
    private val userDao: UserDao,
) {

    /**
     * Ensure the device-owner row exists. Idempotent
     * — a no-op if the row is already present. Called
     * by [com.kaavalan.note.BatonApplication.onCreate] so
     * the row is in place before any other code reads
     * the [UserDao].
     *
     * The device owner id is a client-generated UUID;
     * a multi-officer pilot overrides this at
     * provisioning time (out of scope for v1.8.0).
     */
    suspend fun ensureDeviceOwner() {
        if (userDao.deviceOwner() != null) return
        val deviceUuid = UUID.randomUUID().toString()
        userDao.upsert(
            UserEntity(
                id = deviceUuid,
                displayName = "Device owner",
                role = Role.SENIOR_OFFICER.name,
                deviceOwner = true,
                createdAt = java.time.Instant.now().toString(),
            )
        )
    }
}
