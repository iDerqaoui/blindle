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

### Serveur → client

| Destination | Contenu |
|---|---|
| `/topic/room/{code}` | `RoomView` public (`type: "room"`) : phase, joueurs (pseudo, budget restant, équipe, a agi ou non), indices révélés, prix plancher, `deadline` et `serverTime` (epoch ms), nombre d'offres reçues (pas leur montant en `sealed`), journal, résultat |
| `/user/queue/room` | `PlayerView` privé (`type: "player"`) : mon id, mon offre, passe, `canBid`, `mustPlace`, slots libres, mon vote. Reçoit aussi la `RoomView` lors d'un `sync` |
| `/user/queue/errors` | `{ code, message }` : offre rejetée, action interdite |
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
