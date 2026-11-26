import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GameService } from '../game/game.service';
import { HintComponent } from '../ui/hint.component';
import { TimerComponent } from '../ui/timer.component';
import { CardComponent } from '../ui/card.component';

/** Lot mystère : indices, prix plancher, offre ; puis révélation et placement de la carte. */
@Component({
  selector: 'app-auction',
  imports: [FormsModule, HintComponent, TimerComponent, CardComponent],
  template: `
    @if (game.room(); as room) {
      <section class="panel auction">
        <div class="auction-head">
          <h2>Round {{ room.round }} / {{ room.totalRounds }}</h2>
          <app-timer />
        </div>

        @if (room.lot; as lot) {
          <div class="mystery">
            @for (hint of lot.hints; track hint.level) {
              <app-hint [hint]="hint" class="reveal" />
            }
            <p class="floor">Palier {{ lot.hintLevel }}/{{ lot.maxHintLevel }} · offre minimale <strong>{{ lot.floorPrice }} 🪙</strong></p>
          </div>

          @if (room.phase === 'BIDDING') {
            <p class="muted">{{ lot.actedCount }} / {{ room.players.length }} joueurs ont joué ce palier
              @if (room.bidMode === 'sealed') { · offres secrètes }</p>
            @if (me(); as me) {
              @if (me.canBid) {
                <div class="bid-row">
                  <input class="input" type="number" [min]="lot.floorPrice" [max]="myBudget()" step="10" [(ngModel)]="amount" />
                  <button class="btn primary" (click)="bid()">Miser</button>
                  <button class="btn" (click)="game.pass()">Passer</button>
                </div>
                @if (me.myBid !== null) { <p>Ton offre : <strong>{{ me.myBid }} 🪙</strong> (modifiable jusqu'à la fin)</p> }
                @if (me.passed) { <p class="muted">Tu passes ce palier.</p> }
              } @else {
                <p class="muted">Ton équipe est complète : tu regardes ce lot.</p>
              }
            }
          }

          @if (lot.cards && (room.phase === 'REVEALED' || room.phase === 'SLOT')) {
            <div class="revealed">
              <p>🎉 <strong>{{ game.playerName(lot.winnerId) }}</strong> remporte le lot pour <strong>{{ lot.price }} 🪙</strong></p>
              @for (card of lot.cards; track card.id) { <app-card [card]="card" /> }
              @if (lot.bids && lot.bids.length > 1) {
                <p class="muted">Offres : @for (b of lot.bids; track b.playerId) { {{ game.playerName(b.playerId) }} {{ b.amount }}@if (!$last) {, } }</p>
              }
              @if (me()?.mustPlace) {
                <h3>Où places-tu cette carte ? (définitif)</h3>
                <div class="slot-choices">
                  @for (slot of freeSlots(); track slot.id) {
                    <button class="btn" [class.warn]="!fits(slot.requiredTags)" (click)="game.assignSlot(slot.id)">
                      {{ slot.label }}
                      @if (!fits(slot.requiredTags)) { <small>(tag {{ slot.requiredTags.join(', ') }} conseillé)</small> }
                    </button>
                  }
                </div>
              } @else {
                <p class="muted">{{ game.playerName(lot.winnerId) }} choisit un slot…</p>
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

  readonly freeSlots = computed(() => {
    const free = this.me()?.freeSlots ?? [];
    return (this.game.room()?.slots ?? []).filter(s => free.includes(s.id));
  });

  private readonly lotKey = signal('');

  constructor() {
    // Nouveau palier : l'offre proposée par défaut est le prix plancher.
    effect(() => {
      const room = this.game.room();
      const key = `${room?.round}:${room?.lot?.hintLevel}`;
      if (room?.lot && key !== this.lotKey()) {
        this.lotKey.set(key);
        this.amount = room.lot.floorPrice;
      }
    });
  }

  bid(): void {
    this.game.bid(Math.floor(Number(this.amount)));
  }

  fits(requiredTags: string[]): boolean {
    const tags = this.game.room()?.lot?.cards?.[0]?.tags ?? [];
    return requiredTags.every(t => tags.includes(t));
  }
}
