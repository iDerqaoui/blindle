# Blindle — Architecture (étape 0)

> Proposition de cadrage issue de l'étape 0 (`docs/PROJECT.md` §11–12).
> À valider ; elle sera ajustée au fil des étapes, `PROJECT.md` reste la source de vérité.

## 1. Arborescence

```
blindle/
├── pom.xml                  parent Maven (Spring Boot 3, Java 17)
├── CLAUDE.md
├── docs/                    PROJECT.md, ARCHITECTURE.md
├── content/                 données, aucun code
│   ├── themes/              un fichier JSON par thème
│   ├── presets/             un fichier JSON par preset
(schémas JSON : `content/src/main/resources/schema/`, sur le classpath)
├── engine-core/             Java pur : état, événements, hooks, phases
├── engine-rules/            une classe @Component par règle   → engine-core
├── engine-modes/            BidMode, VerdictMode              → engine-core
├── content/ (module Maven)  chargement + validation           → engine-core
├── realtime/                rooms, STOMP, timers, vues        → core, rules, modes, content
├── app/                     main Spring Boot, config          → realtime
└── web/                     Angular standalone (étape 6)
```

Note : le module Maven `content` et le dossier de données `content/` partagent le même
répertoire. Les JSON sont dans `content/themes` et `content/presets`, le code dans
`content/src`. Le module lit les données depuis le système de fichiers (chemin configurable,
`blindle.content.dir`, défaut `../content` ou `content`).

Règles de dépendances : `engine-core` ne dépend de rien (pas même de Spring) ;
aucun module `engine-*` ne dépend de `content` ni de `realtime`.

## 2. Classes clés de `engine-core`

