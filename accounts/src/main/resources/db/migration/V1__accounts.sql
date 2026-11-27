-- Comptes joueurs. Un compte peut être lié à plusieurs identités (Google, Discord) ;
-- deux identités se rejoignent sur le même compte seulement si elles partagent un e-mail vérifié.
CREATE TABLE user_account (
    id            UUID         PRIMARY KEY,
    display_name  VARCHAR(40)  NOT NULL,
    avatar_url    VARCHAR(1024),
    created_at    TIMESTAMP    NOT NULL,
    last_login_at TIMESTAMP    NOT NULL
);

CREATE TABLE user_identity (
    provider       VARCHAR(20)  NOT NULL,
    subject        VARCHAR(255) NOT NULL,
    account_id     UUID         NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    email          VARCHAR(320),
    email_verified BOOLEAN      NOT NULL,
    PRIMARY KEY (provider, subject)
);

CREATE INDEX user_identity_email ON user_identity (email);
