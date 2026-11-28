import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { AuthService } from './auth/auth.service';
import { AdminComponent } from './screens/admin.component';
import { WhoComponent } from './screens/who.component';
import { GameService } from './game/game.service';
import { I18nService, LANGS, Lang, TranslatePipe } from './i18n/i18n';
import { HomeComponent } from './screens/home.component';
import { LobbyComponent } from './screens/lobby.component';
import { AuctionComponent } from './screens/auction.component';
import { TableComponent } from './screens/table.component';
import { VerdictComponent } from './screens/verdict.component';
import { ResultsComponent } from './screens/results.component';
import { TeamBoardComponent } from './ui/team-board.component';
import { ModalComponent } from './ui/modal.component';
import { LegalComponent, LegalPage } from './screens/legal.component';

const NOTICE_KEY = 'blindle.notice';
const LEGAL_PAGES: LegalPage[] = ['terms', 'privacy', 'legal'];

/**
 * Coque : barre du haut (logo → accueil, room, langue, compte), écran selon la phase reçue du
 * serveur ou la page demandée (#admin, #who, #terms, #privacy, #legal), pied de page légal et
 * bandeau d'entrée (cookies nécessaires seulement, droits des illustrations).
 */
@Component({
  selector: 'app-root',
  imports: [HomeComponent, LobbyComponent, AuctionComponent, TableComponent, VerdictComponent, ResultsComponent,
    TeamBoardComponent, ModalComponent, AdminComponent, WhoComponent, LegalComponent, TranslatePipe],
  template: `
    <div class="backdrop" aria-hidden="true">
      @if (ambiance(); as a) {
        @for (key of [a.key]; track key) {
          <div class="backdrop-universe" [class.quiet]="screen() === 'table'" [style.--universe]="a.color">
            @if (a.image) { <img class="backdrop-art" [src]="a.image" alt="" /> }
            @if (a.faces.length && screen() !== 'table') {
              <div class="cast cast-left">
                @for (f of a.faces.slice(0, 3); track f) { <img [src]="f" alt="" /> }
              </div>
              <div class="cast cast-right">
                @for (f of a.faces.slice(3, 6); track f) { <img [src]="f" alt="" /> }
              </div>
            }
          </div>
        }
      } @else {
        <div class="mosaic">
          @for (c of mosaic(); track $index) { <img [src]="c" alt="" loading="lazy" /> }
        </div>
      }
    </div>

    <header class="topbar">
      <button class="brand" (click)="goHome()" [attr.aria-label]="'app.home' | t">
        <img class="brand-mark" src="icon.svg" alt="" width="34" height="34" /><span class="brand-name">Blindle</span>
      </button>

      @if (game.room(); as room) {
        @if (game.me()) {
          <div class="room-info">
            <span class="room-universe">{{ room.themeName }}</span>
            <button class="room-code-chip" (click)="copyCode(room.code)" [title]="'app.copyCode' | t">
              {{ room.code }} <small>{{ (copied() ? 'app.copied' : 'app.copy') | t }}</small>
            </button>
            <span class="muted small">{{ i18n.plural('app.players', room.players.length) }}</span>
          </div>
        }
      }

      <div class="topbar-end">
        <label class="lang-select">
          <span class="sr-only">{{ 'app.language' | t }}</span>
          <span aria-hidden="true">🌐</span>
          <select [value]="i18n.lang()" (change)="setLang($event)" [attr.aria-label]="'app.language' | t">
            @for (l of langs; track l.code) {
              <option [value]="l.code" [selected]="l.code === i18n.lang()">{{ l.label }}</option>
            }
          </select>
        </label>
        <span class="status-dot" [class.ok]="game.connected()"
              [title]="(game.connected() ? 'app.connected' : 'app.offline') | t"></span>
        @if (inRoom()) {
          <button class="btn ghost small" (click)="confirmLeave.set(true)">{{ 'app.leave' | t }}</button>
        } @else if (auth.me(); as me) {
          <button class="account-chip" (click)="accountOpen.set(true)" [title]="'account.title' | t">
            @if (me.avatarUrl) { <img [src]="me.avatarUrl" alt="" referrerpolicy="no-referrer" /> }
            @else { <span class="account-initial">{{ me.displayName.charAt(0) }}</span> }
            <span class="account-name">{{ me.displayName }}</span>
          </button>
        } @else {
          <button class="btn ghost small" (click)="openLogin('login')">{{ 'account.login' | t }}</button>
          <button class="btn primary small signup" (click)="openLogin('signup')">{{ 'account.signup' | t }}</button>
        }
      </div>
    </header>

    @if (loginError()) {
      <div class="toast" role="alert">{{ 'account.loginError' | t }}</div>
    }

    @if (game.error(); as error) {
      <div class="toast" role="alert">{{ error }}</div>
    }

    <main [class.wide]="screen() === 'table'">
      @switch (screen()) {
        @case ('legal') { <app-legal [page]="legalPage()!" /> }
        @case ('admin') { <app-admin /> }
        @case ('who') { <app-who [theme]="whoTheme()" /> }
        @case ('home') { <app-home /> }
        @case ('lobby') { <app-lobby /> }
        @case ('table') { <app-table /> }
        @case ('game') {
          <div class="game-layout">
            <app-auction />
            <app-team-board />
          </div>
          <details class="panel log">
            <summary>{{ 'app.log' | t }}</summary>
            <ul>@for (line of log(); track $index) { <li>{{ line }}</li> }</ul>
          </details>
        }
        @case ('verdict') { <app-verdict /> }
        @case ('results') { <app-results /> }
      }
    </main>

    @if (!inRoom() || screen() === 'legal') {
      <footer class="site-footer">
        <p class="footer-rights">{{ 'footer.rights' | t }}</p>
        <nav class="footer-links">
          <a href="#terms">{{ 'legal.terms.title' | t }}</a>
          <a href="#privacy">{{ 'legal.privacy.title' | t }}</a>
          <a href="#legal">{{ 'legal.legal.title' | t }}</a>
          <a href="mailto:contact@blindle.app">{{ 'footer.contact' | t }}</a>
        </nav>
      </footer>
    }

    @if (!noticeSeen()) {
      <div class="entry-notice" role="region" [attr.aria-label]="'notice.ok' | t">
        <p>{{ 'notice.text' | t }}
          <a href="#privacy">{{ 'legal.privacy.title' | t }}</a> ·
          <a href="#terms">{{ 'legal.terms.title' | t }}</a>
        </p>
        <button class="btn primary small" (click)="dismissNotice()">{{ 'notice.ok' | t }}</button>
      </div>
    }

    @if (confirmLeave()) {
      <app-modal [title]="'leave.title' | t" (closed)="confirmLeave.set(false)">
        <p>{{ 'leave.body' | t: { code: game.room()?.code } }}</p>
        <div class="modal-actions">
          <button class="btn" (click)="confirmLeave.set(false)">{{ 'leave.stay' | t }}</button>
          <button class="btn danger" (click)="leave()">{{ 'leave.confirm' | t }}</button>
        </div>
      </app-modal>
    }

    @if (loginOpen()) {
      <app-modal [title]="(loginMode() === 'signup' ? 'account.signupTitle' : 'account.loginTitle') | t"
                 (closed)="loginOpen.set(false)">
        <p class="muted">{{ (loginMode() === 'signup' ? 'account.signupBody' : 'account.loginBody') | t }}</p>
        @if (auth.oauthProviders().length) {
          <div class="login-buttons">
            @for (p of auth.oauthProviders(); track p) {
              <button class="btn big" [class]="'btn big login-' + p" (click)="auth.login(p)">
                {{ 'account.with.' + p | t }}
              </button>
            }
          </div>
          <p class="muted small">
            {{ 'account.agree' | t }}
            <a href="#terms" (click)="loginOpen.set(false)">{{ 'legal.terms.title' | t }}</a> ·
            <a href="#privacy" (click)="loginOpen.set(false)">{{ 'legal.privacy.title' | t }}</a>
          </p>
        } @else {
          <p class="admin-error">{{ 'account.noProvider' | t }}</p>
        }
      </app-modal>
    }

    @if (accountOpen() && auth.me(); as me) {
      <app-modal [title]="'account.title' | t" (closed)="accountOpen.set(false)">
        <label class="name-field">
          <span>{{ 'account.displayName' | t }}</span>
          <input class="input" #dn [value]="me.displayName" maxlength="20" />
        </label>
        @if (renameError(); as e) { <p class="admin-error">{{ e }}</p> }
        <p class="muted small">{{ 'account.via' | t: { provider: ('account.provider.' + me.provider | t) } }}</p>
        <div class="modal-actions">
          @if (me.admin) {
            <a class="btn" href="#admin" (click)="accountOpen.set(false)">{{ 'account.admin' | t }}</a>
          }
          <button class="btn ghost" (click)="logout()">{{ 'account.logout' | t }}</button>
          <button class="btn primary" (click)="rename(dn.value)">{{ 'account.save' | t }}</button>
        </div>
        <div class="account-danger">
          @if (confirmDelete()) {
            <p>{{ 'account.deleteConfirm' | t }}</p>
            <div class="modal-actions">
              <button class="btn" (click)="confirmDelete.set(false)">{{ 'account.deleteNo' | t }}</button>
              <button class="btn danger" (click)="deleteAccount()">{{ 'account.deleteYes' | t }}</button>
            </div>
          } @else {
            <button class="btn ghost small danger-text" (click)="confirmDelete.set(true)">{{ 'account.delete' | t }}</button>
          }
        </div>
      </app-modal>
    }
  `,
})
export class App {
  readonly game = inject(GameService);
  readonly i18n = inject(I18nService);
  readonly langs = LANGS;
  readonly confirmLeave = signal(false);
  readonly copied = signal(false);

