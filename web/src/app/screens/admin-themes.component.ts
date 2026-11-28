import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiFetch } from '../auth/auth.service';
import { I18nService, TranslatePipe } from '../i18n/i18n';

interface ThemeSummary { id: string; name: string; cards: number; stored: boolean; updatedAt: string | null; }

interface Hint { level: number; type: string; text?: string | null; value?: string | null; }

interface Card {
  id: string; name: string; tags?: string[] | null; rarity?: string | null; isTrap?: boolean | null;
  stats?: Record<string, number> | null; image?: string | null; hints: Hint[];
}

interface ThemeDetail {
  id: string; name: string; description: string | null;
  rights: { profile: string; imagePolicy: string; channels: string[] };
  attributes: string[]; cards: Card[]; stored: boolean; updatedAt: string | null; presets: string[];
  rarities: string[]; hintTypes: string[];
}

/** Carte en cours d'édition : champs du formulaire (texte simple), convertis à l'enregistrement. */
interface Draft {
  /** Identifiant d'origine (null : nouvelle carte). */
  originalId: string | null;
  id: string; name: string; tags: string; rarity: string; isTrap: boolean; image: string;
  stats: Record<string, string>;
  hints: { type: string; content: string }[];
}

const API = '/api/admin/content/themes';

/**
 * Admin des cartes des thèmes écrits à la main (manga, pays…), en base : chaque enregistrement est
 * validé par le serveur comme un fichier de content/themes (schéma, droits, presets) et servi
 * aussitôt aux nouvelles rooms. Les thèmes anime s'éditent dans l'onglet des personnages.
 */
