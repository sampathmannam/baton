package com.baton.app.data.retention

/**
 * v1.8.0 (PROD-READINESS-P2-#5): the retention policy.
 *
 * **Source.** Per BNSS §152A and the state IT Act the
 * police-side data categories have different minimum
 * retention periods. The v1.8.0 defaults are:
 *
 *  - **audit_chain_events**: 7 years (legal record of
 *    access). After 7 years the payload is redacted (the
 *    hash chain is preserved, the JSON contents are wiped)
 *    so a forensic reader can still verify the chain
 *    integrity but cannot read the historical actions.
 *  - **captures**: 3 years (raw photos + voice audio).
 *  - **important_dates**: 3 years.
 *  - **instructions**: 7 years (these are the user's
 *    active casework; deleting them earlier would lose
 *    audit-trail context).
 *
 * The v1.8.0 trade-off is "use BNSS defaults" — a
 * department with a stricter internal policy overrides
 * the values in a future Settings → Compliance tab.
 * For now the values are compile-time constants; the
 * RetentionWorker reads them.
 */
data class RetentionPolicy(
    val auditChainEventsYears: Int = 7,
    val capturesYears: Int = 3,
    val importantDatesYears: Int = 3,
    val instructionsYears: Int = 7,
) {
    /**
     * Compute the "redact before" epoch-millis for a
     * given table. The RetentionWorker deletes
     * (or redacts) rows whose createdAtMs is older
     * than this.
     */
    fun redactBeforeMs(table: RetentionTable, nowMs: Long): Long =
        nowMs - when (table) {
            RetentionTable.AUDIT_CHAIN_EVENTS -> auditChainEventsYears * MS_PER_YEAR
            RetentionTable.CAPTURES -> capturesYears * MS_PER_YEAR
            RetentionTable.IMPORTANT_DATES -> importantDatesYears * MS_PER_YEAR
            RetentionTable.INSTRUCTIONS -> instructionsYears * MS_PER_YEAR
        }

    companion object {
        const val MS_PER_YEAR: Long = 365L * 24L * 60L * 60L * 1000L

        val DEFAULT = RetentionPolicy()
    }
}

enum class RetentionTable {
    AUDIT_CHAIN_EVENTS,
    CAPTURES,
    IMPORTANT_DATES,
    INSTRUCTIONS,
}