| Type | Nature | Rôle |
|---|---|---|
| `GameState` | record | Snapshot immuable : `gameId`, `seed`, `phase`, `settings`, `players`, `roundIndex`, `currentLot`, `deck` (ordre tiré), `log`. Méthodes `with…()` qui renvoient une copie. |
| `Phase` | enum | `LOBBY, STARTED, DRAW, HINT, BIDDING, AWARDED, REVEALED, SLOT, VERDICT, FINISHED` |
| `PhaseMachine` | classe | Table des transitions autorisées ; `transition(state, target)` lève `IllegalPhaseTransition` sinon. Après `SLOT` : `DRAW` si rounds restants, sinon `VERDICT`. Après `BIDDING` sans gagnant : `HINT` (palier suivant) ou lot invendu. |
| `GameSettings` | record | `budget, rounds, teamSize, hintLevels, bidTimerSec` |
| `PlayerState` | record | `id, name, budget, team` (Map slotId → cardId), `jokers`, `isBot` |
| `CardRef` / `Card` | record | Carte vue par le moteur : `id, name, tags, stats, rarity, isTrap, hints` — fournie par `content`, opaque pour le moteur |
| `Hint` | record | `level, type, payload` |
| `LotState` | record | `cards`, `hintLevel`, `floorPrice`, `bids`, `deadline` (Instant) |
| `Bid` | record | `playerId, amount, at` |
| `GameEvent` | sealed interface | `GameStarted, RoundStarted, HintRevealed, BidPlaced, LotWon, CardRevealed, SlotAssigned, JokerUsed, RoundEnded, VerdictRequested` |
| `GameHook` | enum | `ON_GAME_START … ON_VERDICT` (1 hook ↔ 1 type d'événement) |
| `GameRandom` | classe | Enveloppe `java.util.Random` construit depuis la seed ; seule source d'aléa, injectée. |
| `LogEntry` | record | Message lisible du journal (« X a volé Ace pour 180 pièces ») |
| `GameEngine` | classe | `apply(state, command) → Result(state, events, errors)` ; orchestre phases, `RuleEngine` (étape 2), `BidMode` (étape 3). |

## 3. Protocole STOMP

Endpoint WebSocket : `/ws`. Préfixe des commandes : `/app`. Le serveur identifie le
joueur par la session (`Principal` attribué au `join`), jamais par un champ du message.

### Client → serveur (`/app/...`)

| Destination | Payload | Effet |
|---|---|---|
| `/app/room.create` | `{ name, presetId }` | Crée une room, renvoie le code |
| `/app/room.join` | `{ code, name }` | Rejoint une room |
| `/app/daily.start` | `{ name, presetId }` | Défi du jour : room solo créée avec la seed du jour, rejointe et démarrée (`room.create` refuse un preset `daily`) |
| `/app/room/{code}/sync` | `{}` | Renvoie au joueur ses deux vues (après abonnement au topic) |
| `/app/room/{code}/settings` | `{ budget?, rounds?, bidTimerSec? }` | Hôte seulement, en LOBBY |
| `/app/room/{code}/bot` | `{ profile }` | Hôte seulement, en LOBBY : ajoute un bot (`aggressive`, `stingy`, `fan`, `bluffer`, `strategist`) ; refusé en défi du jour |
| `/app/room/{code}/start` | `{}` | Hôte seulement |
| `/app/room/{code}/bid` | `{ amount }` | Offre sur le lot courant |
| `/app/room/{code}/pass` | `{}` | Passe sur ce palier |
| `/app/room/{code}/slot` | `{ slotId }` | Place la carte gagnée |
| `/app/room/{code}/joker` | `{ jokerId, targetId? }` | Active un joker pendant les enchères : `spy` (sans cible), `steal` (cible `"<joueur>/<slot>"`) |
| `/app/room/{code}/forfeit` | `{}` | Le gagnant défausse la carte, seulement si aucun slot libre ne l'accepte |
| `/app/room/{code}/vote` | `{ teamOwnerId }` | Vote (verdict `vote`) |
| `/app/room/{code}/host` | `{ playerId }` | Hôte seulement : cède le rôle à un joueur humain connecté |
| `/app/room/{code}/catalog` | `{}` | Hôte seulement : renvoie le catalogue de personnalisation sur `/user/queue/catalog` |
| `/app/room/{code}/customize` | `{ excludedCards, rarities, rules }` | Hôte seulement, en LOBBY (§13) ; `rules: null` = règles du preset ; renvoie le catalogue à jour |

### Serveur → client

| Destination | Contenu |
|---|---|
| `/topic/room/{code}` | `RoomView` public (`type: "room"`) : phase, joueurs (pseudo, budget restant, équipe, a agi ou non), indices révélés, prix plancher, `deadline` et `serverTime` (epoch ms), nombre d'offres reçues (pas leur montant en `sealed`), journal, résultat |
| `/user/queue/room` | `PlayerView` privé (`type: "player"`) : mon id, mon offre, passe, `canBid`, `mustPlace`, slots libres, mon vote. Reçoit aussi la `RoomView` lors d'un `sync` |
| `/user/queue/errors` | `{ code, message }` : offre rejetée, action interdite |
| `/user/queue/catalog` | `RoomCatalogView` (`type: "catalog"`), hôte seul : cartes du thème (rareté de base et de la room, incluse ou non), raretés du thème, règles du catalogue (active, paramètres, défauts, modes requis) |
| `GET /api/daily` (REST) | Défis du jour : preset, thème, date, nombre de parties, top 10 du classement |
| `GET /api/bots` (REST) | Profils de bots : `{ profile, label }` |
| `/api/admin/themes[/{theme}/channels/{canal}/block]` (REST) | Admin, en-tête `X-Admin-Token` = `blindle.admin.token` : état (GET), retrait (POST), rétablissement (DELETE). 404 si aucun jeton n'est configuré |

Chaque message porte `type` et `version` (numéro monotone de l'état) pour que le client
ignore les messages obsolètes. Les vues sont calculées par `ViewProjector` côté serveur
à partir de `GameState` et du joueur destinataire : c'est le seul endroit qui décide
de ce qui est visible.

## 4. Ordre des tâches (étapes 1 à 6)

Chaque tâche est testable seule.

1. **engine-core**
   1. `Phase` + `PhaseMachine` — test : transitions valides/invalides.
   2. Records `GameState`, `PlayerState`, `LotState`, `Bid`, `GameSettings`.
   3. `GameRandom` seedé + tirage du deck — test : même seed ⇒ même ordre.
   4. `GameEvent` / `GameHook` + `GameEngine` avec stubs — test : partie simulée LOBBY → FINISHED.
2. **Règles**
   1. `Rule`, `RuleParams`, `RuleResult` (nouvel état | rejet).
   2. `RuleEngine` : dispatch par hook, tri par priorité, arrêt au premier rejet.
   3. `budget-cap` puis `rising-floor` — un test chacun.
3. **Modes**
   1. `BidMode` `sealed` : collecte, résolution complète ou au timeout, égalité via seed.
   2. `VerdictMode` `vote` : interdiction de voter pour soi.
   3. Test bout en bout : 3 joueurs, 3 rounds.
4. **Contenu**
   1. JSON Schema thème + preset (avec `rights`).
   2. Loader + erreurs explicites ; échec au démarrage si invalide.
   3. Thème `manga-shonen` (30 cartes, relues avant commit) + preset `manga-classique`.
5. **Temps réel**
   1. `RoomRegistry` en mémoire + codes de room.
   2. Contrôleurs STOMP + `ViewProjector`.
   3. `TimerService` (`ScheduledExecutorService`), deadline envoyée au client.
   4. Test d'intégration : 3 clients STOMP jouent une partie.
6. **Front Angular** : lobby → enchère → équipe → vote → résultats, rendu d'indice par `hint.type`.

## 5. Points à trancher

- **Dépôt GitLab** : le dépôt git est local ; ajouter le remote GitLab.

## 6. Implémentation temps réel (étape 5)

- `Room` (realtime) tient l'état courant et enchaîne les phases toute seule : tirage, indice 1,
  enchères ; palier clos dès que tous les joueurs éligibles ont misé ou passé, sinon à l'heure
  de fin ; carte révélée puis placement par le gagnant (automatique après
  `blindle.room.slot-timeout-sec`, 20 s) ; verdict par vote (clos quand tous ont voté ou après
  `blindle.room.vote-timeout-sec`, 60 s).
- Dates envoyées en epoch ms (`deadline`, `serverTime`) : le client calcule le temps restant
  en corrigeant l'écart entre son horloge et `serverTime`.
- Identité : `PlayerHandshakeHandler` attribue un id serveur à chaque connexion WebSocket.
  Une reconnexion crée un nouveau joueur (reprise de session : plus tard).
- Rooms en mémoire, code de 4 lettres, purge après 2 h d'inactivité.
- `blindle.room.min-players` (défaut 3) peut être abaissé en dev.

## 7. Règles avancées et mode open (étape 7)

- Le moteur gère des notions génériques : jokers (`PlayerState.jokers`, action `useJoker`),
  indices privés, prix payé par carte, bonus de règles, défausse (`forfeit`) et essai à blanc
  (`canAssign`). Les effets sont portés par les règles, jamais par le moteur.
- Règles : `joker-spy`, `joker-steal`, `trap-lots`, `tag-synergy`, `slot-constraint`,
  `anti-snipe`. Chaque règle déclare `requiredThemeFields`, `requiredBidModes`,
  `conflictsWith` et valide ses paramètres ; `GameFactory` vérifie tout au démarrage.
- Les cartes `isTrap` ne sont dans le deck que si `trap-lots` est active.
- Mode `open` : offres visibles, chaque offre dépasse la meilleure, le meneur ne peut pas
  passer ; clos quand tous les autres ont passé ou à l'heure de fin (repoussée par anti-snipe,
  et le timer serveur est reprogrammé).
- Verdict `vote` : égalité départagée par le bonus des règles, puis le budget, puis la seed.
- Le thème manga-shonen compte 33 cartes, dont 3 pièges (Yamcha, Hercule, Baggy).

## 8. Deuxième thème (étape 8)

- Thème `pays-du-monde` (profil `free`, web + discord, public, sans image) : 40 pays,
  4 indices texte (climat → plat → langues → monument décrit), slots propres dont
  « Île paradisiaque » réservé aux cartes taguées `ile`. Preset `pays-classique`.
- Vérification : `git diff` sur `engine-core`, `engine-rules`, `engine-modes` entre l'étape 7
  et l'étape 8 est vide. Aucune dette de conception relevée.

## 9. Mode solo (étape 9)

### Défi du jour

- Mode d'enchère `market` : chaque offre secrète affronte un prix du marché caché ; une offre
  au moins égale gagne et paie son montant, sinon indice suivant. Prix = valeur du lot ×
  ratio (0,5 au 1er palier → 0,9 au dernier) × bruit seedé ±15 %, jamais sous le plancher.
- `CardValuation` (engine-modes) : valeur = 2 × moyenne des `stats` + bonus de `rarity`
  (25 / 50 / 100 / 150), valeurs neutres si un champ manque. Ne lit que le format générique
  de thème : aucun contenu de thème dans le moteur.
- Verdict `value` : valeur de l'équipe − pièces dépensées (défausses comprises). Exige
  `stats` **ou** `rarity` : `VerdictMode.supportsTheme` remplace le simple « tous les champs
  requis » (extension générique de l'interface, pas de besoin propre à un thème).
- Presets `daily: true` (`defi-manga`, `defi-pays`) : verdict `value` imposé au démarrage.
  Seed = FNV-1a de « date|preset » → même deck et mêmes prix du marché pour tout le monde.
  Jour en `Europe/Paris` (`blindle.daily.zone`).
- Room solo (`RoomSettings.solo()` : 1 humain, démarrage seul). `Room` relève l'issue de
  chaque round (`RoundOutcome`) depuis les événements du moteur ; en fin de partie, la
  `DailyContext` enregistre le score au `DailyLeaderboard` (mémoire, jour courant et veille)
  et le rang est renvoyé dans `RoomView.daily`.
- Partage sans spoiler (`DailyShare`) : une ligne par round, une case par palier —
  ⬜ palier joué, 🟩 achat rentable, 🟥 achat à perte, ⬛ palier non atteint.
- Limite connue : pas de compte, donc rien n'empêche de rejouer le défi (nouvelle connexion).

### Bots

- `engine-modes/bot` : `BotStrategy.decide(BotView)` → offre ou passe. `BotViews` construit la
  vue avec les filtres d'un humain : indices révélés, jamais l'identité du lot, offres adverses
  seulement en mode visible ; plus les cartes encore en jeu (connaissance de l'univers, sans
  l'ordre du deck). Aléa : `botSeed` dérivé de la seed de la partie.
- `BotEstimator` : cartes compatibles avec les indices, moyenne mélangée à celle des cartes en
  jeu avec une confiance = palier / nombre de paliers, bruit personnel ±10 %.
- Profils : Agressif (dès l'indice 1, ≤ 120 %), Radin (dernier indice, ≤ 80 %), Fan (tag favori
  seedé : ≤ 150 % dès l'indice 1, sinon indice 2 et ≤ 90 %), Bluffeur (surenchérit en mode
  visible puis se retire au dernier indice ; en scellé un coup à 70 % au 1er indice), Stratège
  (réserve le plancher pour ses autres slots libres, mise à mi-parcours ≤ 100 %, ≤ 110 % s'il
  manque de rounds).
- `Room` fait jouer les bots à l'ouverture de chaque palier et, en mode visible, après chaque
  offre humaine (le meneur et les bots qui ont passé ne rejouent pas). Une offre refusée par
  une règle devient une passe. Un bot gagnant place sa carte seul après 4 s de révélation.
- Les bots ne votent pas : les presets « Solo · … contre bots » (`solo-manga-bots`,
  `solo-pays-bots`) utilisent le verdict `value`.
- `GameRandom.nextLong()` ajouté (générique) pour dériver la seed des bots.

## 10. Droits et canaux (étape 10)

- `rights` est obligatoire (schéma). `RightsPolicy` (content) refuse les combinaisons
  incohérentes : `imagePolicy: any` réservé au profil `private` ; `real-people` sans photos
  (`generic-only` ou `none`) ; `fictional-ip` et `real-people` sur le web seulement, avec
  `disclaimer` et `takedownContact` ; tout thème public a un `takedownContact` ; `private`
  jamais public ni sur Discord. Un preset sans `channels` hérite de ceux de son thème, sinon il
  ne peut que les restreindre.
- **Canal déduit de l'authentification** : `ChannelAuthenticator` est appelé à la poignée de main
  WebSocket (le canal est porté par le `PlayerPrincipal`) et sur les appels REST. Aujourd'hui
  `WebChannelAuthenticator` répond toujours `web` ; l'étape 11 le remplacera par un
  authentificateur Discord. Aucun champ client n'entre en jeu.
- `ChannelAccess` : canaux effectifs = canaux du preset (ou du thème) − retraits à chaud.
  Vérifié à la création de room, au défi du jour et à **chaque `join`** (un joueur Discord ne
  rejoint pas une room web par son code). `/api/presets` et `/api/daily` sont filtrés.
- Retrait à chaud : API admin ci-dessus, ou `blindle.rights.blocked=theme:canal,…` au démarrage.
  Retraits en mémoire ; les parties en cours vont à leur terme, plus personne n'y entre.
- `visibility` est validée mais pas encore exploitée (toutes les rooms sont sur code).

## 11. Mode principal « Anime Blind Bid »

- Demande produit (2026-11-26) : mode principal en tour par tour, visuel, un univers par partie.
- Mode d'enchère `drawer` : le piocheur tourne à chaque round (en sautant les équipes pleines),
  il est **seul à voir la carte** (`PlayerView.drawnCard`), ouvre l'enchère ; les autres
  surenchérissent (offres visibles) ou laissent. Personne ne mise : poubelle (défausse publique,
  `RoomView.trash`). Gagnée : révélation 4 s puis pose automatique dans le tableau.
- `BidMode.drawerId(state)` et `BidMode.autoPlace()` : extensions génériques ; les vues masquent
  les indices quand un piocheur existe. La partie se termine dès que toutes les équipes sont pleines.
- Contenu : `tools/import-anime.mjs` génère un thème + un preset par univers depuis Tenrai
  (données MyAnimeList, successeur de Jikan ; remplace AniList le 2026-11-26). Images en URL uniquement, fichiers
  `content/{themes,presets}/anime-*.json` ignorés par git, profil `private`, web seulement.
  Champ de thème optionnel `art` (affiche, bannière, couleur) pour le dos de carte.
- Front refait : barre du haut (code, univers, Quitter avec confirmation), accueil par affiches
  d'univers, salon avec bannière, table (pioche animée, retournement, enchère, tableaux de 5,
  poubelle en modale), vote sur les tableaux illustrés. Minimum de joueurs par défaut : 2.
- Rareté « populaire OU puissant » (2026-11-26) : `content/curation/anime-<univers>.json` (commité)
  liste les personnages retenus, leur nom d'affichage et un rang de puissance S/A/B/C/D (S=100,
  A=85, B=70, C=50, D=25). Popularité = favoris MAL en échelle log 0–100 dans l'univers. Score =
  max(popularité, puissance) + 0,25 × min. Top 40 par score, rareté par quotas fixes : 10 %
  légendaires, 20 % épiques, 30 % rares, 40 % communes (4/8/12/16), identiques pour tous les univers.
- Verdict `points` (`PointsVerdictMode`, générique) : points de rareté de l'équipe
  (`RarityPoints` : 10 / 20 / 35 / 60) − 25 par slot vide ; l'or non dépensé ne vaut rien
  (simplification du 2026-11-27 : l'or sert uniquement à acheter) ;
  égalité : bonus de règles, puis or restant, puis seed. `CardView.points` exposé.
- Refonte de l'économie (2026-11-26), contre l'attentisme « tout laisser filer, n'acheter que les
  légendaires » : le piocheur **vend** la carte (inspiré de *Modern Art*).
  - `BidResolver.sellerId(state)` (par défaut null, `BidMode` → `drawerId`) ; `LotWon` porte le
    vendeur ; nouvel événement `LotDiscarded` / hook `ON_LOT_DISCARDED` pour un lot invendu.
  - Règle `seller-collects` : le gagnant paie le piocheur ; si le piocheur garde sa carte (personne
    n'a dépassé son ouverture), il paie la banque.
  - Règle `discard-fee` (`amount`, défaut 150) : le piocheur paie pour jeter une carte invendue.
- Économie revue (2026-11-27, demande produit) : **on ne gagne jamais d'or**. Acheter coûte le prix
  (payé à la banque, y compris par le piocheur qui garde sa carte), passer ne coûte rien, laisser filer
  une carte invendue coûte cher au piocheur (`discard-fee` à 150). Les presets anime n'activent plus
  `seller-collects` (la règle reste au catalogue). `RoomView.ruleParams` donne les montants réels au
  client ; `GameSetup.activeRules` porte les paramètres complets (défauts de la règle compris).
  Exception connue : le joker vol (`joker-steal`, preset manga-chaos) rembourse encore la victime.
  - Bots : en verdict `points`, une carte vaut 10 pièces par point de rareté (`BotEstimator`).
  - Les montants affichés dans les règles du salon (50, 25) sont écrits dans les textes i18n :
    à aligner si on change les paramètres.

## 12. Comptes et page admin des personnages (2026-11-27)

Décisions prises avec l'utilisateur : base PostgreSQL dès maintenant, vrais comptes par connexion
Google et Discord, admins désignés par liste blanche, comptes ouverts aussi aux joueurs, rareté
recalculée au redémarrage, ajout de personnages par recherche MyAnimeList depuis la page.

- Module `accounts` : Spring Security + OAuth2 client (Google sans `openid`, Discord en fournisseur
  déclaré à la main), JPA, Flyway (`V1__accounts.sql`). Tables `user_account` et `user_identity`
  (fournisseur + identifiant) ; deux identités partagent un compte seulement par un e-mail **vérifié**.
  Un fournisseur sans identifiant client est masqué ; sans aucun, le site marche sans comptes.
- Rôle admin : `blindle.admin.emails` (`BLINDLE_ADMIN_EMAILS`), e-mails vérifiés ou
  `fournisseur:identifiant`, évalué à la connexion.
- Sécurité : tout reste ouvert sans compte (jeu, rooms, STOMP). `/api/admin/curation/**` exige le
  rôle admin, `PATCH /api/me` une connexion. CSRF par cookie `XSRF-TOKEN` / en-tête `X-XSRF-TOKEN`
  (`apiFetch` côté front) ; `/api/admin/themes/**` garde son jeton `X-Admin-Token`. Une API non
  autorisée renvoie 401, jamais de redirection. Sessions HTTP en mémoire (7 jours) : un redémarrage
  déconnecte.
- Endpoints : `GET /api/auth/providers`, `GET /api/me` (204 sans connexion), `PATCH /api/me`
  (pseudo, 20 caractères), `POST /api/auth/logout` ; connexion : `/oauth2/authorization/{google|discord}`,
  retour `/login/oauth2/code/{google|discord}`.
- Module `curation` : tables `anime_universe` (les 8 univers, insérés par `V2__curation.sql`) et
  `anime_character` (nom, rang S–D, ordre, favoris / image / rôle MyAnimeList en cache, auteur).
  - Hook générique `ContentGenerator` dans `content` : `ContentRegistry` l'appelle avant de charger.
    `AnimeThemeGenerator` y écrit `themes/` et `presets/anime-*.json` depuis la base ; le contenu
    généré passe par la même validation. Désactivable : `blindle.curation.enabled=false` (tests).
  - Premier démarrage : chaque univers vide est repris de `content/curation/anime-*.json`, puis
    complété par Tenrai (`TenraiClient`, ~30 s). Ensuite la base fait foi et le démarrage ne
    contacte Tenrai que pour des données manquantes. Tenrai injoignable : thème construit avec
    l'existant ; moins de 20 cartes : fichiers existants conservés.
  - Rareté imposée (`forced_rarity`, `V3__forced_rarity.sql`, 2026-11-27) : `PUT …/characters/{malId}` avec
    `{ rarity }` (`auto` = calcul). Elle remplace le calcul, la carte est toujours dans le paquet et
    consomme d'abord le quota de sa rareté ; les quotas remplissent les places restantes.
  - `RarityCalculator` : portage exact du calcul de `tools/import-anime.mjs` (vérifié : mêmes raretés
    sur les 8 univers).
  - API admin `/api/admin/curation/universes[/{slug}[/characters[/{malId}]|/search?q=]]` : chaque
    modification renvoie le classement recalculé, avec `rarity` (au prochain démarrage) et
    `liveRarity` (parties actuelles).
- Front : bouton « Connexion » (fournisseurs configurés seulement), puce du compte (pseudo modifiable,
  déconnexion, lien admin), pseudo pré-rempli à l'accueil s'il est vide, page `#admin`.
- Pas encore : lier le compte au joueur en partie (reconnexion), classement du jour par compte.

## 13. Base de l'administrateur, personnalisation de l'hôte, passation (2026-11-27)

- **Deux niveaux** : l'administrateur fixe la base commune (contenu, classement S–D, presets) ;
  l'hôte d'une room la personnalise **pour sa room seulement**, en LOBBY. Rien n'est écrit : la
  personnalisation (`RoomOverrides`) vit dans le `GameSetup` de la room et disparaît avec elle.
- `RoomOverrides(excludedCards, rarities, rules)` : cartes retirées, rareté imposée (parmi celles
  déjà présentes dans le thème), règles actives à la place de celles du preset (`null` = preset).
  `GameFactory.customized` applique les mêmes contrôles qu'à un preset (règle connue, `requires`,
  `conflictsWith`, modes d'enchère, paramètres) et lève `BAD_ROOM_SETUP` sinon.
- `Room.customize` remplace slots, cartes et moteur via `GameEngine.replaceContent` (LOBBY seulement,
  joueurs et version conservés) ; les manches sont ramenées à la taille du paquet. Refusé en défi du jour.
- `Rule.defaultParams()` : paramètres par défaut affichés à l'hôte quand il active une règle.
- `RoomView` : `customized`, `deckSize`, `rules` = règles réellement actives ; `PlayerSummary.connected`.
- **Passation** : `DisconnectListener` (`SessionDisconnectEvent`) → `RoomRegistry.disconnect` →
  `Room.disconnect`. En LOBBY le joueur est retiré (`GameEngine.removePlayer`) ; en partie il reste,
  marqué déconnecté, et les minuteries jouent pour lui. Si c'était l'hôte, le rôle passe au joueur
  humain connecté suivant dans l'ordre d'arrivée (en boucle, jamais un bot) ; personne : `hostId` null,
  repris par le prochain arrivant en LOBBY.
- Pas encore : reconnexion (une nouvelle connexion reste un nouveau joueur), édition de la base par
  l'admin pour les thèmes non anime et les presets.

## 14. Défi du jour « Who's That? Auction » (2026-11-27)

- Package `realtime/solo/who`, en REST (`/api/daily/who`) : `GET` (accueil), `POST /start {name}`,
  `GET /run`, `POST /buy {guess}`, `POST /pass`, `POST /next`, `GET /image/{index}/{level}` (PNG).
- `WhoDaily` : univers éligibles = thèmes web dont toutes les cartes ont image et rareté connue
  (critère générique), rotation par jour + un jour « Crossover » ; 10 cartes seedées
  (`DailyChallenge.seed(date, "who")`) : 1 légendaire (le jackpot), 2 épiques, 3 rares, 4 communes.
- `WhoRun` (logique pure, heure passée en argument) : palier = temps écoulé / 1,5 s (0 à 6), prix
  40 / 70 / 110 / 160 / 230 / 310 / 400, départ gratuit à 12 s ; jackpot flou 2 paliers de plus (+30) ;
  « Call it! » = nom complet (casse, accents, ponctuation ignorés) → points ×2 ; équipe pleine ou or
  < 40 : cartes restantes sautées ; score = points − 25 par slot vide.
- `Pixelator` : images téléchargées depuis l'URL des cartes (jamais une URL du client), recadrées
  240×340, moyennées par blocs (40 → 1 px), PNG en cache. Le serveur sert au plus le palier atteint ;
  l'URL d'origine n'est envoyée qu'à la révélation.
- Joueur : compte connecté (`Principal`) sinon cookie `blindle_who` (HttpOnly, 3 jours) ; un essai par
  jour, parties et classement (`DailyLeaderboard`, id `who`) en mémoire.
- Front : écran `#who` (`who.component.ts`), bandeau à l'accueil et tuile « défi du jour » des univers.
