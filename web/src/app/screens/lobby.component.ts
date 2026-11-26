import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GameService } from '../game/game.service';

/** Salle d'attente : univers, code à partager, joueurs et bots, réglages de l'hôte, démarrage. */
@Component({
  selector: 'app-lobby',
  imports: [FormsModule],
  template: `
    @if (game.room(); as room) {
      <section class="lobby-banner" [style.--universe]="room.art?.color ?? '#6b5bd6'">
        @if (room.art?.banner ?? room.art?.cover; as img) { <img [src]="img" alt="" /> }
        <div class="lobby-banner-text">
          <span class="eyebrow">{{ blindBid() ? 'Anime Blind Bid' : room.presetName }}</span>
          <h1>{{ room.themeName }}</h1>
          <div class="code-block">
            <span class="muted">Code à partager</span>
            <button class="room-code" (click)="copy(room.code)" title="Copier le code">{{ room.code }}</button>
            <span class="muted small">{{ copied() ? 'Copié !' : 'Clique pour copier' }}</span>
          </div>
        </div>
      </section>

      <div class="lobby-grid">
        <section class="panel">
          <h2>Joueurs <span class="muted">{{ room.players.length }}/8</span></h2>
          <ul class="player-list">
            @for (p of room.players; track p.id) {
              <li>
                <span class="avatar" [style.--hue]="hue(p.id)">{{ p.bot ? '🤖' : p.name.charAt(0).toUpperCase() }}</span>
                <span>{{ p.name }}</span>
                @if (p.host) { <span class="pill">hôte</span> }
                @if (p.id === game.myId()) { <span class="pill pill-me">toi</span> }
              </li>
            }
          </ul>
          @if (isHost() && room.players.length < 8) {
            <div class="bot-row">
              <span class="muted small">Ajouter un bot</span>
              @for (b of game.botProfiles(); track b.profile) {
                <button class="chip" [title]="botHelp[b.profile]" (click)="game.addBot(b.profile)">🤖 {{ b.label }}</button>
              }
            </div>
          }
        </section>

        <section class="panel">
          <h2>Partie</h2>
          @if (blindBid()) {
            <ul class="rules">
              <li>Chaque joueur pioche à tour de rôle : <strong>lui seul voit le personnage</strong>.</li>
              <li>Il ouvre l'enchère, les autres <strong>surenchérissent ou laissent</strong>, à l'aveugle.</li>
              <li>La carte rejoint le tableau du gagnant. Personne n'en veut : <strong>poubelle</strong>, visible par tous.</li>
              <li>Quand les équipes de {{ room.slots.length }} sont complètes, on vote pour la meilleure.</li>
            </ul>
          }
          @if (isHost()) {
            <div class="settings">
              <label class="field">Or par joueur
                <input class="input" type="number" min="100" step="50" [(ngModel)]="budget" />
              </label>
              <label class="field">Temps d'enchère (s)
                <input class="input" type="number" min="5" max="600" [(ngModel)]="timer" />
              </label>
              @if (!blindBid()) {
                <label class="field">Rounds
                  <input class="input" type="number" min="1" max="30" [(ngModel)]="rounds" />
                </label>
              }
              <button class="btn small" (click)="save()">Appliquer</button>
            </div>
            <button class="btn primary big" (click)="game.start()">Lancer la partie</button>
          } @else {
            <p class="muted">En attente du lancement par l'hôte…</p>
          }
          @if (disclaimer(); as d) { <p class="disclaimer">{{ d }}</p> }
        </section>
      </div>
    }
  `,
})
export class LobbyComponent {
  readonly game = inject(GameService);
  readonly isHost = computed(() => this.game.me()?.host ?? false);
  readonly blindBid = computed(() => this.game.room()?.bidMode === 'drawer');
  readonly copied = signal(false);
  readonly preset = computed(() => this.game.presets().find(p => p.id === this.game.room()?.presetId) ?? null);
  readonly disclaimer = computed(() => this.preset()?.disclaimer ?? null);
  budget = 1000;
  rounds = 10;
  timer = 30;

  /** Personnalités des bots (§7), en infobulle. */
  readonly botHelp: Record<string, string> = {
    aggressive: 'Mise vite et fort, jusqu’à 120 % de son estimation',
    stingy: 'Attend le dernier moment, plafonne à 80 %',
    fan: 'Surenchérit sur son type de personnage favori',
    bluffer: 'Fait monter les prix puis se retire',
    strategist: 'Garde de l’or pour compléter son équipe',
  };

  constructor() {
    // Réglages affichés = ceux du preset, une fois connus.
    let loaded = false;
    effect(() => {
      const p = this.preset();
      if (p && !loaded) {
        loaded = true;
        this.budget = p.settings.budget;
        this.rounds = p.settings.rounds;
        this.timer = p.settings.bidTimerSec;
      }
    });
  }

  save(): void {
    this.game.updateSettings({
      budget: this.budget, bidTimerSec: this.timer,
      ...(this.blindBid() ? {} : { rounds: this.rounds }),
    });
  }

  hue(id: string): number {
    return [...id].reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 360, 7);
  }

  copy(code: string): void {
    navigator.clipboard?.writeText(code).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    });
  }
}
