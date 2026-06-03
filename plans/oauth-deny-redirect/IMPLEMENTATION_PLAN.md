# Implementation Plan — OAuth Deny Redirect

## Changes

### 1. `SecurityConfig.java`
- Add `.failureHandler((request, response, exception) -> response.sendRedirect(frontendUrl))` to the `oauth2Login` configuration
- Reuse the existing `frontendUrl` field — no new property needed
- No other changes

## What Does Not Change
- `prompt=select_account` resolver
- Logout configuration
- `application.properties`
- All other `SecurityConfig` settings
