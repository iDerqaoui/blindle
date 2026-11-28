-- Rôle du compte, en base : PLAYER (défaut) ou ADMIN. Remplace la liste d'e-mails et le mot de
-- passe admin. Donner le rôle : deploy/make-admin.sh <e-mail> sur le serveur.
ALTER TABLE user_account ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'PLAYER';
