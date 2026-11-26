import { Component, computed, effect, inject, signal } from '@angular/core';
import { GameService } from '../game/game.service';

/** Temps restant jusqu'à l'heure de fin envoyée par le serveur. */
@Component({
  selector: 'app-timer',
  template: `
    @if (seconds() !== null) {
      <div class="timer" [class.urgent]="seconds()! <= 5">
        <div class="timer-bar" [style.width.%]="percent()"></div>
        <span class="timer-label">{{ seconds() }} s</span>
      </div>
    }
  `,
})
export class TimerComponent {
  private readonly game = inject(GameService);
  private readonly total = signal(1);

  readonly seconds = computed(() => {
    const ms = this.game.remainingMs();
    return ms === null ? null : Math.ceil(ms / 1000);
  });

  readonly percent = computed(() => {
    const ms = this.game.remainingMs() ?? 0;
    return Math.min(100, (ms / this.total()) * 100);
  });

  constructor() {
    // À chaque nouvelle heure de fin, la durée initiale sert de référence pour la barre.
    let lastDeadline: number | null = null;
    effect(() => {
      const deadline = this.game.room()?.deadline ?? null;
      if (deadline !== lastDeadline) {
        lastDeadline = deadline;
        const ms = this.game.remainingMs();
        this.total.set(Math.max(1, ms ?? 1));
      }
    });
  }
}
