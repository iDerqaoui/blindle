import { Component, computed, inject } from '@angular/core';
import { GameService } from '../game/game.service';
import { TranslatePipe } from '../i18n/i18n';
import { TimerComponent } from '../ui/timer.component';
import { BoardComponent } from '../ui/board.component';

/** Vote : chaque joueur choisit la meilleure équipe, sauf la sienne. */
@Component({
  selector: 'app-verdict',
  imports: [TimerComponent, BoardComponent, TranslatePipe],
  template: `
    @if (game.room(); as room) {
      <section class="section">
        <div class="section-head row">
          <div>
            <h1>{{ 'verdict.title' | t }}</h1>
            <p class="muted">{{ 'verdict.sub' | t: { n: room.votesReceived } }}</p>
          </div>
          <app-timer />
        </div>
        @if (game.me()?.votedFor; as voted) {
          <p class="notice">{{ 'verdict.voted' | t: { name: game.playerName(voted) } }}</p>
        }
        <div class="vote-grid">
          @for (p of others(); track p.id) {
            <div class="vote-item" [class.voted]="game.me()?.votedFor === p.id">
              <app-board [player]="p" />
              @if (!game.me()?.votedFor) {
                <button class="btn primary" (click)="game.vote(p.id)">
                  {{ 'verdict.voteFor' | t: { name: game.displayName(p) } }}
                </button>
              }
            </div>
          }
        </div>
        @if (mine(); as me) {
          <h2 class="subtle">{{ 'verdict.yourTeam' | t }}</h2>
          <app-board [player]="me" />
        }
      </section>
    }
  `,
})
export class VerdictComponent {
  readonly game = inject(GameService);
  readonly others = computed(() => (this.game.room()?.players ?? []).filter(p => p.id !== this.game.myId()));
  readonly mine = computed(() => this.game.room()?.players.find(p => p.id === this.game.myId()) ?? null);
}
