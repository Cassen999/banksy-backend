# Test Plan: Baseline Test Coverage

---

## EncryptionService (Unit)

| Test | Condition |
|---|---|
| `shouldEncryptAndDecryptRoundtrip_whenValidPlaintext` | encrypt then decrypt returns original string |
| `shouldProduceDifferentCiphertext_whenSamePlaintextEncryptedTwice` | IV randomisation — two encryptions of same input differ |
| `shouldThrowRuntimeException_whenEncryptedFormatHasNoColon` | malformed `encrypted` string with no `:` separator |
| `shouldThrowRuntimeException_whenCiphertextIsTampered` | AES-GCM authentication tag fails on modified bytes |

---

## SecurityUtils (Unit)

| Test | Condition |
|---|---|
| `shouldReturnUser_whenEmailExistsInRepository` | principal has `email` attribute; repository finds user |
| `shouldThrowNoSuchElementException_whenUserNotFound` | repository returns empty Optional |

---

## CustomOAuth2UserService (Unit)

| Test | Condition |
|---|---|
| `shouldReturnOidcUser_whenOAuthIdentityAlreadyExists` | identity found — no save calls made |
| `shouldCreateUserAndIdentity_whenNeitherExists` | identity not found, email not found — both saved |
| `shouldCreateIdentityOnly_whenEmailAlreadyExists` | identity not found, user found by email — only identity saved |
| `shouldDefaultFirstNameToEmpty_whenGivenNameIsNull` | `oidcUser.getGivenName()` returns null |
| `shouldDefaultLastNameToEmpty_whenFamilyNameIsNull` | `oidcUser.getFamilyName()` returns null |
| `shouldDeriveUsername_whenEmailProvided` | username is the local-part before `@` |

---

## PlaidLinkService (Unit)

| Test | Condition |
|---|---|
| `shouldReturnLinkToken_whenPlaidRespondsSuccessfully` | Plaid returns 200 with link token |
| `shouldThrowRuntimeException_whenPlaidLinkTokenCallFails` | Plaid returns non-2xx response |
| `shouldCreateNewItemAndAccounts_whenPublicTokenIsNew` | itemId not in DB — item + accounts saved |
| `shouldReturnExistingItem_whenItemIdAlreadyExistsAndUserOwnsIt` | itemId found, user already owns it |
| `shouldAddExistingItemToUser_whenItemExistsButUserDoesNotOwnIt` | itemId found, user does not own it — added |
| `shouldSkipDuplicateAccount_whenPlaidAccountIdAlreadyExists` | account with same plaidAccountId exists — not re-saved |
| `shouldThrowRuntimeException_whenTokenExchangeFails` | Plaid exchange returns non-2xx |
| `shouldShareItem_whenRequestingUserOwnsIt` | item found, user owns it, target user exists — item added |
| `shouldThrowIllegalArgument_whenItemNotFound` | `plaidItemRepository.findById` returns empty |
| `shouldThrowSecurityException_whenUserDoesNotOwnItem` | requesting user does not have item in their list |
| `shouldThrowIllegalArgument_whenTargetEmailNotFound` | `userRepository.findByEmail` returns empty |
| `shouldNotDuplicateShare_whenTargetAlreadyOwnsItem` | target user already has item — no duplicate added |

---

## BalanceService (Unit)

| Test | Condition |
|---|---|
| `shouldReturnAllBalances_whenUserHasMultiplePlaidItems` | iterates items, decrypts token, calls Plaid per item |
| `shouldReturnEmptyList_whenUserHasNoPlaidItems` | user's plaid items list is empty |
| `shouldThrowRuntimeException_whenPlaidBalanceCallFails` | Plaid returns non-2xx |

---

## TransactionsService (Unit)

| Test | Condition |
|---|---|
| `shouldReturnAllTransactions_whenUserHasPlaidItems` | iterates items, builds transaction list |
| `shouldReturnEmptyList_whenUserHasNoPlaidItems` | user's plaid items list is empty |
| `shouldThrowRuntimeException_whenPlaidTransactionsCallFails` | Plaid returns non-2xx |
| `shouldRespectDaysParameter_whenCalled` | `startDate` is `now().minusDays(days)` |

---

## AuthController (Integration — MockMvc)

