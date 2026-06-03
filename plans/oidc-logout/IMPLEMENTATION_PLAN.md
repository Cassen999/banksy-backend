# Implementation Plan — OIDC Logout

## Changes

### 1. `SecurityConfig.java`
- Inject `ClientRegistrationRepository` via constructor
- Replace `.logout(logout -> logout.disable())` with full logout config:
  - `logoutUrl("/logout")` with GET matcher
  - `OidcClientInitiatedLogoutSuccessHandler` using `clientRegistrationRepository`
  - `postLogoutRedirectUri` set from existing `${frontend.url}` property
  - Invalidate session, clear auth, delete JSESSIONID cookie
- Change permitted URL from `/api/auth/logout` to `/logout`

### 2. `AuthController.java`
- Remove `POST /api/auth/logout` endpoint (replaced by Spring Security's native `/logout`)
- Remove unused imports left behind by the deletion:
  - `jakarta.servlet.http.HttpServletRequest`
  - `jakarta.servlet.http.HttpServletResponse`
  - `org.springframework.security.core.context.SecurityContextHolder`
  - `org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler`
  - `org.springframework.web.bind.annotation.PostMapping`

### 3. `application.properties`
- No change needed — `frontend.url` already exists

### 4. Scope verification
- `openid` already present in `spring.security.oauth2.client.registration.google.scope` — no change needed
