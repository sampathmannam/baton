-- =============================================================================
-- M2-T7: enable Realtime for persons + instructions
-- Source of truth: docs/superpowers/plans/2026-08-11-baton-m2-capture.md (T7)
-- Adds the two tables to the `supabase_realtime` publication so the
-- postgres_changes channel emits INSERT/UPDATE/DELETE events.
-- Without this step the M2-T7 RealtimeSync subscription fails with
-- "Realtime is enabled for the given connect parameters" (error message
-- from the Realtime extension when the table is not in the publication).
--
-- RLS still applies to the event payload: each subscribed user only
-- sees events for rows they can SELECT. No policy changes are needed
-- here -- the existing per-table SELECT policy from migration 0001
-- already restricts the payload.
-- =============================================================================

-- Defensive: only add if not already in the publication. We can't use
-- `ADD ... IF NOT EXISTS` (not supported by ALTER PUBLICATION), so we
-- check via a DO block.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'persons'
  ) then
    alter publication supabase_realtime add table public.persons;
  end if;

  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'instructions'
  ) then
    alter publication supabase_realtime add table public.instructions;
  end if;
end $$;
