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
}

export interface TeamEntry { slotId: string; card: CardView | null; }

export interface PlayerSummary {
  id: string;
  name: string;
  budget: number;
  host: boolean;
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
}

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
  votedFor: string | null;
}

export interface ErrorMessage { type: 'error'; code: string; message: string; }

export interface PresetSummary {
  id: string;
  name: string;
  description: string | null;
  themeName: string;
  bidMode: string;
  verdictMode: string;
  settings: { budget: number; rounds: number; teamSize: number; hintLevels: number; bidTimerSec: number };
}
