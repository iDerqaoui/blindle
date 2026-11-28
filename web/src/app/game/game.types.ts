/** Miroir des vues serveur (realtime/view/Views.java). Aucune logique de jeu ici. */

export type Phase =
  | 'LOBBY' | 'STARTED' | 'DRAW' | 'HINT' | 'BIDDING'
  | 'AWARDED' | 'REVEALED' | 'SLOT' | 'VERDICT' | 'FINISHED';

export interface SlotView { id: string; label: string; requiredTags: string[]; }

export interface CardView {
  id: string;
  name: string;
  tags: string[];
  rarity: string | null;
  stats: Record<string, number>;
  /** Valeur en pièces (verdict « value »). */
  value: number;
  /** Points de rareté (verdict « points »). */
  points: number;
  /** Image du personnage (thèmes illustrés), sinon null. */
  image: string | null;
}

/** Habillage de l'univers : affiche, bannière, couleur dominante (dos de carte). */
export interface ArtView { cover: string | null; banner: string | null; color: string | null; }

export interface TeamEntry { slotId: string; card: CardView | null; paid: number | null; }

export interface PlayerSummary {
  id: string;
  name: string;
  budget: number;
  bonus: number;
  host: boolean;
  bot: boolean;
  /** Profil du bot (aggressive, stingy…), null pour un humain. */
  botProfile: string | null;
  acted: boolean;
  team: TeamEntry[];
  /** Faux si la connexion du joueur est tombée en cours de partie. */
  connected: boolean;
}

export interface HintView { level: number; type: string; value: string; }

export interface BidView { playerId: string; amount: number; }

export interface LotView {
  hintLevel: number;
  maxHintLevel: number;
  hints: HintView[];
  floorPrice: number;
  actedCount: number;
  bids: BidView[] | null;
  cards: CardView[] | null;
  winnerId: string | null;
  price: number | null;
}

export interface ResultView { ranking: string[]; scores: Record<string, number>; }

export interface RoomView {
  type: 'room';
  code: string;
  /** Room publique : listée sur l'accueil tant qu'elle attend des joueurs. */
  publicRoom: boolean;
  version: number;
  phase: Phase;
  presetId: string;
  presetName: string;
  themeName: string;
  bidMode: string;
  verdictMode: string;
  rules: string[];
  hostId: string;
  round: number;
  totalRounds: number;
  slots: SlotView[];
  players: PlayerSummary[];
  lot: LotView | null;
  deadline: number | null;
  serverTime: number;
  log: string[];
  result: ResultView | null;
  votesReceived: number;
  daily: DailyView | null;
  /** Joueur qui a pioché la carte en cours (mode Blind Bid), tant qu'elle est cachée. */
  drawerId: string | null;
  /** Poubelle : cartes que personne n'a voulues, visibles par tous. */
  trash: CardView[];
  art: ArtView | null;
  /** Vrai si l'hôte a personnalisé cette room (cartes, raretés, règles). */
  customized: boolean;
  /** Cartes du paquet de cette room. */
  deckSize: number;
  /** Paramètres des règles actives, défauts compris (ex. `discard-fee` → `{ amount: 150 }`). */
  ruleParams: Record<string, Record<string, RuleParamValue>>;
}

/** Carte du thème, telle que l'hôte la voit pour personnaliser sa room. */
export interface CatalogCard {
  id: string;
  name: string;
  image: string | null;
  /** Rareté fixée par l'administrateur ; `rarity` est celle de la room. */
  baseRarity: string | null;
  rarity: string | null;
  trap: boolean;
  included: boolean;
}

export type RuleParamValue = number | string | boolean;

export interface CatalogRule {
  type: string;
  active: boolean;
  params: Record<string, RuleParamValue>;
  defaults: Record<string, RuleParamValue>;
  requiredBidModes: string[];
  conflictsWith: string[];
}

/** Ce que l'hôte peut personnaliser dans sa room (envoyé à lui seul). */
export interface RoomCatalogView {
  type: 'catalog';
  code: string;
  cards: CatalogCard[];
  rarities: string[];
  rules: CatalogRule[];
}

/** Personnalisation envoyée par l'hôte. `rules` null : règles du preset. */
export interface RoomCustomization {
  excludedCards: string[];
  rarities: Record<string, string>;
  rules: { ref: string; params: Record<string, RuleParamValue> }[] | null;
}

/** Défi du jour : rang et texte à partager une fois la partie terminée. */
export interface DailyView { date: string; rank: number | null; share: string | null; }

export interface PlayerView {
  type: 'player';
  code: string;
  version: number;
  playerId: string;
  name: string;
  host: boolean;
  myBid: number | null;
  passed: boolean;
  canBid: boolean;
  mustPlace: boolean;
  freeSlots: string[];
  placeableSlots: string[];
  jokers: Record<string, number>;
  privateHints: HintView[];
  votedFor: string | null;
  /** Carte piochée : envoyée au seul piocheur. */
  drawnCard: CardView | null;
}

export interface ErrorMessage { type: 'error'; code: string; message: string; }

export interface PresetSummary {
  id: string;
  name: string;
  description: string | null;
  /** Thème (univers) : lien vers son défi Who's That? (#who/<themeId>). */
  themeId: string;
  themeName: string;
  bidMode: string;
  verdictMode: string;
  daily: boolean;
  /** Avertissement de droits du thème (§8), affiché aux joueurs. */
  disclaimer: string | null;
  art: ArtView | null;
  /** Images des personnages les plus populaires (médaillons). */
  faces: string[];
  /** Nombre de cartes du thème. */
  cards: number;
  settings: { budget: number; rounds: number; teamSize: number; hintLevels: number; bidTimerSec: number };
}

export interface LeaderboardEntry { name: string; score: number; }

export interface DailySummary {
  presetId: string;
  name: string;
  description: string | null;
  themeName: string;
  date: string;
  players: number;
  leaderboard: LeaderboardEntry[];
}

export interface BotProfile { profile: string; label: string; }

/** Une room publique qui attend des joueurs (GET /api/rooms). */
export interface PublicRoom {
  code: string; presetId: string; presetName: string; themeId: string; themeName: string;
  hostName: string; humans: number; players: number; maxPlayers: number; createdAt: string;
}
