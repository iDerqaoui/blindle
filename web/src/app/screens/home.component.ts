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

    <div class="columns">
      <section class="panel">
        <h2>Créer une partie</h2>
        <div class="presets">
          @for (p of game.presets(); track p.id) {
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

  readonly canCreate = computed(() => this.name().trim().length > 0 && this.presetId() !== null);
  readonly canJoin = computed(() => this.name().trim().length > 0 && this.code().trim().length === 4);

  constructor() {
    effect(() => {
      const presets = this.game.presets();
      if (this.presetId() === null && presets.length > 0) {
        this.presetId.set(presets[0].id);
      }
    });
  }

  create(): void {
    this.remember();
    this.game.createRoom(this.name().trim(), this.presetId()!);
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
