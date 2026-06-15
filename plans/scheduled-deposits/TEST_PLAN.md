# Test Plan — Scheduled Deposits

## RecurringServiceTest (unit, Mockito)

### getScheduledDeposits — filtering and sorting
1. `shouldReturnCurrentMonthInflowStreams_whenActiveStreamsExist` — happy path; two active inflow streams with predictedNextDate in current month; verify both returned and sorted ascending
2. `shouldExcludeOutflowStreams` — outflow streams present; verify none appear in result
3. `shouldExcludeInactiveStreams` — inflow stream with isActive=false; verify excluded
4. `shouldExcludeStreamsWithNullPredictedNextDate` — predictedNextDate is null; verify excluded
5. `shouldExcludeStreamsWithPredictedNextDateInPast` — predictedNextDate before today; verify excluded
6. `shouldExcludeStreamsWithPredictedNextDateInFutureMonth` — predictedNextDate in next month; verify excluded
7. `shouldReturnEmpty_whenNoStreamsMatchCurrentMonth` — all streams outside month; verify empty list
8. `shouldReturnEmpty_whenUserHasNoItems` — user has no PlaidItems; verify empty list
9. `shouldSkipUnhealthyItems_andStillReturnStreamsFromHealthyOnes` — mixed healthy/unhealthy items; verify only healthy item's streams returned (relink signals discarded since not returned by this endpoint)
10. `shouldSortByPredictedNextDateAscending` — three streams with different dates; verify order

## RecurringControllerTest (WebMvcTest)

1. `shouldReturn200WithScheduledDeposits_whenAuthenticated` — happy path; service returns list; verify 200 and JSON array
2. `shouldReturn200WithEmptyArray_whenNoDepositsThisMonth` — service returns empty list; verify 200 and `[]`
3. `shouldReturn403WithMessage_whenServiceThrowsException` — service throws IOException; verify 403 and body is `"Error getting scheduled deposit data"`
4. `shouldReturn302_whenNotAuthenticated` — no principal; verify redirect (default Spring Security behavior)
