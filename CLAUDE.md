# Blindle (blindle.me)

Jeu d'enchères à l'aveugle multijoueur en ligne (3 à 8 joueurs, ou solo contre des bots).

**Le concept** : chaque joueur a un budget fixe. Des cartes (personnages de manga, footballeurs, pays, plats…) passent aux enchères cachées derrière des indices révélés du plus vague au plus précis. Plus on mise tôt, moins on a d'information, mais moins c'est cher : le prix plancher monte à chaque indice. Le gagnant d'un lot place la carte dans un slot de son équipe (Capitaine, Bras droit…). À la fin, un verdict (vote entre joueurs, stats…) désigne la meilleure équipe.

**Le moteur est générique** : un thème = un fichier JSON, une règle = une classe branchée sur un hook, une partie = un preset (thème + règles + mode d'enchère + mode de verdict).

📄 **Spécification complète, feuille de route et prompts par étape : `docs/PROJECT.md`.** La lire avant toute tâche structurante ; elle fait foi en cas de doute.

## Stack

- Back : Java 17, Spring Boot 3, Maven, WebSocket STOMP
- Front : Angular (standalone components), RxJS, client STOMP
- Contenu : thèmes et presets JSON dans `content/`, validés par JSON Schema au démarrage
- Rooms en mémoire ; PostgreSQL (Flyway) pour les comptes et la curation des personnages
- Plus tard : Redis, Discord Activity

## Modules

- `engine-core` : `GameState`, `GameEvent`, `GameHook`, `RuleEngine`, machine à états des phases
- `engine-rules` : une classe `@Component` par règle, implémentant `Rule`
- `engine-modes` : `BidMode`, `VerdictMode`
- `content` : chargement et validation des thèmes / presets
- `realtime` : contrôleurs STOMP, rooms, timers
- `accounts` : connexion Google / Discord, comptes, rôle admin, sécurité (ARCHITECTURE §12)
- `curation` : personnages anime en base, page admin, génération des thèmes anime au démarrage
- `web/` : application Angular

## Principes non négociables

1. **Le serveur est la seule source de vérité** : timers, offres, budgets, résolution. Le client ne fait qu'afficher.
2. **Vues filtrées** : chaque joueur ne reçoit que ce qu'il a le droit de voir (en enchère scellée, jamais les offres des autres avant résolution).
3. **Déterminisme** : tout aléa passe par la seed de la partie (`Random` injecté, jamais `Math.random()` ni `new Random()` sans seed).
4. **Aucun contenu en dur dans le moteur** : rien de spécifique à un thème dans `engine-*`.
5. **Une règle = une classe** : hooks, priorité (validation < 100, effets ≥ 100), paramètres typés, `requires` / `conflictsWith`.
6. **État immuable** : records Java, les règles renvoient un nouvel `GameState`.
7. **Timers côté serveur** : le client reçoit l'heure de fin, pas un compte à rebours à lui.
8. **Droits** : chaque thème a un champ `rights` ; pas d'images officielles ni de photos de personnes réelles dans le contenu commité.

## Conventions

- Code et identifiants en anglais ; documentation en français
- Textes du site : jamais en dur, toujours via `I18nService` / pipe `t` (`web/src/app/i18n/en.ts` et `fr.ts`, mêmes clés) ; anglais par défaut, choix mémorisé
- Tests JUnit 5 obligatoires pour chaque règle et chaque mode : état initial + événement → état attendu
- Une étape de la feuille de route par session ; petits commits clairs (GitLab)
- Signaler toute nouvelle dépendance avant de l'ajouter
- Dépendances ajoutées (validées) : `com.networknt:json-schema-validator` (content) ; `spring-boot-starter-security`, `spring-boot-starter-oauth2-client`, `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `flyway-core` + `flyway-database-postgresql`, `com.h2database:h2` (tests) — validées le 2026-11-27 ; front : Angular 21 LTS, `@stomp/stompjs` 7.1.1 (version exacte)
- Appels d'API qui modifient quelque chose côté front : `apiFetch` (`web/src/app/auth/auth.service.ts`), qui envoie le jeton CSRF
- Si une étape exige de modifier `engine-*` pour un besoin propre à un thème : s'arrêter et le signaler

## Commandes

- Base locale (Docker Desktop lancé) : `docker compose up -d db` ; les tests utilisent H2 en mémoire, sans réseau
- Build + tests : `mvn verify`
- Tests d'un module : `mvn -pl engine-core test` (ajouter `-am` pour construire ses dépendances)
- Un test : `mvn -pl engine-rules -am test -Dtest=BudgetCapRuleTest -Dsurefire.failIfNoSpecifiedTests=false`
- Lancer le back : `mvn -pl app -am spring-boot:run` (port 8080, STOMP sur `/ws`)
- Jar exécutable : `mvn clean install -DskipTests` puis `java -jar app/target/app-0.1.0-SNAPSHOT.jar --blindle.content.dir=content` (le `clean` évite un jar embarquant d'anciens modules)
- Front : `cd web && npm install && npm start` → http://localhost:4200 (proxy `/api`, `/ws`, `/oauth2`, `/login/oauth2` vers 8080) ; build : `npx ng build`
- Minimum de joueurs par défaut : 2 ; solo : `--blindle.room.min-players=1`
- Connexion : `GOOGLE_CLIENT_ID`/`_SECRET`, `DISCORD_CLIENT_ID`/`_SECRET`, admins `BLINDLE_ADMIN_EMAILS` (voir `docs/DEPLOY.md`) ; page admin : `#admin`
- Thèmes anime illustrés (non commités) : générés par le serveur au démarrage depuis la base (1er démarrage : reprise de `content/curation/`, ~30 s via Tenrai) ; secours hors base : `node tools/import-anime.mjs`

Java 17 retenu (décision du 2026-11-26) : pas de pattern matching dans `switch` ni de record patterns.

Architecture et protocole STOMP proposés à l'étape 0 : `docs/ARCHITECTURE.md`.

## Étape en cours

Étape 10 terminée. Hors feuille de route : mode principal « Anime Blind Bid » (mode `drawer`, thèmes MyAnimeList privés via Tenrai, rareté populaire-ou-puissant, verdict aux points, le piocheur vend, défausse payante — voir ARCHITECTURE §11). Faits le 2026-11-27 : or non dépensé sans valeur (`PointsVerdictMode`) ; comptes Google / Discord et page admin des personnages (ARCHITECTURE §12). Prochaine : les demandes du 2026-11-27 listées dans `README.md` (« À faire » : valeurs des personnages, cartes, modes de jeu, stats joueurs), puis étape 11 — Discord Activity (voir `docs/PROJECT.md` §11 et §12). Admin sans Google/Discord : `BLINDLE_ADMIN_PASSWORD`.
Mettre à jour cette ligne à chaque étape terminée.

### Notes du 2026-11-27

- Or : les bots misent toujours au plus la valeur estimée, même quand l'or restant ne servira plus ; à revoir en jouant.
- Connexion pas encore testée avec de vrais identifiants Google / Discord (à créer par l'utilisateur, voir `docs/DEPLOY.md`) ; page admin compilée mais pas vue dans un navigateur.
- Le compte n'est pas encore lié au joueur en partie : il pré-remplit seulement le pseudo (base pour la reconnexion).

## Notes de passation (2026-11-26)

- Pas encore vérifié visuellement dans un navigateur (extension Chrome non connectée) : le front compile sans avertissement et des parties scriptées passent par le proxy de dev.
- À relire avant diffusion publique : cartes pièges du thème manga et les 40 cartes de `pays-du-monde`.
- Reconnexion = nouveau joueur (pas de reprise de session) ; rooms en mémoire.
- Défi du jour rejouable à volonté (pas de compte) ; classement en mémoire. Équilibrage du prix du marché et des bots à ajuster en jouant.
- Dépôt : GitHub `iDerqaoui/blindle` (origin). Hébergement de test : Cloudflare Tunnel (voir `docs/DEPLOY.md`).
- Prochaines étapes convenues (2026-11-26), dans l'ordre :
  1. Mise en ligne sur un seul serveur : front construit embarqué dans le jar (ou Nginx devant), image Docker, HTTPS/WSS, `blindle.allowed-origins`, guide de déploiement, base PostgreSQL (le serveur génère les thèmes anime au démarrage).
  2. Reconnexion (jeton qui rend sa place au joueur) et passation d'hôte quand l'hôte part.
  3. Snapshots des rooms dans Redis (survivre aux redémarrages).
  4. Classement du jour et retraits de thèmes dans un stockage partagé (Redis / PostgreSQL).
  5. Si la charge l'exige : une room = un serveur propriétaire, table room → serveur dans Redis.
- Avant toute ouverture publique : les univers anime (profil `private`, images MyAnimeList) sont réservés au jeu entre amis (§8) ; protéger l'accès ou changer d'images.
