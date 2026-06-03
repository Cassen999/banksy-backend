# Backend Task — Redirect OAuth Denial to Frontend

## Context

When a user denies the Google OAuth consent screen, Spring Security redirects the
browser to `http://localhost:8080/login?error`. This shows a Spring-generated error
page. The desired behaviour is to redirect the browser back to the frontend home page
(`http://localhost:5173`) instead.

---

## Change Required

Add a failure handler to the `oauth2Login` configuration in `SecurityConfig.java`.
The `frontendUrl` field already exists from a previous task — reuse it, do not add
a duplicate property.

```java
.oauth2Login(oauth2 -> oauth2
    .failureHandler((request, response, exception) ->
        response.sendRedirect(frontendUrl))
    .authorizationEndpoint(endpoint -> endpoint
        .authorizationRequestResolver(authorizationRequestResolver(...))
    )
    .defaultSuccessUrl(frontendUrl, true)
)
```

This applies to any OAuth2 login failure: user denying consent, Google returning an
error, or any other authorization failure. In all cases the browser lands back at the
frontend home page.

---

## Expected Behaviour After This Change

1. User clicks Login → Google OAuth consent screen appears
2. User clicks **Deny**
3. Browser is redirected to `http://localhost:5173` (the frontend home page)
4. The React app loads normally with the Login button visible

---

## What Does Not Change

- The `frontendUrl` property value
- The `prompt=select_account` authorization request customizer
- The logout configuration
- All other `SecurityConfig` settings not mentioned above
