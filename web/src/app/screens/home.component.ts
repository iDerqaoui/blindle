import { Component, OnDestroy, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../auth/auth.service';
import { GameService } from '../game/game.service';
import { PresetSummary } from '../game/game.types';
import { I18nService, TranslatePipe } from '../i18n/i18n';
import { FEATURES } from '../features';

/** Profils des bots ajoutés pour une partie d'entraînement, dans l'ordre. */
const TRAINING_BOTS = ['aggressive', 'strategist', 'bluffer'];

/**
 * Accueil en deux temps :
 * 1. accueil anime généraliste (mosaïque d'affiches en fond) : choix de l'univers ;
 * 2. page de l'univers (le fond prend son visuel) : choix du mode de jeu, autres univers.
 */
@Component({
  selector: 'app-home',
  imports: [FormsModule, TranslatePipe],
  template: `
    @if (universe(); as u) {
      <!-- Page d'un univers -->
      <section class="universe-page" [style.--universe]="u.art?.color ?? '#6b5bd6'">
        <button class="back-link" (click)="pick(null)">← {{ 'home.allUniverses' | t }}</button>

        <header class="universe-title">
          @if (u.art?.cover; as cover) { <img class="universe-emblem" [src]="cover" alt="" /> }
          <h1>{{ u.name }}</h1>
          <p class="plate">{{ 'home.universeTagline' | t: { n: u.cards } }}</p>
        </header>

        <label class="name-field compact">
          <span>{{ 'home.nickname' | t }}</span>
          <input class="input" [(ngModel)]="name" maxlength="20" [placeholder]="'home.nicknamePlaceholder' | t"
                 autocomplete="nickname" />
        </label>

        <nav class="mode-list" [attr.aria-label]="'home.modes' | t">
          <button class="mode" [disabled]="!hasName()" (click)="create(u.id)">
            <span class="medallion">@if (u.faces[0]; as f) { <img [src]="f" alt="" /> } @else { 👥 }</span>
            <span class="mode-text">
              <strong>{{ 'mode.friends' | t }}</strong>
              <small>{{ 'mode.friendsSub' | t }}</small>
            </span>
          </button>

          <div class="mode" [class.disabled]="!hasName()">
            <span class="medallion">@if (u.faces[1]; as f) { <img [src]="f" alt="" /> } @else { 🤖 }</span>
            <span class="mode-text">
              <strong>{{ 'mode.bots' | t }}</strong>
              <small>{{ 'mode.botsSub' | t }}</small>
            </span>
            <span class="mode-actions">
              @for (n of [1, 2, 3]; track n) {
                <button class="chip" [class.active]="botCount() === n" (click)="botCount.set(n)"
                        [attr.aria-label]="'mode.botCount' | t: { n: n }">{{ n }}</button>
              }
              <button class="btn primary small" [disabled]="!hasName()" (click)="createWithBots(u.id)">
                {{ 'mode.go' | t }}
              </button>
            </span>
          </div>

          <div class="mode" [class.disabled]="!hasName()">
            <span class="medallion">@if (u.faces[2]; as f) { <img [src]="f" alt="" /> } @else { 🔑 }</span>
            <span class="mode-text">
              <strong>{{ 'mode.join' | t }}</strong>
              <small>{{ 'mode.joinSub' | t }}</small>
            </span>
            <span class="mode-actions">
              <input class="input code-input mini" [(ngModel)]="code" maxlength="4" placeholder="CODE"
                     [attr.aria-label]="'home.joinCode' | t" />
              <button class="btn primary small" [disabled]="!canJoin()" (click)="join()">{{ 'home.join' | t }}</button>
            </span>
          </div>

          <a class="mode" href="#who">
            <span class="medallion">@if (u.faces[3]; as f) { <img [src]="f" alt="" /> } @else { ☀️ }</span>
            <span class="mode-text">
              <strong>{{ 'who.tileTitle' | t }}</strong>
              <small>{{ 'who.tileSub' | t }}</small>
            </span>
            <span class="pill pill-points">{{ 'who.tileNew' | t }}</span>
          </a>
        </nav>

        @if (!hasName()) { <p class="hint-line">{{ 'home.needName' | t }}</p> }

        @if (others().length > 0) {
          <aside class="more-box">
            <p class="more-title">{{ 'home.moreTitle' | t }}</p>
            <div class="more-list">
              @for (o of others(); track o.id) {
                <button class="more-item" (click)="pick(o)">
                  <span class="more-icon">@if (o.art?.cover; as c) { <img [src]="c" alt="" /> }</span>
                  <span>{{ o.name }}</span>
                </button>
              }
            </div>
          </aside>
        }
      </section>
    } @else {
      <!-- Accueil anime généraliste -->
      <section class="hero">
        <p class="eyebrow">{{ 'home.eyebrow' | t }}</p>
        <h1>{{ 'home.title1' | t }} <span>{{ 'home.title2' | t }}</span></h1>
        <p>{{ 'home.tagline' | t }}</p>
      </section>

      <a class="who-banner glass" href="#who">
        <span class="who-banner-icon">❓</span>
        <span>
          <strong>{{ 'who.tileTitle' | t }}</strong>
          <small>{{ 'who.bannerSub' | t }}</small>
        </span>
        <span class="btn primary small">{{ 'who.playToday' | t }}</span>
      </a>

      <section class="section">
        <div class="section-head center">
          <h2>{{ 'home.pickUniverse' | t }}</h2>
          <p class="muted">{{ 'home.pickHint' | t }}</p>
        </div>
        @if (universes().length === 0) {
          <div class="empty-state">
            <p><strong>{{ 'home.noneTitle' | t }}</strong></p>
            <p class="muted">{{ 'home.noneHint' | t: { cmd: 'node tools/import-anime.mjs' } }}</p>
          </div>
        } @else {
          <div class="universe-grid">
            @for (u of universes(); track u.id) {
              <button type="button" class="universe" (click)="pick(u)" [style.--universe]="u.art?.color ?? '#6b5bd6'">
                @if (u.art?.cover; as cover) { <img [src]="cover" [alt]="u.name" loading="lazy" /> }
                <span class="universe-name">{{ u.name }}</span>
                <span class="universe-meta">{{ 'home.characters' | t: { n: u.cards } }}</span>
              </button>
            }
          </div>
        }
      </section>

      <section class="section join glass">
        <h2>{{ 'home.joinTitle' | t }}</h2>
        <div class="join-row">
          <input class="input" [(ngModel)]="name" maxlength="20" [placeholder]="'home.nickname' | t"
                 autocomplete="nickname" [attr.aria-label]="'home.nickname' | t" />
          <input class="input code-input" [(ngModel)]="code" maxlength="4" placeholder="CODE"
                 [attr.aria-label]="'home.joinCode' | t" />
          <button class="btn" [disabled]="!canJoin()" (click)="join()">{{ 'home.join' | t }}</button>
        </div>
      </section>

      @if (features.otherModes) {
      <details class="section other-modes glass">
        <summary><h2>{{ 'home.otherModes' | t }}</h2></summary>

        @if (game.daily().length > 0) {
          <h3>{{ 'home.dailyTitle' | t }} <small class="muted">{{ 'home.dailySub' | t }}</small></h3>
          <div class="columns">
            @for (d of game.daily(); track d.presetId) {
              <div class="daily-card">
                <strong>{{ d.themeName }}</strong>
                <span class="muted">{{ i18n.plural('home.dailyGames', d.players) }}</span>
                @if (d.leaderboard.length > 0) {
                  <ol class="ranking compact">
                    @for (e of d.leaderboard.slice(0, 5); track $index) {
                      <li><span>{{ e.name }}</span><span>{{ e.score > 0 ? '+' : '' }}{{ e.score }}</span></li>
                    }
                  </ol>
                }
                <button class="btn" [disabled]="!hasName()" (click)="playDaily(d.presetId)">{{ 'home.play' | t }}</button>
              </div>
            }
          </div>
        }

        <h3>{{ 'home.classicTitle' | t }}</h3>
        <div class="presets">
          @for (p of classic(); track p.id) {
            <button type="button" class="preset" (click)="create(p.id)" [disabled]="!hasName()">
              <strong>{{ p.name }}</strong>
              <span>{{ 'home.classicMeta' | t: { theme: p.themeName, rounds: p.settings.rounds, budget: p.settings.budget } }}</span>
              @if (p.description) { <small>{{ p.description }}</small> }
            </button>
          }
        </div>
        @if (!hasName()) { <p class="muted small">{{ 'home.needName' | t }}</p> }
      </details>
      }
    }
  `,
})
export class HomeComponent implements OnDestroy {
  readonly game = inject(GameService);
  readonly i18n = inject(I18nService);
  readonly features = FEATURES;
  readonly name = signal(localStorageGet('blindle.name'));
  readonly code = signal('');
  readonly botCount = signal(2);
  readonly universe = signal<PresetSummary | null>(null);

  readonly universes = computed(() => this.game.presets().filter(p => p.bidMode === 'drawer'));
  readonly others = computed(() => this.universes().filter(u => u.id !== this.universe()?.id));
  readonly classic = computed(() => this.game.presets().filter(p => p.bidMode !== 'drawer' && !p.daily));
  readonly hasName = computed(() => this.name().trim().length > 0);
  readonly canJoin = computed(() => this.hasName() && this.code().trim().length === 4);

  constructor() {
    // Connecté : le pseudo du compte remplit le champ s'il est vide (le pseudo tapé garde la priorité).
    const auth = inject(AuthService);
    effect(() => {
      const account = auth.me();
      if (account && !untracked(this.name).trim()) {
        this.name.set(account.displayName);
      }
    });
  }

  /** Choisit un univers (ou revient à l'accueil) : le fond de page suit. */
  pick(u: PresetSummary | null): void {
    this.universe.set(u);
    this.game.previewArt.set(u?.art ?? null);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  create(presetId: string): void {
    this.remember();
    this.game.createRoom(this.name().trim(), presetId);
  }

  createWithBots(presetId: string): void {
    this.remember();
    this.game.createRoom(this.name().trim(), presetId, TRAINING_BOTS.slice(0, this.botCount()));
  }

  playDaily(presetId: string): void {
    this.remember();
    this.game.startDaily(this.name().trim(), presetId);
  }

  join(): void {
    this.remember();
    this.game.joinRoom(this.code(), this.name().trim());
  }

  ngOnDestroy(): void {
    // En room, c'est l'univers de la room qui habille le fond.
    this.game.previewArt.set(null);
  }

  private remember(): void {
    try {
      localStorage.setItem('blindle.name', this.name().trim());
    } catch {
      // stockage indisponible : sans importance
    }
  }
}

function localStorageGet(key: string): string {
  try {
    return localStorage.getItem(key) ?? '';
  } catch {
    return '';
  }
}
