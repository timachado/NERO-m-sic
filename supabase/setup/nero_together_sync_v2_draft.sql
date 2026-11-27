-- Draft for isolated staging validation only; no production application.
begin;
create table nero_together_private.playback_v2(
 room text primary key references nero_together_private.rooms(id) on delete cascade,
 video text not null,
 position_ms bigint not null check(position_ms between 0 and 86400000),
 playing boolean not null,
 updated_at timestamptz not null,
 foreign key(room,video) references nero_together_private.songs(room,video) on delete cascade
);
alter table nero_together_private.playback_v2 enable row level security;
revoke all on nero_together_private.playback_v2 from public,anon,authenticated;
create function nero_together_private.sync_v2(p_action text,p_room text,p_video text,p_position bigint,p_playing boolean)
returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); r nero_together_private.rooms%rowtype; s nero_together_private.playback_v2%rowtype; q nero_together_private.songs%rowtype; t timestamptz;
begin
 if u is null or coalesce(auth.jwt()->'app_metadata'->>'provider','')<>'google'
 or coalesce((auth.jwt()->>'is_anonymous')::boolean,false) then raise insufficient_privilege; end if;
 if p_room is null or p_room !~ '^[0-9a-f]{32}$' then raise exception 'invalid room'; end if;
 select * into r from nero_together_private.rooms where id=p_room for update;
 if not found or r.closed or r.expires_at<=clock_timestamp() then raise exception 'room unavailable'; end if;
 if not exists(select 1 from nero_together_private.members where room=p_room and member=u) then raise insufficient_privilege; end if;
 t:=clock_timestamp();
 if p_action in ('publish','stop') then
   if r.owner<>u then raise insufficient_privilege; end if;
   if p_action='stop' then
     delete from nero_together_private.playback_v2 where room=p_room;
     return jsonb_build_object('active',false);
   end if;
   if not exists(select 1 from public.nero_account_plan_v1 where owner=u and not revoked and (valid_until>t or (renewal_failed and grace_until>t)) and (trial_started_at is null or trial_started_at<=t)) then raise insufficient_privilege; end if;
   if p_video is null or p_video !~ '^[A-Za-z0-9_-]{11}$' or p_position is null or p_position not between 0 and 86400000 or p_playing is null then raise exception 'invalid playback'; end if;
   if not exists(select 1 from nero_together_private.songs where room=p_room and video=p_video) then raise exception 'track unavailable'; end if;
   insert into nero_together_private.playback_v2(room,video,position_ms,playing,updated_at)
    values(p_room,p_video,p_position,p_playing,t)
    on conflict(room) do update set video=excluded.video,position_ms=excluded.position_ms,playing=excluded.playing,updated_at=excluded.updated_at
    where nero_together_private.playback_v2.updated_at <= t-interval '500 milliseconds';
 elsif p_action is distinct from 'read' then raise exception 'invalid action'; end if;
 select * into s from nero_together_private.playback_v2 where room=p_room;
 if not found or s.updated_at<t-interval '12 seconds' then return jsonb_build_object('active',false); end if;
 select * into q from nero_together_private.songs where room=p_room and video=s.video;
 return jsonb_build_object('active',true,'video',s.video,'title',q.title,'artist',q.artist,'position_ms',s.position_ms,'playing',s.playing,'age_ms',greatest(0,(extract(epoch from (t-s.updated_at))*1000)::bigint));
end $$;
revoke all on function nero_together_private.sync_v2(text,text,text,bigint,boolean) from public,anon,authenticated;
grant execute on function nero_together_private.sync_v2(text,text,text,bigint,boolean) to authenticated;
create function public.nero_together_sync_v2(p_action text,p_room text,p_video text,p_position bigint,p_playing boolean)
returns jsonb language sql security invoker set search_path='' as $$
 select nero_together_private.sync_v2(p_action,p_room,p_video,p_position,p_playing)
$$;
revoke all on function public.nero_together_sync_v2(text,text,text,bigint,boolean) from public,anon,authenticated;
grant execute on function public.nero_together_sync_v2(text,text,text,bigint,boolean) to authenticated;
commit;
