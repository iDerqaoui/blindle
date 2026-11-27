package me.blindle.curation;

import java.util.List;
import java.util.Optional;

/** Données MyAnimeList d'un univers (via Tenrai en production, faux client en test). */
public interface MalClient {

    /** Personnage d'un anime : nom au format MyAnimeList (« Nom, Prénom »), favoris, rôle, image. */
    record MalCharacter(int malId, String name, int favourites, String role, String image) {
    }

    record AnimeInfo(String title, String cover) {
    }

    /** Distribution fusionnée de plusieurs saisons ; « Main » l'emporte sur « Supporting ». */
    List<MalCharacter> cast(List<Integer> animeIds);

    Optional<AnimeInfo> anime(int animeId);
}
