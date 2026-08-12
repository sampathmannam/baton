-- =============================================================================
-- Baton v1.0: nudge_drafts table + realtime publication
-- The nudge_drafts table is already in the schema (0001_init.sql) with
-- RLS policies. This migration adds the table to the supabase_realtime
-- publication so the on-device NudgeDraftDao observes server-side
-- drafts (e.g. ones the user drafts from a desktop chat via the MCP
-- `draft_nudge` tool).
--
-- Idempotent pattern: ALTER PUBLICATION doesn't support IF NOT EXISTS,
-- so we check pg_publication_tables first.
-- =============================================================================

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'nudge_drafts'
  ) then
    alter publication supabase_realtime add table public.nudge_drafts;
  end if;
end $$;
