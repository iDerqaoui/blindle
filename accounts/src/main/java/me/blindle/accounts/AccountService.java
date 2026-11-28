package me.blindle.accounts;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private final UserAccountRepository accounts;
    private final UserIdentityRepository identities;
    private final Clock clock;
    private final org.springframework.context.ApplicationEventPublisher events;

    public AccountService(UserAccountRepository accounts, UserIdentityRepository identities, Clock clock,
                          org.springframework.context.ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.identities = identities;
        this.clock = clock;
        this.events = events;
    }

    /**
     * Connexion : retrouve le compte de cette identité, sinon celui d'une autre identité au même
     * e-mail vérifié (Google puis Discord = un seul compte), sinon en crée un.
     */
    @Transactional
    public UserAccount signIn(OAuthProfile p) {
        Instant now = clock.instant();
        Optional<UserIdentity> known = identities.findById(new UserIdentity.Key(p.provider(), p.subject()));
        UserAccount account;
        if (known.isPresent()) {
            known.get().update(p);
            account = accounts.findById(known.get().accountId()).orElseThrow();
        } else {
            account = linkedAccount(p).orElseGet(() ->
                    accounts.save(new UserAccount(UUID.randomUUID(), OAuthProfile.nickname(p.name()), p.avatarUrl(), now)));
            identities.save(new UserIdentity(p, account.id()));
        }
        account.loggedIn(p.avatarUrl(), now);
        return account;
    }

    private Optional<UserAccount> linkedAccount(OAuthProfile p) {
        if (!p.emailVerified() || p.email() == null) {
            return Optional.empty();
        }
        return identities.findByEmailAndEmailVerifiedTrue(p.email()).stream().findFirst()
                .flatMap(i -> accounts.findById(i.accountId()));
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> find(UUID id) {
        return accounts.findById(id);
    }

    @Transactional
    public UserAccount rename(UUID id, String name) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty() || n.length() > OAuthProfile.MAX_NAME) {
            throw new IllegalArgumentException("Pseudo de 1 à " + OAuthProfile.MAX_NAME + " caractères");
        }
        UserAccount account = accounts.findById(id).orElseThrow();
        account.rename(n);
        return account;
    }

    /**
     * Suppression du compte (RGPD) : identités Google / Discord comprises (cascade), puis chaque
     * module efface ses données liées ({@link AccountDeletedEvent}).
     */
    @Transactional
    public void delete(UUID id) {
        accounts.findById(id).ifPresent(a -> {
            identities.deleteAll(identities.findByAccountId(id));
            accounts.delete(a);
            events.publishEvent(new AccountDeletedEvent(id));
        });
    }
}
