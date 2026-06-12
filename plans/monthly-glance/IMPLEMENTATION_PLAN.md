# Implementation Plan: monthly-glance

## Goal
`GET /api/monthly-glance` — returns daily spending totals for the current calendar month, excluding bill categories and user-defined exclusions, for all of the authenticated user's linked bank accounts.

## New Files

| File | Purpose |
|---|---|
| `src/main/resources/db/migration/V6__add_user_rejected_categories.sql` | Per-user rejected category table |
| `src/main/resources/db/migration/V7__add_user_excluded_accounts.sql` | Per-user excluded account table |
| `src/main/java/org/example/entity/UserRejectedCategory.java` | JPA entity |
| `src/main/java/org/example/entity/UserExcludedAccount.java` | JPA entity |
| `src/main/java/org/example/repository/UserRejectedCategoryRepository.java` | JPA repo |
| `src/main/java/org/example/repository/UserExcludedAccountRepository.java` | JPA repo |
| `src/main/java/org/example/model/MonthlyGlanceResponse.java` | Response DTO |
| `src/main/java/org/example/service/MonthlyGlanceService.java` | Core logic |
| `src/main/java/org/example/controller/MonthlyGlanceController.java` | HTTP layer |
| `src/main/resources/db/migration/V8__seed_plaid_categories.sql` | Reference table + all 150 category rows |
| `src/main/java/org/example/entity/PlaidCategory.java` | JPA entity |
| `src/main/java/org/example/repository/PlaidCategoryRepository.java` | JPA repo |
| `src/main/java/org/example/model/CategoriesResponse.java` | Response DTO |
| `src/main/java/org/example/controller/CategoriesController.java` | `GET /api/categories` |
| `src/test/java/org/example/service/MonthlyGlanceServiceTest.java` | Service unit tests |
| `src/test/java/org/example/controller/MonthlyGlanceControllerTest.java` | Controller unit tests |
| `src/test/java/org/example/controller/CategoriesControllerTest.java` | Controller unit tests |

No existing source files are modified.

> **Note:** V8 seeds the full Plaid taxonomy so users can search and select categories when adding personal rejections. A companion `GET /api/categories` endpoint exposes this list.

---

## Step 1 — DB Migrations

**V6** creates `user_rejected_categories(id, user_id FK, category VARCHAR, created_at, UNIQUE(user_id, category))`.
`category` holds either a primary value (`PERSONAL_CARE`) or detailed value (`PERSONAL_CARE_GYMS_AND_FITNESS_CENTERS`).

**V7** creates `user_excluded_accounts(id, user_id FK, plaid_account_id VARCHAR, created_at, UNIQUE(user_id, plaid_account_id))`.
Stores the Plaid account ID string (matches balances API response) — no internal UUID lookup needed.

**V8** creates `plaid_categories(category VARCHAR PK, category_type VARCHAR(8), primary_category VARCHAR nullable)` and seeds all 18 primary + ~132 detailed rows from the PFCv2 taxonomy. `category_type` is either `'PRIMARY'` or `'DETAILED'`; `primary_category` is null for primary rows and holds the parent primary string for detailed rows.

---

## Step 2 — Entities

`UserRejectedCategory`: `@Entity`, `@Table(name = "user_rejected_categories")`, fields: `id (UUID)`, `user (ManyToOne → User)`, `category (String)`, `createdAt (LocalDateTime)`.

`UserExcludedAccount`: same pattern, fields: `id (UUID)`, `user (ManyToOne → User)`, `plaidAccountId (String)`, `createdAt (LocalDateTime)`.

Both use `@PrePersist` to set `createdAt`.

---

## Step 3 — Repositories

```java
interface UserRejectedCategoryRepository extends JpaRepository<UserRejectedCategory, UUID> {
    @Query("SELECT r.category FROM UserRejectedCategory r WHERE r.user.id = :userId")
    Set<String> findCategoriesByUserId(@Param("userId") UUID userId);
}

interface UserExcludedAccountRepository extends JpaRepository<UserExcludedAccount, UUID> {
    @Query("SELECT e.plaidAccountId FROM UserExcludedAccount e WHERE e.user.id = :userId")
    Set<String> findPlaidAccountIdsByUserId(@Param("userId") UUID userId);
}
```

