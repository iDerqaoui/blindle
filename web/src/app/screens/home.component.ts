import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GameService } from '../game/game.service';
import { I18nService, TranslatePipe } from '../i18n/i18n';

/**
 * Accueil : pseudo, choix de l'univers (mode principal Anime Blind Bid), rejoindre par code,
 * puis les autres modes (défi du jour, parties à indices).
 */
@Component({
  selector: 'app-home',
  imports: [FormsModule, TranslatePipe],
  template: `
    <section class="hero">
      <h1>{{ 'home.title1' | t }} <span>{{ 'home.title2' | t }}</span></h1>
      <p>{{ 'home.tagline' | t }}</p>
      <label class="name-field">
        <span>{{ 'home.nickname' | t }}</span>
        <input class="input" [(ngModel)]="name" maxlength="20" [placeholder]="'home.nicknamePlaceholder' | t"
               autocomplete="nickname" />
      </label>
    </section>

    <section class="section">
      <div class="section-head">
        <h2>{{ 'home.pickUniverse' | t }}</h2>
        <p class="muted">{{ 'home.pickHint' | t }}</p>
      </div>
      @if (universes().length === 0) {
        <div class="empty-state">
          <p><strong>{{ 'home.noneTitle' | t }}</strong></p>
          <p class="muted">{{ 'home.noneHint' | t: { cmd: 'node tools/import-anilist.mjs' } }}</p>
        </div>
      } @else {
        <div class="universe-grid">
          @for (u of universes(); track u.id) {
            <button type="button" class="universe" [class.selected]="selected() === u.id" (click)="selected.set(u.id)"
                    [style.--universe]="u.art?.color ?? '#6b5bd6'">
              @if (u.art?.cover; as cover) { <img [src]="cover" [alt]="u.name" loading="lazy" /> }
              <span class="universe-name">{{ u.name }}</span>
              <span class="universe-meta">{{ 'home.characters' | t: { n: u.cards } }}</span>
            </button>
          }
        </div>
        <div class="cta-row">
          <button class="btn primary big" [disabled]="!hasName() || !selected()" (click)="create(selected()!)">
            {{ 'home.create' | t }}
          </button>
          @if (!hasName()) { <span class="muted">{{ 'home.needName' | t }}</span> }
        </div>
      }
    </section>

    <section class="section join">
      <h2>{{ 'home.joinTitle' | t }}</h2>
      <div class="join-row">
        <input class="input code-input" [(ngModel)]="code" maxlength="4" placeholder="CODE"
               [attr.aria-label]="'home.joinCode' | t" />
        <button class="btn" [disabled]="!canJoin()" (click)="join()">{{ 'home.join' | t }}</button>
      </div>
    </section>

    <details class="section other-modes">
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
    </details>
  `,
})
export class HomeComponent {
  readonly game = inject(GameService);
  readonly i18n = inject(I18nService);
  readonly name = signal(localStorageGet('blindle.name'));
  readonly code = signal('');
  readonly selected = signal<string | null>(null);

  readonly universes = computed(() => this.game.presets().filter(p => p.bidMode === 'drawer'));
  readonly classic = computed(() => this.game.presets().filter(p => p.bidMode !== 'drawer' && !p.daily));
  readonly hasName = computed(() => this.name().trim().length > 0);
  readonly canJoin = computed(() => this.hasName() && this.code().trim().length === 4);

  constructor() {
    effect(() => {
      const first = this.universes()[0];
      if (this.selected() === null && first) {
        this.selected.set(first.id);
      }
    });
  }

  create(presetId: string): void {
    this.remember();
    this.game.createRoom(this.name().trim(), presetId);
  }

  playDaily(presetId: string): void {
    this.remember();
    this.game.startDaily(this.name().trim(), presetId);
  }

  join(): void {
    this.remember();
    this.game.joinRoom(this.code(), this.name().trim());
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
