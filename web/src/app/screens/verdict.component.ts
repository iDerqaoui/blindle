import { Component, computed, inject } from '@angular/core';
import { GameService } from '../game/game.service';
import { TimerComponent } from '../ui/timer.component';
import { CardComponent } from '../ui/card.component';

/** Vote : chaque joueur choisit la meilleure équipe, sauf la sienne. */
@Component({
  selector: 'app-verdict',
  imports: [TimerComponent, CardComponent],
  template: `
    @if (game.room(); as room) {
      <section class="panel">
        <div class="auction-head">
          <h2>Verdict : vote pour la meilleure équipe</h2>
          <app-timer />
        </div>
        <p class="muted">{{ room.votesReceived }} vote(s) reçu(s)</p>
        @if (game.me()?.votedFor; as voted) {
          <p>Tu as voté pour <strong>{{ game.playerName(voted) }}</strong>. En attente des autres…</p>
        }
        <div class="teams">
          @for (p of others(); track p.id) {
            <div class="team">
              <div class="team-head"><span>{{ p.name }}</span></div>
              <ul class="slots">
                @for (entry of p.team; track entry.slotId) {
                  <li><span class="slot-label">{{ slotLabel(entry.slotId) }}</span>
                    @if (entry.card) { <app-card [card]="entry.card" [compact]="true" /> }</li>
                } @empty { <li class="muted">Équipe vide</li> }
              </ul>
              @if (!game.me()?.votedFor) {
                <button class="btn primary" (click)="game.vote(p.id)">Voter pour {{ p.name }}</button>
              }
            </div>
          }
        </div>
      </section>
    }
  `,
})
export class VerdictComponent {
  readonly game = inject(GameService);
  readonly others = computed(() => (this.game.room()?.players ?? []).filter(p => p.id !== this.game.myId()));

  slotLabel(id: string): string {
    return this.game.room()?.slots.find(s => s.id === id)?.label ?? id;
  }
}
