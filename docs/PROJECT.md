# Blindle (blindle.me) — Spécification complète, étapes et prompts

> Document de référence du projet. À placer dans `docs/PROJECT.md`.
> `CLAUDE.md` (racine) en est le résumé chargé à chaque session ; ce fichier-ci est la source de vérité.

---

## 1. Concept

**Pitch.** De 3 à 8 amis (ou un joueur seul contre des bots), un budget fixe chacun. Des cartes — personnages de manga, footballeurs, pays, plats… — passent aux enchères **cachées derrière des indices**. Chacun construit une équipe à l'aveugle, puis un verdict désigne la meilleure équipe.

**Le dilemme central.** Les indices se révèlent du plus vague au plus précis. **Plus on mise tôt, moins on a d'information, mais moins c'est cher** : le prix plancher monte à chaque indice révélé.

**Exemple de lot (thème Manga)**

| Palier | Indice | Prix plancher |
|---|---|---|
| 1 | « Utilisateur d'un pouvoir lié au feu » | 50 |
| 2 | « A perdu la vie pour protéger un frère » | 100 |
| 3 | Initiale ou silhouette générique | 150 |

**Moteur générique.** Le manga n'est que le premier thème. Le moteur ne connaît que des cartes, des indices, des enchères et des règles :

- **Contenu = données** : un thème est un fichier JSON, sans code.
- **Règles = modules** : une règle s'accroche à un événement du jeu et modifie l'état.
- **Partie = preset** : un preset combine un thème, des règles, un mode d'enchère et un mode de verdict.

---

## 2. Déroulé d'une partie

1. **Lobby** : un hôte crée une room (code à partager) et choisit un preset ; il peut ajuster budget, nombre de rounds, taille d'équipe.
2. **Démarrage** : distribution des budgets et jokers.
3. **Round** (répété N fois) :
   1. Tirage d'un lot mystère.
   2. Révélation d'un indice → phase d'enchères selon le mode actif.
   3. Pas de gagnant → indice suivant (prix plancher plus haut). Gagnant → lot attribué.
   4. La carte est révélée à tous.
   5. Le gagnant la place dans un slot de son équipe (définitif).
4. **Verdict** : vote, duels, stats… selon le mode choisi.
5. **Résultats** : scores, rejouer.

**Slots d'équipe par défaut** : Capitaine, Bras droit, Stratège, Soutien, Joker. Un thème peut les redéfinir (Football : Gardien, Défenseur, Milieu, Attaquant, Coach ; Cuisine : Entrée, Plat, Dessert).

---

## 3. Glossaire

| Terme | Définition | Exemple |
|---|---|---|
| Thème | Univers de contenu : deck + slots + métadonnées | Manga Shonen |
| Deck | Ensemble des cartes d'un thème | 120 personnages |
| Carte | Élément à gagner, avec attributs, tags et indices | Ace |
| Indice | Information partielle révélée par palier | « Pouvoir lié au feu » |
| Lot | Ce qui est mis aux enchères dans un round (1 carte ou plus) | Lot double |
| Slot | Emplacement d'équipe, avec contrainte optionnelle | Capitaine |
| Règle | Module déclencheur + condition + effet | Joker Vol |
| Mode d'enchère | Stratégie de résolution des offres | Scellée |
| Verdict | Stratégie qui désigne le gagnant final | Vote |
| Preset | Combinaison sauvegardée thème + règles + modes + paramètres | « Manga Classique » |

---

## 4. Format d'un thème

```json
{
  "id": "manga-shonen",
  "name": "Manga Shonen",
  "version": "1.0.0",
  "description": "Personnages de shonen populaires",
  "rights": {
    "profile": "fictional-ip",
    "channels": ["web"],
    "visibility": "private",
    "imagePolicy": "none",
    "disclaimer": "Jeu de fans, non affilié aux ayants droit.",
    "takedownContact": "contact@…"
  },
  "defaultSlots": [
    { "id": "captain",   "label": "Capitaine" },
    { "id": "right",     "label": "Bras droit" },
    { "id": "tactician", "label": "Stratège" },
    { "id": "support",   "label": "Soutien", "requiredTags": ["support"] },
    { "id": "joker",     "label": "Joker" }
  ],
  "attributes": ["power", "intelligence", "popularity"],
  "cards": [
    {
      "id": "op-ace",
      "name": "Portgas D. Ace",
      "tags": ["one-piece", "fire", "pirate"],
      "rarity": "epic",
      "isTrap": false,
      "stats": { "power": 82, "intelligence": 60, "popularity": 90 },
      "hints": [
        { "level": 1, "type": "text", "text": "Utilisateur d'un pouvoir lié au feu" },
        { "level": 2, "type": "text", "text": "A perdu la vie pour protéger un frère" },
        { "level": 3, "type": "initial", "value": "A" }
      ]
    }
  ]
}
```

