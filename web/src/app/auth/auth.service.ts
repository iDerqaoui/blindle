import { Injectable, computed, signal } from '@angular/core';

/** Compte connecté (GET /api/me). */
export interface Account {
  id: string;
  displayName: string;
  avatarUrl: string | null;
  provider: string;
  admin: boolean;
}

/**
 * Appel d'API qui modifie quelque chose : renvoie le jeton CSRF que le serveur a posé dans le
 * cookie XSRF-TOKEN (Spring Security), via l'en-tête X-XSRF-TOKEN.
 */
export async function apiFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  const method = (init.method ?? 'GET').toUpperCase();
  if (method !== 'GET' && method !== 'HEAD') {
    const token = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='))?.slice('XSRF-TOKEN='.length);
    if (token) {
      headers.set('X-XSRF-TOKEN', decodeURIComponent(token));
    }
  }
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  return fetch(url, { ...init, headers, credentials: 'same-origin' });
}

/** Message d'erreur d'une réponse d'API (ProblemDetail de Spring), sinon le code HTTP. */
export async function apiError(res: Response): Promise<string> {
  try {
    const body = await res.json();
    return body.detail ?? body.message ?? `HTTP ${res.status}`;
  } catch {
    return `HTTP ${res.status}`;
  }
}

/**
 * Connexion facultative (Google / Discord) : jouer ne demande pas de compte. La connexion est une
 * redirection vers le serveur, qui revient sur la page d'accueil une fois le compte ouvert.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly me = signal<Account | null>(null);
  readonly providers = signal<string[]>([]);
  readonly loaded = signal(false);
  readonly isAdmin = computed(() => !!this.me()?.admin);

  constructor() {
    this.load();
  }

  async load(): Promise<void> {
    try {
      const [me, providers] = await Promise.all([fetch('/api/me'), fetch('/api/auth/providers')]);
      this.me.set(me.status === 200 ? await me.json() : null);
      this.providers.set(providers.ok ? await providers.json() : []);
    } catch {
      this.me.set(null);
    } finally {
      this.loaded.set(true);
    }
  }

  login(provider: string): void {
    window.location.href = `/oauth2/authorization/${encodeURIComponent(provider)}`;
  }

  async logout(): Promise<void> {
    await apiFetch('/api/auth/logout', { method: 'POST' });
    this.me.set(null);
    if (location.hash === '#admin') {
      location.hash = '';
    }
  }

  async rename(displayName: string): Promise<string | null> {
    const res = await apiFetch('/api/me', { method: 'PATCH', body: JSON.stringify({ displayName }) });
    if (!res.ok) {
      return apiError(res);
    }
    this.me.set(await res.json());
    return null;
  }
}
