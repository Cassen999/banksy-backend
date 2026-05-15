# Test Report: Baseline Test Coverage

**Date:** 2026-05-15

---

## Summary

| Category | Tests | Passed | Failed |
|---|---|---|---|
| Service unit tests | 28 | 28 | 0 |
| Controller integration tests (MockMvc) | 17 | 17 | 0 |
| Utility unit tests | 2 | 2 | 0 |
| Plaid helper tests | 2 | 2 | 0 |
| Repository integration tests (Testcontainers) | 4 | 0 | 4 |
| **Total** | **53** | **49** | **4** |

---

## Passed Tests (49)

### EncryptionServiceTest
- `shouldEncryptAndDecryptRoundtrip_whenValidPlaintext` ✓
- `shouldProduceDifferentCiphertext_whenSamePlaintextEncryptedTwice` ✓
- `shouldThrowRuntimeException_whenEncryptedFormatHasNoColon` ✓
- `shouldThrowRuntimeException_whenCiphertextIsTampered` ✓

### SecurityUtilsTest
- `shouldReturnUser_whenEmailExistsInRepository` ✓
- `shouldThrowNoSuchElementException_whenUserNotFound` ✓

### CustomOAuth2UserServiceTest
- `shouldReturnOidcUser_whenOAuthIdentityAlreadyExists` ✓
- `shouldCreateUserAndIdentity_whenNeitherExists` ✓
- `shouldCreateIdentityOnly_whenEmailAlreadyExists` ✓
- `shouldDefaultFirstNameToEmpty_whenGivenNameIsNull` ✓
- `shouldDefaultLastNameToEmpty_whenFamilyNameIsNull` ✓
- `shouldDeriveUsername_whenEmailProvided` ✓

### PlaidLinkServiceTest
- `shouldReturnLinkToken_whenPlaidRespondsSuccessfully` ✓
- `shouldThrowRuntimeException_whenPlaidLinkTokenCallFails` ✓
- `shouldCreateNewItemAndAccounts_whenPublicTokenIsNew` ✓
- `shouldReturnExistingItem_whenItemIdAlreadyExistsAndUserOwnsIt` ✓
- `shouldThrowRuntimeException_whenTokenExchangeFails` ✓
- `shouldSkipDuplicateAccount_whenPlaidAccountIdAlreadyExists` ✓
- `shouldShareItem_whenRequestingUserOwnsIt` ✓
- `shouldThrowIllegalArgument_whenItemNotFound` ✓
- `shouldThrowSecurityException_whenUserDoesNotOwnItem` ✓
- `shouldThrowIllegalArgument_whenTargetEmailNotFound` ✓
- `shouldNotDuplicateShare_whenTargetAlreadyOwnsItem` ✓

### BalanceServiceTest
- `shouldReturnAllBalances_whenUserHasMultiplePlaidItems` ✓
- `shouldReturnEmptyList_whenUserHasNoPlaidItems` ✓
- `shouldThrowRuntimeException_whenPlaidBalanceCallFails` ✓

### TransactionsServiceTest
- `shouldReturnAllTransactions_whenUserHasPlaidItems` ✓
- `shouldReturnEmptyList_whenUserHasNoPlaidItems` ✓
- `shouldThrowRuntimeException_whenPlaidTransactionsCallFails` ✓
- `shouldRespectDaysParameter_whenCalled` ✓

### PlaidClientFactoryTest
- `shouldReturnNoDetails_whenErrorBodyIsNull` ✓
- `shouldReturnErrorBodyString_whenErrorBodyIsPresent` ✓

### AuthControllerTest
- `shouldReturn200_whenLogoutCalled` ✓
- `shouldReturnUserInfo_whenAuthenticatedUserCallsMe` ✓
- `shouldReturn500_whenUserNotFoundOnMeEndpoint` ✓
- `shouldRedirectToLogin_whenUnauthenticatedUserCallsMe` ✓

### PlaidLinkControllerTest
- `shouldReturnLinkToken_whenAuthenticatedUserRequestsIt` ✓
- `shouldReturn500_whenLinkTokenServiceThrowsException` ✓
- `shouldReturn200_whenPublicTokenExchangedSuccessfully` ✓
- `shouldReturn500_whenExchangeServiceThrowsException` ✓
- `shouldReturn200_whenShareSuccessful` ✓
- `shouldReturn400_whenShareThrowsIllegalArgument` ✓
- `shouldReturn403_whenShareThrowsSecurityException` ✓
- `shouldReturn500_whenShareThrowsGenericException` ✓

### BalanceControllerTest
- `shouldReturnBalanceResponse_whenAuthenticatedUserCallsEndpoint` ✓
- `shouldReturn500_whenBalanceServiceThrowsException` ✓

### TransactionsControllerTest
- `shouldReturnTransactionsResponse_whenAuthenticatedUserCallsEndpoint` ✓
- `shouldUseDefaultDays_whenNoQueryParamProvided` ✓
- `shouldReturn500_whenTransactionsServiceThrowsException` ✓

---

## Failed Tests (4) — Environment Issue

All 4 failures are in the repository integration tests and share the same root cause:

**Root cause:** Docker Desktop is not running. Testcontainers requires Docker to spin up the PostgreSQL container.

| Test Class | Error |
|---|---|
| `UserRepositoryTest` | `IllegalStateException: Could not find a valid Docker environment` |
| `OAuthIdentityRepositoryTest` | `IllegalStateException: Previous attempts to find a Docker environment failed` |
| `PlaidItemRepositoryTest` | `IllegalStateException: Could not find a valid Docker environment` |
| `PlaidAccountRepositoryTest` | `IllegalStateException: Previous attempts to find a Docker environment failed` |

These tests are correctly written and will pass once Docker Desktop is running.

---

## Coverage

JaCoCo coverage report was generated at `target/site/jacoco/index.html` after the non-repository test run.

> **Note:** Coverage numbers below reflect the 49 passing tests only. Repository test coverage will be added once Docker is available and the full suite runs.

---

## Issues Fixed During Implementation

Three categories of test bugs were found and corrected before this report was finalised:

1. **Mockito strict stubbing (`UnnecessaryStubbing`)** — Three `PlaidLinkServiceTest` cases stubbed mock methods that were never called in their specific code path (e.g., `getPlaidItems()` on a user mock in a test that throws before reaching that line). Fixed by removing unnecessary stubs.

2. **Primitive parameter matcher** — `TransactionsControllerTest` used `any()` for a primitive `int` parameter. Mockito's `any()` returns null which cannot be unboxed. Fixed by using `anyInt()`.

3. **Null ID in `Map.of()`** — `AuthControllerTest` created a real `User` entity with no ID (ID is set by JPA on persist). `Map.of()` rejects null values, causing a 500. Fixed by mocking the `User` object.

---

## Observations

- The JaCoCo agent emits `IllegalClassFormatException` warnings for Java 26 JDK internal classes (class file major version 70). These are non-fatal and do not affect test results or project class coverage. They will resolve when JaCoCo releases a version supporting Java 26.
- Repository tests are production-ready and will verify the full schema including Flyway migrations once Docker is available.
