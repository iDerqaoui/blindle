import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GameService } from '../game/game.service';
import { TranslatePipe } from '../i18n/i18n';
import { HintComponent } from '../ui/hint.component';
import { TimerComponent } from '../ui/timer.component';
import { CardComponent } from '../ui/card.component';

/** Lot mystère : indices, prix plancher, offre, jokers ; puis révélation et placement de la carte. */
@Component({
  selector: 'app-auction',
  imports: [FormsModule, HintComponent, TimerComponent, CardComponent, TranslatePipe],
  template: `
    @if (game.room(); as room) {
      <section class="panel auction">
        <div class="auction-head">
          <h2>{{ 'auction.round' | t: { n: room.round, total: room.totalRounds } }}</h2>
          <app-timer />
        </div>

        @if (room.lot; as lot) {
          <div class="mystery">
            @for (hint of lot.hints; track hint.level) {
              <app-hint [hint]="hint" class="reveal" />
            }
            @for (hint of me()?.privateHints ?? []; track hint.level) {
              <div class="private-hint reveal">
                <span class="tag">{{ 'auction.spyOnly' | t }}</span>
                <app-hint [hint]="hint" />
              </div>
            }
            <p class="floor">{{ 'auction.tier' | t: { level: lot.hintLevel, max: lot.maxHintLevel } }}
              <strong>{{ lot.floorPrice }} 🪙</strong></p>
          </div>

          @if (room.phase === 'BIDDING') {
            @if (room.bidMode === 'open') {
              <div class="live-bids">
                @for (b of sortedBids(); track b.playerId; let first = $first) {
                  <span class="tag" [class.leader]="first">{{ game.playerName(b.playerId) }} : {{ b.amount }} 🪙</span>
                } @empty { <span class="muted">{{ 'auction.noBids' | t }}</span> }
              </div>
            } @else if (room.bidMode === 'market') {
              <p class="muted">{{ 'auction.market' | t }}</p>
              @if (missed(); as m) { <p class="miss">{{ 'auction.missed' | t: { amount: m } }}</p> }
            } @else {
              <p class="muted">{{ 'auction.acted' | t: { n: lot.actedCount, total: room.players.length } }}</p>
            }
            @if (me(); as me) {
              @if (me.canBid) {
                <div class="bid-row">
                  <input class="input" type="number" [min]="minBid()" [max]="myBudget()" step="10" [(ngModel)]="amount" />
                  <button class="btn primary" (click)="bid()">{{ 'auction.bid' | t }}</button>
                  <button class="btn" (click)="pass()">{{ 'auction.pass' | t }}</button>
                </div>
                @if (me.myBid !== null) {
                  <p>{{ 'auction.yourBid' | t: { amount: me.myBid } }}
                    @if (room.bidMode === 'sealed') { {{ 'auction.editable' | t }} }</p>
                }
                @if (me.passed) { <p class="muted">{{ 'auction.youPass' | t }}</p> }
              } @else {
                <p class="muted">{{ 'auction.teamFull' | t }}</p>
              }
              @if (game.jokersLeft('spy') > 0 || game.jokersLeft('steal') > 0) {
                <div class="jokers">
                  @if (game.jokersLeft('spy') > 0) {
                    <button class="btn small" [disabled]="lot.hintLevel >= lot.maxHintLevel" (click)="game.useJoker('spy')">
                      {{ 'auction.spy' | t: { n: game.jokersLeft('spy') } }}
                    </button>
                  }
                  @if (game.jokersLeft('steal') > 0) {
                    <span class="muted">{{ 'auction.stealInfo' | t: { n: game.jokersLeft('steal') } }}</span>
                  }
                </div>
              }
            }
          }

          @if (lot.cards && (room.phase === 'REVEALED' || room.phase === 'SLOT')) {
            <div class="revealed">
              <p>{{ 'auction.won' | t: { name: game.playerName(lot.winnerId), amount: lot.price } }}</p>
              @for (card of lot.cards; track card.id) { <app-card [card]="card" /> }
              @if (room.verdictMode === 'value') {
                <p>{{ 'auction.value' | t: { value: lotValue() } }} ·
                  {{ (lotValue() >= (lot.price ?? 0) ? 'auction.goodDeal' : 'auction.overpaid') | t }}</p>
              }
              @if (lot.bids && lot.bids.length > 1) {
                <p class="muted">{{ 'auction.bids' | t }} @for (b of lot.bids; track b.playerId) { {{ game.playerName(b.playerId) }} {{ b.amount }}@if (!$last) {, } }</p>
              }
              @if (me()?.mustPlace) {
                <h3>{{ 'auction.where' | t }}</h3>
                <div class="slot-choices">
                  @for (slot of placeable(); track slot.id) {
                    <button class="btn" (click)="game.assignSlot(slot.id)">{{ slot.label }}</button>
                  } @empty {
                    <p class="muted">{{ 'auction.noSlot' | t }}</p>
                    <button class="btn" (click)="game.forfeit()">{{ 'auction.discard' | t }}</button>
                  }
                </div>
              } @else {
                <p class="muted">{{ 'auction.choosing' | t: { name: game.playerName(lot.winnerId) } }}</p>
              }
            </div>
          }
        }
      </section>
    }
`,
})
export class AuctionComponent {
  readonly game = inject(GameService);
  readonly me = this.game.me;
  amount = 0;

