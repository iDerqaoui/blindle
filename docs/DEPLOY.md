# Mise en ligne (test entre amis)

Une seule image Docker (`Dockerfile` à la racine) : front Angular servi par Spring Boot,
STOMP sur `/ws`. Port lu dans `PORT` (défaut 8080). Origines WebSocket : `BLINDLE_ALLOWED_ORIGINS`.

Depuis le 2026-11-27, le serveur a besoin d'une base **PostgreSQL** (comptes, curation des
personnages). Au premier démarrage il reprend `content/curation/*.json` en base, complète les
données depuis Tenrai / MyAnimeList (~30 s, accès Internet requis) et génère les thèmes anime ;
les démarrages suivants n'ont plus besoin de Tenrai.

Rappel §8 : univers anime = profil `private`, images MyAnimeList → lien partagé entre amis seulement.

## Variables d'environnement

| Variable | Rôle |
|---|---|
| `SPRING_DATASOURCE_URL` | ex. `jdbc:postgresql://hôte:5432/blindle` (défaut : localhost, base/utilisateur/mot de passe `blindle`) |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | identifiants de la base |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | connexion Google (facultatif) |
| `DISCORD_CLIENT_ID`, `DISCORD_CLIENT_SECRET` | connexion Discord (facultatif) |
| `BLINDLE_ADMIN_EMAILS` | admins : e-mails vérifiés ou `discord:<id>`, séparés par des virgules |

Sans identifiants Google ni Discord, le bouton « Connexion » n'apparaît pas et le jeu marche sans comptes.

## Connexion Google et Discord

L'adresse de retour à déclarer est `<adresse du site>/login/oauth2/code/google` (ou `/discord`).
Déclarer une adresse par environnement : `http://localhost:4200/...` (front de dev),
`http://localhost:8080/...` (jar), l'URL publique (`https://xxx.trycloudflare.com/...`, qui change
à chaque tunnel rapide : un tunnel nommé garde la même).

- **Google** : console.cloud.google.com → APIs & Services → OAuth consent screen (External, mode
  test : ajouter les comptes autorisés) → Credentials → Create credentials → OAuth client ID →
  Web application → Authorized redirect URIs.
- **Discord** : discord.com/developers/applications → New Application → OAuth2 → copier Client ID
  et Client Secret → Redirects.

## Option A — Cloudflare Tunnel depuis son PC (0 €)

    docker compose up -d db                       (PostgreSQL local, Docker Desktop lancé)
    mvn clean install -DskipTests
    cd web && npx ng build && cd ..
    set GOOGLE_CLIENT_ID=…  (idem secret, Discord, BLINDLE_ADMIN_EMAILS)
    java -jar app/target/app-0.1.0-SNAPSHOT.jar --blindle.content.dir=content ^
         --spring.web.resources.static-locations=file:web/dist/blindle-web/browser/ ^
         --blindle.allowed-origins=https://*.trycloudflare.com,http://localhost:*
    cloudflared tunnel --url http://localhost:8080      (winget install Cloudflare.cloudflared)

Donne une URL `https://xxx.trycloudflare.com` à partager. Le PC doit rester allumé.

## Option B — Render (gratuit, toujours en ligne)

1. Pousser le dépôt sur GitHub ou GitLab (privé accepté).
2. render.com → New → PostgreSQL (plan Free) ; noter l'« Internal Database URL »
   (`postgresql://user:pass@hôte/base`) et en tirer `SPRING_DATASOURCE_URL=jdbc:postgresql://hôte/base`,
   `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
3. New → Web Service → le dépôt → Runtime **Docker**, plan **Free**, variables ci-dessus.
4. Déployer. URL : `https://<nom>.onrender.com` (à déclarer chez Google / Discord).

Limites du plan gratuit : mise en veille après 15 min sans trafic (~1 min de réveil),
512 Mo de RAM ; la base gratuite de Render expire au bout de 30 jours. Les rooms et les sessions
étant en mémoire, une veille ou un redéploiement les efface (la curation, en base, reste).

Alternative équivalente : Koyeb (free, Docker, WebSocket, `*.koyeb.app`) avec une base Neon.

## Discord

- Simple : coller l'URL dans un salon Discord, chacun ouvre le lien.
- Discord Activity (jeu dans le salon vocal) : étape 11 (`docs/PROJECT.md` §11) — application
  Discord, URL mapping vers l'URL ci-dessus, SDK `@discord/embedded-app-sdk` dans le front, et
  mapping des images MyAnimeList (cdn.myanimelist.net) (la CSP de Discord bloque les domaines non déclarés).
