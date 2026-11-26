import { Component, computed, inject } from '@angular/core';
import { GameService } from '../game/game.service';
import { CardComponent } from './card.component';
import { PlayerSummary, TeamEntry } from '../game/game.types';

/** Équipes de tous les joueurs, slot par slot ; bouton « Voler » si le joker est disponible. */
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
              <span>
                @if (p.bonus > 0) { <span class="tag bonus">+{{ p.bonus }} synergie</span> }
                <span class="coins">{{ p.budget }} 🪙</span>
              </span>
            </div>
            <ul class="slots">
              @for (slot of slots(); track slot.id) {
                <li>
                  <span class="slot-label">{{ slot.label }}</span>
                  @if (entry(p, slot.id); as e) {
                    <span class="slot-card">
                      @if (e.card) { <app-card [card]="e.card" [compact]="true" /> }
                      @if (canSteal(p)) {
                        <button class="btn small" (click)="game.useJoker('steal', p.id + '/' + slot.id)"
                                [title]="'Payé ' + (e.paid ?? 0) + ' pièces'">Voler</button>
                      }
                    </span>
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

  entry(player: PlayerSummary, slotId: string): TeamEntry | null {
    return player.team.find(e => e.slotId === slotId) ?? null;
  }

  canSteal(owner: PlayerSummary): boolean {
    return owner.id !== this.game.myId()
      && this.game.room()?.phase === 'BIDDING'
      && this.game.jokersLeft('steal') > 0;
  }
}
