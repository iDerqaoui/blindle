-- Thèmes édités par l'administrateur (cartes de manga-shonen, pays-du-monde…) : le document complet,
-- validé comme un fichier de content/themes, remplace le fichier de même identifiant. Retirer la
-- ligne rend le fichier. Les thèmes anime (générés depuis anime_character) n'y vont pas.
CREATE TABLE theme_document (
    theme_id   VARCHAR(60)      PRIMARY KEY,
    document   VARCHAR(1000000) NOT NULL,
    updated_at TIMESTAMP        NOT NULL,
    updated_by UUID
);
