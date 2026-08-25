package com.kaavalan.note.data.dev

import android.content.Context
import com.kaavalan.note.data.local.CaptureDao
import com.kaavalan.note.data.local.InstructionDao
import com.kaavalan.note.data.local.InstructionFtsDao
import com.kaavalan.note.data.local.InstructionTagDao
import com.kaavalan.note.data.local.PersonDao
import com.kaavalan.note.data.local.TagDao
import com.kaavalan.note.data.local.entities.CaptureEntity
import com.kaavalan.note.data.local.entities.InstructionEntity
import com.kaavalan.note.data.local.entities.InstructionFtsEntity
import com.kaavalan.note.data.local.entities.InstructionTagCrossRef
import com.kaavalan.note.data.local.entities.PersonEntity
import com.kaavalan.note.data.local.entities.TagEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.6.2: developer-only synthetic data loader. Reads
 * `assets/synthetic-data.json` and bulk-inserts the rows into Room
 * using direct DAO calls (we deliberately bypass the repositories
 * here so the fixture's UUIDs and timestamps are preserved
 * verbatim — the repositories would re-stamp both).
 *
 * **Scope.** This is private R&D. The Kaavalan app has a single
 * user (the project owner) and zero Supabase connectivity in
 * vault mode. The fixture exists to drive a UI/UX review on the
 * phone with realistic data without typing it in by hand.
 *
 * **Not exposed in release builds.** The [loadFromAssets] entry
 * point is called from a debug-only menu in Settings (gated on
 * `BuildConfig.DEBUG`); the release APK keeps the class but
 * never references it. The class is also reachable from
 * instrumentation tests via direct injection.
 *
 * **Idempotency.** The loader clears the four relevant tables
 * (persons, instructions, instructions_fts, captures, tags,
 * instruction_tags) before re-inserting. Calling it twice with
 * the same fixture is safe; calling it with two different
 * fixtures is also safe (the second one wins).
 *
 * **Edge-case data.** The fixture includes 12 intentional
 * hazards (title == body, very long rawText, emoji, trailing
 * whitespace, null rawText on a PHOTO capture, etc.) — see
 * `.sdd/synthetic-data/v1.6.2-fixture.json` and the README in
 * that directory. These are *deliberate* and are the whole
 * reason the fixture exists: to find UI bugs the happy-path
 * sample of 1 person + 1 instruction cannot.
 */
