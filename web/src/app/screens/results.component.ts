import { Component, computed, effect, inject, signal } from '@angular/core';
import { GameService } from '../game/game.service';
import { TranslatePipe } from '../i18n/i18n';
import { BoardComponent } from '../ui/board.component';

/** Classement final et équipes ; en défi du jour, détail du score, rang et résultat à partager. */
@Component({
  selector: 'app-results',
  imports: [BoardComponent, TranslatePipe],
  template: `
    @if (game.room()?.result; as result) {
      @if (game.room()?.daily; as daily) {
        <section class="panel narrow">
          <h2>{{ 'results.dailyDone' | t }}</h2>
          @if (breakdown(); as b) {
            <p class="score-line">
              {{ 'results.teamValue' | t }} <strong>{{ b.value }}</strong> − {{ 'results.spent' | t }}
              <strong>{{ b.spent }}</strong> = <strong class="big">{{ b.score > 0 ? '+' : '' }}{{ b.score }}</strong>
            </p>
          }
          @if (daily.rank) { <p>{{ 'results.rank' | t: { rank: daily.rank } }}</p> }
          @if (daily.share) {
            <pre class="share">{{ daily.share }}</pre>
            <button class="btn" (click)="copy(daily.share)">{{ (copied() ? 'results.copied' : 'results.copy') | t }}</button>
          }
          @if (board(); as top) {
            <h3>{{ 'results.topToday' | t }}</h3>
            <ol class="ranking">
              @for (e of top; track $index; let i = $index) {
                <li [class.winner]="i === 0"><span>{{ medal(i) }} {{ e.name }}</span><span>{{ e.score > 0 ? '+' : '' }}{{ e.score }}</span></li>
              }
            </ol>
          }
          <button class="btn primary" (click)="game.leave()">{{ 'results.home' | t }}</button>
        </section>
      } @else {
        <section class="panel narrow">
          <h2>{{ 'results.title' | t }}</h2>
          <ol class="ranking">
            @for (id of result.ranking; track id; let i = $index) {
              <li [class.winner]="i === 0">
                <span>{{ medal(i) }} {{ game.playerName(id) }}</span>
                <span>{{ result.scores[id] }} {{ unit() | t }}@if (pointsDetail(id); as d) {
                  <small class="muted"> · {{ 'results.pointsDetail' | t: d }}</small> }@if (bonusOf(id) > 0) {
                  <small class="muted"> · {{ 'results.synergy' | t: { n: bonusOf(id) } }}</small> }</span>
              </li>
            }
          </ol>
          <button class="btn primary" (click)="game.leave()">{{ 'results.newGame' | t }}</button>
        </section>
      }
      <section class="boards">@for (p of game.room()?.players ?? []; track p.id) { <app-board [player]="p" /> }</section>
    }
  `,
})
export class ResultsComponent {
  readonly game = inject(GameService);
  readonly unit = computed(() => (this.game.room()?.verdictMode === 'vote' ? 'results.votes' : 'results.points'));
  readonly copied = signal(false);

  /** Détail du score « value » du joueur courant, recalculé pour l'affichage à partir des vues serveur. */
  readonly breakdown = computed(() => {
    const room = this.game.room();
    const me = room?.players.find(p => p.id === this.game.myId());
    if (!room?.result || !me) {
      return null;
    }
    const value = me.team.reduce((sum, t) => sum + (t.card?.value ?? 0), 0);
    const score = room.result.scores[me.id] ?? 0;
    return { value, spent: value - score, score };
  });

  /** Détail du verdict « points » : cartes − 25 par slot vide, l'or restant ne compte pas (calcul serveur : PointsVerdictMode). */
  pointsDetail(playerId: string): { cards: number; empty: number } | null {
    const room = this.game.room();
    const p = room?.players.find(x => x.id === playerId);
    if (room?.verdictMode !== 'points' || !p) {
      return null;
    }
    const cards = p.team.reduce((sum, t) => sum + (t.card?.points ?? 0), 0);
    const filled = p.team.filter(t => t.card).length;
    return { cards, empty: 25 * Math.max(0, room.slots.length - filled) };
  }

  readonly board = computed(() =>
    this.game.daily().find(d => d.presetId === this.game.room()?.presetId)?.leaderboard ?? null);

  constructor() {
    // Le score vient d'être enregistré : on rafraîchit le classement du jour.
    effect(() => {
      if (this.game.room()?.daily?.rank) {
        this.game.loadDaily();
      }
    });
  }

  async copy(text: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    } catch {
      // presse-papiers indisponible : le texte reste sélectionnable
    }
  }

  bonusOf(id: string): number {
    return this.game.room()?.players.find(p => p.id === id)?.bonus ?? 0;
  }

  medal(i: number): string {
    return ['🥇', '🥈', '🥉'][i] ?? `${i + 1}.`;
  }
}
