# Diagrams — Logout Fix

## Logout Flow (Before — broken)

```
Browser → GET /logout → Spring invalidates session
        → no end_session_endpoint found
        → falls back to /login
        → Google OAuth → user silently re-authenticated
        → never returns to frontend  ✗
```

## Logout Flow (After — fixed)

```
Browser → GET /logout → Spring invalidates session, deletes JSESSIONID
        → 302 → http://localhost:5173
        → React app loads, /api/auth/me returns 401, user = null  ✓
```

## Login Flow (After — with forced account selection)

```
Browser → /oauth2/authorization/google
        → authorization request includes prompt=select_account
        → Google shows account selection screen  ✓
        → user selects account → OAuth completes → session created
        → 302 → http://localhost:5173
```

## What Changed

| | Before | After |
|---|---|---|
| Logout handler | `OidcClientInitiatedLogoutSuccessHandler` | `logoutSuccessUrl(frontendUrl)` |
| Post-logout destination | Broken loop via `/login` | Direct redirect to frontend |
| Next login | Silent re-auth (no Google screen) | Forced account selection screen |