@Component({
  selector: 'app-admin-themes',
  imports: [FormsModule, TranslatePipe],
  template: `
    <p class="muted">{{ 'adminThemes.liveNote' | t }}</p>

    <nav class="admin-universes" [attr.aria-label]="'adminThemes.themes' | t">
      @for (t of themes(); track t.id) {
        <button class="chip" [class.active]="t.id === themeId()" (click)="open(t.id)">
          {{ t.name }} <small class="muted">{{ t.cards }}</small>
          @if (t.stored) { <small class="tag">{{ 'adminThemes.edited' | t }}</small> }
        </button>
      }
    </nav>

    @if (error(); as e) {
      <div class="admin-error" role="alert">
        <p>{{ e }}</p>
        @if (errors().length > 1) { <ul>@for (m of errors(); track $index) { <li>{{ m }}</li> }</ul> }
      </div>
    }

    @if (detail(); as d) {
      <div class="admin-summary">
        <span class="muted small">{{ 'adminThemes.summary' | t: { n: d.cards.length, presets: d.presets.join(', ') || '–' } }}</span>
        <span class="tag">{{ 'adminThemes.images.' + d.rights.imagePolicy | t }}</span>
        @if (d.stored) {
          <span class="tag">{{ 'adminThemes.storedAt' | t: { at: when(d.updatedAt) } }}</span>
          @if (confirmReset()) {
            <button class="btn small danger" [disabled]="busy()" (click)="reset()">{{ 'adminThemes.confirmReset' | t }}</button>
          } @else {
            <button class="btn small ghost" [title]="'adminThemes.resetHelp' | t" (click)="confirmReset.set(true)">
              {{ 'adminThemes.reset' | t }}
            </button>
          }
        } @else {
          <span class="muted small">{{ 'adminThemes.fromFile' | t }}</span>
        }
        <button class="btn small primary" [disabled]="busy()" (click)="create(d)">{{ 'adminThemes.newCard' | t }}</button>
      </div>

      @if (draft(); as f) {
        <form class="admin-card-form panel" (submit)="$event.preventDefault(); save()">
          <h2>{{ (f.originalId ? 'adminThemes.editTitle' : 'adminThemes.newTitle') | t: { name: f.name || '…' } }}</h2>
          <div class="admin-form-grid">
            <label>{{ 'adminThemes.id' | t }}
              <input class="input" [(ngModel)]="f.id" name="id" required pattern="[a-z0-9]+(-[a-z0-9]+)*"
                     [title]="'adminThemes.idHelp' | t" />
            </label>
            <label>{{ 'admin.name' | t }}
              <input class="input" [(ngModel)]="f.name" name="name" required maxlength="100" />
            </label>
            <label>{{ 'adminThemes.tags' | t }}
              <input class="input" [(ngModel)]="f.tags" name="tags" [placeholder]="'adminThemes.tagsHelp' | t" />
            </label>
            <label>{{ 'admin.rarity' | t }}
              <select class="input" [(ngModel)]="f.rarity" name="rarity">
                <option value="">–</option>
                @for (r of d.rarities; track r) { <option [value]="r">{{ 'rarity.' + r | t }}</option> }
              </select>
            </label>
            @for (a of d.attributes; track a) {
              <label>{{ a }}
                <input class="input" type="number" min="0" max="100" [(ngModel)]="f.stats[a]" [name]="'stat-' + a" />
              </label>
            }
            @if (d.rights.imagePolicy !== 'none') {
              <label class="wide">{{ 'adminThemes.image' | t }}
                <input class="input" [(ngModel)]="f.image" name="image" placeholder="https://…" />
              </label>
            }
            <label class="check">
              <input type="checkbox" [(ngModel)]="f.isTrap" name="isTrap" /> {{ 'adminThemes.trap' | t }}
            </label>
          </div>

          <h3>{{ 'adminThemes.hints' | t }} <small class="muted">{{ 'adminThemes.hintsHelp' | t }}</small></h3>
          <ol class="admin-hints">
            @for (h of f.hints; track $index; let i = $index) {
              <li>
                <select class="input tier" [(ngModel)]="h.type" [name]="'hint-type-' + i" [attr.aria-label]="'adminThemes.hintType' | t">
                  @for (t of d.hintTypes; track t) { <option [value]="t">{{ 'adminThemes.hint.' + t | t }}</option> }
                </select>
                <input class="input" [(ngModel)]="h.content" [name]="'hint-' + i" required
                       [attr.aria-label]="'adminThemes.hints' | t" />
                <button type="button" class="btn small ghost" [disabled]="i === 0" (click)="moveHint(i, -1)"
                        [attr.aria-label]="'adminThemes.up' | t">↑</button>
                <button type="button" class="btn small ghost" (click)="removeHint(i)"
                        [attr.aria-label]="'admin.remove' | t">✕</button>
              </li>
            }
          </ol>
          <button type="button" class="btn small" (click)="addHint()">{{ 'adminThemes.addHint' | t }}</button>

          <div class="admin-form-actions">
            <button class="btn primary" type="submit" [disabled]="busy()">{{ 'account.save' | t }}</button>
            <button class="btn ghost" type="button" (click)="draft.set(null)">{{ 'adminThemes.cancel' | t }}</button>
          </div>
        </form>
      }

      <input class="input admin-filter" [(ngModel)]="filter" [placeholder]="'admin.filter' | t"
             [attr.aria-label]="'admin.filter' | t" />

      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th></th><th>{{ 'admin.name' | t }}</th><th>{{ 'adminThemes.id' | t }}</th><th>{{ 'admin.rarity' | t }}</th>
              <th>{{ 'adminThemes.tags' | t }}</th>
              @for (a of d.attributes; track a) { <th>{{ a }}</th> }
              <th>{{ 'adminThemes.hints' | t }}</th><th></th>
            </tr>
          </thead>
          <tbody>
            @for (c of shown(); track c.id) {
              <tr>
                <td>@if (c.image) { <img [src]="c.image" alt="" loading="lazy" /> }</td>
                <td>{{ c.name }} @if (c.isTrap) { <small class="tag">{{ 'adminThemes.trap' | t }}</small> }</td>
                <td class="muted small">{{ c.id }}</td>
                <td>@if (c.rarity; as r) { <span [class]="'rarity-badge ' + r">{{ 'rarity.' + r | t }}</span> }</td>
                <td class="muted small">{{ (c.tags ?? []).join(', ') }}</td>
                @for (a of d.attributes; track a) { <td class="muted">{{ c.stats?.[a] ?? '–' }}</td> }
                <td class="muted small" [title]="hintsTitle(c)">{{ c.hints.length }}</td>
                <td class="admin-row-actions">
                  <button class="btn small" (click)="edit(c)">{{ 'adminThemes.edit' | t }}</button>
                  @if (confirmRemove() === c.id) {
                    <button class="btn small danger" [disabled]="busy()" (click)="remove(c)">{{ 'admin.confirmRemove' | t }}</button>
                  } @else {
                    <button class="btn small ghost" (click)="confirmRemove.set(c.id)" [attr.aria-label]="'admin.remove' | t">✕</button>
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
  `,
})
export class AdminThemesComponent {
  readonly i18n = inject(I18nService);

  readonly themes = signal<ThemeSummary[]>([]);
  readonly themeId = signal<string | null>(null);
  readonly detail = signal<ThemeDetail | null>(null);
  readonly draft = signal<Draft | null>(null);
  readonly error = signal<string | null>(null);
  readonly errors = signal<string[]>([]);
  readonly busy = signal(false);
  readonly confirmRemove = signal<string | null>(null);
  readonly confirmReset = signal(false);
  readonly filter = signal('');

  readonly shown = computed(() => {
    const f = this.filter().trim().toLowerCase();
    const list = this.detail()?.cards ?? [];
    return f ? list.filter(c => c.name.toLowerCase().includes(f) || c.id.includes(f)
      || (c.tags ?? []).some(t => t.toLowerCase().includes(f))) : list;
  });

  constructor() {
    this.loadThemes();
  }

