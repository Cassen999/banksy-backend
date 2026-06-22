# Test Plan: Account Detail Fields

## TransactionsServiceTest

- `shouldReturnAllTransactions_whenUserHasPlaidItems` — stub `tx.getAccountId()` → `"acct-123"`, assert returned transaction has `accountId` = `"acct-123"`
- `shouldExcludeTransactions_fromHiddenAccounts` — `visibleTx.getAccountId()` already stubbed; add assertion that returned transaction `accountId` = `"acct-visible"`

## BalanceServiceTest

- `shouldReturnAllBalances_whenUserHasMultiplePlaidItems` — stub `item.getInstitutionName()` → `"Chase"`, assert account has `institutionName` = `"Chase"`
- `shouldReturnHealthyAccountsAlongside_whenMixOfHealthyAndUnhealthyItems` — stub `healthyItem.getInstitutionName()` → `"Good Bank"`, assert on returned account
- `shouldExcludeHiddenAccounts_whenSomeAccountsAreHidden` — stub `item.getInstitutionName()`, assert on visible account
- `shouldReturnEmpty_whenAllAccountsAreHidden` — stub `item.getInstitutionName()` to prevent NPE
