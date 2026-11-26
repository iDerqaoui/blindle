import { Component, computed, inject, signal } from '@angular/core';
import { GameService } from './game/game.service';
import { I18nService, LANGS, Lang, TranslatePipe } from './i18n/i18n';
import { HomeComponent } from './screens/home.component';
import { LobbyComponent } from './screens/lobby.component';
import { AuctionComponent } from './screens/auction.component';
import { TableComponent } from './screens/table.component';
import { VerdictComponent } from './screens/verdict.component';
import { ResultsComponent } from './screens/results.component';
import { TeamBoardComponent } from './ui/team-board.component';
import { ModalComponent } from './ui/modal.component';

/** Coque : barre du haut (room, langue, quitter) et écran selon la phase reçue du serveur. */
@Component({
  selector: 'app-root',
  imports: [HomeComponent, LobbyComponent, AuctionComponent, TableComponent, VerdictComponent, ResultsComponent,
    TeamBoardComponent, ModalComponent, TranslatePipe],
  template: `
    <header class="topbar">
      <button class="brand" (click)="inRoom() ? confirmLeave.set(true) : null" [disabled]="!inRoom()"
              [attr.aria-label]="'app.home' | t">
        <span class="brand-mark">B</span><span class="brand-name">Blindle</span>
      </button>

      @if (game.room(); as room) {
        @if (game.me()) {
          <div class="room-info">
            <span class="room-universe">{{ room.themeName }}</span>
            <button class="room-code-chip" (click)="copyCode(room.code)" [title]="'app.copyCode' | t">
              {{ room.code }} <small>{{ (copied() ? 'app.copied' : 'app.copy') | t }}</small>
            </button>
            <span class="muted small">{{ i18n.plural('app.players', room.players.length) }}</span>
          </div>
        }
      }

      <div class="topbar-end">
        <label class="lang-select">
          <span class="sr-only">{{ 'app.language' | t }}</span>
          <span aria-hidden="true">🌐</span>
          <select [value]="i18n.lang()" (change)="setLang($event)" [attr.aria-label]="'app.language' | t">
            @for (l of langs; track l.code) {
              <option [value]="l.code" [selected]="l.code === i18n.lang()">{{ l.label }}</option>
            }
          </select>
        </label>
        <span class="status-dot" [class.ok]="game.connected()"
              [title]="(game.connected() ? 'app.connected' : 'app.offline') | t"></span>
        @if (inRoom()) {
          <button class="btn ghost small" (click)="confirmLeave.set(true)">{{ 'app.leave' | t }}</button>
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
            <summary>{{ 'app.log' | t }}</summary>
            <ul>@for (line of log(); track $index) { <li>{{ line }}</li> }</ul>
          </details>
        }
        @case ('verdict') { <app-verdict /> }
        @case ('results') { <app-results /> }
      }
    </main>

    @if (confirmLeave()) {
      <app-modal [title]="'leave.title' | t" (closed)="confirmLeave.set(false)">
        <p>{{ 'leave.body' | t: { code: game.room()?.code } }}</p>
        <div class="modal-actions">
          <button class="btn" (click)="confirmLeave.set(false)">{{ 'leave.stay' | t }}</button>
          <button class="btn danger" (click)="leave()">{{ 'leave.confirm' | t }}</button>
        </div>
      </app-modal>
    }
  `,
})
export class App {
  readonly game = inject(GameService);
  readonly i18n = inject(I18nService);
  readonly langs = LANGS;
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

  setLang(event: Event): void {
    this.i18n.use((event.target as HTMLSelectElement).value as Lang);
  }

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
