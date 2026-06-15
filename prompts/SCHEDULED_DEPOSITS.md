Implement GET /api/recurring/scheduled-deposits in the banksy Java backend

Context

This is a Java 21 / Spring Boot 3 / Maven project at /Users/hannahgerber/IdeaProjects/banksy. It uses Google OAuth2
for auth (no passwords), Spring Data JPA + PostgreSQL, and the Plaid Java SDK v35.

There is already a working GET /api/recurring endpoint. When called without an accountId param it loops through all
HEALTHY PlaidItems for the logged-in user, calls Plaid's transactionsRecurringGet() per item, and aggregates a
RecurringResponse containing inflowStreams (recurring deposits), outflowStreams (recurring payments), and
relinkRequired signals. Each stream is a RecurringResponse.TransactionStreamDto (a record defined inside
RecurringResponse.java) with fields including predictedNextDate (a LocalDate), isActive (Boolean), and status
(String). The existing code does NO date filtering — it returns everything Plaid gives back.

Task

Add a new endpoint GET /api/recurring/scheduled-deposits that returns only the recurring deposit streams predicted
to occur within the current calendar month, across all of the logged-in user's linked Plaid items and accounts.

Exact behavior:

1. Reuse the existing RecurringService.getAllRecurring(UUID userId) — it is currently private, so change its
   visibility to package-private (remove the private keyword). Do not change anything else about it.
2. From the result, take only inflowStreams.
3. Filter to streams where ALL of the following are true:
   - isActive == true
   - predictedNextDate != null
   - predictedNextDate >= LocalDate.now()
   - predictedNextDate <= YearMonth.now().atEndOfMonth()
4. Sort ascending by predictedNextDate.
5. Map each filtered stream to the new ScheduledDepositDto (see below).
6. Return List<ScheduledDepositDto> directly — no wrapper, no outflow, no relinkRequired.
7. Non-HEALTHY items are already handled inside getAllRecurring — no extra handling needed.

New DTO to create

Create src/main/java/org/example/model/ScheduledDepositDto.java as a record with these fields (same as
TransactionStreamDto but WITHOUT accountId and streamId):

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

Key files to read before writing anything:

- src/main/java/org/example/controller/RecurringController.java — existing controller; follow its exact auth pattern
- src/main/java/org/example/service/RecurringService.java — existing service; getAllRecurring is the method to
  reuse (currently private at line 53)
- src/main/java/org/example/model/RecurringResponse.java — defines TransactionStreamDto, AmountDto,
  PersonalFinanceCategoryDto as nested records
- src/test/java/org/example/controller/RecurringControllerTest.java — follow this style for new controller tests
- src/test/java/org/example/service/RecurringServiceTest.java — follow this style for new service tests

Implementation scope:

- Create ScheduledDepositDto.java as described above.
- Change getAllRecurring visibility from private to package-private (remove private). Do not change anything else
  about it.
- Add getScheduledDeposits(UUID userId) to RecurringService: calls getAllRecurring(), applies the filter + sort,
  maps to ScheduledDepositDto.
- Add GET /api/recurring/scheduled-deposits to RecurringController following the existing auth pattern
  (@AuthenticationPrincipal OAuth2User principal, SecurityUtils.resolveUser()). Returns
  ResponseEntity<List<ScheduledDepositDto>>.
- Add tests for the new service method in RecurringServiceTest and for the new endpoint in
  RecurringControllerTest, following the style of the existing tests.
- Do not change any existing methods, tests, or response structures.

Expected return shape — a JSON array, sorted by predictedNextDate ascending:

[
  {
    "merchantName": "Employer Inc",
    "description": "DIRECT DEPOSIT",
    "frequency": "BIWEEKLY",
    "firstDate": "2025-01-03",
    "lastDate": "2026-06-01",
    "predictedNextDate": "2026-06-20",
    "averageAmount": { "amount": 2500.00, "isoCurrencyCode": "USD" },
    "lastAmount":    { "amount": 2500.00, "isoCurrencyCode": "USD" },
    "isActive": true,
    "personalFinanceCategory": { "primary": "INCOME", "detailed": "INCOME_WAGES" },
    "status": "MATURE"
  }
]

Note: merchantName, description, averageAmount, lastAmount, and personalFinanceCategory may be null.

Compile and run tests when done:

mvn compile
mvn test -Dtest=RecurringServiceTest,RecurringControllerTest

Fix any failures before reporting done.
