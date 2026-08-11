-- =============================================================================
-- 0002_default_user_id.sql
--
-- Default every `user_id` column to `auth.uid()` so the app can INSERT
-- without setting it explicitly. 0001 left the column as `not null` with
-- no default, which means RLS evaluates `private.is_owner(NULL) = false`
-- and the insert is rejected with 403 even for the owning user.
--
-- This is the standard Supabase "owner column default" pattern. RLS
-- policies are unchanged: they still check `user_id = auth.uid()`.
-- =============================================================================

alter table persons
  alter column user_id set default auth.uid();

alter table instructions
  alter column user_id set default auth.uid();

alter table tags
  alter column user_id set default auth.uid();

alter table events
  alter column user_id set default auth.uid();

alter table captures
  alter column user_id set default auth.uid();

alter table nudge_drafts
  alter column user_id set default auth.uid();

alter table daily_briefs
  alter column user_id set default auth.uid();

alter table app_state
  alter column user_id set default auth.uid();

alter table sync_conflicts
  alter column user_id set default auth.uid();
