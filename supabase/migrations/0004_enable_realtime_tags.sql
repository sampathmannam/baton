-- =============================================================================
-- Baton M3-T7: enable Realtime on the `tags` table
-- The migration in 0003_enable_realtime_publication.sql added
-- `persons` + `instructions` to the supabase_realtime publication.
-- For the tag picker in the capture sheet to refresh in real time
-- (when the user creates a tag on another device, or when a
-- background sync writes one), `tags` needs to be in the publication
-- too. Same idempotent pattern as 0003.
-- =============================================================================

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'tags'
  ) then
    alter publication supabase_realtime add table public.tags;
  end if;
end $$;
