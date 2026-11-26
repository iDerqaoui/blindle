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
  themeName: string;
  bidMode: string;
  verdictMode: string;
  daily: boolean;
  /** Avertissement de droits du thème (§8), affiché aux joueurs. */
  disclaimer: string | null;
  art: ArtView | null;
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
