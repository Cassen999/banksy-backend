# Backend Task — Fix Logout Loop + Force Google Account Selection

## Context

The current logout implementation uses `OidcClientInitiatedLogoutSuccessHandler` to
attempt an OIDC RP-initiated logout with Google. This does not work because Google's
OIDC discovery document does not include an `end_session_endpoint`. When Spring cannot
find that endpoint, it falls back to redirecting the browser to its own `/login` page,
which immediately starts a new OAuth flow. The result is a redirect loop:

```
/logout → Spring invalidates session → no end_session_endpoint → falls back to /login
        → Google OAuth → accounts.google.com → user logs back in
```

The React frontend never loads. The user never returns to the app after logout.

---

## Changes Required

### 1. Replace `OidcClientInitiatedLogoutSuccessHandler` with a simple redirect

Remove the `OidcClientInitiatedLogoutSuccessHandler` and replace it with
`.logoutSuccessUrl()` pointing directly to the frontend URL. The session is
invalidated, the JSESSIONID cookie is deleted, and the browser is sent straight
to the React app.

Update the logout configuration in `SecurityConfig.java`:

```java
.logout(logout -> logout
    .logoutUrl("/logout")
    .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
    .logoutSuccessUrl(frontendUrl)   // the existing frontend.url @Value property
    .invalidateHttpSession(true)
    .clearAuthentication(true)
    .deleteCookies("JSESSIONID")
)
```

Remove the `oidcLogoutSuccessHandler()` method and its `OidcClientInitiatedLogoutSuccessHandler`
import entirely — they are no longer needed.

The `frontendUrl` field should already exist from the previous backend task
(`@Value("${frontend.url}") private String frontendUrl`). Reuse it — do not add a
duplicate property.

---

### 2. Add `prompt=select_account` to every OAuth2 authorization request

Since Google does not support RP-initiated logout, the way to prevent silent
re-authentication after logout is to force Google to show the account selection
screen on every login. This is done by adding `prompt=select_account` as an
additional parameter to the OAuth2 authorization request.

Add a custom `OAuth2AuthorizationRequestResolver` bean that wraps the default
resolver and appends `prompt=select_account` to every authorization request.
Wire it into the `oauth2Login` configuration.

Example:

```java
@Bean
public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
        ClientRegistrationRepository clientRegistrationRepository) {
    DefaultOAuth2AuthorizationRequestResolver resolver =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, "/oauth2/authorization");

    resolver.setAuthorizationRequestCustomizer(customizer ->
        customizer.additionalParameters(params ->
            params.put("prompt", "select_account")));

    return resolver;
}
```

Wire it in `SecurityConfig`:

```java
.oauth2Login(oauth2 -> oauth2
    .authorizationEndpoint(endpoint -> endpoint
        .authorizationRequestResolver(authorizationRequestResolver(clientRegistrationRepository))
    )
    .defaultSuccessUrl(frontendUrl, true)
)
```

---

## Expected Behaviour After These Changes

**Logout:**
1. Browser navigates to `GET http://localhost:8080/logout`
2. Spring invalidates the session and deletes the JSESSIONID cookie
3. Browser is redirected to `http://localhost:5173` (the frontend)
4. React app loads, `AuthProvider` calls `/api/auth/me`, gets a 401 (no session),
   sets `user = null`, and shows the Login button

**Next login after logout:**
1. User clicks Login → browser navigates to `/oauth2/authorization/google`
2. Google shows the account selection / sign-in screen (not a silent re-authentication)
3. User selects their account → OAuth flow completes → session created
4. Browser returns to `http://localhost:5173`

---

## What Does Not Change

- The `/logout` URL and the fact that it accepts `GET` requests — the frontend
  navigates there via `window.location.href` and that remains correct.
- The `frontend.url` application property — reuse the existing value.
- All other `SecurityConfig` settings not mentioned above.
