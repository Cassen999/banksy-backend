# Test Plan: monthly-glance

## MonthlyGlanceServiceTest

Unit tests using Mockito (`@ExtendWith(MockitoExtension.class)`), following the pattern in `TransactionsServiceTest` and `RecurringServiceTest`.

### Filtering — categories

| # | Test name | What it verifies |
|---|---|---|
| 1 | `shouldExcludeTransactions_inDefaultRejectedPrimaryCategory` | Transaction with `primary = "RENT_AND_UTILITIES"` is excluded from daily totals |
| 2 | `shouldExcludeTransactions_inDefaultRejectedPrimaryCategory_income` | Transaction with `primary = "INCOME"` is excluded |
| 3 | `shouldIncludeTransactions_withNullPersonalFinanceCategory` | Transaction where `getPersonalFinanceCategory()` returns null is included |
| 4 | `shouldExcludeTransactions_whenUserAddedDetailedCategoryMatches` | Transaction with `detailed = "PERSONAL_CARE_GYMS_AND_FITNESS_CENTERS"` is excluded when user has added that category |
| 5 | `shouldIncludeTransactions_whenCategoryNotInRejectedSet` | Transaction with `primary = "FOOD_AND_DRINK"` is included |

### Filtering — accounts

| # | Test name | What it verifies |
|---|---|---|
| 6 | `shouldExcludeTransactions_fromHiddenAccounts` | Transactions from a globally hidden account are excluded |
| 7 | `shouldExcludeTransactions_fromUserExcludedAccounts` | Transactions from a user-excluded account ID are excluded |
| 8 | `shouldIncludeTransactions_fromNonExcludedAccounts` | Transactions from accounts not in either exclusion set are included |

### Aggregation

| # | Test name | What it verifies |
|---|---|---|
| 9 | `shouldAggregateAmountsByDate` | Two transactions on the same date are summed into one DailyTotal |
| 10 | `shouldNetRefundsAgainstSpending` | A negative-amount transaction (refund) reduces the day's total |
| 11 | `shouldIncludeZeroTotalDays_forDaysWithNoQualifyingTransactions` | Days with no qualifying transactions appear with `total = 0.0` |
| 12 | `shouldReturnAllDaysFromFirstOfMonthThroughToday` | Result list contains one entry per day from 1st through today, sorted ascending |
| 13 | `shouldAggregateAcrossMultipleItems` | Transactions from two different PlaidItems are combined into the same daily map |

### Error handling / relink signals

| # | Test name | What it verifies |
|---|---|---|
| 14 | `shouldSkipItemAndAddRelinkSignal_whenStatusIsNeedsReauth` | Non-HEALTHY item produces RelinkSignal with `LOGIN_REQUIRED`, no Plaid call made |
| 15 | `shouldSkipItemAndAddRelinkSignal_whenStatusIsInvalidToken` | Non-HEALTHY item produces RelinkSignal with `INVALID_TOKEN` |
| 16 | `shouldUpdateStatusAndAddRelinkSignal_whenPlaidReturnsItemLoginRequired` | Status updated to NEEDS_REAUTH, RelinkSignal added |
| 17 | `shouldUpdateStatusAndAddRelinkSignal_whenPlaidReturnsInvalidAccessToken` | Status updated to INVALID_TOKEN, RelinkSignal added |
| 18 | `shouldThrowRuntimeException_whenPlaidReturnsNonTokenError` | Non-token error from Plaid throws RuntimeException |
| 19 | `shouldReturnEmptyDailyTotals_whenUserHasNoItems` | Empty list of PlaidItems returns all-zero daily totals, no Plaid call |

---

## MonthlyGlanceControllerTest

Unit tests using `@WebMvcTest(MonthlyGlanceController.class)`, following the pattern in `TransactionsControllerTest`.

| # | Test name | What it verifies |
|---|---|---|
| 1 | `shouldReturn200_withDailyTotals_whenServiceSucceeds` | 200 OK with serialized `MonthlyGlanceResponse` |
| 2 | `shouldReturn500_whenServiceThrowsException` | 500 returned when service throws |

---

## CategoriesControllerTest

Unit tests using `@WebMvcTest(CategoriesController.class)`.

| # | Test name | What it verifies |
|---|---|---|
| 1 | `shouldReturn200_withAllCategories` | 200 OK with list of CategoryEntry objects from repository |
| 2 | `shouldReturnBothPrimaryAndDetailedTypes` | Response contains entries with type PRIMARY and DETAILED |