- **Obligatoires** : `id`, `name`, `rights`, `cards[].id`, `cards[].name`, `cards[].hints` (au moins 2 niveaux).
- **Optionnels** : `stats`, `rarity`, `isTrap`, `image`. Une règle ou un verdict qui exige un champ absent est désactivé pour ce thème.
- **Tags libres** : servent aux contraintes de slots et aux règles (synergies).
- **Types d'indice** : `text`, `initial`, `silhouette`, `image-blur`, `audio`. Le front choisit le rendu selon le type.

---

## 5. Système de règles

Toute règle suit le modèle **déclencheur → condition → effet**. Le moteur émet un événement à chaque étape ; chaque règle active écoute les siens.

### Hooks

| Hook | Émis quand | Usage typique |
|---|---|---|
| `onGameStart` | La partie démarre | Budgets, jokers |
| `onRoundStart` | Un lot est tiré | Lot double, lot piège |
| `onHintReveal` | Un indice est révélé | Prix plancher croissant |
| `onBidPlaced` | Une offre est reçue | Plafond, relance du timer |
| `onLotWon` | Un joueur remporte un lot | Taxe, bonus |
| `onCardRevealed` | La carte est dévoilée | Malus carte piège |
| `onSlotAssigned` | Carte placée dans un slot | Synergie |
| `onJokerUsed` | Un joker est activé | Espion, Vol |
| `onRoundEnd` | Fin du round | Intérêts |
| `onVerdict` | Calcul du résultat | Bonus, malus |

### Déclaration d'une règle

```json
{
  "id": "rising-floor",
  "type": "FLOOR_PRICE_PER_HINT",
  "name": "Prix plancher croissant",
  "hooks": ["onHintReveal"],
  "priority": 100,
  "params": { "base": 50, "stepPerHint": 50 },
  "requires": { "themeFields": [], "bidModes": ["sealed", "open"] },
  "conflictsWith": []
}
```

### Principes d'exécution

- **Priorité** : par `priority` croissante ; validation < 100, effets ≥ 100.
- **Annulation** : une règle peut rejeter une action (offre au-dessus du budget) → erreur renvoyée au joueur.
- **Compatibilité** : `requires` / `conflictsWith` vérifiés à la création du preset.
- **Déterminisme** : tout aléa utilise la seed de la partie → rejouable à l'identique.
- **Journal** : chaque effet produit un message lisible (« Yassine a volé Ace pour 180 pièces »).

### Catalogue de base

| ID | Effet | Paramètres | Prérequis |
|---|---|---|---|
| `budget-cap` | Rejette une offre > budget restant | — | — |
| `rising-floor` | Minimum d'offre monte à chaque indice | `base`, `stepPerHint` | — |
| `broke-pity` | Budget à 0 → carte aléatoire gratuite | `pool` | — |
| `trap-lots` | Injecte des cartes `isTrap` à indices flatteurs | `frequency` | `isTrap` |
| `double-lot` | Deux cartes pour un seul indice | `chance` | — |
| `joker-spy` | Un indice supplémentaire en privé | `usesPerGame` | — |
| `joker-steal` | Racheter la carte d'un adversaire | `multiplier` (1,5) | — |
| `tag-synergy` | Bonus si N cartes partagent un tag | `minCount`, `bonus` | tags |
| `slot-constraint` | Refuse une carte sans le tag requis | — | `requiredTags` |
| `interest` | +X % sur le budget non dépensé | `rate` | — |
| `anti-snipe` | Relance le timer si offre de dernière seconde | `extendSeconds` | mode `open` |
| `first-buy-bonus` | Remise sur le premier lot gagné | `discount` | — |

---

## 6. Modes d'enchère et de verdict

### Enchère

| ID | Fonctionnement | Ambiance |
|---|---|---|
| `sealed` | Offres secrètes simultanées, la plus haute gagne | Stratégique |
| `open` | Offres visibles, timer relancé à chaque surenchère | Party game |
| `vickrey` | Offres secrètes, le gagnant paie la 2ᵉ offre | Miser sa vraie valeur |
| `dutch` | Le prix descend, le premier qui clique gagne | Réflexes |
| `all-pay` | Tout le monde paie, seul le meilleur gagne | Punitif |
| `market` | Contre un prix du marché caché (solo) | Défi du jour |

### Verdict

