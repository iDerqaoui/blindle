import { Injectable, computed, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { ErrorMessage, PlayerView, PresetSummary, RoomView } from './game.types';

/**
 * Connexion STOMP au serveur et état reçu. Le client ne fait qu'afficher :
 * chaque action est envoyée au serveur, qui renvoie les nouvelles vues.
 */
@Injectable({ providedIn: 'root' })
export class GameService {
  readonly connected = signal(false);
  readonly room = signal<RoomView | null>(null);
  readonly me = signal<PlayerView | null>(null);
  readonly error = signal<string | null>(null);
  readonly presets = signal<PresetSummary[]>([]);

  /** Horloge locale corrigée de l'écart avec le serveur, rafraîchie 4 fois par seconde. */
  readonly now = signal(Date.now());
  private clockOffset = 0;

  readonly remainingMs = computed(() => {
    const deadline = this.room()?.deadline;
    return deadline == null ? null : Math.max(0, deadline - this.now());
  });

  readonly myId = computed(() => this.me()?.playerId ?? null);

  private client: Client;
  private roomSub: StompSubscription | null = null;
  private errorTimer: ReturnType<typeof setTimeout> | undefined;
  private pending: (() => void)[] = [];

  constructor() {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    this.client = new Client({
      brokerURL: `${scheme}://${location.host}/ws`,
      reconnectDelay: 0,
      onConnect: () => {
        this.connected.set(true);
        this.client.subscribe('/user/queue/room', m => this.onMessage(m));
        this.client.subscribe('/user/queue/errors', m => this.onError(m));
        const queued = this.pending;
        this.pending = [];
        queued.forEach(fn => fn());
      },
      onWebSocketClose: () => {
        this.connected.set(false);
        if (this.room()) {
          this.showError('Connexion perdue avec le serveur. Recharge la page pour rejouer.');
        }
      },
    });
    this.client.activate();
    setInterval(() => this.now.set(Date.now() + this.clockOffset), 250);
    this.loadPresets();
  }

  async loadPresets(): Promise<void> {
    try {
      const res = await fetch('/api/presets');
      if (res.ok) {
        this.presets.set(await res.json());
      }
    } catch {
      this.showError('Serveur injoignable');
    }
  }

  // ------------------------------------------------------------------ actions

  createRoom(name: string, presetId: string): void {
    this.send('/app/room.create', { name, presetId });
  }

  joinRoom(code: string, name: string): void {
    this.send('/app/room.join', { code: code.trim().toUpperCase(), name });
  }

  updateSettings(settings: { budget?: number; rounds?: number; bidTimerSec?: number }): void {
    this.sendToRoom('settings', settings);
  }

  start(): void { this.sendToRoom('start'); }
  bid(amount: number): void { this.sendToRoom('bid', { amount }); }
  pass(): void { this.sendToRoom('pass'); }
  assignSlot(slotId: string): void { this.sendToRoom('slot', { slotId }); }
  vote(teamOwnerId: string): void { this.sendToRoom('vote', { teamOwnerId }); }
  useJoker(jokerId: string, targetId: string | null = null): void { this.sendToRoom('joker', { jokerId, targetId }); }
  forfeit(): void { this.sendToRoom('forfeit'); }

  /** Utilisations restantes d'un joker pour le joueur courant. */
  jokersLeft(jokerId: string): number {
    return this.me()?.jokers?.[jokerId] ?? 0;
  }

  /** Quitte la room côté client (nouvelle connexion = nouvelle identité). */
  leave(): void {
    this.roomSub?.unsubscribe();
    this.roomSub = null;
    this.room.set(null);
    this.me.set(null);
    this.client.deactivate().then(() => this.client.activate());
  }

  playerName(id: string | null | undefined): string {
    if (!id) {
      return '';
    }
    return this.room()?.players.find(p => p.id === id)?.name ?? '?';
  }

  // ------------------------------------------------------------------ interne

  private sendToRoom(action: string, body: object = {}): void {
    const code = this.me()?.code;
    if (code) {
      this.send(`/app/room/${code}/${action}`, body);
    }
  }

  private send(destination: string, body: object): void {
    const publish = () => this.client.publish({ destination, body: JSON.stringify(body) });
    if (this.client.connected) {
      publish();
    } else {
      this.pending.push(publish);
    }
  }

  private onMessage(message: IMessage): void {
    const data = JSON.parse(message.body) as PlayerView | RoomView;
    if (data.type === 'player') {
      const first = this.me() === null || this.me()!.code !== data.code;
      if (!this.me() || data.version >= this.me()!.version || first) {
        this.me.set(data);
      }
      if (first) {
        this.watchRoom(data.code);
      }
    } else {
      this.acceptRoom(data);
    }
  }

  private watchRoom(code: string): void {
    this.roomSub?.unsubscribe();
    this.roomSub = this.client.subscribe(`/topic/room/${code}`, m => this.acceptRoom(JSON.parse(m.body)));
    this.send(`/app/room/${code}/sync`, {});
  }

  private acceptRoom(view: RoomView): void {
    const current = this.room();
    if (current && current.code === view.code && view.version < current.version) {
      return; // message obsolète
    }
    this.clockOffset = view.serverTime - Date.now();
    this.room.set(view);
  }

  private onError(message: IMessage): void {
    const err = JSON.parse(message.body) as ErrorMessage;
    this.showError(err.message);
  }

  private showError(text: string): void {
    this.error.set(text);
    clearTimeout(this.errorTimer);
    this.errorTimer = setTimeout(() => this.error.set(null), 4000);
  }
}
