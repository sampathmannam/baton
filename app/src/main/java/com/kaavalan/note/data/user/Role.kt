package com.kaavalan.note.data.user

/**
 * v1.8.0 (PROD-READINESS-P2-#3): the per-officer role.
 *
 * Used to gate write APIs in the v1.8.0 pilot. The
 * default (single-user) build binds every officer
 * to [SENIOR_OFFICER]; a multi-officer build sets the
 * role per user at provisioning time.
 *
 * The role model is **UI-side only** in the v1.x
 * local-only build. A future cloud-sync build
 * (out of scope for v1.8.0) re-enforces the role at
 * the server (RLS policies in the SQL schema).
 * Locally the role is a hint; a [READONLY] officer
 * can still execute the `update` SQL directly. The
 * v1.8.0 trade-off is "the role gates the UI, not
 * the DB" — acceptable for a private R&D pilot, not
 * for a real production deployment.
 */
enum class Role(val displayLabel: String) {
    /**
     * The station head. Full read + write + admin
     * (provisioning, branding override, audit-chain
     * redaction, retention overrides).
     */
    ADMIN("Admin"),

    /**
     * The default role for a single-officer build.
     * Full read + write; no admin actions.
     */
    SENIOR_OFFICER("Senior officer"),

    /**
     * The standard officer. Full read + write; no
     * admin actions.
     */
    OFFICER("Officer"),

    /**
     * A trainee / observer. Read-only; the UI hides
     * the "Save", "Mark done", "Add person" buttons.
     */
    READONLY("Read-only"),

    ;

    /**
     * Parse a string from the [UserEntity.role] column.
     * Falls back to [SENIOR_OFFICER] on unknown values
     * so a future schema addition cannot lock out a
     * user with an old APK.
     */
    companion object {
        fun fromStringOrDefault(value: String?): Role =
            value?.let { runCatching { valueOf(it) }.getOrNull() } ?: SENIOR_OFFICER
    }
}
