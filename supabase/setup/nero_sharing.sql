-- Reviewed deployment draft. Run only on a dedicated NERO project after authorization.
begin;
create schema nero_private;
revoke all on schema nero_private from public, anon, authenticated;
grant usage on schema nero_private to anon, authenticated;
create table nero_private.shares (
 id text primary key check(id ~ '^[0-9a-f]{32}$'),
 owner uuid not null references auth.users(id) on delete cascade,
 created_at timestamptz not null default now(),
 title text not null check(length(title) between 1 and 100),
 subtitle text not null check(length(subtitle)<=300),
 count int not null check(count between 1 and 1000),
 single boolean not null,
 payload text not null check(octet_length(payload)<=180000)
);
alter table nero_private.shares enable row level security;
revoke all on nero_private.shares from public, anon, authenticated;
create index nero_share_owner_time on nero_private.shares(owner,created_at desc);
create function nero_private.is_google() returns boolean language sql stable security invoker set search_path='' as $$
 select coalesce(auth.uid() is not null and auth.jwt()->'app_metadata'->>'provider'='google'
   and auth.jwt()->'amr' @> '[{"method":"oauth"}]'::jsonb
   and not coalesce((auth.jwt()->>'is_anonymous')::boolean,false),false)
$$;
create function nero_private.create_share(p_id text,p_payload text,p_single boolean)
returns jsonb language plpgsql security definer set search_path='' as $$
declare j jsonb; n int; nm text; sub text;
begin
 if not nero_private.is_google() then raise insufficient_privilege; end if;
 if p_id is null or p_id !~ '^[0-9a-f]{32}$' or p_payload is null or octet_length(p_payload)>180000 or p_single is null then raise exception 'invalid share'; end if;
 j:=p_payload::jsonb;
 if j->>'format' is distinct from 'nero-playlist-v1' or jsonb_typeof(j->'songs') is distinct from 'array' then raise exception 'invalid share'; end if;
 n:=jsonb_array_length(j->'songs'); nm:=left(trim(j->>'name'),100);
 if nm is null or nm='' or n<1 or n>1000 or (p_single and n<>1) then raise exception 'invalid share'; end if;
 if exists(select 1 from jsonb_array_elements(j->'songs') s where jsonb_typeof(s) is distinct from 'object' or coalesce(length(trim(s->>'title')),0) not between 1 and 300 or coalesce(length(s->>'artist'),0)>300 or (s->>'videoId' is not null and s->>'videoId' !~ '^[A-Za-z0-9_-]{11}$')) then raise exception 'invalid songs'; end if;
 sub:=case when p_single then left(coalesce(j->'songs'->0->>'artist',''),300) else 'Playlist NERO Music' end;
 perform pg_advisory_xact_lock(hashtext(auth.uid()::text));
 if (select count(*) from nero_private.shares where owner=auth.uid() and created_at>now()-interval '1 day')>=100 then raise exception 'share limit'; end if;
 insert into nero_private.shares(id,owner,title,subtitle,count,single,payload) values(p_id,auth.uid(),nm,sub,n,p_single,p_payload);
 return jsonb_build_object('created',true);
end $$;
create function nero_private.preview_share(p_id text) returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object('title',title,'subtitle',subtitle,'count',count,'single',single)
 from nero_private.shares where id=p_id and p_id ~ '^[0-9a-f]{32}$' and created_at>now()-interval '7 days'
$$;
create function nero_private.read_share(p_id text) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare result jsonb;
begin
 if not nero_private.is_google() then raise insufficient_privilege; end if;
 select jsonb_build_object('payload',payload) into result from nero_private.shares where id=p_id and p_id ~ '^[0-9a-f]{32}$' and created_at>now()-interval '7 days';
 return result;
end $$;
revoke all on function nero_private.is_google(),nero_private.create_share(text,text,boolean),nero_private.preview_share(text),nero_private.read_share(text) from public,anon,authenticated;
grant execute on function nero_private.preview_share(text) to anon,authenticated;
grant execute on function nero_private.create_share(text,text,boolean),nero_private.read_share(text) to authenticated;
create function public.nero_share_create(p_id text,p_payload text,p_single boolean) returns jsonb language sql security invoker set search_path='' as $$ select nero_private.create_share(p_id,p_payload,p_single) $$;
create function public.nero_share_preview(p_id text) returns jsonb language sql stable security invoker set search_path='' as $$ select nero_private.preview_share(p_id) $$;
create function public.nero_share_read(p_id text) returns jsonb language sql stable security invoker set search_path='' as $$ select nero_private.read_share(p_id) $$;
revoke all on function public.nero_share_create(text,text,boolean),public.nero_share_preview(text),public.nero_share_read(text) from public,anon,authenticated;
grant execute on function public.nero_share_preview(text) to anon,authenticated;
grant execute on function public.nero_share_create(text,text,boolean),public.nero_share_read(text) to authenticated;
commit;
