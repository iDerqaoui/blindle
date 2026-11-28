package me.blindle.accounts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    private UUID id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    /** {@link #PLAYER} ou {@link #ADMIN}, changé seulement en base (deploy/make-admin.sh). */
    @Column(nullable = false)
    private String role = PLAYER;

    public static final String PLAYER = "PLAYER";
    public static final String ADMIN = "ADMIN";

    protected UserAccount() {
    }

    public UserAccount(UUID id, String displayName, String avatarUrl, Instant now) {
        this.id = id;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.createdAt = now;
        this.lastLoginAt = now;
    }

    public UUID id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public boolean admin() {
        return ADMIN.equals(role);
    }

    /** Pour les tests et l'outillage ; en production le rôle se donne en base. */
    public void role(String role) {
        this.role = role;
    }

    void rename(String name) {
        this.displayName = name;
    }

    void loggedIn(String avatar, Instant now) {
        if (avatar != null) {
            this.avatarUrl = avatar;
        }
        this.lastLoginAt = now;
    }
}
