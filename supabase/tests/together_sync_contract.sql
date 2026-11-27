-- Disposable PostgreSQL only. Never run on production.
begin;
insert into auth.users values ('00000000-0000-4000-8000-000000000003');
set local request.jwt.claims='{"app_metadata":{"provider":"google"},"is_anonymous":false}';
set local request.jwt.claim.sub='00000000-0000-4000-8000-000000000001';
set local role authenticated;
do $$
declare room text:=repeat('a',32); state jsonb;
begin
 perform public.nero_trial_start_v1();
 perform public.nero_together_v1('create',room,'Sync test','','');
 perform public.nero_together_v1('add',room,'Track','abcdefghijk','Artist');
 state:=public.nero_together_sync_v2('read',room,'',0,false);
 if state->>'active' is distinct from 'false' then raise exception 'unexpected initial state'; end if;
 state:=public.nero_together_sync_v2('publish',room,'abcdefghijk',1234,true);
 if state->>'active' is distinct from 'true' or state->>'position_ms' is distinct from '1234' or state->>'playing' is distinct from 'true' then raise exception 'publish mismatch'; end if;
 begin
   perform public.nero_together_sync_v2('publish',room,'abcdefghijk',-1,true);
   raise exception 'invalid position accepted';
 exception when raise_exception then if sqlerrm<>'invalid playback' then raise; end if; end;
 begin
   perform public.nero_together_sync_v2('publish',room,'zzzzzzzzzzz',0,true);
   raise exception 'nonqueue track accepted';
 exception when raise_exception then if sqlerrm<>'track unavailable' then raise; end if; end;
end $$;
set local request.jwt.claim.sub='00000000-0000-4000-8000-000000000002';
do $$
declare room text:=repeat('a',32); state jsonb;
begin
 begin
   perform public.nero_together_sync_v2('read',room,'',0,false);
   raise exception 'nonmember read accepted';
 exception when insufficient_privilege then null; end;
 perform public.nero_together_v1('join',room,'','','');
 state:=public.nero_together_sync_v2('read',room,'',0,false);
 if state->>'video' is distinct from 'abcdefghijk' then raise exception 'member read mismatch'; end if;
 begin
   perform public.nero_together_sync_v2('publish',room,'abcdefghijk',0,false);
   raise exception 'guest publish accepted';
 exception when insufficient_privilege then null; end;
 begin
   perform public.nero_together_sync_v2('stop',room,'',0,false);
   raise exception 'guest stop accepted';
 exception when insufficient_privilege then null; end;
 begin
   perform * from nero_together_private.playback_v2;
   raise exception 'direct table access accepted';
 exception when insufficient_privilege then null; end;
end $$;
reset role;
update nero_together_private.playback_v2 set updated_at=clock_timestamp()-interval '13 seconds' where room=repeat('a',32);
set local role authenticated;
do $$ begin
 if public.nero_together_sync_v2('read',repeat('a',32),'',0,false)->>'active' is distinct from 'false' then raise exception 'stale host remained active'; end if;
end $$;
set local request.jwt.claim.sub='00000000-0000-4000-8000-000000000001';
do $$
declare room text:=repeat('a',32);
begin
 if public.nero_together_sync_v2('publish',room,'abcdefghijk',4321,false)->>'playing' is distinct from 'false' then raise exception 'pause mismatch'; end if;
 if public.nero_together_sync_v2('stop',room,'',0,false)->>'active' is distinct from 'false' then raise exception 'stop mismatch'; end if;
 perform public.nero_together_v1('close',room,'','','');
 begin
   perform public.nero_together_sync_v2('read',room,'',0,false);
   raise exception 'closed room readable';
 exception when raise_exception then if sqlerrm<>'room unavailable' then raise; end if; end;
end $$;
reset role;
set local role anon;
do $$ begin
 begin
   perform public.nero_together_sync_v2('read',repeat('a',32),'',0,false);
   raise exception 'anonymous accepted';
 exception when insufficient_privilege then null; end;
end $$;
reset role;
rollback;
