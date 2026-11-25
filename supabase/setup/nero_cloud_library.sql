-- Deployment draft: apply only on an authorized, dedicated NERO test project.
-- Does not change any existing tables or authentication settings.
begin;
create table public.nero_cloud_library_v1 (
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
alter table public.nero_cloud_library_v1 enable row level security;
revoke all on public.nero_cloud_library_v1 from public, anon, authenticated;
grant select, insert, update on public.nero_cloud_library_v1 to authenticated;
create policy nero_cloud_read on public.nero_cloud_library_v1 for select to authenticated
    using ((select auth.uid())=owner and coalesce((select auth.jwt())->'app_metadata'->>'provider','')='google'
        and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean,false));
create policy nero_cloud_insert on public.nero_cloud_library_v1 for insert to authenticated
    with check ((select auth.uid())=owner and coalesce((select auth.jwt())->'app_metadata'->>'provider','')='google'
        and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean,false));
create policy nero_cloud_update on public.nero_cloud_library_v1 for update to authenticated
    using ((select auth.uid())=owner and coalesce((select auth.jwt())->'app_metadata'->>'provider','')='google'
        and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean,false))
    with check ((select auth.uid())=owner and coalesce((select auth.jwt())->'app_metadata'->>'provider','')='google'
        and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean,false));
create function public.nero_cloud_read_v1() returns jsonb language sql stable security invoker set search_path='' as $$
    select coalesce((select jsonb_build_object('revision',revision,'payload',payload)
        from public.nero_cloud_library_v1 where owner=(select auth.uid())),
        jsonb_build_object('revision',0,'payload',null))
$$;
create function public.nero_cloud_write_v1(p_expected bigint,p_payload jsonb) returns jsonb
language plpgsql security invoker set search_path='' as $$
declare v_revision bigint;
begin
    if auth.uid() is null or coalesce(auth.jwt()->'app_metadata'->>'provider','')<>'google'
       or coalesce((auth.jwt()->>'is_anonymous')::boolean,false) then raise insufficient_privilege; end if;
    if p_expected is null or p_expected<0 or p_expected>=9007199254740991 or p_payload is null then raise exception 'invalid backup'; end if;
    if p_expected=0 then
        insert into public.nero_cloud_library_v1(owner,revision,payload)
        values(auth.uid(),1,p_payload) on conflict(owner) do nothing returning revision into v_revision;
    else
        update public.nero_cloud_library_v1 set revision=revision+1,payload=p_payload,updated_at=now()
        where owner=auth.uid() and revision=p_expected returning revision into v_revision;
    end if;
    return jsonb_build_object('saved',v_revision is not null,'revision',v_revision);
end $$;
revoke all on function public.nero_cloud_read_v1() from public, anon, authenticated;
revoke all on function public.nero_cloud_write_v1(bigint,jsonb) from public, anon, authenticated;
grant execute on function public.nero_cloud_read_v1() to authenticated;
grant execute on function public.nero_cloud_write_v1(bigint,jsonb) to authenticated;
commit;
