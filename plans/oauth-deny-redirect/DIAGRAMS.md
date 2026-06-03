# Diagrams — OAuth Deny Redirect

## OAuth Denial Flow (Before)

```
User clicks Deny → Google → 302 → /login?error → Spring error page  ✗
```

## OAuth Denial Flow (After)

```
User clicks Deny → Google → failureHandler → 302 → http://localhost:5173  ✓
React app loads with Login button visible
```

## What Changed in oauth2Login

| | Before | After |
|---|---|---|
| On OAuth failure | Redirect to `/login?error` | Redirect to `frontend.url` |
| On OAuth success | Redirect to `frontend.url` | Redirect to `frontend.url` (unchanged) |
