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

Étape 10 terminée. Hors feuille de route : mode principal « Anime Blind Bid » (mode `drawer`, thèmes MyAnimeList privés via Tenrai, rareté populaire-ou-puissant, verdict aux points, on ne gagne jamais d'or, laisser filer une carte coûte 150 au piocheur — voir ARCHITECTURE §11). Faits le 2026-11-27 : or non dépensé sans valeur (`PointsVerdictMode`) ; comptes Google / Discord et page admin des personnages (ARCHITECTURE §12) ; personnalisation de
room par l'hôte et passation d'hôte (ARCHITECTURE §13). Prochaine : les demandes ci-dessous, puis étape 11 — Discord Activity (voir `docs/PROJECT.md` §11 et §12). Admin sans Google/Discord : `BLINDLE_ADMIN_PASSWORD`.
Mettre à jour cette ligne à chaque étape terminée.

### À faire à la prochaine session (demandes du 2026-11-27)

Aujourd'hui la page admin (`#admin`) permet seulement de changer le rang S–D et le nom d'un
personnage anime, d'en ajouter un depuis MyAnimeList, d'en retirer un et de voir la rareté prévue.

1. **Modifier les valeurs des personnages depuis l'admin** (rareté imposée : faite le 2026-11-27, elle remplace le calcul, carte toujours dans le paquet, quotas sur le reste ; reste : popularité, image, indices, tags) : forcer une rareté, corriger la
   popularité, changer l'image, les indices, les tags. À trancher : une valeur forcée remplace-t-elle
   le calcul (rareté imposée hors quotas) ou l'ajuste-t-elle seulement ?
2. **Personnaliser les cartes** de tous les thèmes (manga, pays… pas seulement anime) depuis
   l'admin, en base plutôt que dans `content/`. Garder la validation JSON Schema et le champ `rights`.
3. **Personnaliser les modes de jeu (presets)** depuis l'admin : budget, manches, taille d'équipe,
   minuteurs, règles actives et paramètres (`discard-fee`, `anti-snipe`…), modes d'enchère et de
   verdict. À trancher : à chaud (nouvelles rooms) ou au redémarrage comme la curation.
4. **Statistiques des joueurs** : parties, victoires, scores, cartes préférées par compte ; vue admin
   (comptes, activité), peut-être un profil joueur. Prérequis : lier le compte connecté au joueur en
   partie (aujourd'hui il ne fait que pré-remplir le pseudo) et enregistrer les résultats en base.

Questions à poser avant de coder : ordre de priorité, qui voit les stats (admin seul ou chaque
joueur), stats des parties sans compte. Signaler toute nouvelle dépendance.

### Deux niveaux de contrôle : administrateur et hôte (demande du 2026-11-27)

- **Administrateur principal** (le propriétaire du site, via `BLINDLE_ADMIN_EMAILS` ou
  `BLINDLE_ADMIN_PASSWORD`) : définit la **base commune à toutes les rooms** : classement de
  puissance S–D et rareté, cartes des thèmes, presets (budget, règles, paramètres, modes). C'est
  le contenu de référence (base + `content/`) ; les points 1 à 3 ci-dessus en relèvent.
- **Hôte d'une room** : personnalise **sa room seulement**, en salle d'attente (LOBBY), sans
  jamais toucher à la base : retirer / remettre des cartes du paquet, changer la rareté d'une carte
  pour cette room, activer / désactiver des règles du catalogue et régler leurs paramètres, en plus
  du budget, des manches et du minuteur. Mêmes contrôles que pour un preset (`requires`,
  `conflictsWith`, paramètres, assez de cartes) ; personnalisation en mémoire, perdue avec la room.
  Ce que l'hôte peut changer reste borné par l'admin (les règles du catalogue, les cartes du thème).
- **Passation d'hôte** : si l'hôte se déconnecte, l'hôte passe au joueur humain connecté suivant
  dans l'ordre d'arrivée ; l'hôte peut aussi céder sa place. En salle d'attente, un joueur
  déconnecté est retiré ; en partie, il reste (affiché déconnecté) et les minuteries jouent pour lui.
- Fait le 2026-11-27 : passation, cession, retrait en LOBBY, personnalisation de room (cartes,
  rareté, règles) côté serveur et front. Reste : édition par l'admin de la base (points 1 à 3).

### Notes du 2026-11-27

- Or : les bots misent toujours au plus la valeur estimée, même quand l'or restant ne servira plus ; à revoir en jouant.
- Or (2026-11-27) : on ne gagne jamais d'or ; acheter coûte, passer est gratuit, le piocheur paie 150 pour laisser
  filer une carte invendue (ARCHITECTURE §11). Les bots piocheurs ne tiennent pas encore compte de cette pénalité.
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
