# Backend Task — OIDC Logout (True Google Sign-Out)

## Context

This is a Spring Boot backend that uses Spring Security OAuth2 with Google as the sole
authentication provider. Authentication is session-based (no JWTs). The frontend is a
separate React SPA served at a different origin.

There is currently a custom controller endpoint `POST /api/auth/logout` that invalidates
the Spring session and returns `{ "loggedOut": true }`. This only destroys the Spring
session — it does not sign the user out of Google. The next time the user clicks Login,
Google silently re-authenticates them without showing a login screen.

The goal is to implement true OIDC logout so that clicking Logout also signs the user
out of Google, requiring them to re-enter their Google credentials on next login.

---

## What needs to be implemented

Configure Spring Security's built-in logout mechanism with
`OidcClientInitiatedLogoutSuccessHandler`. This handler:

1. Invalidates the Spring session
2. Redirects the browser to Google's OIDC end session endpoint
3. Google revokes the token and signs the user out
4. Google redirects the browser back to the frontend (post-logout redirect URI)

---

## Specific changes required

### 1. `SecurityConfig.java`

Add logout configuration to the `SecurityFilterChain`. The logout URL should be
`/logout` (Spring Security's native logout endpoint). Configure it to accept GET
requests (the frontend will navigate the browser there directly via
`window.location.href`). Use `OidcClientInitiatedLogoutSuccessHandler` as the
success handler.

The `postLogoutRedirectUri` must be read from a configurable application property
(not hardcoded) so it can differ between development and production environments.

Example structure:

```java
.logout(logout -> logout
    .logoutUrl("/logout")
    .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
    .logoutSuccessHandler(oidcLogoutSuccessHandler())
    .invalidateHttpSession(true)
    .clearAuthentication(true)
    .deleteCookies("JSESSIONID")
)

private OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler() {
    OidcClientInitiatedLogoutSuccessHandler handler =
        new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
    handler.setPostLogoutRedirectUri(frontendUrl); // from @Value property
    return handler;
}
```

Make `/logout` publicly accessible (no session required to call it) — consistent with
the existing `/api/auth/logout` behavior.

### 2. `application.properties`

Add a configurable property for the frontend URL used as the post-logout redirect:

```
frontend.url=http://localhost:5173
```

This property should already exist if it was previously added for `defaultSuccessUrl`.
If so, reuse it rather than adding a duplicate.

### 3. Remove or deprecate `POST /api/auth/logout`

The existing custom controller endpoint at `/api/auth/logout` should be removed or
clearly marked as deprecated. The frontend will no longer call it — it will navigate
to `GET /logout` instead.

If removing the endpoint would break anything else, leave it in place but note that
it no longer performs a complete logout (only removes the Spring session, not the
Google token).

### 4. Verify OIDC scope includes `openid`

`OidcClientInitiatedLogoutSuccessHandler` requires the ID token to be present in the
session, which is only possible if the `openid` scope is requested during login. Verify
that the Google OAuth2 client registration includes `openid` in its scopes:

```yaml
# application.yml or application.properties
spring.security.oauth2.client.registration.google.scope=openid,email,profile
```

If it is already present, no change is needed.

---

## Expected behavior after this change

1. Browser navigates to `GET http://localhost:8080/logout`
2. Spring Security invalidates the session and deletes the JSESSIONID cookie
3. Spring redirects the browser to Google's end session endpoint with
   `post_logout_redirect_uri=http://localhost:5173`
4. Google signs the user out
5. Google redirects the browser back to `http://localhost:5173`
6. The React app loads, `AuthProvider` calls `/api/auth/me`, gets a 302 (no session),
   sets `user = null`, and shows the Login button

---

## What the frontend will do (for your reference — no backend action needed)

The frontend will change its logout call from `POST /api/auth/logout` via Axios to
`window.location.href = http://localhost:8080/logout` (a full browser navigation).
This frontend change will be made separately once the backend endpoint is confirmed
working.

---

## Definition of done

- `GET /logout` invalidates the Spring session AND redirects to Google's end session
  endpoint
- After the redirect chain completes, the browser lands at `http://localhost:5173`
- Clicking Login after logout takes the user back to the Google login screen (not
  a silent re-authentication)
- The `postLogoutRedirectUri` is read from a configurable property, not hardcoded
- The `openid` scope is confirmed present in the Google client registration
