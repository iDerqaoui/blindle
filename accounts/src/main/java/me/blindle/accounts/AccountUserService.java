package me.blindle.accounts;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/** Après l'échange OAuth2 : lit le profil du fournisseur, ouvre ou crée le compte, fixe le rôle. */
@Component
public class AccountUserService extends DefaultOAuth2UserService {

    private final AccountService accounts;
    private final AdminAllowList admins;

    public AccountUserService(AccountService accounts, AdminAllowList admins) {
        this.accounts = accounts;
        this.admins = admins;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User raw = super.loadUser(request);
        return toUser(OAuthProfile.from(request.getClientRegistration().getRegistrationId(), raw.getAttributes()));
    }

    BlindleUser toUser(OAuthProfile profile) {
        UserAccount account = accounts.signIn(profile);
        return new BlindleUser(account.id(), profile.provider(), admins.isAdmin(profile));
    }
}