| Test | Condition |
|---|---|
| `shouldReturn200_whenLogoutCalled` | POST /api/auth/logout with authenticated user |
| `shouldReturnUserInfo_whenAuthenticatedUserCallsMe` | GET /api/auth/me — returns id, email, names, username |
| `shouldReturn500_whenUserNotFoundOnMeEndpoint` | repository throws exception — returns 500 |
| `shouldReturn401_whenUnauthenticatedUserCallsMe` | no authentication principal — Spring Security redirects/401 |

---

## PlaidLinkController (Integration — MockMvc)

| Test | Condition |
|---|---|
| `shouldReturnLinkToken_whenAuthenticatedUserRequestsIt` | GET /api/plaid/link-token — returns `{"link_token":"..."}` |
| `shouldReturn500_whenLinkTokenServiceThrowsException` | service throws IOException |
| `shouldReturn200_whenPublicTokenExchangedSuccessfully` | POST /api/plaid/exchange — returns `{"status":"ok"}` |
| `shouldReturn500_whenExchangeServiceThrowsException` | service throws IOException |
| `shouldReturn200_whenShareSuccessful` | POST /api/plaid/share — returns `{"status":"ok"}` |
| `shouldReturn400_whenShareThrowsIllegalArgument` | service throws `IllegalArgumentException` |
| `shouldReturn403_whenShareThrowsSecurityException` | service throws `SecurityException` |
| `shouldReturn500_whenShareThrowsGenericException` | service throws generic `Exception` |

---

## BalanceController (Integration — MockMvc)

| Test | Condition |
|---|---|
| `shouldReturnBalanceResponse_whenAuthenticatedUserCallsEndpoint` | GET /api/balance — returns account list |
| `shouldReturn500_whenBalanceServiceThrowsException` | service throws exception |

---

## TransactionsController (Integration — MockMvc)

| Test | Condition |
|---|---|
| `shouldReturnTransactionsResponse_whenAuthenticatedUserCallsEndpoint` | GET /api/transactions |
| `shouldUseDefaultDays_whenNoQueryParamProvided` | no `days` param — defaults to 30 |
| `shouldReturn500_whenTransactionsServiceThrowsException` | service throws exception |

---

## UserRepository (Integration — Testcontainers)

| Test | Condition |
|---|---|
| `shouldFindUser_whenEmailExists` | `findByEmail` returns matching user |
| `shouldReturnEmpty_whenEmailNotFound` | `findByEmail` returns empty Optional |
| `shouldReturnUserWithPlaidItems_whenUserHasItems` | `findByIdWithPlaidItems` eagerly loads items |
| `shouldReturnUserWithEmptyList_whenUserHasNoItems` | `findByIdWithPlaidItems` returns user with empty list |

---

## OAuthIdentityRepository (Integration — Testcontainers)

| Test | Condition |
|---|---|
| `shouldFindIdentity_whenProviderAndProviderUserIdMatch` | matching record found |
| `shouldReturnEmpty_whenNoMatchingIdentity` | no match for provider + providerUserId pair |

---

## PlaidItemRepository (Integration — Testcontainers)

| Test | Condition |
|---|---|
| `shouldFindItem_whenItemIdExists` | `findByItemId` returns matching item |
| `shouldReturnEmpty_whenItemIdNotFound` | `findByItemId` returns empty Optional |

---

## PlaidAccountRepository (Integration — Testcontainers)

| Test | Condition |
|---|---|
| `shouldReturnTrue_whenPlaidAccountIdExists` | `existsByPlaidAccountId` returns true |
| `shouldReturnFalse_whenPlaidAccountIdNotFound` | `existsByPlaidAccountId` returns false |

---

## Edge Cases and Failure Scenarios Summary

- **Null names (OAuth2):** `givenName` and `familyName` may be null for some providers — default to empty string
- **AES-GCM tamper detection:** decryption throws when ciphertext is modified — authentication tag check
- **Plaid API non-2xx:** all Plaid callers throw `RuntimeException` with error detail; tests verify message
- **Empty PlaidItems list:** balance and transactions services return empty lists, not errors
- **Item already shared:** no duplicate entries added to target user's item list
- **Unauthenticated requests:** Spring Security blocks access before controller code runs
