# Baton MCP server — public contract

> Last updated: 2026-08-23 · Server version: `0.4.0` · Transport: MCP over Streamable HTTP

The Baton MCP server is a cloud-side read/write interface to the user's Baton data, designed to be consumed by AI chat clients (Claude Desktop, Cursor, etc.). It is deployed as a Supabase Edge Function at `supabase/functions/mcp-server/`.

## TL;DR for clients

- **Transport:** MCP over Streamable HTTP (web standard `Request` / `Response`)
- **Auth:** Supabase JWT in `Authorization: Bearer <token>` header
- **Scope:** RLS-restricted to the calling user's own data
- **Capabilities:** 7 resources (read-only views) + 5 tools (write actions)
- **Server identity:** `name: "baton-mcp"`, `version: "0.4.0"`

## Connecting from Claude Desktop

Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "baton": {
      "url": "https://<your-supabase-project>.supabase.co/functions/v1/mcp-server",
      "transport": "streamable-http",
      "headers": {
        "Authorization": "Bearer <supabase-access-token>"
      }
    }
  }
}
```

The access token is the user's Supabase JWT, obtained from the Baton's sign-in flow. Same token as the app uses.

## Auth model

- The user's Supabase access token (JWT) is passed in the `Authorization: Bearer ...` header.
- Supabase Edge Function gateway validates the JWT (via `config.toml`'s `verify_jwt = true`) before the handler runs.
- Inside the handler, the JWT is used to build a per-request Supabase client. All database queries go through RLS policies scoped to `auth.uid()`.
- **No service-role key on the client side. No long-lived API keys.** The token is the user's session token.

## Resources (7 — read-only)

All resources return JSON-as-text. Errors come back as `text/plain` with a human-readable message in the `text` field.

### 1. `baton://persons`

List of every person in the calling user's address book.

**Returns:**
```json
[
  {
    "id": "uuid",
    "name": "SHO Ramu",
    "designation": "SHO",
    "station": "Bandipora",
    "phone": "+91 98765 43210",
    "created_at": "2026-08-15T..."
  }
]
```

### 2. `baton://person/{personId}`

Single person with their full instruction timeline (up to 100 most recent).

**Returns:**
```json
{
  "person": { "id": "...", "name": "...", "designation": "...", "station": "...", "phone": "...", "created_at": "..." },
  "instructions": [
    { "id": "...", "title": "...", "raw_text": "...", "status": "...", "priority": "...", "direction": "...", "due_at": "...", "captured_at": "..." }
  ]
}
```

### 3. `baton://instructions`

All instructions (capped at 200 most recent). For full search, use the `search_instructions` tool.

**Returns:** array of instruction objects (same shape as `instructions` above).

### 4. `baton://instruction/{instructionId}`

Single instruction, full row including all columns.

**Returns:** full instruction object, or `"Not found"` text.

### 5. `baton://instructions/open`

Open + in-progress instructions — the "what needs you today" surface. Excludes `DONE`, `CARRIED_OVER`, `DROPPED`. Ordered by priority (HIGH first) then due date (earliest first, nulls last).

**Returns:** array of up to 100 instruction objects.

### 6. `baton://instructions/due-today`

Open instructions whose `due_at` falls within today (Asia/Kolkata timezone). The server computes IST day boundaries then filters in UTC.

**Returns:** array of due-today instructions, ordered by `due_at`.

### 7. `baton://stats`

Aggregate counts for dashboards, briefs, and ad-hoc LLM snapshots. Single round-trip; aggregation happens in-process.

**Returns:**
```json
{
  "byStatus": { "OPEN": 12, "IN_PROGRESS": 3, "DONE": 47, ... },
  "byPriority": { "LOW": 2, "NORMAL": 18, "HIGH": 3 },
  "dueToday": 4,
  "overdue": 1,
  "total": 62
}
```

## Tools (5 — write actions)

All tools take a Zod-validated input object and return `{ content: [{ type: "text", text: "..." }], isError?: boolean }`. Errors use the `isError: true` flag.

### T1. `create_person`

Add a new person to the user's address book.

**Input:**
```typescript
{
  name: string,                    // required, min 1 char, e.g. "SHO Ramu"
  designation?: string,            // e.g. "SHO", "DSP", "SP"
  station?: string,                // e.g. "Bandipora"
  phone?: string,                  // e.g. "+91 98765 43210"
}
```

**Returns:** `"Created person id=<uuid>"` on success. `{ isError: true, content: [{ text: "create_person failed: <reason>" }] }` on failure.

### T2. `create_instruction`

Create a new instruction. If `person_name` is given and a person with that exact name exists, links to them; otherwise auto-creates the person. Matches the M1-T5 capture flow.

**Input:**
```typescript
{
  title: string,                   // required, 5-7 word human-readable label
  raw_text: string,                // required, the full instruction verbatim
  person_name?: string,            // if given: link to existing person OR create new
  due_at?: string,                 // ISO 8601, e.g. "2026-08-15T17:00:00+05:30"
  priority?: "LOW" | "NORMAL" | "HIGH",   // default "NORMAL"
  direction?: "INCOMING" | "OUTGOING" | "SELF",  // default "OUTGOING"
}
```

**Returns:** `"Created instruction id=<uuid> for person_id=<uuid>"` (or `"... (no person linked)"` if no `person_name` was given).

