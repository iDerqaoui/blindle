import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GameService } from '../game/game.service';

/** Salle d'attente : code à partager, joueurs, réglages de l'hôte, démarrage. */
@Component({
  selector: 'app-lobby',
  imports: [FormsModule],
  template: `
    @if (game.room(); as room) {
      <section class="panel narrow center">
        <p class="muted">Code de la room</p>
        <p class="room-code">{{ room.code }}</p>
        <button class="btn small" (click)="copy(room.code)">{{ copied() ? 'Copié !' : 'Copier le code' }}</button>
        <p class="muted">{{ room.presetName }} · {{ room.themeName }}</p>
      </section>

      <div class="columns">
        <section class="panel">
          <h2>Joueurs ({{ room.players.length }}/8)</h2>
          <ul class="player-list">
            @for (p of room.players; track p.id) {
              <li>@if (p.bot) { 🤖 }{{ p.name }} @if (p.host) { <span class="tag">hôte</span> } @if (p.id === game.myId()) { <em>(toi)</em> }</li>
            }
          </ul>
          @if (isHost() && room.players.length < 8) {
            <div class="bot-row">
              <span class="muted">Ajouter un bot :</span>
              @for (b of game.botProfiles(); track b.profile) {
                <button class="btn small" [title]="botHelp[b.profile]" (click)="game.addBot(b.profile)">🤖 {{ b.label }}</button>
              }
            </div>
          }
          @if (room.players.length < 3) {
            <p class="muted">Il faut au moins 3 joueurs pour lancer la partie.</p>
          }
        </section>

        <section class="panel">
          <h2>Réglages</h2>
          @if (isHost()) {
            <label class="field">Budget <input class="input" type="number" min="100" step="50" [(ngModel)]="budget" /></label>
            <label class="field">Rounds <input class="input" type="number" min="1" max="30" [(ngModel)]="rounds" /></label>
            <label class="field">Timer d'enchère (s) <input class="input" type="number" min="5" max="600" [(ngModel)]="timer" /></label>
            <button class="btn small" (click)="save()">Appliquer</button>
            <button class="btn primary" [disabled]="room.players.length < 3" (click)="game.start()">Lancer la partie</button>
          } @else {
            <p>Partie en {{ room.totalRounds }} rounds.</p>
            <p class="muted">En attente de l'hôte…</p>
          }
        </section>
      </div>
    }
  `,
})
export class LobbyComponent {
  readonly game = inject(GameService);
  readonly isHost = computed(() => this.game.me()?.host ?? false);
  readonly copied = signal(false);
  budget = 1000;
  rounds = 10;
  timer = 30;

  /** Personnalités des bots (§7), en infobulle. */
  readonly botHelp: Record<string, string> = {
    aggressive: 'Mise dès le premier indice, jusqu’à 120 % de son estimation',
    stingy: 'Attend le dernier indice, plafonne à 80 %',
    fan: 'Surenchérit sur son tag favori',
    bluffer: 'Fait monter les prix puis se retire',
    strategist: 'Garde du budget pour ses slots vides',
  };

  save(): void {
    this.game.updateSettings({ budget: this.budget, rounds: this.rounds, bidTimerSec: this.timer });
  }

  copy(code: string): void {
    navigator.clipboard?.writeText(code).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    });
  }
}
