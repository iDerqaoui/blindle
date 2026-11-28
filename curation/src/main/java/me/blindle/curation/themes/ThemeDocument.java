package me.blindle.curation.themes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Thème édité par l'administrateur : document JSON complet (même format qu'un fichier de content/themes). */
@Entity
@Table(name = "theme_document")
public class ThemeDocument {

    @Id
    @Column(name = "theme_id")
    private String themeId;

    @Column(nullable = false, length = 1_000_000)
    private String document;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected ThemeDocument() {
    }

    public ThemeDocument(String themeId, String document, Instant now, UUID by) {
        this.themeId = themeId;
        update(document, now, by);
    }

    public void update(String document, Instant now, UUID by) {
        this.document = document;
        this.updatedAt = now;
        this.updatedBy = by;
    }

    public String themeId() {
        return themeId;
    }

    public String document() {
        return document;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public UUID updatedBy() {
        return updatedBy;
    }
}
