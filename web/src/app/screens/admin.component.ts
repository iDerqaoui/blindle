import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiError, apiFetch } from '../auth/auth.service';
import { I18nService, TranslatePipe } from '../i18n/i18n';
import { AdminThemesComponent } from './admin-themes.component';

interface UniverseSummary { slug: string; name: string; cover: string | null; color: string | null; characters: number; }

interface CharacterView {
  malId: number; name: string; tier: string; favourites: number | null; role: string | null; image: string | null;
  popularity: number; power: number; score: number; rank: number; rarity: string | null; liveRarity: string | null;
  /** Rareté imposée par l'admin (null : calculée par le score et les quotas). */
  forcedRarity: string | null;
}

interface UniverseDetail {
  slug: string; name: string; note: string | null; deckSize: number;
  rarities: Partial<Record<string, number>>; characters: CharacterView[];
}

interface MalCharacter { malId: number; name: string; favourites: number; role: string | null; image: string | null; }

const API = '/api/admin/curation/universes';
export const TIERS = ['S', 'A', 'B', 'C', 'D'];
const RARITIES = ['legendary', 'epic', 'rare', 'common'];

/**
 * Page admin (#admin) : rang de puissance des personnages anime, ajout depuis MyAnimeList, retrait.
 * La rareté affichée est celle du prochain démarrage du serveur (calcul serveur : RarityCalculator) ;
 * les parties en cours gardent l'ancienne.
 */
