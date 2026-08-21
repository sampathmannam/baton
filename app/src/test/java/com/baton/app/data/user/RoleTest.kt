package com.baton.app.data.user

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.8.0 (PROD-READINESS-P2-#3): the role-enum test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoleTest {

    @Test
    fun `fromStringOrDefault returns the parsed value for a known string`() {
        assertEquals(Role.ADMIN, Role.fromStringOrDefault("ADMIN"))
        assertEquals(Role.SENIOR_OFFICER, Role.fromStringOrDefault("SENIOR_OFFICER"))
        assertEquals(Role.OFFICER, Role.fromStringOrDefault("OFFICER"))
        assertEquals(Role.READONLY, Role.fromStringOrDefault("READONLY"))
    }

    @Test
    fun `fromStringOrDefault falls back to SENIOR_OFFICER on unknown value`() {
        // A future schema addition cannot lock out a
        // user with an old APK; the unknown string
        // falls back to the highest-privilege role
        // (the v1.8.0 single-user default).
        assertEquals(Role.SENIOR_OFFICER, Role.fromStringOrDefault("SUPER_ADMIN"))
        assertEquals(Role.SENIOR_OFFICER, Role.fromStringOrDefault(""))
        assertEquals(Role.SENIOR_OFFICER, Role.fromStringOrDefault(null))
    }

    @Test
    fun `every role has a display label`() {
        for (r in Role.values()) {
            assert(r.displayLabel.isNotBlank()) { "$r has blank label" }
        }
    }

    @Test
    fun `Role values contains exactly the four documented variants`() {
        assertEquals(4, Role.values().size)
    }
}
