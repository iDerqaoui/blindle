import { Component, computed, input } from '@angular/core';
import { ArtView, CardView } from '../game/game.types';

/**
 * Carte à jouer : dos aux couleurs de l'univers, face avec l'illustration du personnage.
 * Le passage dos → face est animé (retournement). Sans image, la face affiche le nom.
 */
@Component({
  selector: 'app-game-card',
  template: `
    <div class="gcard" [class]="'gcard size-' + size()" [class.flipped]="faceUp() && !!card()"
         [style.--universe]="color()">
      <div class="gcard-inner">
        <div class="gcard-back">
          @if (art()?.cover; as cover) { <img class="gcard-back-art" [src]="cover" alt="" loading="lazy" /> }
          <div class="gcard-back-frame">
            <span class="gcard-back-mark">BLINDLE</span>
            <span class="gcard-back-universe">{{ universe() }}</span>
          </div>
        </div>
        <div class="gcard-front" [class]="'gcard-front rarity-' + (card()?.rarity ?? 'none')">
          @if (card(); as c) {
            @if (c.image) {
              <img class="gcard-img" [src]="c.image" [alt]="c.name" loading="lazy" referrerpolicy="no-referrer" />
            } @else {
              <div class="gcard-noimg">{{ c.name.charAt(0) }}</div>
            }
            <div class="gcard-caption">
              <span class="gcard-name">{{ c.name }}</span>
              @if (c.rarity && size() !== 'xs') { <span class="gcard-rarity">{{ rarityLabel(c.rarity) }}</span> }
            </div>
          }
        </div>
      </div>
    </div>
  `,
})
export class GameCardComponent {
  readonly card = input<CardView | null>(null);
  readonly art = input<ArtView | null>(null);
  readonly universe = input('');
  readonly faceUp = input(false);
  /** xs : miniature de tableau ; sm : poubelle, vote ; lg : carte en jeu. */
  readonly size = input<'xs' | 'sm' | 'lg'>('sm');

  readonly color = computed(() => this.art()?.color ?? '#6b5bd6');

  rarityLabel(r: string): string {
    return ({ common: 'Commun', rare: 'Rare', epic: 'Épique', legendary: 'Légendaire' } as Record<string, string>)[r] ?? r;
  }
}
