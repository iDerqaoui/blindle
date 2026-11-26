import { Component, computed, inject } from '@angular/core';
import { GameService } from './game/game.service';
import { HomeComponent } from './screens/home.component';
import { LobbyComponent } from './screens/lobby.component';
import { AuctionComponent } from './screens/auction.component';
import { VerdictComponent } from './screens/verdict.component';
import { ResultsComponent } from './screens/results.component';
import { TeamBoardComponent } from './ui/team-board.component';

/** Écran affiché selon la phase reçue du serveur. */
@Component({
  selector: 'app-root',
  imports: [HomeComponent, LobbyComponent, AuctionComponent, VerdictComponent, ResultsComponent, TeamBoardComponent],
  template: `
    <header class="topbar">
      <h1>Blindle</h1>
      <span class="status" [class.ok]="game.connected()">{{ game.connected() ? 'connecté' : 'hors ligne' }}</span>
    </header>

    @if (game.error(); as error) {
      <div class="toast" role="alert">{{ error }}</div>
    }

    <main>
      @switch (screen()) {
        @case ('home') { <app-home /> }
        @case ('lobby') { <app-lobby /> }
        @case ('game') {
          <div class="game-layout">
            <app-auction />
            <app-team-board />
          </div>
          <details class="panel log">
            <summary>Journal</summary>
            <ul>@for (line of log(); track $index) { <li>{{ line }}</li> }</ul>
          </details>
        }
        @case ('verdict') { <app-verdict /> }
        @case ('results') { <app-results /> }
      }
    </main>
  `,
})
export class App {
  readonly game = inject(GameService);

  readonly screen = computed(() => {
    const phase = this.game.room()?.phase;
    if (!phase || !this.game.me()) {
      return 'home';
    }
    switch (phase) {
      case 'LOBBY': return 'lobby';
      case 'VERDICT': return 'verdict';
      case 'FINISHED': return 'results';
      default: return 'game';
    }
  });

  readonly log = computed(() => [...(this.game.room()?.log ?? [])].reverse());
}
