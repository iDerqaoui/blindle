-- Univers anime et personnages retenus, avec leur rang de puissance S–D (page admin).
-- Les données MyAnimeList (favoris, image, rôle) sont mises en cache ici : le démarrage n'appelle
-- Tenrai que pour les personnages ajoutés depuis le dernier import.
CREATE TABLE anime_universe (
    slug     VARCHAR(60)   PRIMARY KEY,
    name     VARCHAR(100)  NOT NULL,
    mal_ids  VARCHAR(200)  NOT NULL,
    color    VARCHAR(20),
    note     VARCHAR(1000),
    title    VARCHAR(200),
    cover    VARCHAR(1024),
    position INTEGER       NOT NULL
);

CREATE TABLE anime_character (
    universe_slug VARCHAR(60)   NOT NULL REFERENCES anime_universe (slug) ON DELETE CASCADE,
    mal_id        INTEGER       NOT NULL,
    name          VARCHAR(100)  NOT NULL,
    tier          VARCHAR(1)    NOT NULL,
    position      INTEGER       NOT NULL,
    favourites    INTEGER,
    role          VARCHAR(20),
    image         VARCHAR(1024),
    updated_at    TIMESTAMP     NOT NULL,
    updated_by    UUID,
    PRIMARY KEY (universe_slug, mal_id)
);

-- Saisons MyAnimeList fusionnées par univers (les personnages récents n'existent que dans les suites).
INSERT INTO anime_universe (slug, name, mal_ids, color, position) VALUES
    ('one-piece', 'One Piece', '21', '#d9412b', 1),
    ('naruto', 'Naruto', '20,1735', '#f28c28', 2),
    ('dragon-ball', 'Dragon Ball', '813,223,30694', '#f5a623', 3),
    ('attack-on-titan', 'Attack on Titan', '16498,25777,35760,40028', '#7a5c3e', 4),
    ('demon-slayer', 'Demon Slayer', '38000,47778,51019,55701', '#2e8b57', 5),
    ('jujutsu-kaisen', 'Jujutsu Kaisen', '40748,51009', '#4b3fa8', 6),
    ('my-hero-academia', 'My Hero Academia', '31964,33486,36456,38408,41587,49918', '#2a9d8f', 7),
    ('hunter-x-hunter', 'Hunter × Hunter', '11061', '#3a7d44', 8);
