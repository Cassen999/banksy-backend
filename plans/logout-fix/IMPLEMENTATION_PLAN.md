# Implementation Plan — Logout Fix

## Changes

### 1. `SecurityConfig.java`

**Logout handler:**
- Replace `logoutSuccessHandler(oidcLogoutSuccessHandler())` with `logoutSuccessUrl(frontendUrl)`
- Remove the `oidcLogoutSuccessHandler()` private method entirely
- Remove the `OidcClientInitiatedLogoutSuccessHandler` import

**Force account selection on login:**
- Add `@Bean` method `authorizationRequestResolver(ClientRegistrationRepository)` that wraps `DefaultOAuth2AuthorizationRequestResolver` and appends `prompt=select_account` to every authorization request
- Wire it into `oauth2Login` via `.authorizationEndpoint(...authorizationRequestResolver(...))`
- Add imports for `DefaultOAuth2AuthorizationRequestResolver` and `OAuth2AuthorizationRequestResolver`
- `ClientRegistrationRepository` field stays — it's still needed for the resolver

### 2. No other files change
- `application.properties` — `frontend.url` already exists, no change
- `AuthController.java` — untouched
- No test changes expected
