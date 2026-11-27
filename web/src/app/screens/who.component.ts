import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiFetch } from '../auth/auth.service';
import { I18nService, TranslatePipe } from '../i18n/i18n';

interface LeaderEntry { name: string; score: number; }

interface WhoSummary {
  available: boolean; date: string; universe: string | null; color: string | null;
  started: boolean; finished: boolean; players: number; leaderboard: LeaderEntry[];
}

interface LiveView { index: number; startedAt: number; expiresAt: number; level: number; imageLevel: number; price: number; image: string; }

interface OutcomeView {
  index: number; name: string; image: string; rarity: string; universe: string; points: number;
  bought: boolean; price: number; level: number; called: boolean; jackpot: boolean; skipped: boolean;
}

interface RunView {
  date: string; universe: string; color: string | null; total: number; index: number; budget: number; slots: number;
  prices: number[]; stepMs: number; durationMs: number; serverTime: number;
  live: LiveView | null; last: OutcomeView | null; team: OutcomeView[]; finished: boolean;
  score: number; rank: number | null; share: string | null; outcomes: OutcomeView[] | null;
  leaderboard: LeaderEntry[] | null; roster: string[];
}

const API = '/api/daily/who';

/**
 * Défi du jour « Who's That? Auction » : l'image se précise, le prix monte ; acheter quand on ose,
 * nommer le personnage pour doubler ses points. Le serveur juge tout (prix et palier à son heure) ;
 * le client n'affiche qu'une estimation fluide entre deux réponses.
 */
@Component({
  selector: 'app-who',
  imports: [FormsModule, TranslatePipe],
  template: `
    <section class="who" [style.--universe]="run()?.color ?? summary()?.color ?? '#6b5bd6'">
      <header class="who-head">
        <a class="btn small ghost" href="#">← {{ 'who.back' | t }}</a>
        <div>
          <p class="eyebrow">{{ 'who.eyebrow' | t }} · {{ run()?.date ?? summary()?.date }}</p>
          <h1>{{ 'who.title' | t }} <span>{{ run()?.universe ?? summary()?.universe }}</span></h1>
        </div>
      </header>

      @if (error(); as e) { <p class="who-error">{{ e }}</p> }

      @if (!run()) {
        @if (summary(); as s) {
          @if (!s.available) {
            <div class="panel narrow"><p class="muted">{{ 'who.unavailable' | t }}</p></div>
          } @else {
            <div class="panel narrow who-intro">
              <ul class="who-rules">
                <li>🔍 {{ 'who.rule1' | t }}</li>
                <li>💰 {{ 'who.rule2' | t }}</li>
                <li>⚡ {{ 'who.rule3' | t }}</li>
                <li>🎰 {{ 'who.rule4' | t }}</li>
              </ul>
              <label class="field">{{ 'home.nickname' | t }}
                <input class="input" [(ngModel)]="name" maxlength="20" autocomplete="nickname" />
              </label>
              <button class="btn primary big" [disabled]="!name.trim() || busy()" (click)="start()">
                {{ 'who.start' | t }}
              </button>
              <p class="muted small">{{ 'who.oneTry' | t }} · {{ i18n.plural('home.dailyGames', s.players) }}</p>
            </div>
          }
        } @else {
          <p class="muted">{{ 'admin.loading' | t }}</p>
        }
      } @else {
        @if (run(); as r) {
          <div class="who-hud">
            <span>🃏 {{ Math.min(r.index + 1, r.total) }}/{{ r.total }}</span>
            <span>🪙 {{ r.budget }}</span>
            <span class="who-slots">
              @for (i of slotIndexes(); track i) {
                @if (r.team[i]; as c) {
                  <img [src]="c.image" [alt]="c.name" [class]="'who-slot ' + c.rarity" [title]="c.name" />
                } @else {
                  <span class="who-slot empty"></span>
                }
              }
            </span>
          </div>

          @if (r.live; as live) {
            <div class="who-stage">
              <div class="who-card">
                <img class="who-pixel" [src]="live.image + level()" alt="?" />
                <div class="who-timer"><span [style.width.%]="timeLeftPct()"></span></div>
              </div>
              <div class="who-side">
                <ol class="who-ladder">
                  @for (p of r.prices; track $index) {
                    <li [class.now]="$index === level()" [class.past]="$index < level()">{{ p }}</li>
                  }
                </ol>
                <label class="field">{{ 'who.callIt' | t }}
                  <input class="input" [(ngModel)]="guess" list="who-roster" [placeholder]="'who.callHint' | t"
                         autocomplete="off" />
                </label>
                <datalist id="who-roster">
                  @for (n of r.roster; track n) { <option [value]="n"></option> }
                </datalist>
                <button class="btn primary big" [disabled]="busy() || price() > r.budget" (click)="buy()">
                  {{ 'who.buy' | t: { n: price() } }}
                </button>
                <button class="btn" [disabled]="busy()" (click)="pass()">{{ 'who.pass' | t }}</button>
              </div>
            </div>
          } @else if (!r.finished && r.last; as last) {
            <div class="who-reveal" [class]="'who-reveal ' + (last.bought ? last.rarity : 'missed')">
              <img [src]="last.image" [alt]="last.name" />
              <div>
                <span [class]="'rarity-badge ' + last.rarity">{{ 'rarity.' + last.rarity | t }}</span>
                @if (last.jackpot) { <span class="pill pill-points">🎰 {{ 'who.jackpot' | t }}</span> }
                <h2>{{ last.name }}</h2>
                @if (last.bought) {
                  <p>{{ 'who.got' | t: { name: last.name, n: last.price } }}</p>
                  @if (last.called) { <p class="who-called">⚡ {{ 'who.called' | t: { n: last.points * 2 } }}</p> }
                } @else {
                  <p class="muted">{{ 'who.missed' | t: { n: r.prices[0] } }}</p>
                }
                <button class="btn primary" (click)="next()" [disabled]="busy()">{{ 'who.next' | t }} →</button>
              </div>
            </div>
          }

          @if (r.finished) {
            <div class="who-end">
              <div class="panel">
                <h2>{{ 'who.score' | t: { n: r.score } }}</h2>
                @if (r.rank) { <p class="muted">{{ 'who.rank' | t: { n: r.rank } }}</p> }
                @if (r.share; as share) {
                  <pre class="who-share">{{ share }}</pre>
                  <button class="btn primary" (click)="copy(share)">{{ (copied() ? 'lobby.copied' : 'who.share') | t }}</button>
                }
              </div>
              @if (regrets().length) {
                <div class="panel">
                  <h3>💀 {{ 'who.regrets' | t }}</h3>
                  <ul class="who-regrets">
                    @for (o of regrets(); track o.index) {
                      <li>
                        <img [src]="o.image" [alt]="o.name" />
                        <span><strong>{{ o.name }}</strong> <span [class]="'rarity-badge ' + o.rarity">{{ 'rarity.' + o.rarity | t }}</span></span>
                        <span class="muted small">{{ (o.skipped ? 'who.neverSeen' : 'who.couldHave') | t: { n: r.prices[0] } }}</span>
                      </li>
                    }
                  </ul>
                </div>
              }
              @if (r.leaderboard?.length) {
                <div class="panel">
                  <h3>{{ 'who.leaderboard' | t }}</h3>
                  <ol class="ranking">
                    @for (e of r.leaderboard; track $index) { <li><span>{{ e.name }}</span><span>{{ e.score }}</span></li> }
                  </ol>
                </div>
              }
            </div>
          }
        }
      }
    </section>
  `,
})
export class WhoComponent implements OnDestroy {
  readonly i18n = inject(I18nService);
  readonly Math = Math;
  readonly summary = signal<WhoSummary | null>(null);
  readonly run = signal<RunView | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly copied = signal(false);
  /** Heure serveur estimée, rafraîchie 10 fois par seconde. */
  readonly now = signal(Date.now());
  name = localStorage.getItem('blindle.name') ?? '';
  guess = '';
  private offset = 0;
  private refreshing = false;
  private readonly tick = setInterval(() => this.onTick(), 100);

