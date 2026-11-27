-- Défi du jour « Who's That? » : un résultat par joueur et par jour (compte connecté « u:… » ou
-- navigateur « c:… »). Sert au classement du jour, à l'essai unique et à la série de jours.
CREATE TABLE who_result (
    played_on   DATE          NOT NULL,
    player_key  VARCHAR(100)  NOT NULL,
    name        VARCHAR(20)   NOT NULL,
    universe    VARCHAR(100)  NOT NULL,
    score       INTEGER       NOT NULL,
    gold_left   INTEGER       NOT NULL,
    share       VARCHAR(500)  NOT NULL,
    finished_at TIMESTAMP     NOT NULL,
    PRIMARY KEY (played_on, player_key)
);

CREATE INDEX who_result_ranking ON who_result (played_on, score, gold_left);
CREATE INDEX who_result_player ON who_result (player_key, played_on);
