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
