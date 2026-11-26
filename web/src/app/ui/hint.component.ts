import { Component, input } from '@angular/core';
import { HintView } from '../game/game.types';
import { TranslatePipe } from '../i18n/i18n';

/** Rendu d'un indice selon son type. */
@Component({
  selector: 'app-hint',
  imports: [TranslatePipe],
  template: `
    <div class="hint" [class.hint-initial]="hint().type === 'initial'">
      <span class="hint-level">{{ 'hint.level' | t: { n: hint().level } }}</span>
      @switch (hint().type) {
        @case ('text') { <p class="hint-text">« {{ hint().value }} »</p> }
        @case ('initial') { <p class="hint-text">{{ 'hint.initial' | t }} <strong class="initial">{{ hint().value }}</strong></p> }
        @default { <p class="hint-text muted">{{ 'hint.unavailable' | t: { type: hint().type } }}</p> }
      }
    </div>
  `,
})
export class HintComponent {
  readonly hint = input.required<HintView>();
}
