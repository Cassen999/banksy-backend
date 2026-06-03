# Diagrams — OIDC Logout

## Logout Flow

```
Browser                  Spring Boot              Google
  |                          |                       |
  |  GET /logout             |                       |
  |------------------------->|                       |
  |                          | invalidate session    |
  |                          | delete JSESSIONID     |
  |                          |                       |
  |  302 → accounts.google.com/o/oauth2/v2/logout   |
  |         ?post_logout_redirect_uri=localhost:5173  |
  |<-------------------------|                       |
  |                                                  |
  |  GET accounts.google.com/o/oauth2/v2/logout      |
  |------------------------------------------------->|
  |                          revoke token, sign out  |
  |  302 → http://localhost:5173                     |
  |<-------------------------------------------------|
  |                                                  |
  |  GET http://localhost:5173                       |
  | (React app loads, /api/auth/me → 302, user=null)|
```

## Before vs After

| | Before | After |
|---|---|---|
| Logout endpoint | `POST /api/auth/logout` | `GET /logout` |
| Session cleared | Yes | Yes |
| Google token revoked | No | Yes |
| Post-logout destination | JSON `{ loggedOut: true }` | Redirect to frontend |
| Silent re-auth on next login | Yes | No |
