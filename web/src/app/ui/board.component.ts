import { Component, computed, inject, input } from '@angular/core';
import { GameService } from '../game/game.service';
import { PlayerSummary } from '../game/game.types';
import { GameCardComponent } from './game-card.component';
import { TranslatePipe } from '../i18n/i18n';

/**
 * Tableau d'un joueur : ses emplacements, remplis au fil des cartes gagnées (la carte
 * « se colle » avec une animation), son or restant et son statut dans le round.
 */
@Component({
  selector: 'app-board',
  imports: [GameCardComponent, TranslatePipe],
  template: `
    @let p = player();
    <article class="board" [class.mine]="p.id === game.myId()" [class.is-drawer]="p.id === drawerId()"
             [class.is-leader]="p.id === leaderId()">
      <header class="board-head">
        <span class="avatar" [style.--hue]="hue()">{{ p.bot ? '🤖' : p.name.charAt(0).toUpperCase() }}</span>
        <span class="board-name">{{ game.displayName(p) }}@if (p.id === game.myId()) { <small> · {{ 'board.you' | t }}</small> }</span>
        @if (p.id === drawerId()) { <span class="pill pill-drawer">{{ 'board.drawing' | t }}</span> }
        @if (p.id === leaderId()) { <span class="pill pill-leader">{{ 'board.leading' | t }}</span> }
        @if (points() > 0) { <span class="pill pill-points" [title]="'board.pointsHint' | t">{{ 'card.points' | t: { n: points() } }}</span> }
        <span class="gold">{{ p.budget }}<small> {{ 'board.gold' | t }}</small></span>
      </header>
      <div class="board-slots">
        @for (slot of slots(); track slot.id) {
          @if (cardIn(slot.id); as card) {
            <div class="slot filled"><app-game-card [card]="card" [faceUp]="true" size="xs" /></div>
          } @else {
            <div class="slot empty"><span>{{ $index + 1 }}</span></div>
          }
        }
      </div>
    </article>
  `,
})
export class BoardComponent {
  readonly game = inject(GameService);
  readonly player = input.required<PlayerSummary>();

  readonly slots = computed(() => this.game.room()?.slots ?? []);
  readonly drawerId = computed(() => this.game.room()?.drawerId ?? null);
  readonly leaderId = computed(() => {
    const bids = this.game.room()?.lot?.bids ?? [];
    return this.game.room()?.phase === 'BIDDING' && bids.length > 0
      ? bids.reduce((a, b) => (b.amount > a.amount ? b : a)).playerId
      : null;
  });
  /** Total des points de rareté de l'équipe (verdict « points »). */
  readonly points = computed(() => this.player().team.reduce((sum, e) => sum + (e.card?.points ?? 0), 0));
  /** Teinte stable dérivée de l'identifiant, pour distinguer les avatars. */
  readonly hue = computed(() => [...this.player().id].reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 360, 7));

  cardIn(slotId: string) {
    return this.player().team.find(e => e.slotId === slotId)?.card ?? null;
  }
}
