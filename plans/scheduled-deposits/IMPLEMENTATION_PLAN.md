# Implementation Plan — Scheduled Deposits

## Files to create

### `src/main/java/org/example/model/ScheduledDepositDto.java`
New record — same fields as `TransactionStreamDto` but without `accountId` and `streamId`:
```java
public record ScheduledDepositDto(
    String merchantName,
    String description,
    String frequency,
    LocalDate firstDate,
    LocalDate lastDate,
    LocalDate predictedNextDate,
    RecurringResponse.AmountDto averageAmount,
    RecurringResponse.AmountDto lastAmount,
    Boolean isActive,
    RecurringResponse.PersonalFinanceCategoryDto personalFinanceCategory,
    String status
) {}
```

## Files to modify

### `src/main/java/org/example/service/RecurringService.java`
- Change `getAllRecurring(UUID userId)` visibility from `private` to package-private (remove `private` keyword). No other changes to that method.
- Add `getScheduledDeposits(UUID userId)`:
  - Calls `getAllRecurring(userId)`
  - Takes only `inflowStreams`
  - Filters: `isActive == true`, `predictedNextDate != null`, `predictedNextDate >= LocalDate.now()`, `predictedNextDate <= YearMonth.now().atEndOfMonth()`
  - Sorts ascending by `predictedNextDate`
  - Maps each stream to `ScheduledDepositDto`
  - Returns `List<ScheduledDepositDto>`

### `src/main/java/org/example/controller/RecurringController.java`
- Add `GET /api/recurring/scheduled-deposits` handler
- Same auth pattern as existing handler: `@AuthenticationPrincipal OAuth2User principal`, `SecurityUtils.resolveUser()`
- Delegates to `RecurringService.getScheduledDeposits(userId)`
- Returns `ResponseEntity<List<ScheduledDepositDto>>`
- Exception handling: any `Exception` → 403 with body `"Error getting scheduled deposit data"`

## No new entities, repositories, or migrations needed
