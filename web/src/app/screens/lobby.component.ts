import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GameService } from '../game/game.service';
import { TranslatePipe } from '../i18n/i18n';
import { RoomSetupComponent } from './room-setup.component';

/** Salle d'attente : univers, code à partager, joueurs et bots, réglages de l'hôte, démarrage. */
@Component({
  selector: 'app-lobby',
  imports: [FormsModule, TranslatePipe, RoomSetupComponent],
  template: `
    @if (game.room(); as room) {
      <section class="lobby-banner" [style.--universe]="room.art?.color ?? '#6b5bd6'">
        @if (room.art?.banner ?? room.art?.cover; as img) { <img [src]="img" alt="" /> }
        <div class="lobby-banner-text">
          <span class="eyebrow">{{ blindBid() ? 'Anime Blind Bid' : room.presetName }}</span>
          <h1>{{ room.themeName }}</h1>
          <div class="code-block">
            <span class="muted">{{ 'lobby.shareCode' | t }}</span>
            <button class="room-code" (click)="copy(room.code)" [title]="'app.copyCode' | t">{{ room.code }}</button>
            <span class="muted small">{{ (copied() ? 'lobby.copied' : 'lobby.clickToCopy') | t }}</span>
          </div>
        </div>
      </section>

      <div class="lobby-grid">
        <section class="panel">
          <h2>{{ 'lobby.players' | t }} <span class="muted">{{ room.players.length }}/8</span></h2>
          <ul class="player-list">
            @for (p of room.players; track p.id) {
              <li>
                <span class="avatar" [style.--hue]="hue(p.id)">{{ p.bot ? '🤖' : p.name.charAt(0).toUpperCase() }}</span>
                <span>{{ game.displayName(p) }}</span>
                @if (p.host) { <span class="pill">{{ 'lobby.host' | t }}</span> }
                @if (p.id === game.myId()) { <span class="pill pill-me">{{ 'lobby.you' | t }}</span> }
                @if (isHost() && !p.bot && !p.host) {
                  <button class="chip small" (click)="game.transferHost(p.id)" [title]="'lobby.makeHostHelp' | t">
                    {{ 'lobby.makeHost' | t }}
                  </button>
                }
              </li>
            }
          </ul>
          @if (isHost() && room.players.length < 8) {
            <div class="bot-row">
              <span class="muted small">{{ 'lobby.addBot' | t }}</span>
              @for (b of game.botProfiles(); track b.profile) {
                <button class="chip" [title]="'bot.help.' + b.profile | t" (click)="game.addBot(b.profile)">
                  🤖 {{ 'bot.' + b.profile | t }}
                </button>
              }
            </div>
          }
        </section>

        <section class="panel">
          <h2>{{ 'lobby.game' | t }}</h2>
          @if (blindBid()) {
            <ul class="rules">
              <li>{{ 'lobby.rule1' | t }}</li>
              <li>{{ 'lobby.rule2' | t }}</li>
              @if (room.rules.includes('seller-collects')) { <li>{{ 'lobby.ruleSale' | t }}</li> }
              @if (room.rules.includes('discard-fee')) {
                <li>{{ 'lobby.ruleDiscard' | t: { n: discardFee() } }}</li>
              } @else {
                <li>{{ 'lobby.rule3' | t }}</li>
              }
              @if (!room.rules.includes('seller-collects')) { <li>{{ 'lobby.ruleGold' | t }}</li> }
              @if (room.verdictMode === 'points') {
                <li>{{ 'lobby.rule4points' | t: { n: room.slots.length } }}</li>
                <li>{{ 'lobby.rule5points' | t }}</li>
              } @else {
                <li>{{ 'lobby.rule4' | t: { n: room.slots.length } }}</li>
              }
            </ul>
          }
          @if (room.customized) {
            <p class="pill pill-points">{{ 'lobby.customized' | t: { n: room.deckSize } }}</p>
          }
          @if (isHost()) {
            <button class="btn small" (click)="setupOpen.set(true)">{{ 'lobby.customize' | t }}</button>
            <div class="settings">
              <label class="field">{{ 'lobby.gold' | t }}
                <input class="input" type="number" min="100" step="50" [(ngModel)]="budget" />
              </label>
              <label class="field">{{ 'lobby.timer' | t }}
                <input class="input" type="number" min="5" max="600" [(ngModel)]="timer" />
              </label>
              @if (!blindBid()) {
                <label class="field">{{ 'lobby.rounds' | t }}
                  <input class="input" type="number" min="1" max="30" [(ngModel)]="rounds" />
                </label>
              }
              <button class="btn small" (click)="save()">{{ 'lobby.apply' | t }}</button>
            </div>
            <button class="btn primary big" (click)="game.start()">{{ 'lobby.start' | t }}</button>
          } @else {
            <p class="muted">{{ 'lobby.waitingHost' | t }}</p>
          }
          @if (disclaimer(); as d) { <p class="disclaimer">{{ d }}</p> }
        </section>
      </div>
      @if (setupOpen() && isHost()) { <app-room-setup (closed)="setupOpen.set(false)" /> }
    }
  `,
})
export class LobbyComponent {
  readonly game = inject(GameService);
  readonly isHost = computed(() => this.game.me()?.host ?? false);
  readonly blindBid = computed(() => this.game.room()?.bidMode === 'drawer');
  readonly copied = signal(false);
  readonly setupOpen = signal(false);
  /** Or payé par le piocheur pour jeter une carte invendue (règle discard-fee). */
  readonly discardFee = computed(() => Number(this.game.room()?.ruleParams?.['discard-fee']?.['amount'] ?? 0));
  readonly preset = computed(() => this.game.presets().find(p => p.id === this.game.room()?.presetId) ?? null);
  readonly disclaimer = computed(() => this.preset()?.disclaimer ?? null);
  budget = 1000;
  rounds = 10;
  timer = 30;

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
