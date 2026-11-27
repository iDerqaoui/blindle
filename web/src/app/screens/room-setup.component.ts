import { Component, computed, effect, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GameService } from '../game/game.service';
import { CatalogRule, RoomCatalogView, RuleParamValue } from '../game/game.types';
import { I18nService, TranslatePipe } from '../i18n/i18n';
import { ModalComponent } from '../ui/modal.component';

interface RuleDraft { type: string; active: boolean; params: Record<string, RuleParamValue>; source: CatalogRule; }

/**
 * Personnalisation de la room par son hôte, en salle d'attente : cartes du paquet, rareté d'une carte,
 * règles et paramètres. La base (preset de l'administrateur) n'est jamais modifiée ; le serveur valide tout.
 */
@Component({
  selector: 'app-room-setup',
  imports: [FormsModule, TranslatePipe, ModalComponent],
  template: `
    <app-modal [title]="'setup.title' | t" [wide]="true" (closed)="closed.emit()">
      @if (game.catalog(); as cat) {
        <p class="muted small">{{ 'setup.intro' | t }}</p>

        <h3>{{ 'setup.rules' | t }}</h3>
        <ul class="setup-rules">
          @for (r of rules(); track r.type) {
            <li [class.off]="!r.active">
              <label class="setup-rule-head">
                <input type="checkbox" [(ngModel)]="r.active" />
                <strong>{{ ruleLabel(r.type) }}</strong>
                @if (r.source.requiredBidModes.length) {
                  <span class="muted small">({{ 'setup.onlyModes' | t: { modes: r.source.requiredBidModes.join(', ') } }})</span>
                }
              </label>
              @if (r.active) {
                <div class="setup-params">
                  @for (key of paramKeys(r); track key) {
                    <label class="field">{{ paramLabel(key) }}
                      <input class="input" type="number" [step]="isDecimal(r.source.defaults[key]) ? 0.1 : 1"
                             [(ngModel)]="r.params[key]" />
                    </label>
                  }
                </div>
              }
            </li>
          }
        </ul>

        <h3>{{ 'setup.cards' | t: { n: includedCount(), total: cat.cards.length } }}</h3>
        <div class="setup-toolbar">
          <input class="input" type="search" [placeholder]="'setup.search' | t" [(ngModel)]="query" />
          <button class="chip" (click)="setAll(true)">{{ 'setup.all' | t }}</button>
          <button class="chip" (click)="setAll(false)">{{ 'setup.none' | t }}</button>
        </div>
        <ul class="setup-cards">
          @for (c of visibleCards(); track c.id) {
            <li [class.off]="excluded().has(c.id)">
              <input type="checkbox" [checked]="!excluded().has(c.id)" (change)="toggle(c.id)"
                     [attr.aria-label]="c.name" />
              @if (c.image) { <img [src]="c.image" alt="" loading="lazy" /> }
              <span class="setup-card-name">{{ c.name }} @if (c.trap) { <span class="pill">{{ 'setup.trap' | t }}</span> }</span>
              @if (cat.rarities.length) {
                <select class="input" [ngModel]="rarities()[c.id] ?? c.baseRarity" (ngModelChange)="setRarity(c.id, $event, c.baseRarity)">
                  @for (r of cat.rarities; track r) {
                    <option [value]="r">{{ rarityLabel(r) }}{{ r === c.baseRarity ? ' ★' : '' }}</option>
                  }
                </select>
              }
            </li>
          }
        </ul>
        <p class="muted small">{{ 'setup.baseHint' | t }}</p>

        <div class="setup-actions">
          <button class="btn" (click)="reset()">{{ 'setup.reset' | t }}</button>
          <button class="btn primary" (click)="save()">{{ 'setup.save' | t }}</button>
        </div>
      } @else {
        <p class="muted">{{ 'setup.loading' | t }}</p>
      }
    </app-modal>
  `,
})
export class RoomSetupComponent {
  readonly game = inject(GameService);
  private readonly i18n = inject(I18nService);
  readonly closed = output<void>();

  readonly excluded = signal(new Set<string>());
  readonly rarities = signal<Partial<Record<string, string>>>({});
  readonly rules = signal<RuleDraft[]>([]);
  query = '';

  readonly includedCount = computed(() => {
    const cat = this.game.catalog();
    return cat ? cat.cards.filter(c => !this.excluded().has(c.id)).length : 0;
  });

  constructor() {
    this.game.loadCatalog();
    // Brouillon repris du catalogue à chaque réponse du serveur (chargement ou personnalisation validée).
    effect(() => {
      const cat = this.game.catalog();
      if (cat) {
        this.load(cat);
      }
    });
  }

  visibleCards() {
    const cat = this.game.catalog();
    const q = this.query.trim().toLowerCase();
    return !cat ? [] : q ? cat.cards.filter(c => c.name.toLowerCase().includes(q)) : cat.cards;
  }

  toggle(id: string): void {
    const next = new Set(this.excluded());
    next.has(id) ? next.delete(id) : next.add(id);
    this.excluded.set(next);
  }

  /** Coche ou décoche les cartes affichées (filtre de recherche compris). */
  setAll(included: boolean): void {
    const next = new Set(this.excluded());
    this.visibleCards().forEach(c => included ? next.delete(c.id) : next.add(c.id));
    this.excluded.set(next);
  }

  setRarity(id: string, rarity: string, base: string | null): void {
    const next = { ...this.rarities() };
    if (rarity === base) {
      delete next[id];
    } else {
      next[id] = rarity;
    }
    this.rarities.set(next);
  }

  paramKeys(r: RuleDraft): string[] {
    return Object.keys(r.source.defaults);
  }

  isDecimal(v: RuleParamValue | undefined): boolean {
    return typeof v === 'number' && !Number.isInteger(v);
  }

  ruleLabel(type: string): string {
    return this.i18n.has('rule.' + type) ? this.i18n.t('rule.' + type) : type;
  }

  paramLabel(key: string): string {
    return this.i18n.has('param.' + key) ? this.i18n.t('param.' + key) : key;
  }

  rarityLabel(r: string): string {
    return this.i18n.has('rarity.' + r) ? this.i18n.t('rarity.' + r) : r;
  }

  save(): void {
    this.game.customize({
      excludedCards: [...this.excluded()],
      rarities: this.rarities() as Record<string, string>,
      rules: this.rules().filter(r => r.active).map(r => ({ ref: r.type, params: { ...r.params } })),
    });
  }

  /** Retour à la base fixée par l'administrateur. */
  reset(): void {
    this.game.customize({ excludedCards: [], rarities: {}, rules: null });
  }

  private load(cat: RoomCatalogView): void {
    this.excluded.set(new Set(cat.cards.filter(c => !c.included && !c.trap).map(c => c.id)));
    const rar: Partial<Record<string, string>> = {};
    cat.cards.forEach(c => {
      if (c.rarity && c.rarity !== c.baseRarity) {
        rar[c.id] = c.rarity;
      }
    });
    this.rarities.set(rar);
    this.rules.set(cat.rules.map(r => ({ type: r.type, active: r.active, params: { ...r.params }, source: r })));
  }
}