| ID | Fonctionnement | Prérequis |
|---|---|---|
| `vote` | Chacun vote pour la meilleure équipe sauf la sienne | — |
| `bracket` | Duels slot contre slot, vote à chaque duel | — |
| `stats` | Score calculé depuis `stats` + bonus | `stats` |
| `value` | Valeur des cartes − pièces dépensées | `stats` ou `rarity` |
| `hybrid` | Pondération vote / stats | `stats` |

### Preset

```json
{
  "id": "manga-classique",
  "name": "Manga Classique",
  "theme": "manga-shonen",
  "bidMode": "sealed",
  "verdictMode": "vote",
  "settings": { "budget": 1000, "rounds": 10, "teamSize": 5, "hintLevels": 3, "bidTimerSec": 30 },
  "rules": [
    { "ref": "budget-cap" },
    { "ref": "rising-floor", "params": { "base": 50, "stepPerHint": 50 } }
  ]
}
```

---

## 7. Mode solo

| Variante | Adversaire | Verdict | Effort |
|---|---|---|---|
| Défi du jour | Prix du marché caché (`market`) | `value` + classement | Faible |
| Bots à personnalité | 2 à 7 bots | `stats` / `value` | Moyen |
| Fantômes | Enchères enregistrées d'autres joueurs (même seed) | `value` | Moyen |
| Carrière (roguelite) | Bots croissants, boss par thème | `stats` | Élevé |

**Défi du jour** : une seed par jour et par thème, tout le monde joue les mêmes lots ; score = valeur de l'équipe − pièces dépensées ; résultat partageable sans spoiler (grille d'emojis).

**Bots** : un bot reçoit les mêmes événements qu'un humain. Il filtre les cartes compatibles avec les indices révélés, estime leur valeur moyenne, puis applique sa personnalité :

| Profil | Comportement |
|---|---|
| Agressif | Mise dès le 1er indice, jusqu'à 120 % de son estimation |
| Radin | Attend le dernier indice, plafonne à 80 % |
| Fan | Surenchérit sur un tag favori |
| Bluffeur | Fait monter les prix puis se retire |
| Stratège | Garde du budget pour les slots vides |

```java
public interface BotStrategy {
    String profile();
    Optional<Bid> decide(BotView view);   // vue filtrée, comme un humain
}
```

---

## 8. Droits, contenus et canaux

> Repères pratiques, pas un avis juridique.

| Profil | Exemples | Autorisé | Interdit | Canaux par défaut |
|---|---|---|---|---|
| `free` | Pays, cuisine | Textes maison, images sous licence libre vérifiée | Logos, photos sans licence | web, discord |
| `fictional-ip` | Manga, films, jeux vidéo | Noms, indices rédigés, stats maison, illustrations originales | Images officielles (même floutées), logos | web |
| `real-people` | Football, musique | Noms, faits publics, stats maison, drapeaux, avatars génériques | Photos, logos de clubs/ligues, notes FIFA/EA | web |
| `private` | Tout thème avec visuels officiels | Tout, en cercle privé | Diffusion publique | room sur invitation |

- `channels` : `web`, `discord` ou les deux ; un preset ne peut que restreindre ceux de son thème.
- `visibility` : `public` ou `private`.
- `imagePolicy` : `any`, `licensed-only`, `generic-only`, `none`.
- Le validateur refuse les combinaisons incohérentes (ex. `real-people` + `imagePolicy: any` + `public`).
- **Le canal se déduit de l'authentification côté serveur**, jamais d'un paramètre client.
- Un thème peut être retiré d'un canal à chaud (signalement d'un ayant droit).
- Garder un thème sur le web évite les règles Discord, pas le droit d'auteur ni le droit à l'image.

---

## 9. Déploiement en Discord Activity (plus tard)

Une Activity est une appli web lancée dans un salon vocal Discord (iframe + Embedded App SDK).

| Sujet | Web | Discord Activity |
|---|---|---|
| Lobby | Code de room | Room = instance de l'Activity (`instanceId`) |
| Comptes | Pseudo / compte site | Identité Discord (OAuth2 via SDK) |
| Front | Angular | Angular + `@discord/embedded-app-sdk` |
| Back | Spring Boot + STOMP | Identique, derrière le proxy Discord |
| Images externes | URL directes | Domaines déclarés en URL mapping |

**Flux** : `ready()` → `authorize()` (code) → le back échange le code contre un token (client secret côté serveur) → `authenticate(token)` → WebSocket + room de l'`instanceId`.

**À prévoir** : URL mappings obligatoires, HTTPS même en dev (tunnel cloudflared/ngrok), UI responsive (mobile), template `discord/getting-started-activity` comme référence.

---

## 10. Architecture technique

### Stack

