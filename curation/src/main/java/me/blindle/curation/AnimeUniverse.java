package me.blindle.curation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "anime_universe")
public class AnimeUniverse {

    @Id
    private String slug;

    @Column(nullable = false)
    private String name;

    /** Identifiants MyAnimeList des saisons fusionnées, séparés par des virgules. */
    @Column(name = "mal_ids", nullable = false)
    private String malIds;

    private String color;
    private String note;
    private String title;
    private String cover;

    @Column(nullable = false)
    private int position;

    protected AnimeUniverse() {
    }

    public AnimeUniverse(String slug, String name, String malIds, String color, int position) {
        this.slug = slug;
        this.name = name;
        this.malIds = malIds;
        this.color = color;
        this.position = position;
    }

    public String slug() {
        return slug;
    }

    public String name() {
        return name;
    }

    public List<Integer> malIds() {
        return Arrays.stream(malIds.split(",")).map(String::strip).map(Integer::valueOf).toList();
    }

    public String color() {
        return color;
    }

    public String note() {
        return note;
    }

    public String title() {
        return title;
    }

    public String cover() {
        return cover;
    }

    void note(String note) {
        this.note = note;
    }

    void anime(String title, String cover) {
        this.title = title;
        this.cover = cover;
    }
}
