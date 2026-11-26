-- Authorized NERO-only trial and collaborative queue. No checkout/payment changes.
-- STAGING ONLY. Requires nero_account_plan.sql and nero_account_plan_details_draft.sql.
-- No payment changes. Do not deploy before isolated authorization and concurrency tests.

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

-- Required tests: anonymous/password denied, repeated/concurrent activation,
-- existing paid/expired/revoked rows never overwritten, expiry from DB clock,
-- distinct users isolated, no direct insert/update by authenticated clients.
-- Account deletion/re-registration, rate limiting and abuse policy still need review.

-- Staging deployment draft; do not apply to production without approval.
-- Requires the existing NERO account plan table and Google authentication.

create schema nero_together_private;
revoke all on schema nero_together_private from public,anon,authenticated;
grant usage on schema nero_together_private to authenticated;
create table nero_together_private.rooms(
 id text primary key check(id ~ '^[0-9a-f]{32}$'),
 owner uuid not null references auth.users(id) on delete cascade,
 title text not null check(length(title) between 1 and 80),
 created_at timestamptz not null default now(),
 expires_at timestamptz not null default now()+interval '24 hours',
 closed boolean not null default false
);
create index nero_together_owner_time on nero_together_private.rooms(owner,created_at);
create table nero_together_private.members(
 room text references nero_together_private.rooms(id) on delete cascade,
 member uuid references auth.users(id) on delete cascade,
 primary key(room,member)
);
create table nero_together_private.songs(
 room text references nero_together_private.rooms(id) on delete cascade,
 video text check(video ~ '^[A-Za-z0-9_-]{11}$'),
 title text not null check(length(title) between 1 and 300),
 artist text not null check(length(artist)<=300),
 added_at timestamptz not null default now(),
 primary key(room,video)
);
create table nero_together_private.votes(
 room text, video text, member uuid,
 primary key(room,video,member),
 foreign key(room,video) references nero_together_private.songs(room,video) on delete cascade,
 foreign key(room,member) references nero_together_private.members(room,member) on delete cascade
);
alter table nero_together_private.rooms enable row level security;
alter table nero_together_private.members enable row level security;
alter table nero_together_private.songs enable row level security;
alter table nero_together_private.votes enable row level security;
revoke all on all tables in schema nero_together_private from public,anon,authenticated;
create function nero_together_private.command(p_action text,p_room text,p_title text,p_video text,p_artist text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); r nero_together_private.rooms%rowtype; result jsonb;
begin
 if u is null or coalesce(auth.jwt()->'app_metadata'->>'provider','')<>'google'
 or coalesce((auth.jwt()->>'is_anonymous')::boolean,false) then raise insufficient_privilege; end if;
 if p_room is null or p_room !~ '^[0-9a-f]{32}$' then raise exception 'invalid room'; end if;
 if p_action='create' then
   if not exists(select 1 from public.nero_account_plan_v1 where owner=u and not revoked and (valid_until>now() or (renewal_failed and grace_until>now())) and (trial_started_at is null or trial_started_at<=now())) then raise insufficient_privilege; end if;
   if coalesce(length(trim(p_title)),0) not between 1 and 80 then raise exception 'invalid title'; end if;
   perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(u::text), 18273);
   if (select count(*) from nero_together_private.rooms where owner=u and created_at>now()-interval '24 hours')>=5 then raise exception 'room limit'; end if;
   insert into nero_together_private.rooms(id,owner,title) values(p_room,u,trim(p_title));
   insert into nero_together_private.members(room,member) values(p_room,u);
 end if;
 select * into r from nero_together_private.rooms where id=p_room for update;
 if not found or r.closed or r.expires_at<=now() then raise exception 'room unavailable'; end if;
 if p_action='join' then
   if not exists(select 1 from nero_together_private.members where room=p_room and member=u) then
     if (select count(*) from nero_together_private.members where room=p_room)>=12 then raise exception 'room full'; end if;
     insert into nero_together_private.members(room,member) values(p_room,u);
   end if;
 end if;
 if not exists(select 1 from nero_together_private.members where room=p_room and member=u) then raise insufficient_privilege; end if;
 if p_action='close' then
   if r.owner<>u then raise insufficient_privilege; end if;
   update nero_together_private.rooms set closed=true where id=p_room;
   return jsonb_build_object('closed',true);
 elsif p_action='leave' then
   if r.owner=u then raise exception 'host must close'; end if;
   delete from nero_together_private.members where room=p_room and member=u;
   return jsonb_build_object('closed',true);
 elsif p_action='add' then
   if p_video is null or p_video !~ '^[A-Za-z0-9_-]{11}$' or coalesce(length(trim(p_title)),0) not between 1 and 300 or p_artist is null or length(p_artist)>300 then raise exception 'invalid track'; end if;
   if not exists(select 1 from nero_together_private.songs where room=p_room and video=p_video) then
     if (select count(*) from nero_together_private.songs where room=p_room)>=100 then raise exception 'queue full'; end if;
     insert into nero_together_private.songs(room,video,title,artist) values(p_room,p_video,trim(p_title),p_artist);
   end if;
 elsif p_action='vote' then
   if not exists(select 1 from nero_together_private.songs where room=p_room and video=p_video) then raise exception 'track unavailable'; end if;
   insert into nero_together_private.votes(room,video,member) values(p_room,p_video,u) on conflict do nothing;
 elsif p_action='unvote' then
   delete from nero_together_private.votes where room=p_room and video=p_video and member=u;
 elsif p_action not in ('create','join','read') or p_action is null then raise exception 'invalid action'; end if;
 select jsonb_build_object('id',r.id,'title',r.title,'host',r.owner=u,'members',(select count(*) from nero_together_private.members where room=p_room),
 'songs',coalesce((select jsonb_agg(jsonb_build_object('video',s.video,'title',s.title,'artist',s.artist,'votes',s.n,'voted',s.voted) order by s.n desc,s.added_at,s.video)
 from (select q.*, (select count(*) from nero_together_private.votes v where v.room=q.room and v.video=q.video) n,
 exists(select 1 from nero_together_private.votes v where v.room=q.room and v.video=q.video and v.member=u) voted
 from nero_together_private.songs q where q.room=p_room) s),'[]'::jsonb)) into result;
 return result;
end $$;
revoke all on function nero_together_private.command(text,text,text,text,text) from public,anon,authenticated;
grant execute on function nero_together_private.command(text,text,text,text,text) to authenticated;
create function public.nero_together_v1(p_action text,p_room text,p_title text,p_video text,p_artist text)
returns jsonb language sql security invoker set search_path='' as $$
 select nero_together_private.command(p_action,p_room,p_title,p_video,p_artist)
$$;
revoke all on function public.nero_together_v1(text,text,text,text,text) from public,anon,authenticated;
grant execute on function public.nero_together_v1(text,text,text,text,text) to authenticated;

-- Expired rooms are inaccessible, not automatically deleted. Staging retention
-- job and API abuse/rate limit tests are required before deployment.

