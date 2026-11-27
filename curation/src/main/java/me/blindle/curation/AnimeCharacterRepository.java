package me.blindle.curation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnimeCharacterRepository extends JpaRepository<AnimeCharacter, AnimeCharacter.Key> {

    List<AnimeCharacter> findByUniverseSlugOrderByPosition(String universeSlug);

    long countByUniverseSlug(String universeSlug);
}
