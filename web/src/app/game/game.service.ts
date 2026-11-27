import { Injectable, computed, inject, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { ArtView, BotProfile, DailySummary, ErrorMessage, PlayerSummary, PlayerView, PresetSummary, RoomCatalogView, RoomCustomization, RoomView } from './game.types';
import { I18nService } from '../i18n/i18n';

/**
 * Connexion STOMP au serveur et état reçu. Le client ne fait qu'afficher :
 * chaque action est envoyée au serveur, qui renvoie les nouvelles vues.
 */
@Injectable({ providedIn: 'root' })
export class GameService {
  private readonly i18n = inject(I18nService);
  readonly connected = signal(false);
  readonly room = signal<RoomView | null>(null);
  readonly me = signal<PlayerView | null>(null);
  readonly error = signal<string | null>(null);
  readonly presets = signal<PresetSummary[]>([]);
  /** Univers regardé à l'accueil : son visuel habille le fond de page avant d'entrer en room. */
  readonly previewArt = signal<ArtView | null>(null);
  private pendingBots: string[] = [];
  readonly daily = signal<DailySummary[]>([]);
  readonly botProfiles = signal<BotProfile[]>([]);
  /** Catalogue de personnalisation de la room (hôte seulement). */
  readonly catalog = signal<RoomCatalogView | null>(null);

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
  /** Retour dans une room après rechargement : une room disparue entre-temps est oubliée sans bruit. */
  private resuming = false;

  constructor() {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    this.client = new Client({
      brokerURL: `${scheme}://${location.host}/ws`,
      // Connexion perdue : on se reconnecte seul, avec le jeton qui rend sa place au joueur.
      reconnectDelay: 2000,
      beforeConnect: () => {
        const session = readSession();
        const resume = session ? `?resume=${encodeURIComponent(session.token)}` : '';
        this.client.brokerURL = `${scheme}://${location.host}/ws${resume}`;
      },
      onConnect: () => {
        this.connected.set(true);
        this.client.subscribe('/user/queue/room', m => this.onMessage(m));
        this.client.subscribe('/user/queue/errors', m => this.onError(m));
        this.client.subscribe('/user/queue/catalog', m => this.catalog.set(JSON.parse(m.body)));
        this.client.subscribe('/user/queue/session', m => saveSession(JSON.parse(m.body)));
        // Page rechargée ou connexion revenue : on retourne dans sa room (sync rend les vues).
        const session = readSession();
        const code = this.me()?.code ?? session?.code;
        if (code) {
          this.resuming = !this.me();
          this.watchRoom(code);
        }
        const queued = this.pending;
        this.pending = [];
        queued.forEach(fn => fn());
      },
      onWebSocketClose: () => {
        this.connected.set(false);
        if (this.room()) {
          this.showError(this.i18n.t('err.reconnecting'));
        }
      },
    });
    this.client.activate();
    setInterval(() => this.now.set(Date.now() + this.clockOffset), 250);
    this.loadPresets();
    this.loadDaily();
  }

  async loadPresets(): Promise<void> {
    try {
      const res = await fetch('/api/presets');
      if (res.ok) {
        this.presets.set(await res.json());
      }
      const bots = await fetch('/api/bots');
      if (bots.ok) {
        this.botProfiles.set(await bots.json());
      }
    } catch {
      this.showError(this.i18n.t('err.serverDown'));
    }
  }

  /** Défis du jour et leur classement (rafraîchi à l'accueil et en fin de défi). */
  async loadDaily(): Promise<void> {
    try {
      const res = await fetch('/api/daily');
      if (res.ok) {
        this.daily.set(await res.json());
      }
    } catch {
      // déjà signalé par loadPresets
    }
  }

  // ------------------------------------------------------------------ actions

  /** @param bots profils de bots à ajouter dès que la room est créée (partie « contre des bots ») */
  createRoom(name: string, presetId: string, bots: string[] = []): void {
    this.pendingBots = [...bots];
    this.send('/app/room.create', { name, presetId });
  }

  startDaily(name: string, presetId: string): void {
    this.send('/app/daily.start', { name, presetId });
  }

  joinRoom(code: string, name: string): void {
    this.send('/app/room.join', { code: code.trim().toUpperCase(), name });
  }

  updateSettings(settings: { budget?: number; rounds?: number; bidTimerSec?: number }): void {
    this.sendToRoom('settings', settings);
  }

  addBot(profile: string): void { this.sendToRoom('bot', { profile }); }
  start(): void { this.sendToRoom('start'); }
  bid(amount: number): void { this.sendToRoom('bid', { amount }); }
  pass(): void { this.sendToRoom('pass'); }
  assignSlot(slotId: string): void { this.sendToRoom('slot', { slotId }); }
  vote(teamOwnerId: string): void { this.sendToRoom('vote', { teamOwnerId }); }
  useJoker(jokerId: string, targetId: string | null = null): void { this.sendToRoom('joker', { jokerId, targetId }); }
  forfeit(): void { this.sendToRoom('forfeit'); }
  /** L'hôte cède sa place à un autre joueur connecté. */
  transferHost(playerId: string): void { this.sendToRoom('host', { playerId }); }
  /** Demande le catalogue de personnalisation (réponse sur `catalog`). */
  loadCatalog(): void { this.sendToRoom('catalog'); }
  /** L'hôte personnalise sa room ; le serveur valide et renvoie le catalogue à jour. */
  customize(c: RoomCustomization): void { this.sendToRoom('customize', c); }

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
    this.catalog.set(null);
    clearSession(); // nouvelle connexion sans jeton = nouvelle identité
    this.loadDaily();
    this.client.deactivate().then(() => this.client.activate());
  }

  playerName(id: string | null | undefined): string {
    if (!id) {
      return '';
    }
    const p = this.room()?.players.find(x => x.id === id);
    return p ? this.displayName(p) : '?';
  }

  /** Nom affiché : un bot est nommé dans la langue choisie (« Aggressive bot 2 »), un humain par son pseudo. */
  displayName(p: PlayerSummary): string {
    if (!p.bot || !p.botProfile) {
      return p.name;
    }
    const suffix = /\s(\d+)$/.exec(p.name)?.[1];
    const base = this.i18n.t('bot.name', { profile: this.i18n.t('bot.' + p.botProfile) });
    return suffix ? `${base} ${suffix}` : base;
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
      this.resuming = false;
      const first = this.me() === null || this.me()!.code !== data.code;
      if (!this.me() || data.version >= this.me()!.version || first) {
        this.me.set(data);
      }
      if (first) {
        this.watchRoom(data.code);
        // Room « contre des bots » : l'hôte vient d'arriver, on ajoute les bots demandés.
        if (data.host && this.pendingBots.length > 0) {
          this.pendingBots.forEach(profile => this.send(`/app/room/${data.code}/bot`, { profile }));
          this.pendingBots = [];
        }
      }
    } else {
      this.acceptRoom(data);
    }
  }

  private watchRoom(code: string): void {
    try {
      this.roomSub?.unsubscribe();
    } catch {
      // abonnement d'une connexion précédente
    }
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
    if (this.resuming && (err.code === 'NOT_IN_ROOM' || err.code === 'ROOM_NOT_FOUND')) {
      // La room a fermé (ou la place a été rendue) pendant l'absence : retour à l'accueil.
      this.resuming = false;
      clearSession();
      this.roomSub?.unsubscribe();
      this.roomSub = null;
      return;
    }
    // Message traduit à partir du code ; à défaut, celui du serveur.
    const text = this.i18n.has('err.' + err.code) ? this.i18n.t('err.' + err.code) : err.message;
    // Personnalisation refusée : le détail du serveur dit quelle règle ou quelle carte pose problème.
    this.showError(err.code === 'BAD_ROOM_SETUP' ? `${text} ${err.message}` : text);
  }

  private showError(text: string): void {
    this.error.set(text);
    clearTimeout(this.errorTimer);
    this.errorTimer = setTimeout(() => this.error.set(null), 4000);
  }
}

/** Jeton de reconnexion de l'onglet (sessionStorage : survit au rechargement, propre à l'onglet). */
interface StoredSession { code: string; token: string; }

const SESSION_KEY = 'blindle.session';

function readSession(): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY);
    return raw ? (JSON.parse(raw) as StoredSession) : null;
  } catch {
    return null;
  }
}

function saveSession(s: { code: string; token: string }): void {
  try {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify({ code: s.code, token: s.token }));
  } catch {
    // stockage indisponible : pas de reconnexion, le jeu marche quand même
  }
}

function clearSession(): void {
  try {
    sessionStorage.removeItem(SESSION_KEY);
  } catch {
    // idem
  }
}
