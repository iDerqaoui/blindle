package me.blindle.accounts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identité chez un fournisseur (Google, Discord), rattachée à un compte. */
@Entity
@Table(name = "user_identity")
@IdClass(UserIdentity.Key.class)
public class UserIdentity {

    @Id
    private String provider;

    @Id
    private String subject;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    protected UserIdentity() {
    }

    public UserIdentity(OAuthProfile p, UUID accountId) {
        this.provider = p.provider();
        this.subject = p.subject();
        this.accountId = accountId;
        update(p);
    }

    void update(OAuthProfile p) {
        this.email = p.email();
        this.emailVerified = p.emailVerified();
    }

    public UUID accountId() {
        return accountId;
    }

    public static class Key implements Serializable {
        private String provider;
        private String subject;

        protected Key() {
        }

        public Key(String provider, String subject) {
            this.provider = provider;
            this.subject = subject;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) {
                return false;
            }
            Key k = (Key) o;
            return Objects.equals(provider, k.provider) && Objects.equals(subject, k.subject);
        }

        @Override
        public int hashCode() {
            return Objects.hash(provider, subject);
        }
    }
}
