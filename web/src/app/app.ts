import { Component, computed, inject, signal } from '@angular/core';
import { GameService } from './game/game.service';
import { HomeComponent } from './screens/home.component';
import { LobbyComponent } from './screens/lobby.component';
import { AuctionComponent } from './screens/auction.component';
import { TableComponent } from './screens/table.component';
import { VerdictComponent } from './screens/verdict.component';
import { ResultsComponent } from './screens/results.component';
import { TeamBoardComponent } from './ui/team-board.component';
import { ModalComponent } from './ui/modal.component';

/** Coque de l'application : barre du haut (room, quitter) et écran selon la phase reçue du serveur. */
@Component({
  selector: 'app-root',
  imports: [HomeComponent, LobbyComponent, AuctionComponent, TableComponent, VerdictComponent, ResultsComponent,
    TeamBoardComponent, ModalComponent],
  template: `
    <header class="topbar">
      <button class="brand" (click)="inRoom() ? confirmLeave.set(true) : null" [disabled]="!inRoom()"
              aria-label="Blindle, accueil">
        <span class="brand-mark">B</span><span class="brand-name">Blindle</span>
      </button>

      @if (game.room(); as room) {
        @if (game.me()) {
          <div class="room-info">
            <span class="room-universe">{{ room.themeName }}</span>
            <button class="room-code-chip" (click)="copyCode(room.code)" title="Copier le code">
              {{ room.code }} <small>{{ copied() ? 'copié' : 'copier' }}</small>
            </button>
            <span class="muted small">{{ room.players.length }} joueur{{ room.players.length > 1 ? 's' : '' }}</span>
          </div>
        }
      }

      <div class="topbar-end">
        <span class="status-dot" [class.ok]="game.connected()" [title]="game.connected() ? 'Connecté' : 'Hors ligne'"></span>
        @if (inRoom()) {
          <button class="btn ghost small" (click)="confirmLeave.set(true)">Quitter</button>
        }
      </div>
    </header>

    @if (game.error(); as error) {
      <div class="toast" role="alert">{{ error }}</div>
    }

    <main [class.wide]="screen() === 'table'">
      @switch (screen()) {
        @case ('home') { <app-home /> }
        @case ('lobby') { <app-lobby /> }
        @case ('table') { <app-table /> }
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

    @if (confirmLeave()) {
      <app-modal title="Quitter la partie ?" (closed)="confirmLeave.set(false)">
        <p>Tu quittes la room <strong>{{ game.room()?.code }}</strong>. Tu ne pourras pas reprendre ta place
          (rejoindre à nouveau te compte comme un nouveau joueur).</p>
        <div class="modal-actions">
          <button class="btn" (click)="confirmLeave.set(false)">Rester</button>
          <button class="btn danger" (click)="leave()">Quitter</button>
        </div>
      </app-modal>
    }
  `,
})
export class App {
  readonly game = inject(GameService);
  readonly confirmLeave = signal(false);
  readonly copied = signal(false);

  readonly inRoom = computed(() => !!this.game.room() && !!this.game.me());

  readonly screen = computed(() => {
    const room = this.game.room();
    const phase = room?.phase;
    if (!phase || !this.game.me()) {
      return 'home';
    }
    switch (phase) {
      case 'LOBBY': return 'lobby';
      case 'VERDICT': return 'verdict';
      case 'FINISHED': return 'results';
      default: return room!.bidMode === 'drawer' ? 'table' : 'game';
    }
  });

  readonly log = computed(() => [...(this.game.room()?.log ?? [])].reverse());

  leave(): void {
    this.confirmLeave.set(false);
    this.game.leave();
  }

  copyCode(code: string): void {
    navigator.clipboard?.writeText(code).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    });
  }
}
