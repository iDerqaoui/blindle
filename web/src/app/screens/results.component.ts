import { Component, computed, inject } from '@angular/core';
import { GameService } from '../game/game.service';
import { TeamBoardComponent } from '../ui/team-board.component';

/** Classement final et équipes. */
@Component({
  selector: 'app-results',
  imports: [TeamBoardComponent],
  template: `
    @if (game.room()?.result; as result) {
      <section class="panel narrow">
        <h2>Résultats</h2>
        <ol class="ranking">
          @for (id of result.ranking; track id; let i = $index) {
            <li [class.winner]="i === 0">
              <span>{{ medal(i) }} {{ game.playerName(id) }}</span>
              <span>{{ result.scores[id] }} {{ unit() }}@if (bonusOf(id) > 0) { <small class="muted"> · +{{ bonusOf(id) }} synergie</small> }</span>
            </li>
          }
        </ol>
        <button class="btn primary" (click)="game.leave()">Nouvelle partie</button>
      </section>
      <app-team-board />
    }
  `,
})
export class ResultsComponent {
  readonly game = inject(GameService);
  readonly unit = computed(() => (this.game.room()?.verdictMode === 'vote' ? 'voix' : 'pts'));

  bonusOf(id: string): number {
    return this.game.room()?.players.find(p => p.id === id)?.bonus ?? 0;
  }

  medal(i: number): string {
    return ['🥇', '🥈', '🥉'][i] ?? `${i + 1}.`;
  }
}
