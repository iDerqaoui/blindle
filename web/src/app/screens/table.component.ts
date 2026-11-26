import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GameService } from '../game/game.service';
import { TranslatePipe } from '../i18n/i18n';
import { BoardComponent } from '../ui/board.component';
import { GameCardComponent } from '../ui/game-card.component';
import { ModalComponent } from '../ui/modal.component';
import { TimerComponent } from '../ui/timer.component';

/**
 * Table du mode Anime Blind Bid : pioche animée, carte visible du seul piocheur, enchère
 * (ouvrir / surenchérir / laisser), tableaux des joueurs et poubelle consultable.
 * Le client n'affiche que ce que le serveur lui envoie.
 */
@Component({
  selector: 'app-table',
  imports: [FormsModule, BoardComponent, GameCardComponent, ModalComponent, TimerComponent, TranslatePipe],
  template: `
    @if (game.room(); as room) {
      <div class="table" [style.--universe]="room.art?.color ?? '#6b5bd6'">
        <section class="stage">
          <div class="stage-top">
            <span class="round-chip">{{ 'table.card' | t: { n: room.round } }}</span>
            <app-timer />
          </div>

          <div class="stage-cards">
            <div class="deck" aria-hidden="true">
              <app-game-card [art]="room.art" [universe]="room.themeName" size="lg" />
              <app-game-card class="deck-under" [art]="room.art" [universe]="room.themeName" size="lg" />
            </div>

            @for (key of drawKey(); track key) {
              <div class="drawn">
                <app-game-card [card]="shownCard()" [art]="room.art" [universe]="room.themeName"
                               [faceUp]="!!shownCard()" size="lg" />
              </div>
            }

            <button class="trash-pile" (click)="trashOpen.set(true)"
                    [attr.aria-label]="'table.trashAria' | t: { n: room.trash.length }">
              @if (lastTrashed(); as top) {
                <app-game-card class="trash-top" [card]="top" [faceUp]="true" size="sm" />
                <span class="trash-count">{{ room.trash.length }}</span>
              } @else {
                <span class="trash-empty">🗑️</span>
              }
              <span>{{ 'table.trash' | t }}</span>
            </button>
          </div>

          <div class="stage-info">
            @switch (situation()) {
              @case ('my-draw') {
                <p class="headline">{{ 'table.myDraw' | t: { name: game.me()?.drawnCard?.name } }}</p>
                <p class="muted">{{ 'table.myDrawHint' | t }}</p>
              }
              @case ('waiting-drawer') {
                <p class="headline">{{ 'table.waitingDrawer' | t: { name: game.playerName(room.drawerId) } }}</p>
                <p class="muted">{{ 'table.waitingDrawerHint' | t }}</p>
              }
              @case ('bidding') {
                @if (leader(); as l) {
                  <p class="headline">{{ 'table.leading' | t: { name: game.playerName(l.playerId), amount: l.amount } }}</p>
                } @else {
                  <p class="headline">{{ 'table.drawerPassed' | t }}</p>
                }
                <p class="muted">{{ 'table.biddingHint' | t: { name: game.playerName(room.drawerId) } }}</p>
              }
              @case ('revealed') {
                <p class="headline">{{ 'table.won' | t: { name: game.playerName(room.lot?.winnerId),
                    card: room.lot?.cards?.[0]?.name, amount: room.lot?.price } }}</p>
                <p class="muted">{{ 'table.wonHint' | t }}</p>
              }
              @default { <p class="muted">{{ 'table.next' | t }}</p> }
            }
          </div>

          @if (room.phase === 'BIDDING' && game.me(); as me) {
            <div class="bid-bar">
              @if (!me.canBid) {
                <p class="muted">{{ 'table.teamFull' | t }}</p>
              } @else if (situation() === 'waiting-drawer') {
                <p class="muted">{{ 'table.waitDrawer' | t }}</p>
              } @else if (iLead()) {
                <p class="lead-note">{{ 'table.youLead' | t: { amount: leader()?.amount } }}</p>
              } @else if (me.passed) {
                <p class="muted">{{ 'table.youPassed' | t }}</p>
                <button class="btn small" (click)="bid()">{{ 'table.comeBack' | t: { amount: amount } }}</button>
              } @else {
                <div class="quick">
                  @for (step of [10, 50, 100]; track step) {
                    <button class="chip" (click)="raise(step)">+{{ step }}</button>
                  }
                </div>
                <div class="bid-input">
                  <input class="input" type="number" [min]="minBid()" [max]="myGold()" step="10"
                         [(ngModel)]="amount" [attr.aria-label]="'table.amount' | t" />
                  <span class="suffix">{{ 'table.gold' | t }}</span>
                </div>
                <button class="btn primary" [disabled]="amount < minBid() || amount > myGold()" (click)="bid()">
                  {{ (situation() === 'my-draw' ? 'table.open' : 'table.bid') | t: { amount: amount } }}
                </button>
                <button class="btn" (click)="game.pass()"
                        [attr.title]="situation() === 'my-draw' && discardFee() ? ('table.passDrawHint' | t) : null">
                  {{ (situation() === 'my-draw' && discardFee() ? 'table.passDraw' : 'table.pass') | t }}
                </button>
              }
            </div>
          }
        </section>

        <section class="boards">
          @for (p of room.players; track p.id) {
            <app-board [player]="p" />
          }
        </section>
      </div>

      @if (trashOpen()) {
        <app-modal [title]="'table.trash' | t" [wide]="true" (closed)="trashOpen.set(false)">
          @if (room.trash.length === 0) {
            <p class="muted">{{ 'table.trashEmpty' | t }}</p>
          } @else {
            <div class="card-grid">
              @for (c of room.trash; track c.id) {
                <app-game-card [card]="c" [faceUp]="true" size="sm" />
              }
            </div>
          }
        </app-modal>
      }
    }
  `,
})
export class TableComponent {
  readonly game = inject(GameService);
  readonly trashOpen = signal(false);
  amount = 10;

