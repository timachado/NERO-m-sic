-- Run ONLY in the disposable CI database created by the companion fixture.
begin;
set local request.jwt.claim.sub='00000000-0000-4000-8000-000000000001';
set local request.jwt.claims='{"app_metadata":{"provider":"google"},"is_anonymous":false}';
set local role authenticated;
do $$
begin
 if public.nero_plan_read_v1()->>'phase' is distinct from 'free' then raise exception 'initial free'; end if;
 if public.nero_trial_start_v1()->>'result' is distinct from 'started' then raise exception 'trial'; end if;
 if public.nero_plan_read_v1()->>'phase' is distinct from 'trial' then raise exception 'trial status'; end if;
 if public.nero_trial_start_v1()->>'result' is distinct from 'ineligible' then raise exception 'repeat trial'; end if;
 begin
   perform public.nero_commerce_snapshot_v1('00000000-0000-4000-8000-000000000001',1,repeat('a',64),'premium_monthly',now()+interval '30 days',false);
   raise exception 'client could write entitlement';
 exception when insufficient_privilege then null;
 end;
end $$;
reset role;
set local role service_role;
do $$
declare a uuid:='00000000-0000-4000-8000-000000000001'; b uuid:='00000000-0000-4000-8000-000000000002'; until_at timestamptz:=now()+interval '30 days';
begin
 if public.nero_commerce_snapshot_v1(a,1,repeat('a',64),'premium_monthly',until_at,false)->>'result' is distinct from 'applied' then raise exception 'purchase'; end if;
 if public.nero_commerce_snapshot_v1(a,1,repeat('a',64),'premium_monthly',until_at,false)->>'result' is distinct from 'duplicate' then raise exception 'duplicate'; end if;
 begin
   perform public.nero_commerce_snapshot_v1(a,1,repeat('b',64),'premium_monthly',until_at,false);
   raise exception 'conflict accepted';
 exception when raise_exception then
   if sqlerrm<>'revision conflict' then raise; end if;
 end;
 if public.nero_commerce_snapshot_v1(a,2,repeat('c',64),'premium_monthly',until_at+interval '30 days',false)->>'result' is distinct from 'applied' then raise exception 'renewal'; end if;
 if public.nero_commerce_snapshot_v1(a,1,repeat('a',64),'premium_monthly',until_at,false)->>'result' is distinct from 'stale' then raise exception 'out of order'; end if;
 if public.nero_commerce_snapshot_v1(b,1,repeat('d',64),'premium_quarterly',now()+interval '90 days',false)->>'result' is distinct from 'applied' then raise exception 'second owner'; end if;
 if public.nero_commerce_snapshot_v1(a,3,repeat('e',64),null,now(),true)->>'result' is distinct from 'applied' then raise exception 'revocation'; end if;
 if public.nero_commerce_snapshot_v1(a,2,repeat('c',64),'premium_monthly',until_at+interval '30 days',false)->>'result' is distinct from 'stale' then raise exception 'revocation bypass'; end if;
end $$;
reset role;
set local role authenticated;
do $$
begin
 if public.nero_plan_read_v1()->>'phase' is distinct from 'revoked' then raise exception 'refund status'; end if;
 if public.nero_trial_start_v1()->>'result' is distinct from 'ineligible' then raise exception 'trial restored'; end if;
 if (select count(*) from public.nero_account_plan_v1)<>1 then raise exception 'cross-account visibility'; end if;
end $$;
set local request.jwt.claim.sub='00000000-0000-4000-8000-000000000002';
do $$
begin
 if public.nero_plan_read_v1()->>'phase' is distinct from 'active' then raise exception 'second account status'; end if;
 if public.nero_plan_read_v1()->>'plan_id' is distinct from 'premium_quarterly' then raise exception 'second account plan'; end if;
 if (select count(*) from public.nero_account_plan_v1)<>1 then raise exception 'cross-account visibility'; end if;
end $$;
reset role;
rollback;
