-- Authorized production migration; creation and sync verified with rollback.
CREATE OR REPLACE FUNCTION nero_together_private.command(p_action text, p_room text, p_title text, p_video text, p_artist text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare u uuid:=auth.uid(); r nero_together_private.rooms%rowtype; result jsonb;
begin
 if u is null or NOT nero_identity_private.google_session()
 or coalesce((auth.jwt()->>'is_anonymous')::boolean,false) then raise insufficient_privilege; end if;
 if p_room is null or p_room !~ '^[0-9a-f]{32}$' then raise exception 'invalid room'; end if;
 if p_action='create' then
   if coalesce(public.nero_plan_read_v1()->>'phase','free') not in ('active','trial','grace') then raise insufficient_privilege; end if;
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
end $function$;

CREATE OR REPLACE FUNCTION nero_together_private.sync_v2(p_action text, p_room text, p_video text, p_position bigint, p_playing boolean)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare u uuid:=auth.uid(); r nero_together_private.rooms%rowtype; s nero_together_private.playback_v2%rowtype; q nero_together_private.songs%rowtype; t timestamptz;
begin
 if u is null or NOT nero_identity_private.google_session()
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
   if coalesce(public.nero_plan_read_v1()->>'phase','free') not in ('active','trial','grace') then raise insufficient_privilege; end if;
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
end $function$;
