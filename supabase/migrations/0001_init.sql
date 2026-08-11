-- =============================================================================
-- Baton M0 initial schema
-- Source of truth: docs/superpowers/specs/2026-08-10-baton-design.md §4.1-§4.10
-- This migration is the verbatim DDL from the spec, with the addition of:
--   * a private.is_owner(uuid) helper used by every RLS policy
--   * per-table RLS enable + four policies (SELECT/INSERT/UPDATE/DELETE)
-- The "owner" predicate is `auth.uid() = <table>.user_id`. The spec uses
-- `user_id` (not `owner_user_id`); per-table column names are not renamed.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. Helper: private.is_owner(uuid)
-- -----------------------------------------------------------------------------
-- Returns true when the row's user_id matches the calling auth.uid().
-- Putting the predicate in one place keeps every RLS policy identical and
-- auditable. The function is in a private schema so PostgREST does not expose
-- it under /rest/v1/.
-- -----------------------------------------------------------------------------
create schema if not exists private;

create or replace function private.is_owner(row_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public, private
as $$
  select row_user_id = auth.uid();
$$;

-- -----------------------------------------------------------------------------
-- 1. Enums
-- -----------------------------------------------------------------------------

-- §4.2 Instructions
create type instruction_direction as enum ('INCOMING', 'OUTGOING', 'SELF');
create type instruction_status as enum (
  'OPEN',              -- just captured, no one acted
  'ACK_PENDING',       -- sent to subordinate, waiting for ack
  'IN_PROGRESS',       -- someone is working on it
  'WAITING_ON_OTHER',  -- blocked on someone else
  'DONE',              -- closed
  'CARRIED_OVER',      -- moved to today silently
  'DROPPED'            -- explicitly cancelled, with reason
);
create type instruction_source as enum ('VOICE', 'TEXT', 'PHOTO', 'MCP');
create type instruction_priority as enum ('LOW', 'NORMAL', 'HIGH');

-- §4.3 Tags
create type tag_kind as enum ('PERSON', 'DESIGNATION', 'STATION', 'CASE', 'FIR', 'PRIORITY', 'FREE');

-- -----------------------------------------------------------------------------
-- 2. persons  (§4.1)
-- -----------------------------------------------------------------------------
create table persons (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references auth.users(id) on delete cascade,
  name          text not null,
  designation   text,  -- SP, DSP, SHO, IO, Addl SP, Inspector, etc.
  station       text,  -- Bandipora, Srinagar, etc.
  phone         text,
  notes         text,
  avatar_url    text,
  is_sensitive  boolean default false,  -- if true, this person + their instructions stay local-only
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  deleted_at    timestamptz,
  unique(user_id, name, designation, station)  -- dedupes on capture
);

create index persons_user_idx on persons(user_id) where deleted_at is null;

-- -----------------------------------------------------------------------------
-- 3. instructions  (§4.2)
-- -----------------------------------------------------------------------------
create table instructions (
  id                    uuid primary key default gen_random_uuid(),
  user_id               uuid not null references auth.users(id) on delete cascade,
  person_id             uuid references persons(id) on delete set null,
  direction             instruction_direction not null,
  status                instruction_status not null default 'OPEN',
  source                instruction_source not null,
  priority              instruction_priority not null default 'NORMAL',
  title                 text not null,           -- AI-generated, 5-7 words
  raw_text              text not null,           -- original capture verbatim
  due_at                timestamptz,
  captured_at           timestamptz not null,    -- when it happened in real world
  last_nudged_at        timestamptz,
  completed_at          timestamptz,
  dropped_reason        text,
  is_sensitive          boolean default false,   -- true: row never syncs to Supabase; stays in local DB only
  -- Auto-extracted fields (the LLM's output)
  extracted_fir         text,                    -- e.g., "FIR 47/2026"
  extracted_due_text    text,                    -- e.g., "Friday EOD", preserved for display
  location_text         text,                    -- e.g., "Bandipora"
  -- Audit
  created_at            timestamptz not null default now(),
  updated_at            timestamptz not null default now(),
  deleted_at            timestamptz
);

create index instructions_user_status_idx
  on instructions(user_id, status) where deleted_at is null;
create index instructions_user_person_idx
  on instructions(user_id, person_id) where deleted_at is null;
create index instructions_user_due_idx
  on instructions(user_id, due_at) where deleted_at is null;
create index instructions_user_priority_due_idx
  on instructions(user_id, priority desc, due_at asc nulls last) where deleted_at is null;

-- -----------------------------------------------------------------------------
-- 4. tags + instruction_tags  (§4.3)
-- -----------------------------------------------------------------------------
create table tags (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references auth.users(id) on delete cascade,
  name          text not null,
  kind          tag_kind not null default 'FREE',
  color         text,                      -- hex string
  usage_count   int not null default 0,
  last_used_at  timestamptz,
  created_at    timestamptz not null default now(),
  unique(user_id, name, kind)
);

create table instruction_tags (
  instruction_id uuid not null references instructions(id) on delete cascade,
  tag_id         uuid not null references tags(id) on delete cascade,
  primary key (instruction_id, tag_id)
);

-- -----------------------------------------------------------------------------
-- 5. events  (§4.4) — append-only audit log
-- -----------------------------------------------------------------------------
create table events (
  id              uuid primary key default gen_random_uuid(),
  instruction_id  uuid not null references instructions(id) on delete cascade,
  user_id         uuid not null references auth.users(id) on delete cascade,
  type            text not null,  -- CREATED, STATUS_CHANGED, NUDGE_SENT, ACKNOWLEDGED, COMPLETED, DROPPED, EDITED, NUDGED_BACK
  payload         jsonb,          -- { from, to, actor, draftId, ... }
  created_at      timestamptz not null default now()
);
-- Append-only. No updates, no deletes. The "measure before you claim" trail.

-- -----------------------------------------------------------------------------
-- 6. captures  (§4.5) — raw inputs, before they become instructions
-- -----------------------------------------------------------------------------
create table captures (
  id                          uuid primary key default gen_random_uuid(),
  user_id                     uuid not null references auth.users(id) on delete cascade,
  mode                        instruction_source not null,
  raw_text                    text,           -- Whisper output, typed text, or OCR text
  audio_uri                   text,           -- local file (Supabase Storage for cloud mirror)
  image_uri                   text,
  processed                   boolean not null default false,
  extracted_instruction_ids   uuid[],         -- populated by the LLM
  llm_model                   text,           -- e.g., "qwen3-1.7b-q4"
  llm_latency_ms              int,
  created_at                  timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- 7. nudge_drafts  (§4.6) — AI-drafted messages, sent
-- -----------------------------------------------------------------------------
create table nudge_drafts (
  id              uuid primary key default gen_random_uuid(),
  instruction_id  uuid not null references instructions(id) on delete cascade,
  user_id         uuid not null references auth.users(id) on delete cascade,
  draft_text      text not null,
  status          text not null default 'DRAFT',  -- DRAFT, EDITED, SENT, CANCELLED
  sent_via        text,                            -- WHATSAPP, SMS, COPY
  sent_at         timestamptz,
  created_at      timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- 8. daily_briefs  (§4.7) — morning + evening snapshots
-- -----------------------------------------------------------------------------
create table daily_briefs (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references auth.users(id) on delete cascade,
  type          text not null,  -- MORNING, EVENING
  brief_date    date not null,
  content       jsonb not null,  -- { needsYouToday: [...], waitingOnOthers: [...], carriedOver: [...] }
  generated_at  timestamptz not null default now(),
  viewed_at     timestamptz,
  unique(user_id, type, brief_date)
);

-- -----------------------------------------------------------------------------
-- 9. app_state  (§4.8) — cross-app state, shared with MindAnchor
-- -----------------------------------------------------------------------------
create table app_state (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  source      text not null,  -- BATON, MINDANCHOR
  key         text not null,  -- e.g., "energy_state", "sunset_mode", "batching_rules"
  value       jsonb not null,
  updated_at  timestamptz not null default now(),
  unique(user_id, source, key)
);

-- -----------------------------------------------------------------------------
-- 10. sync_conflicts  (§4.9) — logged, not silent
-- -----------------------------------------------------------------------------
create table sync_conflicts (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null references auth.users(id) on delete cascade,
  table_name      text not null,
  row_id          uuid not null,
  local_payload   jsonb,
  remote_payload  jsonb,
  resolution      text not null,  -- LOCAL_WON, REMOTE_WON, MERGED
  created_at      timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- 11. settings  (§4.10) — per-user, key-value
-- -----------------------------------------------------------------------------
create table settings (
  user_id                 uuid primary key references auth.users(id) on delete cascade,
  brief_time              time not null default '07:30',
  review_time             time not null default '20:30',
  nudge_threshold_days    int not null default 3,
  llm_model               text not null default 'qwen3-1.7b-q4',
  voice_language          text not null default 'en',
  mindanchor_enabled      boolean not null default false,
  updated_at              timestamptz not null default now()
);

-- =============================================================================
-- 12. Row-Level Security
-- One ENABLE per table, four policies per table (SELECT, INSERT, UPDATE, DELETE).
-- Every policy uses private.is_owner(<table>.user_id) so the predicate is in
-- exactly one place. Spec §13: cross-user reads must return zero rows. The M0
-- finding test (Task 9) depends on this.
-- =============================================================================

-- persons
alter table persons enable row level security;
create policy persons_select_own on persons
  for select using (private.is_owner(user_id));
create policy persons_insert_own on persons
  for insert with check (private.is_owner(user_id));
create policy persons_update_own on persons
  for update using (private.is_owner(user_id))
            with check (private.is_owner(user_id));
create policy persons_delete_own on persons
  for delete using (private.is_owner(user_id));

-- instructions
alter table instructions enable row level security;
create policy instructions_select_own on instructions
  for select using (private.is_owner(user_id));
create policy instructions_insert_own on instructions
  for insert with check (private.is_owner(user_id));
create policy instructions_update_own on instructions
  for update using (private.is_owner(user_id))
            with check (private.is_owner(user_id));
create policy instructions_delete_own on instructions
  for delete using (private.is_owner(user_id));

-- tags
alter table tags enable row level security;
create policy tags_select_own on tags
  for select using (private.is_owner(user_id));
create policy tags_insert_own on tags
  for insert with check (private.is_owner(user_id));
create policy tags_update_own on tags
  for update using (private.is_owner(user_id))
            with check (private.is_owner(user_id));
create policy tags_delete_own on tags
  for delete using (private.is_owner(user_id));

-- instruction_tags  (composite-key table; ownership inherited via the parent rows)
alter table instruction_tags enable row level security;
-- Reads are allowed when the calling user owns the instruction row.
create policy instruction_tags_select_own on instruction_tags
  for select using (
    exists (
      select 1 from instructions i
      where i.id = instruction_tags.instruction_id
        and private.is_owner(i.user_id)
    )
  );
-- Inserts/updates/deletes require the same ownership.
create policy instruction_tags_insert_own on instruction_tags
  for insert with check (
    exists (
      select 1 from instructions i
      where i.id = instruction_tags.instruction_id
        and private.is_owner(i.user_id)
    )
  );
create policy instruction_tags_update_own on instruction_tags
  for update using (
    exists (
      select 1 from instructions i
      where i.id = instruction_tags.instruction_id
        and private.is_owner(i.user_id)
    )
  )
  with check (
    exists (
      select 1 from instructions i
      where i.id = instruction_tags.instruction_id
        and private.is_owner(i.user_id)
    )
  );
create policy instruction_tags_delete_own on instruction_tags
  for delete using (
    exists (
      select 1 from instructions i
      where i.id = instruction_tags.instruction_id
        and private.is_owner(i.user_id)
    )
  );

-- events  (append-only — no UPDATE/DELETE policy means writes are impossible post-create)
alter table events enable row level security;
create policy events_select_own on events
  for select using (private.is_owner(user_id));
create policy events_insert_own on events
  for insert with check (private.is_owner(user_id));
-- No update/delete policies. The "measure before you claim" trail is immutable
-- from the row's perspective. Deletes are still possible via ON DELETE CASCADE
-- from the parent instruction; row-level UPDATE is impossible.

-- captures
alter table captures enable row level security;
create policy captures_select_own on captures
  for select using (private.is_owner(user_id));
create policy captures_insert_own on captures
  for insert with check (private.is_owner(user_id));
create policy captures_update_own on captures
  for update using (private.is_owner(user_id))
            with check (private.is_owner(user_id));
create policy captures_delete_own on captures
  for delete using (private.is_owner(user_id));

-- nudge_drafts
alter table nudge_drafts enable row level security;
create policy nudge_drafts_select_own on nudge_drafts
  for select using (private.is_owner(user_id));
create policy nudge_drafts_insert_own on nudge_drafts
  for insert with check (private.is_owner(user_id));
create policy nudge_drafts_update_own on nudge_drafts
  for update using (private.is_owner(user_id))
            with check (private.is_owner(user_id));
create policy nudge_drafts_delete_own on nudge_drafts
  for delete using (private.is_owner(user_id));

-- daily_briefs
alter table daily_briefs enable row level security;
create policy daily_briefs_select_own on daily_briefs
  for select using (private.is_owner(user_id));
create policy daily_briefs_insert_own on daily_briefs
  for insert with check (private.is_owner(user_id));
create policy daily_briefs_update_own on daily_briefs
  for update using (private.is_owner(user_id))
            with check (private.is_owner(user_id));
create policy daily_briefs_delete_own on daily_briefs
  for delete using (private.is_owner(user_id));

-- app_state
alter table app_state enable row level security;
create policy app_state_select_own on app_state
  for select using (private.is_owner(user_id));
create policy app_state_insert_own on app_state
  for insert with check (private.is_owner(user_id));
create policy app_state_update_own on app_state
  for update using (private.is_owner(user_id))
            with check (private.is_owner(user_id));
create policy app_state_delete_own on app_state
  for delete using (private.is_owner(user_id));

-- sync_conflicts
alter table sync_conflicts enable row level security;
create policy sync_conflicts_select_own on sync_conflicts
  for select using (private.is_owner(user_id));
create policy sync_conflicts_insert_own on sync_conflicts
  for insert with check (private.is_owner(user_id));
create policy sync_conflicts_update_own on sync_conflicts
  for update using (private.is_owner(user_id))
            with check (private.is_owner(user_id));
create policy sync_conflicts_delete_own on sync_conflicts
  for delete using (private.is_owner(user_id));

-- settings
alter table settings enable row level security;
create policy settings_select_own on settings
  for select using (private.is_owner(user_id));
create policy settings_insert_own on settings
  for insert with check (private.is_owner(user_id));
create policy settings_update_own on settings
  for update using (private.is_owner(user_id))
            with check (private.is_owner(user_id));
create policy settings_delete_own on settings
  for delete using (private.is_owner(user_id));
