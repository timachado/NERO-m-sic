-- STAGING ONLY. Requires nero_account_plan.sql and nero_account_plan_details_draft.sql.
-- No payment changes. Do not deploy before isolated authorization and concurrency tests.
begin;
create schema nero_trial_private;
revoke all on schema nero_trial_private from public,anon,authenticated;
grant usage on schema nero_trial_private to authenticated;
create table nero_trial_private.uses (
 owner uuid primary key references auth.users(id) on delete cascade,
 started_at timestamptz not null default now()
);
alter table nero_trial_private.uses enable row level security;
revoke all on nero_trial_private.uses from public,anon,authenticated;
create function nero_trial_private.start_trial() returns jsonb
language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); started timestamptz:=now();
begin
 if u is null or coalesce(auth.jwt()->'app_metadata'->>'provider','')<>'google'
 or coalesce((auth.jwt()->>'is_anonymous')::boolean,false) then raise insufficient_privilege; end if;
 -- Shared lock namespace must also be used by future payment entitlement writers.
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(u::text), 28461);
 if exists(select 1 from nero_trial_private.uses where owner=u)
 or exists(select 1 from public.nero_account_plan_v1 where owner=u) then
   return jsonb_build_object('result','ineligible');
 end if;
 insert into nero_trial_private.uses(owner,started_at) values(u,started);
 insert into public.nero_account_plan_v1(owner,valid_until,revoked,trial_started_at,auto_renew)
 values(u,started+interval '168 hours',false,started,false);
 return jsonb_build_object('result','started');
end $$;
revoke all on function nero_trial_private.start_trial() from public,anon,authenticated;
grant execute on function nero_trial_private.start_trial() to authenticated;
create function public.nero_trial_start_v1() returns jsonb
language sql security invoker set search_path='' as $$
 select nero_trial_private.start_trial()
$$;
revoke all on function public.nero_trial_start_v1() from public,anon,authenticated;
grant execute on function public.nero_trial_start_v1() to authenticated;
commit;
-- Required tests: anonymous/password denied, repeated/concurrent activation,
-- existing paid/expired/revoked rows never overwritten, expiry from DB clock,
-- distinct users isolated, no direct insert/update by authenticated clients.
-- Account deletion/re-registration, rate limiting and abuse policy still need review.
