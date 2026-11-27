package me.blindle.realtime.solo.who;

import me.blindle.content.Theme;
import me.blindle.core.GameException;
import me.blindle.realtime.solo.DailyLeaderboard;
import me.blindle.realtime.solo.who.WhoLineup.WhoCard;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Défi « Who's That? Auction » : prix et netteté à l'heure serveur, jackpot, score, essai unique. */
class WhoTest {

    private static final Instant T0 = Instant.parse("2026-11-27T10:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 27);

    /** 10 cartes : légendaire en 0 (jackpot), puis épique, rares, communes. */
    private static WhoLineup lineup() {
        String[] rarities = {"legendary", "epic", "epic", "rare", "rare", "rare", "common", "common", "common", "common"};
        List<WhoCard> cards = new ArrayList<>();
        for (int i = 0; i < rarities.length; i++) {
            cards.add(new WhoCard("c" + i, "Perso Numéro " + i, rarities[i], "https://img.test/" + i + ".jpg", "Univers"));
        }
        return new WhoLineup(DAY, "Univers", "#123456", cards, 0, List.of("Perso Numéro 0"));
    }

    private static Instant at(double seconds) {
        return T0.plusMillis((long) (seconds * 1000));
    }

    // ------------------------------------------------------------------ partie

    @Test
    void priceAndSharpnessClimbTogetherAndTheJackpotStaysBlurry() {
        WhoRun run = new WhoRun("k", "Ana", lineup());
        run.next(T0);

        assertThat(run.price(at(0))).isEqualTo(40);
        assertThat(run.level(at(1.6))).isEqualTo(1);
        assertThat(run.price(at(4.6))).isEqualTo(160);
        assertThat(run.level(at(30 - 20))).isEqualTo(WhoRun.LEVELS - 1); // plafonné
        // La légendaire jackpot reste floue 2 paliers de plus, au même prix.
        assertThat(run.imageLevel(at(4.6))).isEqualTo(1);
        assertThat(run.imageLevel(at(1))).isZero();
    }

    @Test
    void buyingAtTheRightMomentAndCallingTheNameDoublesThePoints() {
        WhoRun run = new WhoRun("k", "Ana", lineup());
        run.next(T0);

        var o = run.buy(at(1.6), "  perso numero 0 ");

        assertThat(o.price()).isEqualTo(70);
        assertThat(o.called()).isTrue();
        assertThat(o.jackpot()).isTrue();
        assertThat(run.budget()).isEqualTo(930);
        // 60 × 2 + 30 (jackpot) − 4 slots vides × 25
        assertThat(run.score()).isEqualTo(120 + 30 - 100);
        assertThatThrownBy(() -> run.buy(at(2), null)).hasFieldOrPropertyWithValue("code", "WHO_NOT_LIVE");
    }

    @Test
    void doingNothingLetsTheCardGoForFree() {
        WhoRun run = new WhoRun("k", "Ana", lineup());
        run.next(T0);

        assertThat(run.live(at(11.9))).isTrue();
        assertThat(run.live(at(12))).isFalse();

        assertThat(run.outcomes()).singleElement().matches(o -> !o.bought() && o.price() == 0);
        assertThat(run.budget()).isEqualTo(1000);
        assertThatThrownBy(() -> run.pass(at(12))).hasFieldOrPropertyWithValue("code", "WHO_NOT_LIVE");
    }

    @Test
    void wrongNameIsNotCalledAndCannotOverspend() {
        WhoRun run = new WhoRun("k", "Ana", lineup());
        run.next(T0);
        assertThat(run.buy(at(9), "Quelqu'un d'autre").called()).isFalse(); // 400
        run.next(at(20));
        run.buy(at(29), null); // 400 de plus
        run.next(at(40));

        assertThat(run.budget()).isEqualTo(200);
        assertThat(run.buy(at(42), null).price()).isEqualTo(70); // tôt : encore abordable
        run.next(at(60));
        assertThatThrownBy(() -> run.buy(at(69), null)).hasFieldOrPropertyWithValue("code", "WHO_TOO_EXPENSIVE");
        assertThat(run.budget()).isEqualTo(130);
    }

    @Test
    void fullTeamSkipsTheRestAndFinishes() {
        WhoRun run = new WhoRun("k", "Ana", lineup());
        for (int i = 0; i < 5; i++) {
            run.next(at(i * 20));
            run.buy(at(i * 20), null); // 40 chacune
        }
        run.next(at(200));

        assertThat(run.finished()).isTrue();
        assertThat(run.outcomes()).hasSize(10);
        assertThat(run.outcomes().subList(5, 10)).allMatch(WhoRun.Outcome::skipped);
        assertThat(run.score()).isEqualTo(60 + 30 + 35 + 35 + 20 + 20); // aucun slot vide
        assertThat(WhoController.share(run)).contains("🟨🟪🟪🟦🟦⬛⬛⬛⬛⬛").contains("🎰");
    }

    // ------------------------------------------------------------------ choix du jour

    private static Theme theme(String id, int cards) {
        List<Theme.CardDefinition> defs = new ArrayList<>();
        String[] rarities = {"legendary", "epic", "rare", "common"};
        for (int i = 0; i < cards; i++) {
            defs.add(new Theme.CardDefinition(id + "-" + i, id + " " + i, Set.of(), rarities[i % 4], false, null,
                    "https://img.test/" + id + i + ".jpg", List.of()));
        }
        return new Theme(id, id.toUpperCase(), "1", "", null, new Theme.Art(null, null, "#abcdef"), List.of(),
                List.of(), defs);
    }

    @Test
    void sameDaySameLineupWithTheTargetMixAndAJackpot() {
        List<Theme> themes = List.of(theme("aa", 40), theme("bb", 40));

        WhoLineup a = WhoDaily.build(DAY, themes);
        WhoLineup b = WhoDaily.build(DAY, themes);

        assertThat(a.cards()).isEqualTo(b.cards()).hasSize(WhoDaily.CARDS);
        assertThat(a.cards()).filteredOn(c -> c.rarity().equals("legendary")).hasSize(1);
        assertThat(a.cards()).filteredOn(c -> c.rarity().equals("common")).hasSize(4);
        assertThat(a.cards().get(a.jackpotIndex()).rarity()).isEqualTo("legendary");
        assertThat(a.roster()).hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    void universesRotateWithACrossoverDay() {
        List<Theme> themes = List.of(theme("aa", 40), theme("bb", 40));
        List<String> universes = new ArrayList<>();
        for (int d = 0; d < 3; d++) {
            universes.add(WhoDaily.build(LocalDate.ofEpochDay(d), themes).universe());
        }
        assertThat(universes).containsExactlyInAnyOrder("AA", "BB", WhoDaily.CROSSOVER);
    }

    // ------------------------------------------------------------------ service et images

    @Test
    void oneTryPerDayAndImagesOnlyUpToTheReachedLevel() {
        AtomicReference<Instant> now = new AtomicReference<>(T0);
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        WhoDaily daily = new WhoDaily(null) {
            @Override
            public synchronized WhoLineup lineup(LocalDate date) {
                return WhoTest.lineup();
            }
        };
        List<String> fetched = new ArrayList<>();
        Pixelator pixelator = new Pixelator(url -> {
            fetched.add(url);
            return new BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB);
        });
        DailyLeaderboard board = new DailyLeaderboard();
        WhoService who = new WhoService(daily, board, pixelator, clock, "UTC");

        WhoRun run = who.start("u:1", "Ana");
        assertThat(who.start("u:1", "Autre")).isSameAs(run); // pas de deuxième essai

        assertThat(who.image("u:1", 0, 0)).isNotEmpty();
        // Palier pas encore atteint : on reçoit le palier atteint, jamais plus net.
        assertThat(who.image("u:1", 0, 3)).isEqualTo(who.image("u:1", 0, 0));
        assertThatThrownBy(() -> who.image("u:1", 1, 0)).hasFieldOrPropertyWithValue("code", "WHO_IMAGE_LOCKED");
        assertThatThrownBy(() -> who.image("u:2", 0, 0)).isInstanceOf(GameException.class);

        // Jusqu'au bout en laissant tout passer : score au classement, une seule fois.
        for (int i = 0; i < 10; i++) {
            if (i > 0) {
                who.next("u:1");
            }
            who.pass("u:1");
        }
        assertThat(who.find("u:1").finished()).isTrue();
        assertThat(who.find("u:1").rank()).isEqualTo(1);
        assertThat(board.size(DAY, WhoService.LEADERBOARD_ID)).isEqualTo(1);
        assertThat(fetched).containsExactly("https://img.test/0.jpg");
    }
}
