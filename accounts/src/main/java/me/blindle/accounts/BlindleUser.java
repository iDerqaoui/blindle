package me.blindle.accounts;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utilisateur connecté, gardé en session : son compte, son fournisseur et son rôle (lu en base à la
 * connexion : un changement de rôle vaut à la connexion suivante). Le pseudo, modifiable, est relu en base.
 */
public record BlindleUser(UUID accountId, String provider, boolean admin) implements OAuth2User, Serializable {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of("accountId", accountId.toString(), "provider", provider);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return admin
                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority(ROLE_ADMIN))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getName() {
        return accountId.toString();
    }
}
