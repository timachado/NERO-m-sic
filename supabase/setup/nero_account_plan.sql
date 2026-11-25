-- DRAFT ONLY. Apply on an authorized NERO staging database before production.
-- No changes to shared authentication, payments or existing app tables.
begin;
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
commit;
-- No client INSERT/UPDATE/DELETE. Future verified payment webhook/service must
-- manage entitlements on the server; do not grant access based on client claims.
