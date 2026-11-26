import { Component, HostListener, input, output } from '@angular/core';

/** Fenêtre modale accessible : fond cliquable et touche Échap pour fermer. */
@Component({
  selector: 'app-modal',
  template: `
    <div class="modal-backdrop" (click)="closed.emit()">
      <div class="modal" [class.wide]="wide()" role="dialog" aria-modal="true" [attr.aria-label]="title()"
           (click)="$event.stopPropagation()">
        <header class="modal-head">
          <h2>{{ title() }}</h2>
          <button class="icon-btn" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </header>
        <ng-content />
      </div>
    </div>
  `,
})
export class ModalComponent {
  readonly title = input('');
  readonly wide = input(false);
  readonly closed = output<void>();

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }
}
