import { Component, computed, inject, input } from '@angular/core';
import { I18nService, TranslatePipe } from '../i18n/i18n';

export type LegalPage = 'terms' | 'privacy' | 'legal';

/** Nombre de sections de chaque page (clés legal.<page>.s<n>.h / .p dans en.ts et fr.ts). */
const SECTIONS: Record<LegalPage, number> = { terms: 9, privacy: 9, legal: 5 };

/**
 * Pages légales (#terms, #privacy, #legal) : CGU, politique de confidentialité et cookies,
 * mentions légales. Textes dans les fichiers de traduction ; un paragraphe par ligne vide.
 */
@Component({
  selector: 'app-legal',
  imports: [TranslatePipe],
  template: `
    <article class="panel legal-page">
      <a class="back-link" href="#">← {{ 'legal.back' | t }}</a>
      <h1>{{ 'legal.' + page() + '.title' | t }}</h1>
      <p class="muted small">{{ 'legal.updated' | t: { date: '2026-11-28' } }}</p>
      @for (s of sections(); track s) {
        <section>
          <h2>{{ 'legal.' + page() + '.s' + s + '.h' | t }}</h2>
          @for (p of paragraphs(s); track $index) { <p>{{ p }}</p> }
        </section>
      }
      <nav class="legal-links">
        <a href="#terms">{{ 'legal.terms.title' | t }}</a>
        <a href="#privacy">{{ 'legal.privacy.title' | t }}</a>
        <a href="#legal">{{ 'legal.legal.title' | t }}</a>
      </nav>
    </article>
  `,
})
export class LegalComponent {
  private readonly i18n = inject(I18nService);
  readonly page = input.required<LegalPage>();
  readonly sections = computed(() => Array.from({ length: SECTIONS[this.page()] }, (_, i) => i + 1));

  paragraphs(s: number): string[] {
    return this.i18n.t(`legal.${this.page()}.s${s}.p`).split('\n\n');
  }
}