### T3. `update_instruction_status`

Move an instruction through the status state machine.

**Input:**
```typescript
{
  id: string,                      // UUID of the instruction
  status: "OPEN" | "ACK_PENDING" | "IN_PROGRESS" | "WAITING_ON_OTHER" | "DONE" | "CARRIED_OVER" | "DROPPED"
}
```

If `status === "DONE"`, the server also stamps `completed_at = now()`.

**Returns:** `"Updated instruction id=<uuid> status=<status>"`.

### T4. `search_instructions`

Free-text search over `title` and `raw_text` using Postgres `ilike`. Returns up to 50 matches ordered by recency.

> **Note:** The M3 contract uses `ilike` for v1 row counts (hundreds). When the dataset grows past ~10k rows, this will be replaced with a `tsvector + GIN` index.

**Input:**
```typescript
{
  query: string                    // required, min 1 char
}
```

**Returns:** JSON-stringified array of up to 50 matching instructions.

### T5. `draft_nudge`

Generate a nudge draft for an `OUTGOING` instruction that's gone quiet. Cloud-side baseline template; the on-device LLM rewrites it in the user's voice. The draft is persisted to `nudge_drafts` (best-effort; insert errors are non-fatal).

**Input:**
```typescript
{
  instruction_id: string,          // UUID, must be an OUTGOING instruction
  tone?: "polite" | "urgent" | "casual"  // default "polite"
}
```

**Returns:** JSON-stringified object:
```json
{
  "instruction_id": "...",
  "person_name": "SHO Ramu",
  "tone": "polite",
  "draft_text": "Hi SHO Ramu — following up on \"Send FIR 47\". Let me know if you need anything from me."
}
```

**Error cases:**
- Instruction not found → `isError: true`
- Instruction is `INCOMING` or `SELF` (only `OUTGOING` is nudgeable) → `isError: true`

## Data model

The MCP server reads/writes these tables. All operations are scoped via RLS to the calling user.

### `persons`
- `id` (uuid, PK)
- `user_id` (uuid, FK → auth.users, RLS scopes to this)
- `name` (text, required)
- `designation` (text, optional)
- `station` (text, optional)
- `phone` (text, optional)
- `created_at` (timestamptz)
- `deleted_at` (timestamptz, soft-delete)

### `instructions`
- `id` (uuid, PK)
- `user_id` (uuid, FK → auth.users)
- `person_id` (uuid, FK → persons, nullable)
- `title` (text)
- `raw_text` (text)
- `status` (enum: `OPEN | ACK_PENDING | IN_PROGRESS | WAITING_ON_OTHER | DONE | CARRIED_OVER | DROPPED`)
- `priority` (enum: `LOW | NORMAL | HIGH`)
- `direction` (enum: `INCOMING | OUTGOING | SELF`)
- `source` (enum: `TEXT | VOICE | PHOTO | MCP | ...`)
- `due_at` (timestamptz, nullable)
- `captured_at` (timestamptz)
- `completed_at` (timestamptz, nullable)
- `created_at`, `updated_at` (timestamptz)
- `deleted_at` (timestamptz, soft-delete)

### `captures`
- The raw input stream from the device (text / voice / photo). The on-device LLM extraction populates `instructions` from these. The MCP server doesn't expose this table yet.

### `events`
- Audit log of what happened to an instruction (created, status changed, nudged, etc.). The MCP server doesn't expose this table yet.

### `nudge_drafts`
- Server-side draft buffer (used by `draft_nudge`). Best-effort writes; client-side `instructions` table is the source of truth for status.

### `app_state`
- Cross-app state shared with MindAnchor. MCP server doesn't expose this.

## Rate limits & quotas

- No explicit rate limits in v1.0. Supabase Edge Functions inherit the project's default (varies by plan).
- Each tool call is one DB round-trip (or two, in the case of `create_instruction` which resolves the person).
- `search_instructions` and the instructions/* resources can return up to 200 rows. Heavy users should paginate via `?offset=...` (not yet implemented; see "Open work" below).

## Open work (not in v0.4.0)

- **Pagination** for `baton://instructions` and `search_instructions` results. Today these are capped at 200/50. Add `?offset=&limit=` query params when row counts grow.
- **Streaming** for `draft_nudge` — the cloud draft is plain text, but a streaming version would let the on-device LLM refine it token-by-token.
- **Webhook subscriptions** — let the user subscribe to instruction status changes from the desktop client. Today the desktop client must poll.
- **`app_state` resource** — expose MindAnchor energy state (or whatever it represents after §3.6 lands) so the desktop client can mirror the energy-aware UX.
- **Delete tools** — `delete_person` and `delete_instruction` (soft-delete via `deleted_at`). Not yet exposed; the v1 contract is create/update/read only.

## Versioning

- Server reports `version: "0.4.0"` in the MCP `initialize` handshake.
- Backward compatibility: tools and resources only get added, never renamed. Removed endpoints bump the major version and ship a deprecation notice.
- The contract version is decoupled from the Baton app version — the MCP server can ship at its own cadence.

## See also

- `supabase/functions/mcp-server/index.ts` — the implementation
- `supabase/functions/_shared/cors.ts` — CORS handling
- `docs/PLAN.md` §3.5 — the original plan for this doc
- `AGENTS.md` — project rules
