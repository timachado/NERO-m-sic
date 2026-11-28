-- DRAFT: requires owner approval before deployment. No changes to existing objects.
begin;
create table public.nero_lyrics_ai_pilot_budget(
  singleton boolean primary key default true check(singleton),
  attempts integer not null default 0 check(attempts between 0 and 5),
  seconds_reserved integer not null default 0 check(seconds_reserved between 0 and 1500),
  last_attempt timestamptz
);
insert into public.nero_lyrics_ai_pilot_budget(singleton) values(true);
alter table public.nero_lyrics_ai_pilot_budget enable row level security;
revoke all on public.nero_lyrics_ai_pilot_budget from public,anon,authenticated;
grant select,update on public.nero_lyrics_ai_pilot_budget to service_role;
create function public.nero_lyrics_ai_reserve_pilot(p_seconds integer)
returns boolean language plpgsql security invoker set search_path='' as $$
declare b public.nero_lyrics_ai_pilot_budget%rowtype;
begin
  if p_seconds is null or p_seconds<1 or p_seconds>300 then return false; end if;
  select * into b from public.nero_lyrics_ai_pilot_budget where singleton for update;
  if not found or b.attempts>=5 or b.seconds_reserved+p_seconds>1500
      or (b.last_attempt is not null and b.last_attempt>clock_timestamp()-interval '120 seconds') then return false; end if;
  update public.nero_lyrics_ai_pilot_budget
  set attempts=attempts+1,seconds_reserved=seconds_reserved+p_seconds,last_attempt=clock_timestamp()
  where singleton;
  return true;
end;
$$;
revoke all on function public.nero_lyrics_ai_reserve_pilot(integer) from public,anon,authenticated;
grant execute on function public.nero_lyrics_ai_reserve_pilot(integer) to service_role;
commit;