  async loadThemes(): Promise<void> {
    const list = await this.call<ThemeSummary[]>(API);
    if (list) {
      this.themes.set(list);
      if (!this.themeId() && list.length) {
        this.open(list[0].id);
      }
    }
  }

  async open(id: string): Promise<void> {
    this.themeId.set(id);
    this.draft.set(null);
    this.confirmRemove.set(null);
    this.confirmReset.set(false);
    this.detail.set(await this.call<ThemeDetail>(`${API}/${id}`));
  }

  create(d: ThemeDetail): void {
    this.draft.set({
      originalId: null, id: '', name: '', tags: '', rarity: '', isTrap: false, image: '',
      stats: Object.fromEntries(d.attributes.map(a => [a, ''])),
      hints: [{ type: 'text', content: '' }, { type: 'text', content: '' }],
    });
  }

  edit(c: Card): void {
    const d = this.detail();
    this.draft.set({
      originalId: c.id, id: c.id, name: c.name, tags: (c.tags ?? []).join(', '), rarity: c.rarity ?? '',
      isTrap: !!c.isTrap, image: c.image ?? '',
      stats: Object.fromEntries((d?.attributes ?? []).map(a => [a, c.stats?.[a] == null ? '' : String(c.stats[a])])),
      hints: [...c.hints].sort((a, b) => a.level - b.level)
        .map(h => ({ type: h.type, content: h.text ?? h.value ?? '' })),
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  addHint(): void {
    this.draft.update(f => (f ? { ...f, hints: [...f.hints, { type: 'text', content: '' }] } : f));
  }

  removeHint(i: number): void {
    this.draft.update(f => (f ? { ...f, hints: f.hints.filter((_, j) => j !== i) } : f));
  }

  moveHint(i: number, delta: number): void {
    this.draft.update(f => {
      if (!f) return f;
      const hints = [...f.hints];
      [hints[i], hints[i + delta]] = [hints[i + delta], hints[i]];
      return { ...f, hints };
    });
  }

  /** Brouillon → carte au format du schéma (champ vide = absent ; indice « text » en text, sinon value). */
  async save(): Promise<void> {
    const f = this.draft();
    const id = this.themeId();
    if (!f || !id) return;
    const tags = f.tags.split(',').map(t => t.trim()).filter(t => t);
    const stats: Record<string, number> = {};
    for (const [k, v] of Object.entries(f.stats)) {
      if (v !== '' && v != null) stats[k] = Number(v);
    }
    const card: Card = {
      id: f.id.trim(), name: f.name.trim(),
      tags: tags.length ? tags : null,
      rarity: f.rarity || null,
      isTrap: f.isTrap ? true : null,
      stats: Object.keys(stats).length ? stats : null,
      image: f.image.trim() || null,
      hints: f.hints.map((h, i) => (h.type === 'text'
        ? { level: i + 1, type: h.type, text: h.content.trim() }
        : { level: i + 1, type: h.type, value: h.content.trim() })),
    };
    const url = f.originalId ? `${API}/${id}/cards/${encodeURIComponent(f.originalId)}` : `${API}/${id}/cards`;
    if (await this.update(url, { method: f.originalId ? 'PUT' : 'POST', body: JSON.stringify(card) })) {
      this.draft.set(null);
    }
  }

  remove(c: Card): void {
    this.confirmRemove.set(null);
    this.update(`${API}/${this.themeId()}/cards/${encodeURIComponent(c.id)}`, { method: 'DELETE' });
  }

  async reset(): Promise<void> {
    this.confirmReset.set(false);
    this.draft.set(null);
    await this.update(`${API}/${this.themeId()}/stored`, { method: 'DELETE' });
  }

  hintsTitle(c: Card): string {
    return c.hints.map(h => `${h.level}. ${h.text ?? h.value}`).join('\n');
  }

  when(iso: string | null): string {
    return iso ? new Date(iso).toLocaleString(this.i18n.lang()) : '';
  }

  /** Modification : la réponse est le thème tel qu'il est désormais servi. */
  private async update(url: string, init: RequestInit): Promise<boolean> {
    const d = await this.call<ThemeDetail>(url, init);
    if (d) {
      this.detail.set(d);
      this.themes.update(list => list.map(t => (t.id === d.id
        ? { ...t, cards: d.cards.length, stored: d.stored, updatedAt: d.updatedAt } : t)));
    }
    return !!d;
  }

  private async call<T>(url: string, init: RequestInit = {}): Promise<T | null> {
    this.busy.set(true);
    this.error.set(null);
    this.errors.set([]);
    try {
      const res = await apiFetch(url, init);
      if (!res.ok) {
        if (res.status === 401 || res.status === 403) {
          this.error.set(this.i18n.t('admin.forbidden'));
        } else {
          const body = await res.json().catch(() => ({}));
          this.error.set(body.detail ?? `HTTP ${res.status}`);
          this.errors.set(Array.isArray(body.errors) ? body.errors : []);
        }
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
