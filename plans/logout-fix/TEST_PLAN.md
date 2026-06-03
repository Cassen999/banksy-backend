# Test Plan — Logout Fix

## Existing tests
No test changes expected — the logout URL, method, and session invalidation behaviour are unchanged.

## Manual Verification

**Logout:**
1. Log in via Google
2. Navigate to `GET http://localhost:8080/logout`
3. Confirm browser lands directly at `http://localhost:5173` (no Google redirect loop)
4. Confirm JSESSIONID cookie is gone

**Account selection on next login:**
1. After logout, click Login
2. Navigate to `http://localhost:8080/oauth2/authorization/google`
3. Confirm Google shows the account selection / sign-in screen (not a silent re-authentication)
