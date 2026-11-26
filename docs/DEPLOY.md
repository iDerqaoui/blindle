# Mise en ligne (test entre amis)

Une seule image Docker (`Dockerfile` à la racine) : front Angular servi par Spring Boot,
STOMP sur `/ws`, thèmes anime générés au build depuis Tenrai / MyAnimeList (`tools/import-anime.mjs`).
Port lu dans `PORT` (défaut 8080). Origines WebSocket : `BLINDLE_ALLOWED_ORIGINS`.

Rappel §8 : univers anime = profil `private`, images MyAnimeList → lien partagé entre amis seulement.

## Option A — Cloudflare Tunnel depuis son PC (0 €, 2 minutes)

    mvn clean install -DskipTests
    cd web && npx ng build && cd ..
    java -jar app/target/app-0.1.0-SNAPSHOT.jar --blindle.content.dir=content ^
         --spring.web.resources.static-locations=file:web/dist/blindle-web/browser/ ^
         --blindle.allowed-origins=https://*.trycloudflare.com,http://localhost:*
    cloudflared tunnel --url http://localhost:8080      (winget install Cloudflare.cloudflared)

Donne une URL `https://xxx.trycloudflare.com` à partager. Le PC doit rester allumé.

## Option B — Render (gratuit, toujours en ligne)

1. Pousser le dépôt sur GitHub ou GitLab (privé accepté).
2. render.com → New → Web Service → le dépôt → Runtime **Docker**, plan **Free**.
3. Déployer. URL : `https://<nom>.onrender.com`.

Limites du plan gratuit : mise en veille après 15 min sans trafic (~1 min de réveil),
512 Mo de RAM. Les rooms étant en mémoire, une veille ou un redéploiement les efface.

Alternative équivalente : Koyeb (free, Docker, WebSocket, `*.koyeb.app`).

## Discord

- Simple : coller l'URL dans un salon Discord, chacun ouvre le lien.
- Discord Activity (jeu dans le salon vocal) : étape 11 (`docs/PROJECT.md` §11) — application
  Discord, URL mapping vers l'URL ci-dessus, SDK `@discord/embedded-app-sdk` dans le front, et
  mapping des images MyAnimeList (cdn.myanimelist.net) (la CSP de Discord bloque les domaines non déclarés).
