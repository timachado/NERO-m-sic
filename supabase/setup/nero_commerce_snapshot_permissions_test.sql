-- STAGING ONLY: run as administrator after installing the draft schema.
-- Metadata assertions; never touches auth users or financial data.
begin;
do $$
begin
 if has_function_privilege('anon','public.nero_commerce_snapshot_v1(uuid,bigint,text,text,timestamptz,boolean)','EXECUTE') then raise exception 'anon can write'; end if;
 if has_function_privilege('authenticated','public.nero_commerce_snapshot_v1(uuid,bigint,text,text,timestamptz,boolean)','EXECUTE') then raise exception 'authenticated can write'; end if;
 if not has_function_privilege('service_role','public.nero_commerce_snapshot_v1(uuid,bigint,text,text,timestamptz,boolean)','EXECUTE') then raise exception 'service role unavailable'; end if;
 if has_table_privilege('authenticated','nero_commerce_private.snapshots','SELECT') or has_table_privilege('authenticated','nero_commerce_private.snapshots','INSERT') then raise exception 'ledger exposed'; end if;
 if has_table_privilege('authenticated','public.nero_account_plan_v1','UPDATE') or has_table_privilege('authenticated','public.nero_account_plan_v1','INSERT') then raise exception 'plan writable'; end if;
 if not (select relrowsecurity from pg_class where oid='nero_commerce_private.snapshots'::regclass) then raise exception 'RLS disabled'; end if;
end $$;
rollback;
-- Separate synthetic identity tests required: duplicate, conflict, stale,
-- two users, refund/revocation, trial conversion, race and no unauthorized grants.
