# Diagrams: Custom Account Names

## Write flow
```
Frontend
  └── PUT /api/plaid/account/{plaidAccountId}/name  { "customName": "My Card" }
        └── AccountCustomizationController
              └── AccountCustomizationService
                    ├── PlaidAccountRepository.findByPlaidAccountIdWithItem → verify exists (404)
                    ├── UserRepository.findByIdWithPlaidItems → verify linked (403)
                    └── UserAccountNameRepository.findByUserIdAndPlaidAccountId → upsert
```

## Read flow
```
Frontend
  └── GET /api/balance
        └── BalanceService
              ├── UserAccountNameRepository.findAllByUserId → Map<accountId, customName>
              └── toAccount(accountBase, institutionName, customName) → Account { ..., customName }
```

## Table relationship
```
users (id)
  └── user_account_names (user_id FK, plaid_account_id, custom_name)
        UNIQUE (user_id, plaid_account_id)
```
