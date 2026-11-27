# Blindle (blindle.me)

Jeu d'enchères à l'aveugle multijoueur en ligne (3 à 8 joueurs, ou solo contre des bots).

**Le concept** : chaque joueur a un budget fixe. Des cartes (personnages de manga, footballeurs, pays, plats…) passent aux enchères cachées derrière des indices révélés du plus vague au plus précis. Plus on mise tôt, moins on a d'information, mais moins c'est cher : le prix plancher monte à chaque indice. Le gagnant d'un lot place la carte dans un slot de son équipe (Capitaine, Bras droit…). À la fin, un verdict (vote entre joueurs, stats…) désigne la meilleure équipe.

**Le moteur est générique** : un thème = un fichier JSON, une règle = une classe branchée sur un hook, une partie = un preset (thème + règles + mode d'enchère + mode de verdict).

📄 **Spécification complète, feuille de route et prompts par étape : `docs/PROJECT.md`.** La lire avant toute tâche structurante ; elle fait foi en cas de doute.

## Stack

- Back : Java 17, Spring Boot 3, Maven, WebSocket STOMP
- Front : Angular (standalone components), RxJS, client STOMP
- Contenu : thèmes et presets JSON dans `content/`, validés par JSON Schema au démarrage
- MVP : état des rooms en mémoire, pas de base de données
- Plus tard : PostgreSQL, Redis, Discord Activity

## Modules

- `engine-core` : `GameState`, `GameEvent`, `GameHook`, `RuleEngine`, machine à états des phases
- `engine-rules` : une classe `@Component` par règle, implémentant `Rule`
- `engine-modes` : `BidMode`, `VerdictMode`
- `content` : chargement et validation des thèmes / presets
- `realtime` : contrôleurs STOMP, rooms, timers
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
- Dépendances ajoutées (validées) : `com.networknt:json-schema-validator` (content) ; front : Angular 21 LTS, `@stomp/stompjs` 7.1.1 (version exacte)
- Si une étape exige de modifier `engine-*` pour un besoin propre à un thème : s'arrêter et le signaler

## Commandes

- Build + tests : `mvn verify`
- Tests d'un module : `mvn -pl engine-core test` (ajouter `-am` pour construire ses dépendances)
- Un test : `mvn -pl engine-rules -am test -Dtest=BudgetCapRuleTest -Dsurefire.failIfNoSpecifiedTests=false`
- Lancer le back : `mvn -pl app -am spring-boot:run` (port 8080, STOMP sur `/ws`)
- Jar exécutable : `mvn clean install -DskipTests` puis `java -jar app/target/app-0.1.0-SNAPSHOT.jar --blindle.content.dir=content` (le `clean` évite un jar embarquant d'anciens modules)
- Front : `cd web && npm install && npm start` → http://localhost:4200 (proxy `/api` et `/ws` vers 8080) ; build : `npx ng build`
- Minimum de joueurs par défaut : 2 ; solo : `--blindle.room.min-players=1`
- Thèmes anime illustrés (non commités) : `node tools/import-anime.mjs` (Tenrai/MyAnimeList + rangs de puissance `content/curation/`)

Java 17 retenu (décision du 2026-11-26) : pas de pattern matching dans `switch` ni de record patterns.

Architecture et protocole STOMP proposés à l'étape 0 : `docs/ARCHITECTURE.md`.

## Étape en cours

Étape 10 terminée. Hors feuille de route : mode principal « Anime Blind Bid » (mode `drawer`, thèmes MyAnimeList privés via Tenrai, rareté populaire-ou-puissant, verdict aux points, le piocheur vend, défausse payante — voir ARCHITECTURE §11). Prochaine : la demande 2 ci-dessous (la 1 est faite), puis étape 11 — Discord Activity (voir `docs/PROJECT.md` §11 et §12).
Mettre à jour cette ligne à chaque étape terminée.

### À faire à la prochaine session (demandes du 2026-11-26, dans l'ordre)

1. ~~**Or simplifié**~~ — fait le 2026-11-27 : l'or non dépensé ne compte plus dans `PointsVerdictMode`
   (reste le départage). Bots inchangés : ils misent toujours au plus la valeur estimée.
2. **Compte admin pour classer les personnages soi-même.** Aujourd'hui les rangs S–D sont dans
   `content/curation/anime-*.json` (fichiers commités, appliqués par `tools/import-anime.mjs`).
   Besoin : un accès admin (authentification à concevoir : le site n'a pas de comptes) et une page
   pour changer le rang de puissance d'un personnage, ajouter ou retirer des personnages et voir la
   rareté obtenue, sans relancer l'import à la main. Questions à trancher avec l'utilisateur avant de
   coder : stockage des modifications (fichier de curation ou base), recalcul de la rareté à chaud
   ou au redémarrage, forme de l'authentification (mot de passe unique en variable
   d'environnement vs vrais comptes). Signaler toute nouvelle dépendance.

## Notes de passation (2026-11-26)

- Pas encore vérifié visuellement dans un navigateur (extension Chrome non connectée) : le front compile sans avertissement et des parties scriptées passent par le proxy de dev.
- À relire avant diffusion publique : cartes pièges du thème manga et les 40 cartes de `pays-du-monde`.
- Reconnexion = nouveau joueur (pas de reprise de session) ; rooms en mémoire.
- Défi du jour rejouable à volonté (pas de compte) ; classement en mémoire. Équilibrage du prix du marché et des bots à ajuster en jouant.
- Dépôt : GitHub `iDerqaoui/blindle` (origin). Hébergement de test : Cloudflare Tunnel (voir `docs/DEPLOY.md`).
- Prochaines étapes convenues (2026-11-26), dans l'ordre :
  1. Mise en ligne sur un seul serveur : front construit embarqué dans le jar (ou Nginx devant), image Docker, HTTPS/WSS, `blindle.allowed-origins`, guide de déploiement ; lancer `node tools/import-anime.mjs` sur le serveur.
  2. Reconnexion (jeton qui rend sa place au joueur) et passation d'hôte quand l'hôte part.
  3. Snapshots des rooms dans Redis (survivre aux redémarrages).
  4. Classement du jour et retraits de thèmes dans un stockage partagé (Redis / PostgreSQL).
  5. Si la charge l'exige : une room = un serveur propriétaire, table room → serveur dans Redis.
- Avant toute ouverture publique : les univers anime (profil `private`, images MyAnimeList) sont réservés au jeu entre amis (§8) ; protéger l'accès ou changer d'images.
