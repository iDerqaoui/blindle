package me.blindle.curation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Personnage retenu dans un univers : nom d'affichage, rang S–D, données MyAnimeList en cache. */
@Entity
@Table(name = "anime_character")
@IdClass(AnimeCharacter.Key.class)
public class AnimeCharacter {

    @Id
    @Column(name = "universe_slug")
    private String universeSlug;

    @Id
    @Column(name = "mal_id")
    private int malId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String tier;

    /** Ordre de la liste de curation (départage déterministe à score égal). */
    @Column(nullable = false)
    private int position;

    private Integer favourites;
    private String role;
    private String image;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected AnimeCharacter() {
    }

    public AnimeCharacter(String universeSlug, int malId, String name, String tier, int position,
                          Instant now, UUID by) {
        this.universeSlug = universeSlug;
        this.malId = malId;
        this.name = name;
        this.tier = tier;
        this.position = position;
        touch(now, by);
    }

    public int malId() {
        return malId;
    }

    public String name() {
        return name;
    }

    public String tier() {
        return tier;
    }

    public int position() {
        return position;
    }

    public Integer favourites() {
        return favourites;
    }

    public String role() {
        return role;
    }

    public String image() {
        return image;
    }

    public boolean hasMalData() {
        return favourites != null;
    }

    void edit(String name, String tier, Instant now, UUID by) {
        if (name != null) {
            this.name = name;
        }
        if (tier != null) {
            this.tier = tier;
        }
        touch(now, by);
    }

    void malData(MalClient.MalCharacter c) {
        this.favourites = c.favourites();
        this.role = c.role();
        this.image = c.image();
    }

    private void touch(Instant now, UUID by) {
        this.updatedAt = now;
        this.updatedBy = by;
    }

    RarityCalculator.Candidate candidate() {
        return new RarityCalculator.Candidate(malId, name, tier, favourites, role, image);
    }

    public static class Key implements Serializable {
        private String universeSlug;
        private int malId;

        protected Key() {
        }

        public Key(String universeSlug, int malId) {
            this.universeSlug = universeSlug;
            this.malId = malId;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) {
                return false;
            }
            Key k = (Key) o;
            return malId == k.malId && Objects.equals(universeSlug, k.universeSlug);
        }

        @Override
        public int hashCode() {
            return Objects.hash(universeSlug, malId);
        }
    }
}
