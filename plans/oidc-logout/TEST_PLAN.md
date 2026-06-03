# Test Plan — OIDC Logout

## Unit / Integration Tests

### `AuthControllerTest`
- Remove test for `POST /api/auth/logout` (endpoint is being deleted)

### `SecurityConfigTest` (new or existing)
- `GET /logout` returns a redirect (3xx) to Google's end session endpoint when authenticated
- `GET /logout` is accessible without authentication (permit all)
- Response redirect URL contains `post_logout_redirect_uri=http://localhost:5173`
- Session is invalidated after logout (subsequent request to protected endpoint returns 401/302)

## Manual Verification
1. Log in via Google
2. Navigate to `GET http://localhost:8080/logout`
3. Confirm redirect to `accounts.google.com/o/oauth2/...` (Google end session)
4. Confirm final redirect lands at `http://localhost:5173`
5. Click Login again — confirm Google login screen appears (no silent re-auth)
