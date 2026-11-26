-- NERO-only additive migration; commercial writes deliberately absent.
-- DRAFT ONLY. Apply on an authorized NERO staging database before production.
-- No changes to shared authentication, payments or existing app tables.

create table public.nero_account_plan_v1 (
    owner uuid primary key references auth.users(id) on delete cascade,
    valid_until timestamptz not null,
    revoked boolean not null default false
);
alter table public.nero_account_plan_v1 enable row level security;
revoke all on public.nero_account_plan_v1 from public, anon, authenticated;
grant select on public.nero_account_plan_v1 to authenticated;
create policy nero_plan_owner_read on public.nero_account_plan_v1
    for select to authenticated using (
        owner=(select auth.uid())
        and coalesce((select auth.jwt())->'app_metadata'->>'provider','')='google'
        and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean,false)
    );
create function public.nero_plan_read_v1() returns jsonb
    language sql stable security invoker set search_path='' as $$
    select jsonb_build_object('status',coalesce(
        (select case when revoked then 'none'
          when valid_until>now() then 'active' else 'expired' end
         from public.nero_account_plan_v1 where owner=(select auth.uid())),
        'none'))
$$;
revoke all on function public.nero_plan_read_v1() from public,anon,authenticated;
grant execute on function public.nero_plan_read_v1() to authenticated;

-- No client INSERT/UPDATE/DELETE. Future verified payment webhook/service must
-- manage entitlements on the server; do not grant access based on client claims.

-- DRAFT: requires the original nero_account_plan.sql in an isolated staging DB.
-- DO NOT APPLY to production. No client write, trial activation or payment event handler.

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

-- A separate trusted transactional writer must implement once-per-account trial ledger,
-- provider verification, renewal/revocation, idempotency and immutable event processing.
-- Never allow client-provided status or grace flags. Validate old row compatibility
-- and test RLS with two different users before any production deployment.

-- Deployment draft: apply only on an authorized, dedicated NERO test project.
-- Does not change any existing tables or authentication settings.

create schema nero_cloud_private;
revoke all on schema nero_cloud_private from public,anon,authenticated;
grant usage on schema nero_cloud_private to authenticated;
create table nero_cloud_private.nero_cloud_library_v1 (
    owner uuid primary key references auth.users(id) on delete cascade,
    revision bigint not null check (revision between 1 and 9007199254740991),
    updated_at timestamptz not null default now(),
    payload jsonb not null check (
        jsonb_typeof(payload) = 'object'
        and payload->>'format' is not distinct from 'nero-cloud-v1'
        and jsonb_typeof(payload->'favorites') is not distinct from 'array'
        and jsonb_typeof(payload->'playlists') is not distinct from 'array'
        and octet_length(payload::text) <= 300000
    )
);
alter table nero_cloud_private.nero_cloud_library_v1 enable row level security;
revoke all on nero_cloud_private.nero_cloud_library_v1 from public, anon, authenticated;
-- No direct authenticated table grants.
create policy nero_cloud_read on nero_cloud_private.nero_cloud_library_v1 for select to authenticated
    using ((select auth.uid())=owner and coalesce((select auth.jwt())->'app_metadata'->>'provider','')='google'
        and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean,false));
create policy nero_cloud_insert on nero_cloud_private.nero_cloud_library_v1 for insert to authenticated
    with check ((select auth.uid())=owner and coalesce((select auth.jwt())->'app_metadata'->>'provider','')='google'
        and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean,false));
create policy nero_cloud_update on nero_cloud_private.nero_cloud_library_v1 for update to authenticated
    using ((select auth.uid())=owner and coalesce((select auth.jwt())->'app_metadata'->>'provider','')='google'
        and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean,false))
    with check ((select auth.uid())=owner and coalesce((select auth.jwt())->'app_metadata'->>'provider','')='google'
        and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean,false));
create function nero_cloud_private.read_library() returns jsonb language sql stable security definer set search_path='' as $$
    select coalesce((select jsonb_build_object('revision',revision,'payload',payload)
        from nero_cloud_private.nero_cloud_library_v1 where owner=(select auth.uid())
        and coalesce((select auth.jwt())->'app_metadata'->>'provider','')='google'
        and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean,false)),
        jsonb_build_object('revision',0,'payload',null))
$$;
create function nero_cloud_private.write_library(p_expected bigint,p_payload jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare v_revision bigint;
begin
    if auth.uid() is null or coalesce(auth.jwt()->'app_metadata'->>'provider','')<>'google'
       or coalesce((auth.jwt()->>'is_anonymous')::boolean,false) then raise insufficient_privilege; end if;
    if p_expected is null or p_expected<0 or p_expected>=9007199254740991 or p_payload is null then raise exception 'invalid backup'; end if;
    if jsonb_typeof(p_payload) is distinct from 'object'
       or p_payload->>'format' is distinct from 'nero-cloud-v1'
       or octet_length(p_payload::text)>300000 then raise exception 'invalid library'; end if;
    if jsonb_typeof(p_payload->'favorites') is distinct from 'array'
       or jsonb_typeof(p_payload->'playlists') is distinct from 'array' then raise exception 'invalid collection'; end if;
    if jsonb_array_length(p_payload->'favorites')>1000 or jsonb_array_length(p_payload->'playlists')>100 then raise exception 'collection limit'; end if;
    if exists(select 1 from jsonb_array_elements(p_payload->'playlists') p where jsonb_typeof(p) is distinct from 'object' or jsonb_typeof(p->'songs') is distinct from 'array') then raise exception 'invalid playlist'; end if;
    if exists(select 1 from jsonb_array_elements(p_payload->'playlists') p where jsonb_array_length(p->'songs')>1000) then raise exception 'playlist limit'; end if;
    if p_expected=0 then
        insert into nero_cloud_private.nero_cloud_library_v1(owner,revision,payload)
        values(auth.uid(),1,p_payload) on conflict(owner) do nothing returning revision into v_revision;
    else
        update nero_cloud_private.nero_cloud_library_v1 set revision=revision+1,payload=p_payload,updated_at=now()
        where owner=auth.uid() and revision=p_expected returning revision into v_revision;
    end if;
    return jsonb_build_object('saved',v_revision is not null,'revision',v_revision);
end $$;
revoke all on function nero_cloud_private.read_library(),nero_cloud_private.write_library(bigint,jsonb) from public,anon,authenticated;
grant execute on function nero_cloud_private.read_library(),nero_cloud_private.write_library(bigint,jsonb) to authenticated;
create function public.nero_cloud_read_v1() returns jsonb language sql stable security invoker set search_path='' as $$ select nero_cloud_private.read_library() $$;
create function public.nero_cloud_write_v1(p_expected bigint,p_payload jsonb) returns jsonb language sql security invoker set search_path='' as $$ select nero_cloud_private.write_library(p_expected,p_payload) $$;
revoke all on function public.nero_cloud_read_v1() from public, anon, authenticated;
revoke all on function public.nero_cloud_write_v1(bigint,jsonb) from public, anon, authenticated;
grant execute on function public.nero_cloud_read_v1() to authenticated;
grant execute on function public.nero_cloud_write_v1(bigint,jsonb) to authenticated;

