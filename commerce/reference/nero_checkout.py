"""Sandbox reference: local transactional checkout binding only.
No HTTP server, WooCommerce calls, payment processing or entitlement writes.
The host must authenticate the account and merchant before calling this core.
"""
import hashlib
import re
import secrets
import sqlite3

PLANS = {"premium_monthly": 990, "premium_quarterly": 2490, "premium_annual": 8990}
TTL_SECONDS = 600

def digest(token):
    if not isinstance(token, str) or not re.fullmatch(r"[A-Za-z0-9_-]{43}", token):
        raise ValueError("invalid token")
    return hashlib.sha256(token.encode("ascii")).hexdigest()

def initialize(db):
    db.executescript("""
        CREATE TABLE IF NOT EXISTS nero_intent (
          token_hash TEXT PRIMARY KEY, owner TEXT NOT NULL, plan TEXT NOT NULL,
          expires_at INTEGER NOT NULL, order_id TEXT UNIQUE
        );
    """)

def create_intent(db, authenticated_owner, plan, now):
    # Owner is trusted server authentication result, never a client-supplied field.
    if not isinstance(authenticated_owner, str) or not authenticated_owner.strip() or plan not in PLANS:
        raise ValueError("invalid checkout request")
    token = secrets.token_urlsafe(32)
    with db:
        db.execute("INSERT INTO nero_intent VALUES (?, ?, ?, ?, NULL)",
                   (digest(token), authenticated_owner, plan, now + TTL_SECONDS))
    return token

def bind_order(db, token, trusted_order_id, trusted_plan, trusted_amount, trusted_currency, now):
    # Only a verified, isolated merchant integration may call this method.
    # The server must verify the product, order, account handoff and price.
    # This action does not indicate payment and grants no Premium access.
    if (not isinstance(trusted_order_id, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", trusted_order_id)
            or trusted_plan not in PLANS or type(trusted_amount) is not int
            or trusted_amount != PLANS[trusted_plan] or trusted_currency != "BRL"):
        raise ValueError("order mismatch")
    hashed = digest(token)
    with db:
        cursor = db.execute("""
            UPDATE nero_intent SET order_id=?
            WHERE token_hash=? AND plan=? AND expires_at>? AND order_id IS NULL
        """, (trusted_order_id, hashed, trusted_plan, now))
        if cursor.rowcount != 1:
            raise ValueError("expired, mismatched or already consumed intent")
        row = db.execute("SELECT owner, plan FROM nero_intent WHERE token_hash=?", (hashed,)).fetchone()
    return {"owner": row[0], "plan": row[1], "order_id": trusted_order_id}

def order_binding(db, trusted_order_id):
    row = db.execute("SELECT owner, plan FROM nero_intent WHERE order_id=?", (trusted_order_id,)).fetchone()
    return {"owner": row[0], "plan": row[1], "order_id": trusted_order_id} if row else None
