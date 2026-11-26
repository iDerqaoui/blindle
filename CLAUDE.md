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

- Code et identifiants en anglais ; textes affichés et documentation en français
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
- Dev à moins de 3 joueurs : `--blindle.room.min-players=1`

Java 17 retenu (décision du 2026-11-26) : pas de pattern matching dans `switch` ni de record patterns.

Architecture et protocole STOMP proposés à l'étape 0 : `docs/ARCHITECTURE.md`.

## Étape en cours

Étape 8 terminée (thème pays-du-monde, 40 cartes, moteur inchangé). Prochaine : étape 9 — mode solo (`market`, défi du jour, puis bots) (voir `docs/PROJECT.md` §11 et §12).
Mettre à jour cette ligne à chaque étape terminée.

## Notes de passation (2026-11-26)

- Pas encore vérifié visuellement dans un navigateur (extension Chrome non connectée) : le front compile sans avertissement et des parties scriptées passent par le proxy de dev.
- À relire avant diffusion publique : cartes pièges du thème manga et les 40 cartes de `pays-du-monde`.
- Reconnexion = nouveau joueur (pas de reprise de session) ; rooms en mémoire.
- Dépôt local uniquement : ajouter le remote GitLab puis `git push -u origin main`.