@Singleton
class FixtureLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val personDao: PersonDao,
    private val instructionDao: InstructionDao,
    private val instructionFtsDao: InstructionFtsDao,
    private val instructionTagDao: InstructionTagDao,
    private val captureDao: CaptureDao,
    private val tagDao: TagDao,
) {
    /**
     * v1.6.2: load `assets/synthetic-data.json` and replace the
     * local mirror contents. Returns a short report so the
     * caller (debug menu) can show "Loaded 12 people, 36
     * instructions, 7 captures, 12 tags" in a snackbar.
     *
     * v1.7.3 (P0-A): also writes the asset's `version` field
     * into SharedPreferences so the next-launch reseed-if-stale
     * check can compare. The "Clear & reload" button in Settings
     * still calls this directly to force a fresh load even when
     * the stored version matches.
     */
    suspend fun loadFromAssets(
        assetPath: String = "synthetic-data.json",
    ): LoadReport = withContext(Dispatchers.IO) {
        val raw = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val fixture = json.decodeFromString(Fixture.serializer(), raw)
        // Clear in FK-safe order. The schema doesn't declare
        // foreign keys (this is SQLite), but logical FKs exist
        // (instruction.personId -> person.id). Clearing
        // instructions first, then captures, then
        // instruction_tags, then tags, then persons is safe.
        instructionTagDao.deleteAll()
        instructionFtsDao.deleteAll()
        instructionDao.deleteAll()
        captureDao.deleteAll()
        tagDao.deleteAll()
        personDao.deleteAll()
        // Insert in topological order: persons, tags, then
        // instructions + FTS in lock-step, then instruction_tags,
        // then captures.
        personDao.upsertAll(fixture.persons.map { it.toEntity() })
        tagDao.upsertAll(fixture.tags.map { it.toEntity() })
        fixture.instructions.forEach { ins ->
            val entity = ins.toEntity()
            instructionDao.upsert(entity)
            // FTS row uses the main table's rowid; pull the
            // max to be safe (matches the pattern in
            // RoomInstructionRepository.create).
            val rowid = instructionFtsDao.maxInstructionRowid() ?: 0L
            instructionFtsDao.upsert(
                InstructionFtsEntity(
                    rowid = rowid,
                    title = entity.title,
                    rawText = entity.rawText,
                    personId = entity.personId,
                    capturedAt = entity.capturedAt,
                ),
            )
        }
        fixture.instructionTags.map { it.toCrossRef() }.also {
            if (it.isNotEmpty()) instructionTagDao.attachAll(it)
        }
        captureDao.upsertAll(fixture.captures.map { it.toEntity() })
        // v1.7.3: record the asset version we just loaded. Next
        // launch will compare against this and skip the reseed
        // if the user has the current fixture.
        fixturePrefs.edit().putInt(KEY_FIXTURE_VERSION, fixture.version).apply()
        LoadReport(
            persons = fixture.persons.size,
            instructions = fixture.instructions.size,
            captures = fixture.captures.size,
            tags = fixture.tags.size,
            worries = fixture.instructions.count { it.urgency != "normal" },
            instructionTags = fixture.instructionTags.size,
        )
    }

    /**
     * v1.7.3 (P0-A): on next app launch, if the asset's
     * `version` is strictly greater than the stored one, run a
     * full re-seed. This closes the gap where v1.7.2 fixed the
     * synthetic-data.json file but existing users' Room DBs
     * still had the v1.7.1 dates (year 3995).
     *
     * For a fresh install the stored version starts at 0 and
     * the asset version is 2, so the re-seed runs once on the
     * cold start. The clear+insert path is idempotent against
     * an empty DB so this is a no-op for the data (only
     * SharedPreferences is updated).
     *
     * For an existing v1.7.1/v1.7.2 user the stored version is
     * 0 (never set), the asset is 2, so the re-seed runs and
     * replaces the stale dates with the new ones. The user
     * sees the correct Worry box on their next Today scroll.
     *
     * Returns null when no reseed was needed. Logs at INFO when
     * a reseed fires so the rebuild path is visible in logcat.
     */
    suspend fun reseedIfStale(
        assetPath: String = "synthetic-data.json",
    ): LoadReport? = withContext(Dispatchers.IO) {
        val raw = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val fixture = json.decodeFromString(Fixture.serializer(), raw)
        val storedVersion = fixturePrefs.getInt(KEY_FIXTURE_VERSION, 0)
        if (storedVersion < fixture.version) {
            android.util.Log.i(
                "FixtureLoader",
                "stored fixture v$storedVersion < asset v${fixture.version}; auto-reseeding",
            )
            loadFromAssets(assetPath)
        } else {
            null
        }
    }

    @Serializable
    private data class Fixture(
        // v1.7.3 (P0-A): bump this whenever the asset's data
        // changes in a way that existing users need to pick up.
        // The value is read at load time; if `storedFixtureVersion`
        // (SharedPreferences) is strictly less than this, the
        // AppInitializer re-seeds the DB on next launch so the
        // user sees the new fixture without tapping "Clear &
        // reload" manually. The default of 1 keeps backward
        // compatibility with assets that don't carry a version
        // field at all (e.g. the v1.6.4 baseline).
        val version: Int = 1,
        val persons: List<PersonDto> = emptyList(),
        val instructions: List<InstructionDto> = emptyList(),
        val captures: List<CaptureDto> = emptyList(),
        val tags: List<TagDto> = emptyList(),
        val instructionTags: List<InstructionTagDto> = emptyList(),
    )

    @Serializable
    private data class PersonDto(
        val id: String,
        val name: String,
        val designation: String? = null,
        val station: String? = null,
        val phone: String? = null,
        val userId: String = "",
        val createdAt: String,
        val updatedAt: String,
        val isSensitive: Boolean = false,
        val syncStatus: String = "SYNCED",
        val tier: String = "Active",
        val cadenceOverrideDays: Int? = null,
        val lastInteractionAt: Long? = null,
        val vaultMode: String = "visible",
    )

    @Serializable
    private data class InstructionDto(
        val id: String,
        val personId: String? = null,
        val direction: String = "OUTGOING",
        val status: String,
        val source: String = "TEXT",
        val priority: String = "NORMAL",
        val title: String,
        val rawText: String,
        val dueAt: String? = null,
        val capturedAt: String,
        val createdAt: String,
        val updatedAt: String,
        val isSensitive: Boolean = false,
        val syncStatus: String = "SYNCED",
        val completedAt: String? = null,
        val droppedReason: String? = null,
        val nextActionAt: Long? = null,
        val caseType: String? = null,
        val urgency: String = "normal",
        val reviewAtEpochDay: Long? = null,
    )

    @Serializable
    private data class CaptureDto(
        val id: String,
        val mode: String,
        val rawText: String? = null,
        val audioUri: String? = null,
        val imageUri: String? = null,
        val processed: Boolean = false,
        val createdAt: String,
        val syncStatus: String = "SYNCED",
        val ocrText: String? = null,
        val calendarEventId: String? = null,
        val urgency: String = "normal",
        val reviewAtEpochDay: Long? = null,
    )

    @Serializable
    private data class TagDto(
        val id: String,
        val name: String,
        val kind: String,
        val color: String? = null,
        val usageCount: Int = 0,
        val lastUsedAt: String? = null,
        val userId: String = "",
        val createdAt: String,
        val updatedAt: String,
        val syncStatus: String = "SYNCED",
    )

    @Serializable
    private data class InstructionTagDto(
        val instructionId: String,
        val tagId: String,
    )

    private fun PersonDto.toEntity(): PersonEntity = PersonEntity(
        id = id,
        name = name,
        designation = designation,
        station = station,
        phone = phone,
        userId = userId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSensitive = isSensitive,
        syncStatus = syncStatus,
        tier = tier,
        cadenceOverrideDays = cadenceOverrideDays,
        lastInteractionAt = lastInteractionAt,
        vaultMode = vaultMode,
    )

    private fun InstructionDto.toEntity(): InstructionEntity = InstructionEntity(
        id = id,
        personId = personId,
        direction = direction,
        status = status,
        source = source,
        priority = priority,
        title = title,
        rawText = rawText,
        dueAt = dueAt,
        capturedAt = capturedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSensitive = isSensitive,
        syncStatus = syncStatus,
        completedAt = completedAt,
        droppedReason = droppedReason,
        nextActionAt = nextActionAt,
        caseType = caseType,
        urgency = urgency,
        reviewAtEpochDay = reviewAtEpochDay,
    )

    private fun CaptureDto.toEntity(): CaptureEntity = CaptureEntity(
        id = id,
        mode = mode,
        rawText = rawText,
        audioUri = audioUri,
        imageUri = imageUri,
        processed = processed,
        createdAt = createdAt,
        syncStatus = syncStatus,
        ocrText = ocrText,
        calendarEventId = calendarEventId,
        urgency = urgency,
        reviewAtEpochDay = reviewAtEpochDay,
    )

    private fun TagDto.toEntity(): TagEntity = TagEntity(
        id = id,
        name = name,
        kind = kind,
        color = color,
        usageCount = usageCount,
        lastUsedAt = lastUsedAt,
        userId = userId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus,
    )

    private fun InstructionTagDto.toCrossRef(): InstructionTagCrossRef =
        InstructionTagCrossRef(instructionId = instructionId, tagId = tagId)

    data class LoadReport(
        val persons: Int,
        val instructions: Int,
        val captures: Int,
        val tags: Int,
        val worries: Int,
        val instructionTags: Int,
    )

    companion object {
        // v1.7.3 (P0-A): SharedPreferences key for the last
        // loaded fixture version. Stored in a private prefs file
        // so the entry can be inspected via `adb shell run-as`
        // during drive-verify. The default of 0 (never set)
        // means "reseed on next launch" for fresh installs.
        private const val PREFS_NAME = "fixture_version"
        private const val KEY_FIXTURE_VERSION = "version"

        // ignoreUnknownKeys so the loader survives future
        // additions to the fixture shape; explicit
        // coerceInputValues=false so a missing required field
        // crashes loudly during dev (it's our fixture, we
        // control it).
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    private val fixturePrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }
}
