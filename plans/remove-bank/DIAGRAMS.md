# Diagrams — remove-bank

---

## Request Flow — Soft Hide (HIDE_ACCOUNT)

```
Frontend
  │
  │  PUT /api/plaid/account/{plaidAccountId}/hide
  ▼
RemoveBankController
  │  resolveUser(principal) → userId
  │  delegate to RemoveBankService.hideAccount(plaidAccountId, userId)
  ▼
RemoveBankService
  │
  ├─► PlaidAccountRepository.findByIdWithItem(plaidAccountId)
  │     → PlaidAccount (with PlaidItem loaded)
  │
  ├─► Verify requesting user is in user_plaid_items for this item
  │     SecurityException (403) if not linked
  │
  ├─► UserRepository.findAllWithPlaidItem(plaidItemId)
  │     → all users linked to this item (for notifications)
  │
  ├─► plaidAccount.setHidden(true)
  │   PlaidAccountRepository.save(plaidAccount)
  │
  ├─► [SUCCESS] NotificationRepository.saveAll(
  │     one Notification per linked user:
  │     "Removed bank account ending in <mask>"
  │   )
  │
  └─► [FAILURE] NotificationRepository.saveAll(
        one Notification per linked user:
        "There was an error removing your account,
         please try again in a few minutes or contact Cassen"
      )
      re-throw → controller returns 500
```

---

## Request Flow — Full Item Removal (REMOVE_ITEM)

```
Frontend
  │
  │  DELETE /api/plaid/item/{plaidItemId}
  ▼
RemoveBankController
  │  resolveUser(principal) → userId
  │  delegate to RemoveBankService.removeItem(plaidItemId, userId)
  ▼
RemoveBankService
  │
  ├─► PlaidItemRepository.findById(plaidItemId)
  │     → PlaidItem
  │
  ├─► Verify requesting user is in user_plaid_items for this item
  │     SecurityException (403) if not linked
  │
  ├─► UserRepository.findAllWithPlaidItem(plaidItemId)
  │     → snapshot of all linked users (captured before deletion)
  │
  ├─► [if status != INVALID_TOKEN]
  │   EncryptionService.decrypt(accessToken)
  │   PlaidApi.itemRemove(accessToken)   ← revokes token at Plaid
  │
  ├─► Delete all user_plaid_items rows for this item
  │   PlaidItemRepository.delete(plaidItem)
  │     └─► cascades to plaid_accounts rows
  │
  ├─► [SUCCESS] NotificationRepository.saveAll(
  │     one Notification per previously linked user:
  │     "Removed <institutionName> from Banksy,
  │      in order to see these accounts again you must re-link bank."
  │   )
  │
  └─► [FAILURE] NotificationRepository.saveAll(
        one Notification per linked user:
        "There was an error removing your account,
         please try again in a few minutes or contact Cassen"
      )
      re-throw → controller returns 500
```

---

## Entity Relationships (affected)

```
User ──────────────────────────────────────────┐
  │                                            │
  │  user_plaid_items (join table)             │
  │  many-to-many                              │
  ▼                                            ▼
PlaidItem ──────────────── PlaidAccount    Notification
  │ id                       │ id            │ id
  │ owner_user_id FK         │ plaid_item_id │ user_id FK
  │ institution_name         │ plaid_acct_id │ message
  │ access_token (encrypted) │ name          │ created_at
  │ status                   │ mask          │ read
  │                          │ type/subtype  │
  │                          │ hidden  ◄─────┼── NEW COLUMN
  │                          │ created_at    │
  └──────────────────────────┘               │
```

---

## Hidden Account Filter (Balance + Transactions)

```
Before (current):
  BalanceService / TransactionsService
    └─► iterate all PlaidAccounts for user's PlaidItems
          └─► fetch from Plaid for each account

After:
  BalanceService / TransactionsService
    └─► iterate all PlaidAccounts where hidden = false
          └─► fetch from Plaid for each account
```