package me.blindle.curation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnimeUniverseRepository extends JpaRepository<AnimeUniverse, String> {

    List<AnimeUniverse> findAllByOrderByPosition();
}
