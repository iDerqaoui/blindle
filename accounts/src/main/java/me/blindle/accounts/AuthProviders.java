package me.blindle.accounts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fournisseurs de connexion configurés. Un fournisseur sans identifiant client est simplement
 * absent : sans aucun fournisseur, le site fonctionne sans comptes.
 * Adresse de retour à déclarer chez le fournisseur : {@code <site>/login/oauth2/code/<google|discord>}.
 */
@Component
public class AuthProviders {

    private static final String REDIRECT = "{baseUrl}/login/oauth2/code/{registrationId}";

    private final List<ClientRegistration> registrations;

    public AuthProviders(@Value("${blindle.auth.google.client-id:}") String googleId,
                         @Value("${blindle.auth.google.client-secret:}") String googleSecret,
                         @Value("${blindle.auth.discord.client-id:}") String discordId,
                         @Value("${blindle.auth.discord.client-secret:}") String discordSecret) {
        List<ClientRegistration> list = new ArrayList<>();
        if (!googleId.isBlank()) {
            // Sans « openid » : profil lu sur l'endpoint userinfo, comme pour Discord.
            list.add(CommonOAuth2Provider.GOOGLE.getBuilder(OAuthProfile.GOOGLE)
                    .clientId(googleId).clientSecret(googleSecret)
                    .scope("profile", "email")
                    .redirectUri(REDIRECT)
                    .build());
        }
        if (!discordId.isBlank()) {
            list.add(ClientRegistration.withRegistrationId(OAuthProfile.DISCORD)
                    .clientId(discordId).clientSecret(discordSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(REDIRECT)
                    .scope("identify", "email")
                    .authorizationUri("https://discord.com/oauth2/authorize")
                    .tokenUri("https://discord.com/api/oauth2/token")
                    .userInfoUri("https://discord.com/api/users/@me")
                    .userNameAttributeName("id")
                    .clientName("Discord")
                    .build());
        }
        this.registrations = List.copyOf(list);
    }

    public List<ClientRegistration> registrations() {
        return registrations;
    }

    public List<String> ids() {
        return registrations.stream().map(ClientRegistration::getRegistrationId).toList();
    }
}
