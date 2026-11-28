#!/usr/bin/env bash
# Donne (ou retire) le rôle ADMIN à un compte, d'après l'e-mail de son identité Google / Discord.
# Le compte doit s'être connecté au moins une fois sur le site. Effet à sa prochaine connexion.
#   ./make-admin.sh moi@gmail.com          → ADMIN
#   ./make-admin.sh moi@gmail.com PLAYER   → retire le rôle
set -euo pipefail
cd "$(dirname "$0")"
EMAIL="${1:?usage : ./make-admin.sh <e-mail> [ADMIN|PLAYER]}"
ROLE="${2:-ADMIN}"
case "$ROLE" in ADMIN|PLAYER) ;; *) echo "rôle : ADMIN ou PLAYER" >&2; exit 1;; esac
case "$EMAIL" in *\'*) echo "e-mail invalide" >&2; exit 1;; esac

docker compose -f docker-compose.prod.yml exec -T db psql -U blindle -d blindle -v ON_ERROR_STOP=1 <<SQL
UPDATE user_account SET role = '$ROLE'
 WHERE id IN (SELECT account_id FROM user_identity WHERE lower(email) = lower('$EMAIL'));
SELECT a.display_name, a.role, i.provider, i.email
  FROM user_account a JOIN user_identity i ON i.account_id = a.id
 WHERE lower(i.email) = lower('$EMAIL');
SQL
echo "Reconnecte-toi sur le site pour que le rôle s'applique."
