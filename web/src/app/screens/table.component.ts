import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GameService } from '../game/game.service';
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
  imports: [FormsModule, BoardComponent, GameCardComponent, ModalComponent, TimerComponent],
  template: `
    @if (game.room(); as room) {
      <div class="table" [style.--universe]="room.art?.color ?? '#6b5bd6'">
        <section class="stage">
          <div class="stage-top">
            <span class="round-chip">Carte {{ room.round }}</span>
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
                    [attr.aria-label]="'Poubelle : ' + room.trash.length + ' carte(s)'">
              @if (lastTrashed(); as top) {
                <app-game-card class="trash-top" [card]="top" [faceUp]="true" size="sm" />
                <span class="trash-count">{{ room.trash.length }}</span>
              } @else {
                <span class="trash-empty">🗑️</span>
              }
              <span>Poubelle</span>
            </button>
          </div>

          <div class="stage-info">
            @switch (situation()) {
              @case ('my-draw') {
                <p class="headline">Tu as pioché <strong>{{ game.me()?.drawnCard?.name }}</strong></p>
                <p class="muted">Toi seul la vois. Ouvre l'enchère, ou laisse-la : si personne n'en veut, elle part à la poubelle.</p>
              }
              @case ('waiting-drawer') {
                <p class="headline"><strong>{{ game.playerName(room.drawerId) }}</strong> regarde sa carte…</p>
                <p class="muted">Tu ne vois que le dos. Attends sa mise d'ouverture.</p>
              }
              @case ('bidding') {
                @if (leader(); as l) {
                  <p class="headline">
                    <strong>{{ game.playerName(l.playerId) }}</strong> mène à <span class="amount">{{ l.amount }} or</span>
                  </p>
                } @else {
                  <p class="headline">Le piocheur a laissé : personne n'a encore misé</p>
                }
                <p class="muted">Pioche de {{ game.playerName(room.drawerId) }}. Surenchéris ou laisse.</p>
              }
              @case ('revealed') {
                <p class="headline">
                  🎉 <strong>{{ game.playerName(room.lot?.winnerId) }}</strong> remporte
                  <strong>{{ room.lot?.cards?.[0]?.name }}</strong> pour <span class="amount">{{ room.lot?.price }} or</span>
                </p>
                <p class="muted">La carte rejoint son tableau…</p>
              }
              @default { <p class="muted">Préparation de la carte suivante…</p> }
            }
          </div>

          @if (room.phase === 'BIDDING' && game.me(); as me) {
            <div class="bid-bar">
              @if (!me.canBid) {
                <p class="muted">Ton équipe est complète : tu regardes la fin de partie.</p>
              } @else if (situation() === 'waiting-drawer') {
                <p class="muted">En attente du piocheur…</p>
              } @else if (iLead()) {
                <p class="lead-note">Tu mènes à <strong>{{ leader()?.amount }} or</strong>. Les autres doivent surenchérir.</p>
              } @else if (me.passed) {
                <p class="muted">Tu as laissé cette carte.</p>
                <button class="btn small" (click)="bid()">Revenir à {{ amount }} or</button>
              } @else {
                <div class="quick">
                  @for (step of [10, 50, 100]; track step) {
                    <button class="chip" (click)="raise(step)">+{{ step }}</button>
                  }
                </div>
                <div class="bid-input">
                  <input class="input" type="number" [min]="minBid()" [max]="myGold()" step="10"
                         [(ngModel)]="amount" aria-label="Montant de l'offre" />
                  <span class="suffix">or</span>
                </div>
                <button class="btn primary" [disabled]="amount < minBid() || amount > myGold()" (click)="bid()">
                  {{ situation() === 'my-draw' ? 'Ouvrir à' : 'Miser' }} {{ amount }} or
                </button>
                <button class="btn" (click)="game.pass()">Laisser</button>
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
        <app-modal title="Poubelle" [wide]="true" (closed)="trashOpen.set(false)">
          @if (room.trash.length === 0) {
            <p class="muted">Aucune carte jetée pour l'instant. Une carte dont personne ne veut finit ici, visible par tous.</p>
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
