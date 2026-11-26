import { Injectable, Pipe, PipeTransform, inject, signal } from '@angular/core';
import { EN } from './en';
import { FR } from './fr';

export type Lang = 'en' | 'fr';
export type Dict = Record<string, string>;

/** Langues proposées, dans l'ordre du sélecteur. */
export const LANGS: { code: Lang; label: string }[] = [
  { code: 'en', label: 'English' },
  { code: 'fr', label: 'Français' },
];

const DICTS: Record<Lang, Dict> = { en: EN, fr: FR };
const STORAGE_KEY = 'blindle.lang';

/**
 * Traductions de l'interface, sans dépendance externe : la langue est un signal, tout ce qui
 * l'affiche se met à jour au changement. Anglais par défaut ; le choix est mémorisé.
 * Clé absente : anglais, puis la clé elle-même (repérable en développement).
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly lang = signal<Lang>(initialLang());

  constructor() {
    document.documentElement.lang = this.lang();
  }

  use(lang: Lang): void {
    this.lang.set(lang);
    document.documentElement.lang = lang;
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // stockage indisponible : la langue vaut pour la session
    }
  }

  /** Texte traduit ; {nom} est remplacé par params.nom. */
  t(key: string, params?: Record<string, string | number | null | undefined>): string {
    const text = DICTS[this.lang()][key] ?? EN[key] ?? key;
    if (!params) {
      return text;
    }
    return text.replace(/\{(\w+)\}/g, (_, name: string) => String(params[name] ?? ''));
  }

  /** Variante singulier / pluriel : clé.one ou clé.other, {n} vaut le nombre. */
  plural(key: string, n: number, params?: Record<string, string | number | null | undefined>): string {
    return this.t(`${key}.${n === 1 ? 'one' : 'other'}`, { n, ...params });
  }

  has(key: string): boolean {
    return key in DICTS[this.lang()] || key in EN;
  }
}

/** {{ 'key' | t }} ou {{ 'key' | t: { name: x } }} — impur : suit le changement de langue. */
@Pipe({ name: 't', pure: false })
export class TranslatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(key: string, params?: Record<string, string | number | null | undefined>): string {
    return this.i18n.t(key, params);
  }
}

function initialLang(): Lang {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'en' || saved === 'fr') {
      return saved;
    }
  } catch {
    // ignore
  }
  return 'en';
}
