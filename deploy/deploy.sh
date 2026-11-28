#!/usr/bin/env bash
# Déploie une image sur le serveur : ./deploy.sh ghcr.io/<compte>/blindle:<sha>
# Garde l'image précédente et y revient si la nouvelle ne répond pas en 3 minutes.
set -euo pipefail
cd "$(dirname "$0")"

IMAGE="${1:?usage : ./deploy.sh <image>}"
PREVIOUS="$(cat .current-image 2>/dev/null || true)"
export BLINDLE_IMAGE="$IMAGE"

compose() { docker compose -f docker-compose.prod.yml "$@"; }

healthy() {
  # premier démarrage : ~30 s de génération des thèmes anime (Tenrai) avant d'écouter
  for _ in $(seq 1 90); do
    if curl -fsS -o /dev/null http://127.0.0.1:8080/api/auth/providers; then
      return 0
    fi
    sleep 2
  done
  return 1
}

compose pull app
compose up -d --remove-orphans
if healthy; then
  echo "$IMAGE" > .current-image
  docker image prune -f >/dev/null
  echo "Déployé : $IMAGE"
  exit 0
fi

echo "La nouvelle version ne répond pas. Journaux :" >&2
compose logs --tail=80 app >&2 || true
if [ -n "$PREVIOUS" ] && [ "$PREVIOUS" != "$IMAGE" ]; then
  echo "Retour à $PREVIOUS" >&2
  BLINDLE_IMAGE="$PREVIOUS" compose up -d app
fi
exit 1