---

## Step 4 — MonthlyGlanceResponse DTO

```java
public record MonthlyGlanceResponse(List<DailyTotal> dailyTotals, List<RelinkSignal> relinkRequired) {
    public record DailyTotal(String transactionDate, double total) {}
}
```

`transactionDate` is ISO-8601 string (e.g. `"2026-06-12"`) produced by `LocalDate.toString()`.

---

## Step 5 — MonthlyGlanceService

Default rejected set (Java constant):
```java
private static final Set<String> DEFAULT_REJECTED = Set.of(
    "RENT_AND_UTILITIES",  // primary — catches all subcategories
    "INCOME",
    "TRANSFER_IN",
    "LOAN_DISBURSEMENTS"
);
```

Algorithm:
1. Load user + items via `userRepository.findByIdWithPlaidItems(userId)`
2. Load upfront: `rejectedCategories = DEFAULT_REJECTED ∪ userRejectedCategoryRepository.findCategoriesByUserId(userId)` and `excludedAccountIds = userExcludedAccountRepository.findPlaidAccountIdsByUserId(userId)`
3. For each item: skip non-HEALTHY → add RelinkSignal
4. Decrypt token, call `transactionsGet` with `startDate = LocalDate.now().withDayOfMonth(1)`, `endDate = LocalDate.now()`
5. On error: classify token error → update status + RelinkSignal; other → throw
6. Build `hiddenIds` via `plaidAccountRepository.findHiddenAccountIdsByItemId(item.getId())`
7. Filter transactions: exclude if account is in `hiddenIds` OR `excludedAccountIds`
8. Filter transactions: exclude if `pfc.getPrimary()` OR `pfc.getDetailed()` is in `rejectedCategories`; if `pfc` is null → include
9. `Map<LocalDate, Double> dailyTotals`: accumulate `t.getAmount()` per date (`merge` with `Double::sum`)
10. Fill result list: iterate day-by-day from `monthStart` through `today`, using `getOrDefault(d, 0.0)`
11. Return `MonthlyGlanceResponse(result, relinkRequired)`

---

## Step 6 — PlaidCategory Entity + Repository + CategoriesController

**Entity** `PlaidCategory`: `@Entity`, `@Table(name = "plaid_categories")`, fields: `category (String @Id)`, `categoryType (String)`, `primaryCategory (String nullable)`. No `@PrePersist` needed — rows are seeded by migration, never written by the app.

**Repository**:
```java
interface PlaidCategoryRepository extends JpaRepository<PlaidCategory, String> {
    // findAll() is sufficient; Spring Data provides it
}
```

**Response DTO**:
```java
public record CategoriesResponse(List<CategoryEntry> categories) {
    public record CategoryEntry(String category, String type, String primaryCategory) {}
}
```

**Controller** `GET /api/categories`: loads all rows, maps to `CategoryEntry` list, returns 200. No auth-specific logic — all authenticated users see the same taxonomy. Follows the same `@RestController / @RequestMapping("/api")` pattern.

Response example:
```json
{
  "categories": [
    { "category": "RENT_AND_UTILITIES", "type": "PRIMARY", "primaryCategory": null },
    { "category": "RENT_AND_UTILITIES_RENT", "type": "DETAILED", "primaryCategory": "RENT_AND_UTILITIES" },
    ...
  ]
}
```

---

## Step 7 — MonthlyGlanceController

```java
@RestController @RequestMapping("/api")
public class MonthlyGlanceController {
    @GetMapping("/monthly-glance")
    public ResponseEntity<MonthlyGlanceResponse> getMonthlyGlance(@AuthenticationPrincipal OAuth2User principal) {
        try {
            return ResponseEntity.ok(service.getMonthlyGlance(
                SecurityUtils.resolveUser(principal, userRepository).getId()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
```

Constructor-injected `MonthlyGlanceService` and `UserRepository`. Follows same pattern as `TransactionsController`.