- Back : Java 21, Spring Boot 3, Maven, WebSocket STOMP
- Front : Angular (standalone components), RxJS, client STOMP
- Contenu : JSON dans `content/`, validé par JSON Schema
- MVP : état en mémoire ; plus tard PostgreSQL, Redis

### Modules

| Module | Contenu |
|---|---|
| `engine-core` | `GameState`, `GameEvent`, `GameHook`, `RuleEngine`, machine à états |
| `engine-rules` | Une classe `@Component` par règle |
| `engine-modes` | `BidMode`, `VerdictMode` |
| `content` | Chargement et validation des thèmes / presets |
| `realtime` | Contrôleurs STOMP, rooms, timers |
| `web/` | Angular : lobby, enchère, équipe, verdict |

### Interfaces cœur

```java
public interface Rule {
    String type();
    Set<GameHook> hooks();
    int priority();
    RuleResult apply(GameEvent event, GameState state, RuleParams params);
}

public interface BidMode {
    String id();
    void onBid(Bid bid, LotState lot);
    Optional<LotResult> resolve(LotState lot);
}

public interface VerdictMode {
    String id();
    Set<String> requiredThemeFields();
    GameResult compute(GameState state, List<Vote> votes);
}
```

### Principes non négociables

1. Serveur = seule source de vérité (timers, offres, budgets).
2. Vues filtrées par joueur (en `sealed`, jamais les offres des autres avant résolution).
3. Tout aléa passe par la seed de la partie.
4. Aucun contenu spécifique à un thème dans `engine-*`.
5. Une règle = une classe, testée.
6. État immuable (records), les règles renvoient un nouvel état.
7. Timers côté serveur ; le client reçoit l'heure de fin, pas un compte à rebours à lui.

---

## 11. Feuille de route

| Étape | Contenu | Livrable |
|---|---|---|
| 0 | Repo, CLAUDE.md, docs, squelette | Projet qui compile |
| 1 | `engine-core` + machine à états | Partie simulable en test |
| 2 | `RuleEngine` + `budget-cap` + `rising-floor` | Règles testées |
| 3 | Mode `sealed` + verdict `vote` | Partie complète en test |
| 4 | Thèmes JSON + JSON Schema + 30 cartes Manga texte | Contenu chargé au démarrage |
| 5 | `realtime` : rooms, STOMP, timers, vues filtrées | Jouable via client STOMP |
| 6 | Front Angular : lobby, enchère, équipe, vote | **MVP jouable entre potes** |
| 7 | Jokers, lots pièges, synergies, mode `open` | Règles avancées |
| 8 | 2ᵉ thème (Pays ou Football texte) | Moteur vérifié multi-thèmes |
| 9 | Mode solo : `market` + défi du jour, puis bots | Jouable seul |
| 10 | Droits et canaux (`rights`, filtrage) | Prêt pour diffusion |
| 11 | Discord Activity | Jouable dans un vocal |
| 12 | Persistance, classements, éditeur de thèmes | Version publique |

---

## 12. Prompts Claude Code par étape

> Une étape par session. Mode plan (Shift+Tab) pour les étapes structurantes. Commit GitLab après chaque étape validée.

### Étape 0 — Cadrage

```
Lis CLAUDE.md et docs/PROJECT.md en entier.
Propose, sans coder :
1. l'arborescence Maven multi-modules + le dossier web/ Angular
2. les classes clés de engine-core (GameState, GameEvent, GameHook, phases)
3. le protocole STOMP (topics, messages entrants/sortants, vues filtrées)
4. l'ordre des tâches de l'étape 1 à 6, chacune testable seule
Attends ma validation.
```

Puis :

```
Plan validé. Génère le squelette Maven multi-modules (engine-core, engine-rules,
engine-modes, content, realtime, app) qui compile, sans logique métier.
Complète la section "Commandes" de CLAUDE.md (build, tests, lancement).
```

### Étape 1 — Moteur de base

```
Étape 1 de docs/PROJECT.md §11.
Implémente dans engine-core : GameState (record immuable), GameEvent, GameHook,
la machine à états des phases (LOBBY → STARTED → ROUND[DRAW → HINT → BIDDING →
AWARDED → REVEALED → SLOT] → VERDICT → FINISHED), et un Random injecté depuis la seed.
Aucune règle ni mode pour l'instant : utilise des stubs.
Tests JUnit : enchaînement des phases, transitions invalides refusées,
même seed = même tirage.
```

### Étape 2 — Règles

