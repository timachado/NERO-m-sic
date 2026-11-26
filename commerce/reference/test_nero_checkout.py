import sqlite3
import unittest
from nero_checkout import initialize, create_intent, bind_order, order_binding, PLANS

class CheckoutTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        initialize(self.db)
        self.plan = "premium_monthly"
        self.token = create_intent(self.db, "account-a", self.plan, 1000)

    def bind(self, token=None, order="woo-123", plan=None, amount=990, currency="BRL", now=1001):
        return bind_order(self.db, token or self.token, order, plan or self.plan, amount, currency, now)

    def test_server_catalog(self):
        self.assertEqual(list(PLANS.values()), [990, 2490, 8990])
        with self.assertRaises(ValueError):
            create_intent(self.db, "account-a", "other-app", 1000)

    def test_one_time_binding_and_no_grant(self):
        self.assertEqual(self.bind(), {"owner":"account-a","plan":self.plan,"order_id":"woo-123"})
        with self.assertRaises(ValueError):
            self.bind(order="woo-124")
        self.assertEqual(order_binding(self.db, "woo-123")["owner"], "account-a")
        self.assertIsNone(order_binding(self.db, "woo-124"))

    def test_invalid_amount_currency_and_plan(self):
        for kwargs in ({"amount":1},{"currency":"USD"},{"plan":"premium_annual","amount":8990}):
            with self.assertRaises(ValueError):
                self.bind(**kwargs)
        self.assertIsNone(order_binding(self.db, "woo-123"))
        self.bind()

    def test_expired_and_unrecognized(self):
        with self.assertRaises(ValueError):
            self.bind(now=1600)
        with self.assertRaises(ValueError):
            self.bind(token="x"*43)

    def test_duplicate_order_rollback_and_account_separation(self):
        self.bind()
        other = create_intent(self.db, "account-b", self.plan, 1000)
        with self.assertRaises(sqlite3.IntegrityError):
            self.bind(token=other)
        self.assertEqual(self.bind(token=other, order="woo-456")["owner"], "account-b")
        self.assertEqual(order_binding(self.db, "woo-123")["owner"], "account-a")

if __name__ == "__main__":
    unittest.main()
