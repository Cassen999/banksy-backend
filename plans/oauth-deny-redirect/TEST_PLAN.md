# Test Plan — OAuth Deny Redirect

## Existing Tests
No test changes expected — no existing test covers the OAuth failure path.

## Manual Verification
1. Navigate to `http://localhost:8080/oauth2/authorization/google`
2. On the Google consent screen, click **Deny**
3. Confirm browser lands at `http://localhost:5173` (not Spring's `/login?error` page)
4. Confirm the React app loads normally with the Login button visible
