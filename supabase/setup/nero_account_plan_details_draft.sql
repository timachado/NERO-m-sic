-- DRAFT: requires the original nero_account_plan.sql in an isolated staging DB.
-- DO NOT APPLY to production. No client write, trial activation or payment event handler.
begin;
alter table public.nero_account_plan_v1
    add column plan_id text check (plan_id in ('premium_monthly','premium_quarterly','premium_annual')),
    add column trial_started_at timestamptz,
    add column renewal_failed boolean not null default false,
    add column grace_until timestamptz,
    add column auto_renew boolean,
    add constraint nero_trial_duration_check check (
        trial_started_at is null or (
            valid_until > trial_started_at
            and valid_until <= trial_started_at + interval '168 hours'
            and plan_id is null and grace_until is null
            and not renewal_failed and auto_renew is not true
        )
    ),
    add constraint nero_grace_duration_check check (
        grace_until is null or (
            trial_started_at is null and plan_id is not null and renewal_failed
            and grace_until > valid_until and grace_until <= valid_until + interval '48 hours'
        )
    );
-- Keep RLS and explicit owner restriction; no change to shared auth or other apps.
revoke all on public.nero_account_plan_v1 from public,anon,authenticated;
grant select on public.nero_account_plan_v1 to authenticated;
create or replace function public.nero_plan_read_v1() returns jsonb
language sql stable security invoker set search_path='' as $$
    with own_plan as (
        select *, case
            when revoked then 'revoked'
            when trial_started_at is not null and trial_started_at > now() then 'free'
            when valid_until > now() then case when trial_started_at is null then 'active' else 'trial' end
            when renewal_failed and grace_until > now() then 'grace'
            else 'expired'
        end as phase
        from public.nero_account_plan_v1
        where owner=(select auth.uid())
          and coalesce((select auth.jwt())->'app_metadata'->>'provider','')='google'
          and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean,false)
    )
    select coalesce((
        select jsonb_build_object(
            'status',case when phase in ('active','trial','grace') then 'active' when phase='expired' then 'expired' else 'none' end,
            'phase',phase,
            'plan_id',case when phase not in ('revoked','free') then plan_id end,
            'access_until',case when phase not in ('revoked','free') then floor(extract(epoch from case when phase='grace' then grace_until else valid_until end))::bigint end,
            'auto_renew',case when phase not in ('revoked','free') then auto_renew end
        ) from own_plan
    ),jsonb_build_object('status','none','phase','free'))
$$;
revoke all on function public.nero_plan_read_v1() from public,anon,authenticated;
grant execute on function public.nero_plan_read_v1() to authenticated;
commit;
-- A separate trusted transactional writer must implement once-per-account trial ledger,
-- provider verification, renewal/revocation, idempotency and immutable event processing.
-- Never allow client-provided status or grace flags. Validate old row compatibility
-- and test RLS with two different users before any production deployment.
