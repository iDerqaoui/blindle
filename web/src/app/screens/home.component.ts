import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GameService } from '../game/game.service';

/** Accueil : créer une room (choix du preset) ou en rejoindre une avec un code. */
@Component({
  selector: 'app-home',
  imports: [FormsModule],
  template: `
    <section class="panel narrow">
      <h2>Ton pseudo</h2>
      <input class="input" [(ngModel)]="name" maxlength="20" placeholder="Ex. Yassine" autocomplete="nickname" />
    </section>

    @if (game.daily().length > 0) {
      <section class="panel daily">
        <h2>☀️ Défi du jour <small class="muted">· seul contre le marché</small></h2>
        <p class="muted">Mêmes lots pour tout le monde aujourd'hui. Ton offre doit atteindre un prix du marché caché, plus bas aux premiers indices. Score = valeur de ton équipe − pièces dépensées.</p>
        <div class="columns">
          @for (d of game.daily(); track d.presetId) {
            <div class="daily-card">
              <strong>{{ d.themeName }}</strong>
              <span class="muted">{{ d.players }} partie(s) aujourd'hui</span>
              @if (d.leaderboard.length > 0) {
                <ol class="ranking compact">
                  @for (e of d.leaderboard.slice(0, 5); track $index) {
                    <li><span>{{ e.name }}</span><span>{{ e.score > 0 ? '+' : '' }}{{ e.score }}</span></li>
                  }
                </ol>
              }
              <button class="btn primary" [disabled]="!hasName()" (click)="playDaily(d.presetId)">Jouer</button>
            </div>
          }
        </div>
      </section>
    }

    <div class="columns">
      <section class="panel">
        <h2>Créer une partie</h2>
        <div class="presets">
          @for (p of multiPresets(); track p.id) {
            <button type="button" class="preset" [class.selected]="presetId() === p.id" (click)="presetId.set(p.id)">
              <strong>{{ p.name }}</strong>
              <span>{{ p.themeName }} · {{ p.settings.rounds }} rounds · {{ p.settings.budget }} 🪙</span>
              @if (p.description) { <small>{{ p.description }}</small> }
            </button>
          } @empty {
            <p class="muted">Chargement des presets…</p>
          }
        </div>
        <button class="btn primary" [disabled]="!canCreate()" (click)="create()">Créer la room</button>
      </section>

      <section class="panel">
        <h2>Rejoindre</h2>
        <input class="input code-input" [(ngModel)]="code" maxlength="4" placeholder="CODE" />
        <button class="btn" [disabled]="!canJoin()" (click)="join()">Rejoindre</button>
      </section>
    </div>
  `,
})
export class HomeComponent {
  readonly game = inject(GameService);
  readonly name = signal(localStorageGet('blindle.name'));
  readonly code = signal('');
  readonly presetId = signal<string | null>(null);

  readonly multiPresets = computed(() => this.game.presets().filter(p => !p.daily));
  readonly hasName = computed(() => this.name().trim().length > 0);
  readonly canCreate = computed(() => this.name().trim().length > 0 && this.presetId() !== null);
  readonly canJoin = computed(() => this.name().trim().length > 0 && this.code().trim().length === 4);

  constructor() {
    effect(() => {
      const presets = this.multiPresets();
      if (this.presetId() === null && presets.length > 0) {
        this.presetId.set(presets[0].id);
      }
    });
  }

  create(): void {
    this.remember();
    this.game.createRoom(this.name().trim(), this.presetId()!);
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
