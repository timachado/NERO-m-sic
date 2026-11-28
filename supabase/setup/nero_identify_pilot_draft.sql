-- DRAFT ONLY: not deployed. Independent from commerce, library and entitlement.
-- Pilot lifetime ceiling: 200 provider attempts globally, 10 per account.
-- Reserve before calling AudD; failures count too. No audio stored.
begin;
create table public.nero_identify_pilot_budget (
  singleton boolean primary key default true check (singleton),
  used integer not null default 0 check (used between 0 and 200)
);
insert into public.nero_identify_pilot_budget(singleton) values(true);
create table public.nero_identify_pilot_usage (
  user_id uuid primary key,
  used integer not null default 0 check (used between 0 and 10),
  last_attempt timestamptz not null
);
alter table public.nero_identify_pilot_budget enable row level security;
alter table public.nero_identify_pilot_usage enable row level security;
revoke all on public.nero_identify_pilot_budget, public.nero_identify_pilot_usage from public, anon, authenticated;
grant select,update on public.nero_identify_pilot_budget to service_role;
grant select,insert,update on public.nero_identify_pilot_usage to service_role;
create function public.nero_identify_reserve_pilot(p_user uuid)
returns boolean language plpgsql security invoker set search_path = '' as $$
declare total integer; account_used integer; last_at timestamptz;
begin
  if p_user is null then return false; end if;
  select used into total from public.nero_identify_pilot_budget where singleton for update;
  if total is null or total >= 200 then return false; end if;
  select used,last_attempt into account_used,last_at from public.nero_identify_pilot_usage where user_id=p_user;
  if coalesce(account_used,0)>=10 or (last_at is not null and last_at > clock_timestamp() - interval '15 seconds') then return false; end if;
  insert into public.nero_identify_pilot_usage(user_id,used,last_attempt) values(p_user,1,clock_timestamp())
  on conflict(user_id) do update set used=public.nero_identify_pilot_usage.used+1,last_attempt=clock_timestamp();
  update public.nero_identify_pilot_budget set used=used+1 where singleton;
  return true;
end;
$$;
revoke all on function public.nero_identify_reserve_pilot(uuid) from public, anon, authenticated;
grant execute on function public.nero_identify_reserve_pilot(uuid) to service_role;
commit;
