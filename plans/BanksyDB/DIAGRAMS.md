# BanksyDB — Diagrams

## Entity-Relationship Diagram

```
┌───────────────────┐         ┌────────────────────────┐
│       users       │  1    * │    oauth_identities    │
├───────────────────┤         ├────────────────────────┤
│ id (PK)           ├─────────┤ user_id (FK)           │
│ first_name        │         │ id (PK)                │
│ last_name         │         │ provider               │
│ username          │         │ provider_user_id       │
│ email             │         │ created_at             │
│ created_at        │         └────────────────────────┘
│ updated_at        │
└────────┬──────────┘
         │
         │ * (via user_plaid_items)
         │
┌────────▼──────────────┐         ┌────────────────────────┐
│   user_plaid_items    │  *    1 │      plaid_items       │
├───────────────────────┤         ├────────────────────────┤
│ user_id (FK, PK)      ├─────────┤ id (PK)               │
│ plaid_item_id (FK, PK)│         │ access_token_enc       │
│ added_at              │         │ item_id                │
└───────────────────────┘         │ institution_id         │
                                  │ institution_name       │
                                  │ transaction_cursor     │
                                  │ created_at             │
                                  │ updated_at             │
                                  └───────────┬────────────┘
                                              │
                                              │ 1    *
                                              │
                                  ┌───────────▼────────────┐
                                  │     plaid_accounts     │
                                  ├────────────────────────┤
                                  │ id (PK)                │
                                  │ plaid_item_id (FK)     │
                                  │ plaid_account_id       │
                                  │ name                   │
                                  │ official_name          │
                                  │ type                   │
                                  │ subtype                │
                                  │ mask                   │
                                  │ created_at             │
                                  └────────────────────────┘
```

### Reading the diagram
- One `user` → many `oauth_identities` (allows adding GitHub login later without breaking anything)
- Many `users` ↔ many `plaid_items` via `user_plaid_items` — this is the shared account mechanism
- One `plaid_item` (a bank connection) → many `plaid_accounts` (e.g. Chase checking + Chase savings)

---

## Shared Account Data Flow

```
Step 1 — User A links Chase bank:

  ┌─────────┐     creates     ┌───────────────────┐
  │ User A  │────────────────►│   plaid_items     │
  └─────────┘                 │  (Chase entry)    │
       │                      └───────────────────┘
       │  also creates                 ▲
       ▼                               │ references
  ┌──────────────────────┐            │
  │   user_plaid_items   │────────────┘
  │   user_id = A        │
  └──────────────────────┘


Step 2 — User A shares with User B:

                            ┌───────────────────┐
                            │   plaid_items     │
                            │  (same row as     │◄──────────────────────┐
                            │   Step 1)         │                       │
                            └───────────────────┘                       │
                                                            ┌──────────────────────┐
                                                            │   user_plaid_items   │
                                                            │   user_id = B        │
                                                            └──────────────────────┘

Result: both users query the same plaid_item.
One access_token. One row. No duplicated bank credentials.
```

---

## Plaid Link Flow (one-time bank setup)

```
  Frontend              Backend                  Plaid API
     │                     │                         │
     │  open Plaid Link UI │                         │
     │────────────────────►│                         │
     │                     │                         │
     │  user logs into     │                         │
     │  their bank in      │                         │
     │  Plaid's UI         │                         │
     │◄────────────────────────────────────────────► │
     │                     │                         │
     │  onSuccess fires:   │                         │
     │  { public_token,    │                         │
     │    institution,     │                         │
     │    accounts }       │                         │
     │                     │                         │
     │  POST /plaid/exchange                         │
     │  { public_token,    │                         │
     │    institution,     │                         │
     │    accounts }       │                         │
     │────────────────────►│                         │
     │                     │  exchange public_token  │
     │                     │────────────────────────►│
     │                     │◄── access_token ────────│
     │                     │                         │
     │                     │  encrypt(access_token)  │
     │                     │  INSERT plaid_items     │
     │                     │  INSERT plaid_accounts  │
     │                     │  INSERT user_plaid_items│
     │                     │                         │
     │◄──── 200 OK ────────│                         │
```

---

## Data Read Flow (balances, transactions)

```
  Frontend              Backend                  Plaid API
     │                     │                         │
     │  GET /accounts      │                         │
     │────────────────────►│                         │
     │                     │  lookup user's          │
     │                     │  plaid_items via        │
     │                     │  user_plaid_items       │
     │                     │                         │
     │                     │  decrypt(access_token)  │
     │                     │                         │
     │                     │  GET /accounts/get      │
     │                     │────────────────────────►│
     │                     │◄── account data ────────│
     │                     │                         │
     │◄── account data ────│                         │
     │    (never stored)   │                         │
```

**Key point:** financial data (balances, transaction amounts, merchant names) is never written
to the database. It exists in memory only for the duration of the request.

---

## Encryption Data Flow

```
WRITE (storing a new Plaid access token):

  access_token (plaintext)
       │
       ▼
  AES-256-GCM encrypt
  using ENCRYPTION_KEY env var
       │
       ▼
  "AES256:<iv>:<ciphertext>"  ──► stored in plaid_items.access_token_enc


READ (making a Plaid API call):

  plaid_items.access_token_enc
       │
       ▼
  AES-256-GCM decrypt
  using ENCRYPTION_KEY env var
       │
       ▼
  access_token (plaintext, in memory only)
       │
       ▼
  Plaid API call  ──► discard after response
```
