import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiFetch } from '../auth/auth.service';
import { I18nService, TranslatePipe } from '../i18n/i18n';

interface LeaderEntry { name: string; score: number; }

interface WhoSummary {
  available: boolean; date: string; universe: string | null; color: string | null;
  started: boolean; finished: boolean; players: number; leaderboard: LeaderEntry[];
  /** Mon résultat du jour, déjà enregistré : on ne rejoue pas. */
  mine: { score: number; rank: number; streak: number; share: string } | null;
}

interface LiveView {
  index: number; level: number; imageLevel: number; price: number; image: string;
  /** Mauvais noms déjà proposés : chacun a rendu l'image plus nette d'un palier. */
  wrong: string[];
}

interface OutcomeView {
  index: number; name: string; image: string; rarity: string; universe: string; points: number;
  bought: boolean; price: number; level: number; called: boolean; jackpot: boolean; skipped: boolean; misses: number;
}

interface RunView {
  date: string; universe: string; color: string | null; total: number; index: number; budget: number; slots: number;
  prices: number[]; serverTime: number;
  live: LiveView | null; last: OutcomeView | null; team: OutcomeView[]; finished: boolean;
  score: number; rank: number | null; streak: number; share: string | null; outcomes: OutcomeView[] | null;
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
          } @else if (s.mine; as mine) {
            <div class="who-end">
              <div class="panel narrow">
                <p class="eyebrow">{{ 'who.alreadyPlayed' | t }}</p>
                <h2>{{ 'who.score' | t: { n: mine.score } }}</h2>
                <p class="muted">{{ 'who.rank' | t: { n: mine.rank } }} · {{ i18n.plural('home.dailyGames', s.players) }}</p>
                @if (mine.streak >= 2) { <p class="who-streak">🔥 {{ 'who.streak' | t: { n: mine.streak } }}</p> }
                <pre class="who-share">{{ mine.share }}</pre>
                <button class="btn primary" (click)="copy(mine.share)">{{ (copied() ? 'lobby.copied' : 'who.share') | t }}</button>
                <p class="muted small">{{ 'who.comeBack' | t }}</p>
              </div>
              @if (s.leaderboard.length) {
                <div class="panel narrow">
                  <h3>{{ 'who.leaderboard' | t }}</h3>
                  <ol class="ranking">
                    @for (e of s.leaderboard; track $index) { <li><span>{{ e.name }}</span><span>{{ e.score }}</span></li> }
                  </ol>
                </div>
              }
            </div>
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
                <img class="who-blur" [class.shake]="shake()" [src]="live.image + level()" alt="?" />
              </div>
              <div class="who-side">
                <ol class="who-ladder">
                  @for (p of r.prices; track $index) {
                    <li [class.now]="$index === level()" [class.past]="$index < level()">{{ p }}</li>
                  }
                </ol>
                <div class="who-guess">
                  <label class="field" for="who-guess">⚡ {{ 'who.callIt' | t }}</label>
                  <input id="who-guess" class="input" [ngModel]="guess()" (ngModelChange)="onType($event)"
                         [placeholder]="'who.callHint' | t" autocomplete="off" spellcheck="false"
                         role="combobox" aria-autocomplete="list" [attr.aria-expanded]="suggestions().length > 0"
                         (keydown)="onKey($event)" (blur)="closeSoon()" />
                  @if (suggestions().length) {
                    <ul class="who-suggest" role="listbox">
                      @for (n of suggestions(); track n; let i = $index) {
                        <li role="option" [class.active]="i === active()" [attr.aria-selected]="i === active()"
                            (mousedown)="$event.preventDefault(); pick(n)" (mouseenter)="active.set(i)">
                          <span [innerHTML]="highlight(n)"></span>
                        </li>
                      }
                    </ul>
                  }
                  @if (live.wrong.length) {
                    <p class="who-wrong">
                      @for (w of live.wrong; track w) { <span class="pill">✕ {{ w }}</span> }
                    </p>
                  }
                </div>
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
                @if (r.streak >= 2) { <p class="who-streak">🔥 {{ 'who.streak' | t: { n: r.streak } }}</p> }
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
export class WhoComponent {
  readonly i18n = inject(I18nService);
  readonly Math = Math;
  readonly summary = signal<WhoSummary | null>(null);
  readonly run = signal<RunView | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly copied = signal(false);
  name = localStorage.getItem('blindle.name') ?? '';
  /** Texte tapé dans « Call it! » : les suggestions n'apparaissent qu'à partir de 2 lettres. */
  readonly guess = signal('');
  readonly active = signal(0);
  readonly shake = signal(false);
  private open = signal(false);

  readonly slotIndexes = computed(() => Array.from({ length: this.run()?.slots ?? 5 }, (_, i) => i));

  /** Palier : un par mauvais nom, le temps ne compte pas (le serveur bride l'image et fixe le prix). */
  readonly level = computed(() => this.run()?.live?.level ?? 0);

  /** Noms de l'univers qui correspondent à la saisie : début d'un mot d'abord, 6 au plus. */
  readonly suggestions = computed(() => {
    const r = this.run();
    const q = norm(this.guess());
    if (!r?.live || !this.open() || q.length < 2) {
      return [];
    }
    const tried = new Set(r.live.wrong.map(norm));
    const starts: string[] = [];
    const contains: string[] = [];
    for (const n of r.roster) {
      const k = norm(n);
      if (tried.has(k)) {
        continue;
      }
      if (k.startsWith(q) || n.split(/[\s\-·.]+/).some(w => norm(w).startsWith(q))) {
        starts.push(n);
      } else if (k.includes(q)) {
        contains.push(n);
      }
    }
    return [...starts, ...contains].slice(0, 6);
  });

  readonly price = computed(() => this.run()?.prices[this.level()] ?? 0);

  /** Ce qu'on a laissé passer (ou jamais vu), légendaires et épiques d'abord. */
  readonly regrets = computed(() => {
    const order = ['legendary', 'epic', 'rare', 'common'];
    return (this.run()?.outcomes ?? []).filter(o => !o.bought)
      .sort((a, b) => order.indexOf(a.rarity) - order.indexOf(b.rarity));
  });

  constructor() {
    this.load();
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
    this.call('POST', '/buy');
  }

  onType(value: string): void {
    this.guess.set(value);
    this.active.set(0);
    this.open.set(true);
  }

  onKey(e: KeyboardEvent): void {
    const list = this.suggestions();
    if (e.key === 'ArrowDown' && list.length) {
      e.preventDefault();
      this.active.set((this.active() + 1) % list.length);
    } else if (e.key === 'ArrowUp' && list.length) {
      e.preventDefault();
      this.active.set((this.active() - 1 + list.length) % list.length);
    } else if (e.key === 'Enter' && list.length) {
      e.preventDefault();
      this.pick(list[Math.min(this.active(), list.length - 1)]);
    } else if (e.key === 'Escape') {
      this.open.set(false);
    }
  }

  closeSoon(): void {
    setTimeout(() => this.open.set(false), 120);
  }

  /** Propose ce nom : juste = achat ×2 ; faux = l'image se précise (et le prix monte). */
  async pick(name: string): Promise<void> {
    this.open.set(false);
    this.guess.set('');
    const before = this.run()?.live?.wrong.length ?? 0;
    await this.call('POST', '/guess', { name });
    const live = this.run()?.live;
    if (live && live.wrong.length > before) {
      this.shake.set(true);
      setTimeout(() => this.shake.set(false), 450);
    }
  }

  /** Met en gras la partie tapée (texte échappé : les noms viennent du contenu). */
  highlight(name: string): string {
    const esc = (t: string) => t.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
    const q = this.guess().trim();
    const i = q ? name.toLowerCase().indexOf(q.toLowerCase()) : -1;
    if (i < 0) {
      return esc(name);
    }
    return esc(name.slice(0, i)) + '<b>' + esc(name.slice(i, i + q.length)) + '</b>' + esc(name.slice(i + q.length));
  }

  pass(): void {
    this.call('POST', '/pass');
  }

  next(): void {
    this.guess.set('');
    this.call('POST', '/next');
  }

  copy(text: string): void {
    navigator.clipboard?.writeText(text).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    });
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
      this.run.set(data as RunView);
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

/** Comparaison tolérante : sans accents, casse ni ponctuation. */
function norm(s: string): string {
  return s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/[^\p{L}\p{N}]/gu, '');
}
