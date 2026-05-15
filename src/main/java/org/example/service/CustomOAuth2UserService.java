package org.example.service;

import org.example.entity.OAuthIdentity;
import org.example.entity.User;
import org.example.repository.OAuthIdentityRepository;
import org.example.repository.UserRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class CustomOAuth2UserService extends OidcUserService {

    private final UserRepository userRepository;
    private final OAuthIdentityRepository oAuthIdentityRepository;

    public CustomOAuth2UserService(UserRepository userRepository,
                                   OAuthIdentityRepository oAuthIdentityRepository) {
        this.userRepository = userRepository;
        this.oAuthIdentityRepository = oAuthIdentityRepository;
    }

    protected OidcUser loadFromOidcProvider(OidcUserRequest userRequest) {
        return super.loadUser(userRequest);
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = loadFromOidcProvider(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        String providerUserId = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String firstName = oidcUser.getGivenName();
        String lastName = oidcUser.getFamilyName();

        oAuthIdentityRepository.findByProviderAndProviderUserId(provider, providerUserId)
            .orElseGet(() -> registerNewUser(provider, providerUserId, email, firstName, lastName));

        return oidcUser;
    }

    private OAuthIdentity registerNewUser(String provider, String providerUserId,
                                          String email, String firstName, String lastName) {
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setFirstName(firstName != null ? firstName : "");
            newUser.setLastName(lastName != null ? lastName : "");
            newUser.setUsername(deriveUsername(email));
            return userRepository.save(newUser);
        });

        OAuthIdentity identity = new OAuthIdentity();
        identity.setUser(user);
        identity.setProvider(provider);
        identity.setProviderUserId(providerUserId);
        return oAuthIdentityRepository.save(identity);
    }

    private String deriveUsername(String email) {
        return email.split("@")[0];
    }
}
