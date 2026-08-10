# Baton — Design Specification

**Status:** Final draft, awaiting user approval
**Date:** 2026-08-10
**Author:** Mavis (with Sampath M)
**Repo:** `github.com/sampathmannam/baton` (private)

---

## 1. What this is

Baton is a native Android (Kotlin/Compose) app for an IPS officer (or any coordination-heavy role) with ADHD. It tracks instructions flowing **in** from superiors and **out** to subordinates, with a single note-bar capture flow, on-device AI for extraction, multi-device sync via Supabase, and a cloud MCP server for desktop tools.

The name comes from the police baton: a symbol of authority, and the thing you pass from person to person.

## 2. The problem it solves

Current productivity apps fail people with ADHD because they assume the user can:

- feel time passing (you can't — time blindness is clinical)
- tolerate a red "overdue" badge as motivation (it triggers shame and avoidance)
- hold 40 items in working memory (75-81% of ADHD adults have impaired working memory; Kofler et al. 2020)
- remember to open the app (you won't — out of sight, out of mind)
- do the setup ritual, the weekly review, and the consistent logging (78% of ADHD adults abandon 3+ apps/year; ADHD Foundation 2023)

A working IPS officer gets instructions from a dozen people, gives instructions to a dozen more, is in meetings, on calls, and on the move. Baton is built for *that*.

## 3. Design principles (non-negotiable, applied at the component level)

1. **One next action.** No "what do I do now?" screens. Drill-down only.
2. **Show less, not more.** Tabs = 3. Capture is always one tap away.
3. **"Carried over", never "overdue."** No red badges, no streaks, no shame. Language copy is reviewed.
4. **Capture in < 5 seconds.** Measured. CI fails if it regresses.
5. **Forgive inconsistency.** Skip the review for a month → still works, still calm, still has your data.
6. **Energy-aware.** Reads MindAnchor's state, dials down when you're low.
7. **External scaffolding, not rigid.** Suggestions, not diktats. The app guides; the user decides.

---

## 4. Data model

The Postgres schema (cloud, source of truth) and the local Room/SQLCipher schema (Android, mirror) are identical.

### 4.1 Persons

```sql
CREATE TABLE persons (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,
  designation   TEXT,  -- SP, DSP, SHO, IO, Addl SP, Inspector, etc.
  station       TEXT,  -- Bandipora, Srinagar, etc.
  phone         TEXT,
  notes         TEXT,
  avatar_url    TEXT,
  is_sensitive  BOOLEAN DEFAULT false,  -- if true, this person + their instructions stay local-only
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ,
  UNIQUE(user_id, name, designation, station)  -- dedupes on capture
);

CREATE INDEX persons_user_idx ON persons(user_id) WHERE deleted_at IS NULL;
```

### 4.2 Instructions

```sql
CREATE TYPE instruction_direction AS ENUM ('INCOMING', 'OUTGOING', 'SELF');
CREATE TYPE instruction_status AS ENUM (
  'OPEN',              -- just captured, no one acted
  'ACK_PENDING',       -- sent to subordinate, waiting for ack
  'IN_PROGRESS',       -- someone is working on it
  'WAITING_ON_OTHER',  -- blocked on someone else
  'DONE',              -- closed
  'CARRIED_OVER',      -- moved to today silently
  'DROPPED'            -- explicitly cancelled, with reason
);
CREATE TYPE instruction_source AS ENUM ('VOICE', 'TEXT', 'PHOTO', 'MCP');
CREATE TYPE instruction_priority AS ENUM ('LOW', 'NORMAL', 'HIGH');

CREATE TABLE instructions (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  person_id       UUID REFERENCES persons(id) ON DELETE SET NULL,
  direction       instruction_direction NOT NULL,
  status          instruction_status NOT NULL DEFAULT 'OPEN',
  source          instruction_source NOT NULL,
  priority        instruction_priority NOT NULL DEFAULT 'NORMAL',
  title           TEXT NOT NULL,           -- AI-generated, 5-7 words
  raw_text        TEXT NOT NULL,           -- original capture verbatim
  due_at          TIMESTAMPTZ,
  captured_at     TIMESTAMPTZ NOT NULL,    -- when it happened in real world
  last_nudged_at  TIMESTAMPTZ,
  completed_at    TIMESTAMPTZ,
  dropped_reason  TEXT,
  is_sensitive    BOOLEAN DEFAULT false,   -- true: row never syncs to Supabase; stays in local DB only
  -- Auto-extracted fields (the LLM's output)
  extracted_fir   TEXT,                    -- e.g., "FIR 47/2026"
  extracted_due_text TEXT,                 -- e.g., "Friday EOD", preserved for display
  location_text   TEXT,                    -- e.g., "Bandipora"
  -- Audit
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at      TIMESTAMPTZ
);

CREATE INDEX instructions_user_status_idx
  ON instructions(user_id, status) WHERE deleted_at IS NULL;
CREATE INDEX instructions_user_person_idx
  ON instructions(user_id, person_id) WHERE deleted_at IS NULL;
CREATE INDEX instructions_user_due_idx
  ON instructions(user_id, due_at) WHERE deleted_at IS NULL;
CREATE INDEX instructions_user_priority_due_idx
  ON instructions(user_id, priority DESC, due_at ASC NULLS LAST) WHERE deleted_at IS NULL;
```

### 4.3 Tags (first-class)

```sql
CREATE TYPE tag_kind AS ENUM ('PERSON', 'DESIGNATION', 'STATION', 'CASE', 'FIR', 'PRIORITY', 'FREE');

CREATE TABLE tags (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,
  kind          tag_kind NOT NULL DEFAULT 'FREE',
  color         TEXT,                      -- hex string
  usage_count   INT NOT NULL DEFAULT 0,
  last_used_at  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(user_id, name, kind)
);

CREATE TABLE instruction_tags (
  instruction_id UUID NOT NULL REFERENCES instructions(id) ON DELETE CASCADE,
  tag_id         UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
  PRIMARY KEY (instruction_id, tag_id)
);
```

Tags are auto-created on first sighting of a person, designation, station, FIR number, or free-form `#tag`. The `kind` column drives UI coloring (auto-extracted = colored dot; free = plain).

### 4.4 Events (immutable audit log)

```sql
CREATE TABLE events (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  instruction_id  UUID NOT NULL REFERENCES instructions(id) ON DELETE CASCADE,
  user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  type            TEXT NOT NULL,  -- CREATED, STATUS_CHANGED, NUDGE_SENT, ACKNOWLEDGED, COMPLETED, DROPPED, EDITED, NUDGED_BACK
  payload         JSONB,          -- { from, to, actor, draftId, ... }
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Append-only. No updates, no deletes. The "measure before you claim" trail.
```

### 4.5 Captures (raw inputs, before they become instructions)

```sql
CREATE TABLE captures (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  mode            instruction_source NOT NULL,
  raw_text        TEXT,           -- Whisper output, typed text, or OCR text
  audio_uri       TEXT,           -- local file (Supabase Storage for cloud mirror)
  image_uri       TEXT,
  processed       BOOLEAN NOT NULL DEFAULT false,
  extracted_instruction_ids UUID[],  -- populated by the LLM
  llm_model       TEXT,           -- e.g., "qwen3-1.7b-q4"
  llm_latency_ms  INT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

A single capture can produce 1+ instructions (e.g., "Met SP at 4. He wants the Bandipora report by Friday. Also follow up with DSP on the protest case." → 2 instructions).

### 4.6 NudgeDrafts (AI-drafted messages, sent)

```sql
CREATE TABLE nudge_drafts (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  instruction_id  UUID NOT NULL REFERENCES instructions(id) ON DELETE CASCADE,
  user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  draft_text      TEXT NOT NULL,
  status          TEXT NOT NULL DEFAULT 'DRAFT',  -- DRAFT, EDITED, SENT, CANCELLED
  sent_via        TEXT,                            -- WHATSAPP, SMS, COPY
  sent_at         TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 4.7 DailyBriefs (morning + evening snapshots)

```sql
CREATE TABLE daily_briefs (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  type          TEXT NOT NULL,  -- MORNING, EVENING
  brief_date    DATE NOT NULL,
  content       JSONB NOT NULL,  -- { needsYouToday: [...], waitingOnOthers: [...], carriedOver: [...] }
  generated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  viewed_at     TIMESTAMPTZ,
  UNIQUE(user_id, type, brief_date)
);
```

### 4.8 AppState (cross-app state, shared with MindAnchor)

```sql
CREATE TABLE app_state (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  source        TEXT NOT NULL,  -- BATON, MINDANCHOR
  key           TEXT NOT NULL,  -- e.g., "energy_state", "sunset_mode", "batching_rules"
  value         JSONB NOT NULL,
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(user_id, source, key)
);
```

### 4.9 SyncConflicts (logged, not silent)

```sql
CREATE TABLE sync_conflicts (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  table_name      TEXT NOT NULL,
  row_id          UUID NOT NULL,
  local_payload   JSONB,
  remote_payload  JSONB,
  resolution      TEXT NOT NULL,  -- LOCAL_WON, REMOTE_WON, MERGED
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 4.10 Settings (per-user, key-value)

```sql
CREATE TABLE settings (
  user_id                 UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  brief_time              TIME NOT NULL DEFAULT '07:30',
  review_time             TIME NOT NULL DEFAULT '20:30',
  nudge_threshold_days    INT NOT NULL DEFAULT 3,
  llm_model               TEXT NOT NULL DEFAULT 'qwen3-1.7b-q4',
  voice_language          TEXT NOT NULL DEFAULT 'en',
  mindanchor_enabled      BOOLEAN NOT NULL DEFAULT false,
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 5. Architecture

```
┌──────────────────────┐         ┌──────────────────────────┐
│  Android (Kotlin)    │         │  Supabase (cloud)        │
│  ─────────────       │  HTTPS  │  ──────────────          │
│  UI (Compose)        │ ◀──────▶│  Postgres (RLS-enforced) │
│  Single note bar     │         │  Auth (email + PKCE)     │
│  Whisper.cpp (JNI)   │         │  Storage (audio/photo)   │
│  llama.cpp (JNI)     │         │  Realtime (multi-dev)    │
│  ML Kit (OCR)        │         │  Edge Functions:         │
│  WorkManager         │         │   • Brief scheduler (cron)│
│  Room + SQLCipher    │         │   • Cloud MCP server      │
│  (local mirror)      │         │   • Push notifications   │
└──────────────────────┘         └──────────────────────────┘
        ▲                                     ▲
        │                                     │
        │             localhost/stdio         │
        ▼                                     │
┌──────────────────────┐                      │
│  MindAnchor (Android)│                      │
│  (opt-in shared      │   ┌──────────────────┴───────────┐
│   crypto module +    │   │  MCP clients (e.g. Claude    │
│   AppState IPC)      │   │  Desktop) — read Baton data, │
└──────────────────────┘   │  trigger captures, draft     │
                          │  nudges                       │
                          └──────────────────────────────┘
```

**Three planes, all on-device except the cloud data plane:**

1. **UI plane** — Jetpack Compose, Material 3. Three bottom tabs: Home (people), Today (brief), Settings. The single note bar floats above all of them.
2. **AI plane (on-device only)** — Whisper.cpp (JNI) for STT, llama.cpp (JNI) for LLM, ML Kit Text Recognition for OCR. No API calls. No third-party AI ever sees the data.
3. **Data plane** — Supabase Postgres (RLS-enforced, source of truth), Storage for audio/photo, Realtime for cross-device sync, Edge Functions for scheduled brief generation and the cloud MCP server.

The local Room/SQLCipher DB is a write-through mirror; the sync engine handles conflict resolution (last-write-wins on `updated_at`, with conflicts logged to `sync_conflicts`).

---

## 6. Component layout

```
baton/
├── app/                         # Main Android app
│   ├── src/main/...             # UI + capture + sync
│   └── src/test/...             # Unit + UI tests
├── ai/                          # On-device AI
│   ├── llama/                   # llama.cpp JNI wrapper
│   ├── whisper/                 # Whisper.cpp JNI wrapper
│   └── ocr/                     # ML Kit OCR wrapper
├── data/                        # Persistence + sync
│   ├── db/                      # Room/SQLCipher local DB
│   ├── sync/                    # Supabase sync engine
│   └── mcp/                     # MCP client (for ingest later)
├── features/
│   ├── capture/                 # Single note bar, voice/text/photo flow
│   ├── people/                  # Person registry + timeline
│   ├── brief/                   # Morning brief + evening review
│   ├── nudge/                   # AI-drafted nudge flow
│   └── tags/                    # Tag management screen
├── shared/
│   ├── crypto/                  # Argon2id + AES-GCM (shared with MindAnchor)
│   ├── ui/                      # Design system, Compose components
│   └── time/                    # "Carried over" logic
└── server/                      # Supabase project
    ├── functions/               # Edge Functions (brief scheduler, MCP server)
    └── migrations/              # Postgres migrations
```

---

## 7. Capture flow

The single note bar is at the bottom of every screen. Tap, speak/type/snap, get out. No multiple forms.

### 7.1 Voice path

1. User taps mic icon (or lock-screen widget, or quick-settings tile). `CaptureService` starts as a `FOREGROUND_SERVICE_TYPE_MICROPHONE` foreground service — required for API 30+ so the mic survives the app being backgrounded.
2. AudioRecord streams 16kHz mono PCM into a ring buffer in native code.
3. User taps stop (or releases a hold). The accumulated PCM is sent to Whisper.cpp via direct JNI push (S16 → float32 conversion in native, no WAV round-trip) — **P50 latency 80-150ms with Vulkan, 300-500ms CPU-only**.
4. The transcript goes to llama.cpp with a structured prompt:

   ```text
   You are an instruction extractor for an Indian police officer.
   From the following captured text, return JSON:
   {
     "instructions": [{
       "direction": "INCOMING" | "OUTGOING" | "SELF",
       "personName": "...",
       "designation": "SP|DSP|SHO|IO|Addl SP|Inspector|null",
       "station": "...",
       "firNumber": "...",
       "title": "5-7 word summary",
       "rawText": "verbatim or shortened",
       "dueAt": "ISO8601 or null",
       "dueText": "preserved phrasing like 'Friday EOD'",
       "priority": "LOW|NORMAL|HIGH",
       "tags": ["#free-form-tag", "..."]
     }]
   }
   ```
   Output is constrained via llama.cpp GBNF grammar → guaranteed valid JSON.
5. For each instruction, the extractor matches person against `persons` table; if not found, the confirmation card shows "+ Add new person: Ramu, SHO, Bandipora?" with one-tap add.
6. Confirmation card appears (one screen, no navigation): person name, title, due, priority, tags. Edit-in-place if needed.
7. Tap **Save** → done. **Total time: <5 seconds.** Or **swipe down** to discard.

### 7.2 Text path

Identical to voice, skipping the Whisper step. Tap note bar → keyboard appears → type → confirm.

### 7.3 Photo path

Tap note bar → camera icon → snap or pick. ML Kit Text Recognition v2 extracts text (on-device, ~200-500ms). The same LLM extraction runs. The original photo is preserved as an attachment to the instruction.

### 7.4 Multiple instructions in one capture

The LLM can return 1+ instructions. *"Met SP at 4. He wants the Bandipora report by Friday. Also follow up with DSP Srinagar on the protest case."* → 2 instructions, 2 confirmation cards, one tap to save both.

### 7.5 First-run model download

On first launch, the user picks an AI model:

| Model | Size | RAM needed | Speed | Use case |
|---|---|---|---|---|
| Qwen 3 1.7B Q4_K_M | 1.1 GB | 6 GB+ | ~15-25 tok/s | Default, any modern Android |
| Gemma 3 4B Q4_K_M | 2.5 GB | 8 GB+ | ~8-12 tok/s | Best multilingual (Tamil/Telugu/Hindi) |
| Phi-4-mini 3.8B Q4_K_M | 2.7 GB | 8 GB+ | ~10-15 tok/s | Strongest English reasoning |

Models are downloaded once, SHA-256 verified, cached in `models-cache/`. The app works fully without a model (raw text capture only, no auto-extraction).

---

## 8. Follow-up flow (four layers, working together)

### 8.1 Layer 1: Morning brief

A Supabase Edge Function runs as a cron at 4 AM user's timezone, generating the brief and writing it to `daily_briefs`. The user gets a push notification at their `brief_time` (default 7:30 AM). Tapping the notification opens the Today screen:

1. **Needs you today** — `direction IN ('INCOMING','SELF') AND status IN ('OPEN','ACK_PENDING','IN_PROGRESS') AND (due_at::date = today OR (priority = 'HIGH' AND status = 'OPEN') OR (now() - updated_at) > interval '7 days')`. Sorted by priority (HIGH first), then due date, then oldest first.
2. **Waiting on others** — `direction = 'OUTGOING' AND status IN ('OPEN','ACK_PENDING','IN_PROGRESS')`. Sorted by how long they've been waiting (oldest first).
3. **Carried over** — `direction IN ('INCOMING','SELF') AND status = 'OPEN' AND (now() - updated_at) > interval '7 days' AND (now() - updated_at) <= interval '30 days'`. Just listed, no count, no emphasis. Older than 30 days get dropped silently.

Each section is collapsible. Tap an item → its detail. Tap a person → their full timeline.

### 8.2 Layer 2: Stale surface

The home screen's people list shows badge = open count. After 3 days of no activity on an OUTGOING instruction (configurable), the person badge turns from neutral to a soft amber dot. Not red. Not a count-up. Just a quiet "this has been quiet."

### 8.3 Layer 3: AI-drafted nudge

Tapping an OUTGOING instruction that's been quiet 3+ days shows a "Draft nudge" button. Tap → llama.cpp drafts a WhatsApp-style message in the user's voice (Hindi-English mixed, polite, with context). The user sees the draft, edits if wanted, taps **Copy** or **Share via WhatsApp** (opens WhatsApp with the person pre-selected if their number is on file). The `nudge_drafts` row is marked SENT with `sent_via`.

### 8.4 Layer 4: Evening review

A push at `review_time` (default 8:30 PM) opens the review screen. One screen. "What got done today: N. What's still open: M. Anything to carry over?" Three taps max. Or swipe to dismiss entirely. Missing it for a week → no punishment; next review picks up.

---

## 9. ADHD UX rules (baked into components)

These are not just principles — they are **finding tests** in the test suite. If a rule breaks, CI fails.

| Rule | Test |
|---|---|
| No red "overdue" badge anywhere | UI snapshot test that greps the rendered tree for `Color(0xFF...)` in red on instruction rows |
| No streak counter | UI snapshot test that greps for "streak" / "day streak" |
| "Carried over" is the only status for silent rollover | Unit test on the rollover worker |
| Capture completes in <5s (P95) | Espresso test that measures tap-to-save time |
| 3 tabs only (Home, Today, Settings) | UI tree test that asserts no other top-level destinations |
| Empty state is one inviting sentence, not a guilt wall | Snapshot test on the empty persons list |
| Lock-screen widget is one button, voice | UI test on the widget layout |
| Brief has no counts in titles (no "3 things overdue") | Snapshot test that greps the brief JSON |
| App survives a 30-day gap (no nag, no broken state) | Integration test: simulate 30 days of no use, verify clean resume |

---

## 10. MCP server interface (cloud, deployed as a Supabase Edge Function)

The MCP server exposes Baton's data to any MCP-compatible client. It uses the official `io.modelcontextprotocol:kotlin-sdk` and runs on Deno (Supabase Edge Functions runtime).

### Resources

| URI | Returns |
|---|---|
| `baton://persons` | List of all persons |
| `baton://persons/{id}` | Single person + their open instructions |
| `baton://instructions/open` | All open instructions, grouped by person |
| `baton://instructions/{id}` | Single instruction + its event log |
| `baton://brief/today` | Today's morning brief |
| `baton://tags` | All tags with usage counts |

### Tools

| Tool | Args | Effect |
|---|---|---|
| `draft_nudge` | `instructionId: string` | Generates a nudge draft, returns text |
| `mark_done` | `instructionId: string, reason?: string` | Marks an instruction DONE |
| `mark_dropped` | `instructionId: string, reason: string` | Marks DROPPED with reason |
| `add_person` | `name, designation?, station?, phone?` | Creates a person, returns id |

**Note on server-side AI:** The cloud MCP server is a pure data layer. It does not run any AI. The four state-changing tools above are mechanical database operations; they do not require an LLM. AI extraction always happens on the user's device. This is deliberate: no third-party AI ever sees the data, and the MCP server remains a thin stateless proxy that could be replaced or self-hosted without touching any model.

### Authentication

The cloud MCP server requires the user's Supabase access token in the request. Implemented via OAuth 2.1 with PKCE (standard MCP auth flow), backed by Supabase Auth.

---

## 11. MindAnchor integration

Both apps are Android, both Kotlin, both zero-backend. Integration is **MCP at both ends** + a shared `app-anchor-crypto` Kotlin module.

### 11.1 Shared module

`app-anchor-crypto` (lives in MindAnchor's repo, depended on by Baton via Maven local during dev, published to internal Maven for releases):
- `Argon2id` key derivation from app passphrase
- `AES-GCM` encryption helpers
- `SQLCipher` open helpers
- `AppState` IPC helpers (local + cloud via the `app_state` table)

### 11.2 Baton reads from MindAnchor

| Key | What MindAnchor provides | What Baton does |
|---|---|---|
| `energy_state` | `NOMINAL \| FAIR \| LOW \| CRITICAL` | Adjusts brief size, nudge frequency, UI density |
| `sunset_mode` | `bool` | Suppresses evening review, batches nudges |
| `notification_batching` | `Map<channel, batchWindow>` | Routes Baton notifications through MindAnchor's batcher |

### 11.3 MindAnchor reads from Baton

| Key | What MindAnchor reads |
|---|---|
| `open_count` | Number of open instructions |
| `done_today` | Number of instructions closed today |
| `next_event` | Next due instruction (for "what's next" surface) |

### 11.4 Behavior

- **Low energy** → Baton brief shrinks to just "needs you today". Nudges pause for 4 hours.
- **High stress** (MindAnchor signal) → Baton's UI density reduces: fewer items, calmer colors, one thing at a time.
- **Sunset mode** → Baton's evening review suppressed, nudges batched into tomorrow's brief.
- **Done counter** → MindAnchor shows it in its "what got done today" surface (opt-in).
- **Opt-in.** If MindAnchor isn't installed, Baton runs without adaptive behavior. If the user disables the integration, both apps ignore each other's state.

---

## 12. Error handling

### Capture
- Mic permission denied → in-app explainer (one screen) + deep-link to system settings.
- Whisper fails → fall back to text, prompt "type it instead?" (never lose the thought).
- LLM extraction fails → raw text saved as-is with a `needs_review=true` flag; user can tag/edit later.
- No network → capture still works fully on-device; sync queue holds the writes, drains when online.

### Sync
- Supabase unreachable → local writes go to SQLite, sync queue retries with exponential backoff (1m → 5m → 15m → 1h).
- Conflict (same row edited on two devices) → last-write-wins on `updated_at`; conflict logged in `sync_conflicts`; user can review in Settings → Sync → Conflicts.

### AI
- Model file corrupted → re-download prompt on next launch.
- OOM during inference → model unloaded, user shown a friendly "out of memory" with suggestions.
- LLM returns malformed JSON → retry once with the same prompt, then save as raw text.

### Backup
- All data is in Supabase (which has its own backups). Local-only `is_sensitive=true` instructions are also encrypted locally and exportable as a single encrypted file (Argon2id-derived key from a user passphrase).
- Passphrase forgotten → unrecoverable for local-only items. Documented. No backdoor.

---

## 13. Privacy & security

- **No third-party AI ever sees the data.** On-device Whisper + llama.cpp only.
- **Supabase is the cloud of record.** Encrypted in transit (TLS 1.3) and at rest (AES-256). Supabase is GDPR-compliant, ISO 27001 certified; data lives in the region chosen at project creation.
- **`is_sensitive=true`** on a person or instruction → that row never syncs to Supabase; stays in the local encrypted DB only.
- **No analytics, no telemetry, no crash reporting that sends data off-device.** Local logs only.
- **Auth:** Supabase Auth with email + password (or magic link), PKCE flow.
- **Encryption passphrase:** if set by the user, derives an additional key used for local SQLCipher encryption and any local-only data.
- **MCP server auth:** OAuth 2.1 with PKCE, backed by Supabase Auth. Standard MCP auth flow.

---

## 14. Build plan

Five milestones, each independently shippable. Each has a finding test that proves it works.

### M0 — Skeleton (week 1)

- Android project: Kotlin 2.0, Compose, Hilt, Room/SQLCipher, WorkManager, Ktor
- Gradle Version Catalog
- Supabase project created, RLS policies written, migrations applied
- Cloud MCP server: minimal "list persons" resource only
- Finding test: app launches, shows empty Home, can create a person

### M1 — Single note bar + capture (weeks 2-3)

- Single note bar UI, text capture path
- llama.cpp JNI integrated, model download flow
- LLM extraction (Qwen 3 1.7B default), GBNF-constrained JSON
- Confirmation card flow
- Person auto-creation on first sight
- Finding test: tap note bar, type "Tell SHO Ramu to send FIR 47 by Friday", see a correctly extracted INSTRUCTION

### M2 — Voice + photo + sync (weeks 4-5)

- Whisper.cpp JNI integrated, direct PCM push pattern
- Foreground service for mic capture (type `microphone`)
- Quick-settings tile + lock-screen widget
- ML Kit OCR for photo path
- Supabase sync (Postgrest + Realtime), conflict resolution
- Multi-device smoke test
- Finding test: voice capture → extraction → save → app reopened on a second device → instruction appears

### M3 — People + tags + MCP server (week 6)

- People list (Home tab) with badge
- Person detail (timeline)
- Tag management screen
- Tags auto-creation, auto-coloring
- Full MCP server (all resources + tools) deployed as Supabase Edge Function
- Finding test: Claude Desktop connects to Baton MCP, lists persons and open instructions

### M4 — Brief + nudge + MindAnchor (weeks 7-8)

- Brief scheduler (Supabase cron + Edge Function)
- Morning brief notification + Today screen
- Stale surface (amber dot logic)
- AI-drafted nudge (llama.cpp)
- Evening review
- `app-anchor-crypto` module shared with MindAnchor
- AppState IPC
- Finding test: simulate 3 days of no activity on an OUTGOING instruction, verify amber dot, verify nudge draft quality, verify MindAnchor energy signal changes brief

### M5 — Polish + deploy (week 9)

- ADHD UX finding tests (all 9)
- Performance pass (capture P95 < 5s)
- Crash-free beta on Sampath's actual district workflow
- App Store metadata, screenshots, signed release
- Finding test: all 9 ADHD UX rules pass; capture P95 < 5s on Pixel 7 / SD 8 Gen 1

Total: ~9 weeks to v1.0 release. Solo effort. Can be parallelised if a contractor joins for AI integration.

---

## 15. Testing strategy

Three layers:

1. **Unit tests** (JVM, fast) — domain logic, extractor, sync engine, brief generator.
2. **Finding tests** (JVM + Compose UI tests) — assert the ADHD UX rules and design conclusions. If a rule from §9 is broken, CI fails. These are the "measure before you claim" tests.
3. **Instrumented tests** (Android device) — real LLM inference, real Whisper, real sync, real capture flow. Run on Firebase Test Lab on a Pixel 7 (representative mid-range) and a Pixel 8 Pro (representative flagship).

**Performance budgets enforced in CI:**
- Capture P95 (tap → save) < 5s
- Whisper transcription (30s of audio) < 2s
- LLM extraction (1.7B model, 200-token prompt) < 3s
- Sync round-trip (single instruction) < 1s
- App cold start < 1.5s

**Reference devices for testing:**
- Pixel 7 (SD 8 Gen 1, 8GB) — flagship, 2 years old
- Pixel 8a (SD 8 Gen 1, 8GB) — mid-range
- Samsung Galaxy A54 (Exynos 1380, 8GB) — Samsung-specific quirks
- OnePlus Nord CE 3 (SD 782G, 8GB) — Indian-market-common

---

## 16. Open questions

1. **Tamil/Telugu/Hindi voice support.** Default model is English-only. Multilingual Whisper models are bigger. v1 ships with English + Hindi; Tamil/Telugu is a v1.1+ ask.
2. **Calendar integration.** Should Baton be able to add events to the system calendar when a due date is set? Easy via Android `CalendarContract`; need to decide if it should ask first time.
3. **WhatsApp Business API for actual nudge sending.** Currently a "tap to copy + open WhatsApp" flow. Sending programmatically requires a WhatsApp Business account and BSP. Not in v1.
4. **Share-target.** Should Baton register as a share target so WhatsApp messages / emails can be forwarded into it? Yes for v1.1, no for v1.
5. **Backup destination.** v1 ships with Supabase as the de-facto backup. Local encrypted export to a file is a v1.0.1 add.

---

## 17. Out of scope (for v1)

- Multi-user / team mode
- CCTNS / eCourts integration
- WhatsApp Business API (programmatic send)
- iOS app
- Wear OS app
- Web app
- Calendar integration
- Share-target ingest

These may come in v1.1+.

---

## 18. References (research backing this design)

- **ADHD + task management failure modes:** Kofler et al. 2020 (working memory); Russell Barkley (executive function model); ADHD Foundation 2023 (78% abandon 3+ apps); ADDitude Magazine 2023 (68% abandoned 3+ apps in past year).
- **What works:** Dr. Edward Hallowell (external scaffolding, interest-based activation); GTD "next action" principle; Tiimo's visual timeline approach; Goblin Tools' AI task breakdown; arXiv 2507.06864 (systems + AI for ADHD professionals).
- **Tech stack:** llama.cpp official `llama.android` example; whisper.cpp `examples/whisper.android`; Supabase Kotlin SDK (`io.github.jan-tennert.supabase`); official MCP Kotlin SDK (`io.modelcontextprotocol:kotlin-sdk`); Android `TileService` and foreground service docs.
- **Realistic on-device LLM benchmarks:** 1-3s latency on 1.7-4B models on modern Android; sub-100ms perceived Whisper latency with direct PCM JNI push; mmap loading pattern; thermal throttling around 30s on Tensor.
