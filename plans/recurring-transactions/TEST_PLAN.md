# Test Plan — Recurring Transactions

## RecurringServiceTest (unit, Mockito)

### Get-all mode (accountId = null)
1. `shouldAggregateStreams_whenUserHasMultipleHealthyItems` — two items, each returns streams; verify flat merge
2. `shouldReturnEmptyStreams_whenUserHasNoItems` — empty plaidItems list; no Plaid call
3. `shouldSkipItemAndAddRelinkSignal_whenItemStatusIsNeedsReauth` — NEEDS_REAUTH item skipped, RelinkSignal added
4. `shouldSkipItemAndAddRelinkSignal_whenItemStatusIsInvalidToken` — INVALID_TOKEN item skipped
5. `shouldUpdateStatusAndAddRelinkSignal_whenPlaidReturnsItemLoginRequired` — token error at call time
6. `shouldUpdateStatusAndAddRelinkSignal_whenPlaidReturnsInvalidAccessToken` — token error at call time
7. `shouldThrowRuntimeException_whenPlaidReturnsNonTokenError` — 500 error body, no token error code
8. `shouldReturnHealthyStreamsAlongside_whenMixOfHealthyAndUnhealthyItems`

### Per-account mode (accountId provided)
9. `shouldThrowNoSuchElementException_whenAccountIdNotFound`
10. `shouldThrowSecurityException_whenUserNotLinkedToItem`
11. `shouldReturnRelinkSignal_whenItemIsNotHealthy` — account found, user linked, item unhealthy
12. `shouldReturnFilteredStreams_whenAccountIdIsValid` — happy path, accountIds filter passed to Plaid
13. `shouldUpdateStatusAndAddRelinkSignal_whenPerAccountPlaidTokenError`

## RecurringControllerTest (WebMvcTest)

1. `shouldReturn200WithStreams_whenGetAllAuthenticated`
2. `shouldReturn200WithStreams_whenPerAccountAuthenticated`
3. `shouldReturn404_whenServiceThrowsNoSuchElementException`
4. `shouldReturn403_whenServiceThrowsSecurityException`
5. `shouldReturn500_whenServiceThrowsGenericException`
6. `shouldReturn302_whenNotAuthenticated` (default Spring Security behavior, no mock needed)
