import { Component, computed, inject } from '@angular/core';
import { GameService } from '../game/game.service';
import { CardComponent } from './card.component';
import { CardView, PlayerSummary } from '../game/game.types';

/** Équipes de tous les joueurs, slot par slot. */
@Component({
  selector: 'app-team-board',
  imports: [CardComponent],
  template: `
    <section class="panel">
      <h2>Équipes</h2>
      <div class="teams">
        @for (p of players(); track p.id) {
          <div class="team" [class.mine]="p.id === game.myId()">
            <div class="team-head">
              <span>{{ p.name }}@if (p.id === game.myId()) { <em> (toi)</em> }</span>
              <span class="coins">{{ p.budget }} 🪙</span>
            </div>
            <ul class="slots">
              @for (slot of slots(); track slot.id) {
                <li>
                  <span class="slot-label">{{ slot.label }}</span>
                  @if (cardIn(p, slot.id); as card) {
                    <app-card [card]="card" [compact]="true" />
                  } @else {
                    <span class="empty">—</span>
                  }
                </li>
              }
            </ul>
          </div>
        }
      </div>
    </section>
  `,
})
export class TeamBoardComponent {
  readonly game = inject(GameService);
  readonly players = computed(() => this.game.room()?.players ?? []);
  readonly slots = computed(() => this.game.room()?.slots ?? []);

  cardIn(player: PlayerSummary, slotId: string): CardView | null {
    return player.team.find(e => e.slotId === slotId)?.card ?? null;
  }
}
