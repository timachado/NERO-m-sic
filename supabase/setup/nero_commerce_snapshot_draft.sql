-- STAGING ONLY. Requires nero_account_plan.sql, details and trial drafts.
-- Receives a COMPLETE authoritative entitlement snapshot from the trusted Core
-- adapter, not a webhook directly or client input. No payment operations.
-- The adapter MUST verify the buyer identity and provider evidence, serialize
-- reconciliation and generate a strictly increasing revision per owner.
begin;
create schema nero_commerce_private;
revoke all on schema nero_commerce_private from public,anon,authenticated;
grant usage on schema nero_commerce_private to service_role;
create table nero_commerce_private.snapshots (
 owner uuid not null references auth.users(id) on delete cascade,
 revision bigint not null check(revision>0),
 evidence_hash text not null check(evidence_hash ~ '^[0-9a-f]{64}$'),
 plan_id text check(plan_id in ('premium_monthly','premium_quarterly','premium_annual')),
 valid_until timestamptz not null,
 revoked boolean not null,
 received_at timestamptz not null default now(),
 primary key(owner,revision),
 unique(evidence_hash)
);
alter table nero_commerce_private.snapshots enable row level security;
revoke all on nero_commerce_private.snapshots from public,anon,authenticated;
grant select,insert on nero_commerce_private.snapshots to service_role;
grant select,insert,update on public.nero_account_plan_v1 to service_role;
create function public.nero_commerce_snapshot_v1(
 p_owner uuid,p_revision bigint,p_evidence_hash text,p_plan_id text,
 p_valid_until timestamptz,p_revoked boolean
) returns jsonb language plpgsql security invoker set search_path='' as $$
declare last_row nero_commerce_private.snapshots%rowtype;
begin
 -- Only the server adapter is allowed to invoke this function.
 if current_user <> 'service_role' then raise insufficient_privilege; end if;
 if p_owner is null or p_revision is null or p_revision<=0
 or p_evidence_hash is null or p_evidence_hash !~ '^[0-9a-f]{64}$'
 or p_valid_until is null or not pg_catalog.isfinite(p_valid_until)
 or p_revoked is null
 or (p_plan_id is not null and p_plan_id not in ('premium_monthly','premium_quarterly','premium_annual'))
 or (not p_revoked and (p_plan_id is null or p_valid_until<=now()))
 or p_valid_until>now()+interval '370 days' then
   raise exception 'invalid snapshot';
 end if;
 -- Match trial activation lock so a paid event cannot race a trial.
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(p_owner::text),28461);
 select * into last_row from nero_commerce_private.snapshots where owner=p_owner
 order by revision desc limit 1;
 if found then
   if p_revision<last_row.revision then return jsonb_build_object('result','stale'); end if;
   if p_revision=last_row.revision then
     if last_row.evidence_hash=p_evidence_hash and last_row.plan_id is not distinct from p_plan_id
       and last_row.valid_until=p_valid_until and last_row.revoked=p_revoked then
       return jsonb_build_object('result','duplicate');
     end if;
     raise exception 'revision conflict';
   end if;
 end if;
 insert into nero_commerce_private.snapshots(owner,revision,evidence_hash,plan_id,valid_until,revoked)
 values(p_owner,p_revision,p_evidence_hash,p_plan_id,p_valid_until,p_revoked);
 -- Preserve trial usage ledger; never restore an old trial after a refund.
 insert into public.nero_account_plan_v1(owner,valid_until,revoked,plan_id,trial_started_at,renewal_failed,grace_until,auto_renew)
 values(p_owner,p_valid_until,p_revoked,p_plan_id,null,false,null,null)
 on conflict(owner) do update set valid_until=excluded.valid_until,revoked=excluded.revoked,
 plan_id=excluded.plan_id,trial_started_at=null,renewal_failed=false,grace_until=null,auto_renew=null;
 return jsonb_build_object('result','applied');
end $$;
revoke all on function public.nero_commerce_snapshot_v1(uuid,bigint,text,text,timestamptz,boolean) from public,anon,authenticated;
grant execute on function public.nero_commerce_snapshot_v1(uuid,bigint,text,text,timestamptz,boolean) to service_role;
commit;
-- No automatic grants from email, order creation, client callback, or browser return.
-- The trusted adapter is NOT implemented/deployed by this migration. It must
-- reconcile full current order history (including refunds) and issue revisions
-- durably, with verified identity and a distinct evidence hash for each snapshot.
-- Bounds deliberately reject prepaid periods beyond 370 days; review before launch.
-- Test in isolated PostgreSQL/Supabase before installing; no production execution.
