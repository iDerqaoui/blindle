#!/usr/bin/env bash
# Préparation unique d'un serveur Ubuntu 24.04 neuf, en root :
#   ssh root@<ip> "DEPLOY_KEY='$(cat ~/.ssh/blindle-deploy.pub)' bash -s" < deploy/setup-server.sh
# Installe Docker, crée l'utilisateur « deploy » (clé fournie, membre du groupe docker), ferme tout
# sauf SSH / HTTP / HTTPS, active les mises à jour de sécurité et la sauvegarde quotidienne.
set -euo pipefail
# Clé publique : variable DEPLOY_KEY, ou arguments (ssh les découpe aux espaces : on les rejoint)
DEPLOY_KEY="${DEPLOY_KEY:-$*}"
[ -n "$DEPLOY_KEY" ] || { echo "usage : setup-server.sh \"ssh-ed25519 AAAA… github-actions\"" >&2; exit 1; }

apt-get update
apt-get install -y ca-certificates curl ufw unattended-upgrades
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sh
fi

id deploy >/dev/null 2>&1 || useradd -m -s /bin/bash deploy
usermod -aG docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
touch /home/deploy/.ssh/authorized_keys
grep -qF "$DEPLOY_KEY" /home/deploy/.ssh/authorized_keys || echo "$DEPLOY_KEY" >> /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys

install -d -o deploy -g deploy /opt/blindle

ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

dpkg-reconfigure -f noninteractive unattended-upgrades

echo "15 4 * * * deploy /opt/blindle/backup.sh >> /opt/blindle/backup.log 2>&1" > /etc/cron.d/blindle-backup

echo "Prêt. Reste : créer /opt/blindle/.env (modèle deploy/.env.example), puis pousser sur main."