@Component({
  selector: 'app-admin',
  imports: [FormsModule, TranslatePipe, AdminThemesComponent],
  template: `
    <section class="panel admin">
      <header class="admin-head">
        <h1>{{ 'admin.page' | t }}</h1>
        <nav class="admin-tabs" role="tablist">
          <button class="chip" role="tab" [class.active]="tab() === 'anime'" [attr.aria-selected]="tab() === 'anime'"
                  (click)="tab.set('anime')">{{ 'admin.title' | t }}</button>
          <button class="chip" role="tab" [class.active]="tab() === 'themes'" [attr.aria-selected]="tab() === 'themes'"
                  (click)="tab.set('themes')">{{ 'adminThemes.title' | t }}</button>
        </nav>
      </header>

      @if (tab() === 'themes') {
        <app-admin-themes />
      } @else {
      <p class="muted">{{ 'admin.restartNote' | t }}</p>

      <nav class="admin-universes" [attr.aria-label]="'admin.universes' | t">
        @for (u of universes(); track u.slug) {
          <button class="chip" [class.active]="u.slug === slug()" [style.--universe]="u.color ?? '#6b5bd6'"
                  (click)="open(u.slug)">
            {{ u.name }} <small class="muted">{{ u.characters }}</small>
          </button>
        }
      </nav>

      @if (error(); as e) { <p class="admin-error" role="alert">{{ e }}</p> }

      @if (detail(); as d) {
        <div class="admin-summary">
          @for (r of rarities; track r) {
            <span class="rarity-badge" [class]="'rarity-badge ' + r">{{ 'rarity.' + r | t }} · {{ d.rarities[r] ?? 0 }}</span>
          }
          <span class="muted small">{{ 'admin.deck' | t: { n: inDeck(), max: d.deckSize, total: d.characters.length } }}</span>
          @if (pending() > 0) { <span class="tag">{{ i18n.plural('admin.pending', pending()) }}</span> }
        </div>
        @if (d.note) { <p class="muted small">{{ d.note }}</p> }

        <details class="admin-add">
          <summary>{{ 'admin.add' | t }}</summary>
          <form class="admin-search" (submit)="$event.preventDefault(); search()">
            <input class="input" [(ngModel)]="query" name="q" [placeholder]="'admin.searchPlaceholder' | t" />
            <button class="btn small" type="submit" [disabled]="busy()">{{ 'admin.search' | t }}</button>
          </form>
          @if (results(); as list) {
            @if (list.length === 0) { <p class="muted small">{{ 'admin.noResult' | t }}</p> }
            <ul class="admin-results">
              @for (c of list; track c.malId) {
                <li>
                  @if (c.image) { <img [src]="c.image" alt="" loading="lazy" /> }
                  <span class="grow">{{ c.name }} <small class="muted">{{ c.role }} · ♥ {{ c.favourites }}</small></span>
                  <select class="input tier" #tier [attr.aria-label]="'admin.tier' | t">
                    @for (t of tiers; track t) { <option [value]="t" [selected]="t === 'C'">{{ t }}</option> }
                  </select>
                  <button class="btn small primary" [disabled]="busy()" (click)="add(c, tier.value)">{{ 'admin.addOne' | t }}</button>
                </li>
              }
            </ul>
          }
        </details>

        <input class="input admin-filter" [(ngModel)]="filter" [placeholder]="'admin.filter' | t"
               [attr.aria-label]="'admin.filter' | t" />

        <div class="admin-table-wrap">
          <table class="admin-table">
            <thead>
              <tr>
                <th>#</th><th></th><th>{{ 'admin.name' | t }}</th><th>{{ 'admin.tier' | t }}</th>
                <th title="MyAnimeList">♥</th><th>{{ 'admin.popularity' | t }}</th><th>{{ 'admin.score' | t }}</th>
                <th>{{ 'admin.rarity' | t }}</th><th [title]="'admin.forceHelp' | t">{{ 'admin.force' | t }}</th><th></th>
              </tr>
            </thead>
            <tbody>
              @for (c of shown(); track c.malId) {
                <tr [class.out]="!c.rarity">
                  <td class="muted">{{ c.rank >= 0 ? c.rank + 1 : '–' }}</td>
                  <td>@if (c.image) { <img [src]="c.image" alt="" loading="lazy" /> }</td>
                  <td>
                    <input class="input name" [value]="c.name" maxlength="100" [attr.aria-label]="'admin.name' | t"
                           (change)="rename(c, $any($event.target).value)" />
                  </td>
                  <td>
                    <select class="input tier" [attr.aria-label]="'admin.tier' | t" [disabled]="busy()"
                            (change)="setTier(c, $any($event.target).value)">
                      @for (t of tiers; track t) { <option [value]="t" [selected]="t === c.tier">{{ t }}</option> }
                    </select>
                  </td>
                  <td class="muted">{{ c.favourites ?? '?' }}</td>
                  <td class="muted">{{ c.popularity }}</td>
                  <td>{{ c.score }}</td>
                  <td>
                    @if (c.rarity; as r) {
                      <span [class]="'rarity-badge ' + r">{{ 'rarity.' + r | t }}</span>
                    } @else {
                      <span class="muted small">{{ (c.favourites === null ? 'admin.noData' : 'admin.outOfDeck') | t }}</span>
                    }
                    @if (c.liveRarity !== c.rarity) {
                      <small class="muted"> {{ 'admin.was' | t: { r: (c.liveRarity ? ('rarity.' + c.liveRarity | t) : '–') } }}</small>
                    }
                  </td>
                  <td>
                    <select class="input tier" [attr.aria-label]="'admin.force' | t" [disabled]="busy()"
                            [title]="'admin.forceHelp' | t" (change)="setRarity(c, $any($event.target).value)">
                      <option value="auto" [selected]="!c.forcedRarity">{{ 'admin.auto' | t }}</option>
                      @for (r of rarities; track r) {
                        <option [value]="r" [selected]="r === c.forcedRarity">{{ 'rarity.' + r | t }}</option>
                      }
                    </select>
                  </td>
                  <td>
                    @if (confirmRemove() === c.malId) {
                      <button class="btn small danger" (click)="remove(c)">{{ 'admin.confirmRemove' | t }}</button>
                    } @else {
                      <button class="btn small ghost" (click)="confirmRemove.set(c.malId)"
                              [attr.aria-label]="'admin.remove' | t">✕</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else if (!error()) {
        <p class="muted">{{ 'admin.loading' | t }}</p>
      }
      }
    </section>
  `,
})
export class AdminComponent {
  readonly i18n = inject(I18nService);
  readonly tiers = TIERS;
  readonly rarities = RARITIES;