  readonly auth = inject(AuthService);
  readonly loginOpen = signal(false);
  readonly accountOpen = signal(false);
  readonly renameError = signal<string | null>(null);
  /** Retour d'une connexion refusée ou annulée chez le fournisseur (/?login=error). */
  readonly loginError = signal(new URLSearchParams(location.search).get('login') === 'error');
  /** Page admin : #admin, hors room, réservée aux comptes admin (le serveur vérifie aussi). */
  readonly hash = signal(location.hash);

  /** Fenêtre de connexion : « Se connecter » ou « S'inscrire » (même parcours Google / Discord). */
  readonly loginMode = signal<'login' | 'signup'>('login');
  readonly confirmDelete = signal(false);
  /** Bandeau d'entrée (cookies nécessaires, droits des illustrations) déjà fermé sur ce navigateur. */
  readonly noticeSeen = signal(storageGet(NOTICE_KEY) === '1');
  /** Page légale demandée (#terms, #privacy, #legal), sinon null. */
  readonly legalPage = computed(() => {
    const page = this.hash().slice(1) as LegalPage;
    return LEGAL_PAGES.includes(page) ? page : null;
  });

  readonly inRoom = computed(() => !!this.game.room() && !!this.game.me());
  /** Défi Who's That? : univers choisi (#who/<thème>), ou null pour la liste des univers. */
  readonly whoTheme = computed(() => {
    const h = this.hash();
    return h.startsWith('#who/') ? decodeURIComponent(h.slice('#who/'.length)) || null : null;
  });