  readonly myBudget = computed(() =>
    this.game.room()?.players.find(p => p.id === this.game.myId())?.budget ?? 0);

  readonly placeable = computed(() => {
    const ids = this.me()?.placeableSlots ?? [];
    return (this.game.room()?.slots ?? []).filter(s => ids.includes(s.id));
  });

  readonly sortedBids = computed(() =>
    [...(this.game.room()?.lot?.bids ?? [])].sort((a, b) => b.amount - a.amount));

  /** Mise minimale affichée : plancher, ou meilleure offre + 10 en mode ouvert. */
  readonly minBid = computed(() => {
    const room = this.game.room();
    const floor = room?.lot?.floorPrice ?? 0;
    const top = this.sortedBids()[0]?.amount;
    return room?.bidMode === 'open' && top !== undefined ? Math.max(floor, top + 10) : floor;
  });

  readonly lotValue = computed(() =>
    (this.game.room()?.lot?.cards ?? []).reduce((sum, c) => sum + c.value, 0));

  /** Marché : offre envoyée au palier précédent, refusée (le palier a avancé sans gagnant). */
  readonly missed = signal<number | null>(null);
  private lastTier = '';
  private lastBid: number | null = null;

  private readonly lotKey = signal('');

  constructor() {
    effect(() => {
      const room = this.game.room();
      const tier = `${room?.round}:${room?.lot?.hintLevel}`;
      if (tier !== this.lastTier) {
        const sameRound = this.lastTier.split(':')[0] === String(room?.round);
        this.missed.set(sameRound ? this.lastBid : null);
        this.lastTier = tier;
        this.lastBid = null;
      }
    });
    // Nouveau palier, ou offre dépassée en mode ouvert : on propose la mise minimale.
    effect(() => {
      const room = this.game.room();
      const key = `${room?.round}:${room?.lot?.hintLevel}:${this.sortedBids()[0]?.amount ?? ''}`;
      if (room?.lot && key !== this.lotKey()) {
        this.lotKey.set(key);
        this.amount = this.minBid();
      }
    });
  }

  bid(): void {
    // En solo, l'offre est résolue aussitôt : on retient le montant envoyé pour expliquer un échec.
    this.lastBid = Math.floor(Number(this.amount));
    this.game.bid(this.lastBid);
  }

  pass(): void {
    this.lastBid = null;
    this.game.pass();
  }
}