  /** Onglet : personnages anime (curation) ou cartes des autres thèmes. */
  readonly tab = signal<'anime' | 'themes'>('anime');
  readonly universes = signal<UniverseSummary[]>([]);
  readonly slug = signal<string | null>(null);
  readonly detail = signal<UniverseDetail | null>(null);
  readonly results = signal<MalCharacter[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly confirmRemove = signal<number | null>(null);
  readonly filter = signal('');
  query = '';

  readonly inDeck = computed(() => this.detail()?.characters.filter(c => c.rarity).length ?? 0);
  readonly pending = computed(() => this.detail()?.characters.filter(c => c.liveRarity !== c.rarity).length ?? 0);
  readonly shown = computed(() => {
    const f = this.filter().trim().toLowerCase();
    const list = this.detail()?.characters ?? [];
    return f ? list.filter(c => c.name.toLowerCase().includes(f)) : list;
  });

  constructor() {
    this.loadUniverses();
  }

  async loadUniverses(): Promise<void> {
    const list = await this.call<UniverseSummary[]>(API);
    if (list) {
      this.universes.set(list);
      if (!this.slug() && list.length) {
        this.open(list[0].slug);
      }
    }
  }

  async open(slug: string): Promise<void> {
    this.slug.set(slug);
    this.results.set(null);
    this.confirmRemove.set(null);
    this.detail.set(await this.call<UniverseDetail>(`${API}/${slug}`));
  }

  /** Impose une rareté (hors quotas, toujours dans le paquet) ou la rend au calcul (« auto »). */
  setRarity(c: CharacterView, rarity: string): void {
    this.update(`${API}/${this.slug()}/characters/${c.malId}`, { method: 'PUT', body: JSON.stringify({ rarity }) });
  }

  setTier(c: CharacterView, tier: string): void {
    this.update(`${API}/${this.slug()}/characters/${c.malId}`, { method: 'PUT', body: JSON.stringify({ tier }) });
  }

  rename(c: CharacterView, name: string): void {
    if (name.trim() && name.trim() !== c.name) {
      this.update(`${API}/${this.slug()}/characters/${c.malId}`, { method: 'PUT', body: JSON.stringify({ name }) });
    }
  }

  remove(c: CharacterView): void {
    this.confirmRemove.set(null);
    this.update(`${API}/${this.slug()}/characters/${c.malId}`, { method: 'DELETE' });
  }

  async add(c: MalCharacter, tier: string): Promise<void> {
    await this.update(`${API}/${this.slug()}/characters`,
      { method: 'POST', body: JSON.stringify({ malId: c.malId, name: c.name, tier }) });
    this.results.update(list => list?.filter(x => x.malId !== c.malId) ?? null);
  }

  async search(): Promise<void> {
    this.results.set(await this.call<MalCharacter[]>(`${API}/${this.slug()}/search?q=${encodeURIComponent(this.query)}`));
  }

  /** Modification : la réponse est le classement recalculé de l'univers. */
  private async update(url: string, init: RequestInit): Promise<void> {
    const d = await this.call<UniverseDetail>(url, init);
    if (d) {
      this.detail.set(d);
      this.universes.update(list => list.map(u => (u.slug === d.slug ? { ...u, characters: d.characters.length } : u)));
    }
  }

  private async call<T>(url: string, init: RequestInit = {}): Promise<T | null> {
    this.busy.set(true);
    this.error.set(null);
    try {
      const res = await apiFetch(url, init);
      if (!res.ok) {
        this.error.set(res.status === 401 || res.status === 403 ? this.i18n.t('admin.forbidden') : await apiError(res));
        return null;
      }
      return (await res.json()) as T;
    } catch {
      this.error.set(this.i18n.t('admin.offline'));
      return null;
    } finally {
      this.busy.set(false);
    }
  }
}
