#!/usr/bin/env bash
# Sauvegarde quotidienne de la base (cron installé par setup-server.sh) : /opt/blindle/backups,
# 14 derniers jours. Restaurer : gunzip -c <fichier> | docker compose -f docker-compose.prod.yml exec -T db psql -U blindle blindle
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p backups
file="backups/blindle-$(date +%F).sql.gz"
BLINDLE_IMAGE="$(cat .current-image)" docker compose -f docker-compose.prod.yml exec -T db \
  pg_dump -U blindle --clean --if-exists blindle | gzip > "$file"
find backups -name 'blindle-*.sql.gz' -mtime +14 -delete
echo "Sauvegarde : $file"