```
Étape 2. Implémente l'interface Rule, le RuleEngine (dispatch par hook, tri par
priorité, rejet d'action possible) et les règles budget-cap et rising-floor
selon docs/PROJECT.md §5. Une classe @Component par règle, paramètres typés.
Tests : état + événement → état attendu, et rejet d'une offre hors budget.
```

### Étape 3 — Modes

```
Étape 3. Implémente BidMode "sealed" et VerdictMode "vote" (§6).
sealed : offres secrètes, résolution quand tous ont misé ou au timeout, égalité
départagée par la seed. vote : chacun vote pour une équipe autre que la sienne.
Écris un test qui simule une partie complète de 3 joueurs, 3 rounds, de bout en bout.
```

### Étape 4 — Contenu

```
Étape 4. Module content : chargement des thèmes et presets JSON depuis content/,
validation par JSON Schema au démarrage (format §4, champ rights §8 inclus),
erreurs explicites. Crée le preset "manga-classique" (§6) et un thème
manga-shonen de 30 cartes : noms + 3 indices texte chacune (du vague au précis),
stats maison, imagePolicy "none", visibility "private". Pas d'images.
Liste-moi les 30 cartes et leurs indices pour relecture avant de commiter.
```

### Étape 5 — Temps réel

```
Étape 5. Module realtime : création/join de room par code, pseudo libre,
STOMP selon le protocole validé à l'étape 0, timers serveur
(ScheduledExecutorService, le client reçoit l'heure de fin), vues filtrées
par joueur (en sealed, aucune offre adverse avant résolution).
Tests d'intégration avec un client STOMP de test : 3 joueurs jouent une partie.
```

### Étape 6 — Front Angular

```
Étape 6. Front Angular dans web/ : écrans Lobby (créer/rejoindre, choix du preset),
Enchère (lot mystère, indices révélés progressivement avec animation, prix plancher,
saisie d'offre, timer basé sur l'heure de fin serveur), Équipe (slots), Verdict (vote),
Résultats. Composant d'indice choisi selon hint.type. Responsive.
Aucune logique de jeu côté client : tout vient du serveur.
```

### Étape 7 — Règles avancées

```
Étape 7. Ajoute les règles joker-spy, joker-steal, trap-lots, tag-synergy,
slot-constraint, anti-snipe et le mode d'enchère "open" (§5, §6).
Vérifie requires / conflictsWith à la création du preset.
Un test par règle. Ajoute un preset "manga-chaos" qui les active.
```

### Étape 8 — Deuxième thème

```
Étape 8. Ajoute un thème "pays-du-monde" (profil free, 40 cartes, slots propres,
indices : climat, plat typique, langue, monument décrit en texte).
Objectif : prouver qu'aucune ligne de engine-* ne change. Si un changement
est nécessaire, signale-le comme dette de conception avant de le faire.
```

### Étape 9 — Solo

```
Étape 9. Mode solo (§7). D'abord : mode d'enchère "market" (prix du marché caché
dérivé de la valeur de la carte), verdict "value", défi du jour (seed par
jour et par thème), classement en mémoire, résultat partageable en emojis.
Ensuite seulement : joueurs BOT avec BotStrategy et les 5 profils.
```

### Étape 10 — Droits et canaux

```
Étape 10. Implémente §8 : champ rights obligatoire, validation des combinaisons
incohérentes, filtrage du catalogue et de la création de room selon le canal
déduit de l'authentification (jamais d'un paramètre client), retrait à chaud
d'un thème d'un canal. Tests : un joueur "discord" ne peut pas lancer un thème "web".
```

### Étape 11 — Discord Activity

```
Étape 11. Déploiement en Discord Activity (§9). Intègre @discord/embedded-app-sdk
dans le front, le flux ready → authorize → échange du code côté Spring Boot →
authenticate, la room liée à l'instanceId, le canal "discord".
Base-toi sur le template officiel discord/getting-started-activity pour le flux OAuth.
Documente dans docs/ la config du Developer Portal (URL mappings) et le tunnel HTTPS de dev.
```

---

## 13. Nom et domaine

- **Nom du jeu : Blindle** — domaine **blindle.me**.
- Clin d'œil aux jeux quotidiens type Wordle : cohérent avec le futur défi du jour (§7).
- Nom générique, compatible avec tous les thèmes.
- À réserver aussi : pseudo « blindle » sur Discord, X, TikTok.

---

## 14. Points ouverts

- Mode d'enchère par défaut : `sealed` (stratégique) ou `open` (party) ?
- Stockage des thèmes : fichiers du repo ou base de données (nécessaire pour un éditeur) ?
- Images : aucune au MVP ; plus tard illustrations originales ou images sous licence libre.
- Monétisation éventuelle : consulter un juriste en propriété intellectuelle avant.
