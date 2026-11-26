import { Component, input } from '@angular/core';
import { HintView } from '../game/game.types';

/** Rendu d'un indice selon son type. */
@Component({
  selector: 'app-hint',
  template: `
    <div class="hint" [class.hint-initial]="hint().type === 'initial'">
      <span class="hint-level">Indice {{ hint().level }}</span>
      @switch (hint().type) {
        @case ('text') { <p class="hint-text">« {{ hint().value }} »</p> }
        @case ('initial') { <p class="hint-text">Initiale : <strong class="initial">{{ hint().value }}</strong></p> }
        @default { <p class="hint-text muted">Indice {{ hint().type }} (non disponible dans cette version)</p> }
      }
    </div>
  `,
})
export class HintComponent {
  readonly hint = input.required<HintView>();
}