  readonly slotIndexes = computed(() => Array.from({ length: this.run()?.slots ?? 5 }, (_, i) => i));

  /** Palier estimé (le serveur bride l'image et fixe le prix réel à l'achat). */
  readonly level = computed(() => {
    const r = this.run();
    if (!r?.live) {
      return 0;
    }
    const elapsed = Math.max(0, this.now() - r.live.startedAt);
    return Math.min(r.prices.length - 1, Math.floor(elapsed / r.stepMs));
  });

  readonly price = computed(() => this.run()?.prices[this.level()] ?? 0);

  readonly timeLeftPct = computed(() => {
    const live = this.run()?.live;
    if (!live) {
      return 0;
    }
    const total = live.expiresAt - live.startedAt;
    return Math.max(0, Math.min(100, 100 * (live.expiresAt - this.now()) / total));
  });

  /** Ce qu'on a laissé passer (ou jamais vu), légendaires et épiques d'abord. */
  readonly regrets = computed(() => {
    const order = ['legendary', 'epic', 'rare', 'common'];
    return (this.run()?.outcomes ?? []).filter(o => !o.bought)
      .sort((a, b) => order.indexOf(a.rarity) - order.indexOf(b.rarity));
  });

  constructor() {
    this.load();
  }

  ngOnDestroy(): void {
    clearInterval(this.tick);
  }

  async load(): Promise<void> {
    const res = await fetch(API, { credentials: 'same-origin' });
    if (res.ok) {
      const s = (await res.json()) as WhoSummary;
      this.summary.set(s);
      if (s.started) {
        await this.call('GET', '/run');
      }
    }
  }

  start(): void {
    localStorage.setItem('blindle.name', this.name.trim());
    this.call('POST', '/start', { name: this.name.trim() });
  }

  buy(): void {
    this.call('POST', '/buy', { guess: this.guess.trim() || null });
  }

  pass(): void {
    this.call('POST', '/pass');
  }

  next(): void {
    this.guess = '';
    this.call('POST', '/next');
  }

  copy(text: string): void {
    navigator.clipboard?.writeText(text).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    });
  }

  private onTick(): void {
    this.now.set(Date.now() + this.offset);
    const live = this.run()?.live;
    // Temps écoulé : le serveur fait partir la carte, on va chercher la révélation.
    if (live && this.now() >= live.expiresAt + 150 && !this.refreshing && !this.busy()) {
      this.refreshing = true;
      this.call('GET', '/run').finally(() => (this.refreshing = false));
    }
  }

  private async call(method: string, path: string, body?: object): Promise<void> {
    this.busy.set(true);
    try {
      const res = await apiFetch(API + path, { method, body: body ? JSON.stringify(body) : undefined });
      const data = await res.json();
      if (!res.ok) {
        const key = 'err.' + data.code;
        this.showError(this.i18n.has(key) ? this.i18n.t(key) : data.message);
        if (data.code === 'WHO_NOT_LIVE') {
          await this.call('GET', '/run');
        }
        return;
      }
      const run = data as RunView;
      this.offset = run.serverTime - Date.now();
      this.now.set(run.serverTime);
      this.run.set(run);
    } catch {
      this.showError(this.i18n.t('err.serverDown'));
    } finally {
      this.busy.set(false);
    }
  }

  private showError(text: string): void {
    this.error.set(text);
    setTimeout(() => this.error.set(null), 3000);
  }
}