  constructor() {
    if (this.loginError()) {
      history.replaceState(null, '', location.pathname + location.hash);
      setTimeout(() => this.loginError.set(false), 5000);
    }
  }

  @HostListener('window:hashchange')
  onHashChange(): void {
    this.hash.set(location.hash);
  }

  readonly screen = computed(() => {
    // Pages légales : accessibles partout (la room continue en arrière-plan, « retour » la rend).
    if (this.legalPage()) {
      return 'legal';
    }
    const room = this.game.room();
    const phase = room?.phase;
    if (!phase || !this.game.me()) {
      if (this.hash() === '#who' || this.hash().startsWith('#who/')) {
        return 'who';
      }
      return this.hash() === '#admin' && this.auth.isAdmin() ? 'admin' : 'home';
    }
    switch (phase) {
      case 'LOBBY': return 'lobby';
      case 'VERDICT': return 'verdict';
      case 'FINISHED': return 'results';
      default: return room!.bidMode === 'drawer' ? 'table' : 'game';
    }
  });

  /**
   * Habillage du fond : l'univers de la room, sinon celui regardé à l'accueil.
   * La clé change avec l'image, ce qui recrée le calque et rejoue le fondu.
   */
  readonly ambiance = computed(() => {
    const room = this.game.room();
    const art = (this.inRoom() ? room?.art : null) ?? this.game.previewArt();
    if (!art) {
      return null;
    }
    const presetId = this.inRoom() ? room?.presetId : null;
    const preset = this.game.presets().find(p => (presetId ? p.id === presetId : p.art?.cover === art.cover));
    const image = art.banner ?? art.cover;
    return { key: image ?? art.color ?? 'none', image, color: art.color ?? '#6b5bd6', faces: preset?.faces ?? [] };
  });

  /** Accueil généraliste : affiches de tous les univers, répétées pour couvrir l'écran. */
  readonly mosaic = computed(() => {
    const covers = this.game.presets().map(p => p.art?.cover).filter((c): c is string => !!c);
    return covers.length ? Array.from({ length: 24 }, (_, i) => covers[i % covers.length]) : [];
  });

  readonly log = computed(() => [...(this.game.room()?.log ?? [])].reverse());

  setLang(event: Event): void {
    this.i18n.use((event.target as HTMLSelectElement).value as Lang);
  }

  leave(): void {
    this.confirmLeave.set(false);
    this.game.leave();
    this.clearHash();
  }

  async rename(name: string): Promise<void> {
    const error = await this.auth.rename(name.trim());
    this.renameError.set(error);
    if (!error) {
      this.accountOpen.set(false);
    }
  }

  openLogin(mode: 'login' | 'signup'): void {
    this.loginMode.set(mode);
    this.loginOpen.set(true);
  }

  async deleteAccount(): Promise<void> {
    const error = await this.auth.deleteAccount();
    this.confirmDelete.set(false);
    this.renameError.set(error);
    if (!error) {
      this.accountOpen.set(false);
    }
  }

  /** Logo : retour à l'accueil (en room : confirmation, puisque quitter fait perdre sa place). */
  goHome(): void {
    if (this.inRoom() && !this.legalPage()) {
      this.confirmLeave.set(true);
      return;
    }
    this.clearHash();
  }

  dismissNotice(): void {
    this.noticeSeen.set(true);
    try {
      localStorage.setItem(NOTICE_KEY, '1');
    } catch {
      // stockage indisponible : le bandeau reviendra à la prochaine visite
    }
  }

  private clearHash(): void {
    if (location.hash) {
      history.pushState(null, '', location.pathname + location.search);
    }
    this.hash.set('');
    window.scrollTo({ top: 0 });
  }

  async logout(): Promise<void> {
    this.accountOpen.set(false);
    await this.auth.logout();
  }

  copyCode(code: string): void {
    navigator.clipboard?.writeText(code).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    });
  }
}

function storageGet(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}
