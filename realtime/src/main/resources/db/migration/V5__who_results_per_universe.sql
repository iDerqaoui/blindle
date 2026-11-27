-- Un défi Who's That? par univers et par jour : l'univers entre dans la clé. La table ne contenait
-- que les essais de test du jour de sa création (2026-11-27) : on la recrée.
DROP TABLE who_result;

CREATE TABLE who_result (
    played_on   DATE          NOT NULL,
    theme       VARCHAR(100)  NOT NULL,
    player_key  VARCHAR(100)  NOT NULL,
    name        VARCHAR(20)   NOT NULL,
    universe    VARCHAR(100)  NOT NULL,
    score       INTEGER       NOT NULL,
    gold_left   INTEGER       NOT NULL,
    share       VARCHAR(500)  NOT NULL,
    finished_at TIMESTAMP     NOT NULL,
    PRIMARY KEY (played_on, theme, player_key)
);

CREATE INDEX who_result_ranking ON who_result (played_on, theme, score, gold_left);
CREATE INDEX who_result_player ON who_result (player_key, played_on);