  readonly leader = computed(() => {
    const bids = this.game.room()?.lot?.bids ?? [];
    return bids.length === 0 ? null : bids.reduce((a, b) => (b.amount > a.amount ? b : a));
  });
  /** Dernière carte jetée, posée face visible sur la pile. */
  readonly lastTrashed = computed(() => {
    const trash = this.game.room()?.trash ?? [];
    return trash.length ? trash[trash.length - 1] : null;
  });
  readonly iLead = computed(() => this.leader()?.playerId === this.game.myId());
  readonly myGold = computed(() => this.game.room()?.players.find(p => p.id === this.game.myId())?.budget ?? 0);
  readonly minBid = computed(() => (this.leader()?.amount ?? 0) + 10);

  /** Jeter une carte coûte de l'or au piocheur (règle discard-fee). */
  readonly discardFee = computed(() => this.game.room()?.rules.includes('discard-fee') ?? false);

  /** Ce que la table raconte en ce moment. */
  readonly situation = computed(() => {
    const room = this.game.room();
    if (!room) {
      return 'idle';
    }
    if (room.phase === 'REVEALED' || room.phase === 'SLOT') {
      return 'revealed';
    }
    if (room.phase !== 'BIDDING') {
      return 'idle';
    }
    if (room.drawerId && room.drawerId === this.game.myId() && !this.game.me()?.passed && !this.game.me()?.myBid) {
      return 'my-draw';
    }
    const drawer = room.players.find(p => p.id === room.drawerId);
    return drawer && !drawer.acted ? 'waiting-drawer' : 'bidding';
  });

  /** Face visible : pour le piocheur pendant l'enchère, pour tous à la révélation. */
  readonly shownCard = computed(() => {
    const room = this.game.room();
    if (room?.lot?.cards?.length) {
      return room.lot.cards[0];
    }
    return this.game.me()?.drawnCard ?? null;
  });

  /** Clé du round : une nouvelle carte recrée l'élément, ce qui rejoue l'animation de pioche. */
  readonly drawKey = computed(() => {
    const room = this.game.room();
    return room?.lot ? [room.round] : [];
  });

  constructor() {
    // Nouvelle carte ou offre dépassée : on propose la mise minimale.
    let lastKey = '';
    effect(() => {
      const key = `${this.game.room()?.round}:${this.leader()?.amount ?? 0}`;
      if (key !== lastKey) {
        lastKey = key;
        this.amount = Math.min(this.minBid(), Math.max(this.myGold(), 0));
      }
    });
  }

  raise(step: number): void {
    this.amount = Math.min(this.myGold(), Math.max(this.minBid(), Number(this.amount) || 0) + step);
  }

  bid(): void {
    this.game.bid(Math.floor(Number(this.amount)));
  }
}
