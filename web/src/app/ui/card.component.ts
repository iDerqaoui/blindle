import { Component, input } from '@angular/core';
import { KeyValuePipe } from '@angular/common';
import { CardView } from '../game/game.types';

/** Carte révélée : nom, rareté, tags, stats maison. Pas d'image (imagePolicy). */
@Component({
  selector: 'app-card',
  imports: [KeyValuePipe],
  template: `
    <div class="card" [class]="'card rarity-' + (card().rarity ?? 'none')" [class.compact]="compact()">
      <div class="card-name">{{ card().name }}</div>
      @if (!compact()) {
        @if (card().rarity) { <div class="card-rarity">{{ card().rarity }}</div> }
        <div class="tags">
          @for (tag of card().tags; track tag) { <span class="tag">{{ tag }}</span> }
        </div>
        <div class="stats">
          @for (stat of card().stats | keyvalue; track stat.key) {
            <div class="stat"><span>{{ stat.key }}</span><strong>{{ stat.value }}</strong></div>
          }
        </div>
      }
    </div>
  `,
})
export class CardComponent {
  readonly card = input.required<CardView>();
  readonly compact = input(false);
}
