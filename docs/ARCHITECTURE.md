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
| `/app/room/{code}/sync` | `{}` | Renvoie au joueur ses deux vues (après abonnement au topic) |
| `/app/room/{code}/settings` | `{ budget?, rounds?, bidTimerSec? }` | Hôte seulement, en LOBBY |
| `/app/room/{code}/start` | `{}` | Hôte seulement |
| `/app/room/{code}/bid` | `{ amount }` | Offre sur le lot courant |
| `/app/room/{code}/pass` | `{}` | Passe sur ce palier |
| `/app/room/{code}/slot` | `{ slotId }` | Place la carte gagnée |
| `/app/room/{code}/joker` | `{ jokerId, target? }` | Active un joker (étape 7) |
| `/app/room/{code}/vote` | `{ teamOwnerId }` | Vote (verdict `vote`) |

### Serveur → client

| Destination | Contenu |
|---|---|
| `/topic/room/{code}` | `RoomView` public (`type: "room"`) : phase, joueurs (pseudo, budget restant, équipe, a agi ou non), indices révélés, prix plancher, `deadline` et `serverTime` (epoch ms), nombre d'offres reçues (pas leur montant en `sealed`), journal, résultat |
| `/user/queue/room` | `PlayerView` privé (`type: "player"`) : mon id, mon offre, passe, `canBid`, `mustPlace`, slots libres, mon vote. Reçoit aussi la `RoomView` lors d'un `sync` |
| `/user/queue/errors` | `{ code, message }` : offre rejetée, action interdite |

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
