package me.blindle.realtime.solo.who;

import me.blindle.content.Theme;
import me.blindle.core.GameException;
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
    private static final String U = "univers";

    /** 10 cartes : légendaire en 0 (jackpot), puis épique, rares, communes. */
    private static WhoLineup lineup() {
        String[] rarities = {"legendary", "epic", "epic", "rare", "rare", "rare", "common", "common", "common", "common"};
        List<WhoCard> cards = new ArrayList<>();
        for (int i = 0; i < rarities.length; i++) {
            cards.add(new WhoCard("c" + i, "Perso Numéro " + i, rarities[i], "https://img.test/" + i + ".jpg", "Univers"));
        }
        List<String> roster = cards.stream().map(WhoCard::name).toList();
        return new WhoLineup(DAY, "univers", "Univers", "#123456", cards, 0, roster);
    }

    private static Instant at(double seconds) {
        return T0.plusMillis((long) (seconds * 1000));
    }

    // ------------------------------------------------------------------ partie

    @Test
    void timeAloneNeverSharpensThePictureNorRaisesThePrice() {
        WhoRun run = new WhoRun("k", "Ana", lineup());
        run.next(T0);

        assertThat(run.level(at(3600))).isZero();
        assertThat(run.price(at(3600))).isEqualTo(40);
        assertThat(run.live(at(3600))).isTrue(); // pas de limite de temps
    }

    @Test
    void theJackpotStaysBlurryTwoGuessesLonger() {
        WhoRun run = new WhoRun("k", "Ana", lineup());
        run.next(T0); // carte 0 = jackpot
        for (String miss : List.of("Perso Numéro 1", "Perso Numéro 2", "Perso Numéro 3")) {
            run.guess(at(1), miss);
        }
        assertThat(run.level(at(1))).isEqualTo(3);
        assertThat(run.price(at(1))).isEqualTo(160);
        assertThat(run.imageLevel(at(1))).isEqualTo(1);
    }

    @Test
    void buyingAtTheRightMomentAndCallingTheNameDoublesThePoints() {
        WhoRun run = new WhoRun("k", "Ana", lineup());
        run.next(T0);

        run.guess(at(1), "Perso Numéro 5");
        var o = run.guess(at(2), "  perso numero 0 ");

        assertThat(o.price()).isEqualTo(70);
        assertThat(o.called()).isTrue();
        assertThat(o.jackpot()).isTrue();
        assertThat(run.budget()).isEqualTo(930);
        // 60 × 2 + 30 (jackpot) − 4 slots vides × 25
        assertThat(run.score()).isEqualTo(120 + 30 - 100);
        assertThatThrownBy(() -> run.buy(at(2))).hasFieldOrPropertyWithValue("code", "WHO_NOT_LIVE");
    }

    @Test
    void lettingACardGoIsFree() {
        WhoRun run = new WhoRun("k", "Ana", lineup());
        run.next(T0);

        run.pass(at(5));

        assertThat(run.outcomes()).singleElement().matches(o -> !o.bought() && o.price() == 0);
        assertThat(run.budget()).isEqualTo(1000);
        assertThatThrownBy(() -> run.pass(at(6))).hasFieldOrPropertyWithValue("code", "WHO_NOT_LIVE");
    }

    /** Achète la carte en jeu au palier voulu : autant de mauvais noms que de paliers. */
    private static void buyAtLevel(WhoRun run, int level) {
        int miss = 0;
        while (run.level(T0) < level) {
            String name = "Perso Numéro " + miss++;
            if (!name.equals(run.current().name())) {
                run.guess(T0, name);
            }
        }
        run.buy(T0);
    }

    @Test
    void wrongNameIsNotCalledAndCannotOverspend() {
        WhoRun run = new WhoRun("k", "Ana", lineup());
        run.next(T0);
        buyAtLevel(run, 6); // 400
        run.next(T0);
        buyAtLevel(run, 6); // 400 de plus
        run.next(T0);

        assertThat(run.budget()).isEqualTo(200);
        buyAtLevel(run, 1); // 70 : encore abordable
        run.next(T0);
        for (int i = 3; i <= 9; i++) {
            if (!("Perso Numéro " + i).equals(run.current().name())) {
                run.guess(T0, "Perso Numéro " + i);
            }
        }
        assertThatThrownBy(() -> run.buy(T0)).hasFieldOrPropertyWithValue("code", "WHO_TOO_EXPENSIVE");
        assertThat(run.budget()).isEqualTo(130);
    }

    @Test
    void fullTeamSkipsTheRestAndFinishes() {
        WhoRun run = new WhoRun("k", "Ana", lineup());
        for (int i = 0; i < 5; i++) {
            run.next(at(i * 20));
            run.buy(at(i * 20)); // 40 chacune
        }
        run.next(at(200));

        assertThat(run.finished()).isTrue();
        assertThat(run.outcomes()).hasSize(10);
        assertThat(run.outcomes().subList(5, 10)).allMatch(WhoRun.Outcome::skipped);
        assertThat(run.score()).isEqualTo(60 + 30 + 35 + 35 + 20 + 20); // aucun slot vide
        assertThat(run.share()).contains("🟨🟪🟪🟦🟦⬛⬛⬛⬛⬛").contains("🎰");
    }

    @Test
    void eachWrongNameMakesThePictureClearerAndPricier() {
        WhoRun run = new WhoRun("k", "Ana", lineup());
        run.next(at(20));
        run.pass(at(21));
        run.next(at(40)); // carte 1 (épique, pas le jackpot)
        Instant t = at(40.5);

        assertThat(run.guess(t, "Perso Numéro 7")).isNull();
        assertThat(run.level(t)).isEqualTo(1);
        assertThat(run.price(t)).isEqualTo(70);
        assertThat(run.guess(t, "perso numero 7")).isNull(); // même nom : pas de nouvelle pénalité
        assertThat(run.guess(t, "Perso Numéro 8")).isNull();
        assertThat(run.imageLevel(t)).isEqualTo(2);
        assertThat(run.wrongGuesses()).containsExactly("Perso Numéro 7", "Perso Numéro 8");
        assertThatThrownBy(() -> run.guess(t, "Inconnu au bataillon"))
                .hasFieldOrPropertyWithValue("code", "WHO_UNKNOWN_NAME");

        var o = run.guess(t, "Perso Numéro 1");
        assertThat(o.bought()).isTrue();
        assertThat(o.called()).isTrue();
        assertThat(o.price()).isEqualTo(110);
        assertThat(o.misses()).isEqualTo(2);
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
        WhoLineup a = WhoDaily.build(DAY, theme("aa", 40));
        WhoLineup b = WhoDaily.build(DAY, theme("aa", 40));

        assertThat(a.cards()).isEqualTo(b.cards()).hasSize(WhoDaily.CARDS);
        assertThat(a.cards()).filteredOn(c -> c.rarity().equals("legendary")).hasSize(1);
        assertThat(a.cards()).filteredOn(c -> c.rarity().equals("common")).hasSize(4);
        assertThat(a.cards().get(a.jackpotIndex()).rarity()).isEqualTo("legendary");
        assertThat(a.roster()).hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    void eachUniverseHasItsOwnDailyLineupThatChangesEveryDay() {
        WhoLineup aa = WhoDaily.build(DAY, theme("aa", 40));
        WhoLineup bb = WhoDaily.build(DAY, theme("bb", 40));

        assertThat(aa.themeId()).isEqualTo("aa");
        assertThat(aa.cards()).allMatch(c -> c.id().startsWith("aa-"));
        assertThat(bb.cards()).allMatch(c -> c.id().startsWith("bb-"));
        assertThat(aa.roster()).noneMatch(n -> n.startsWith("bb"));
        assertThat(WhoDaily.build(DAY.plusDays(1), theme("aa", 40)).cards()).isNotEqualTo(aa.cards());
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
            public synchronized WhoLineup lineup(LocalDate date, String theme) {
                return WhoTest.lineup();
            }
        };
        List<String> fetched = new ArrayList<>();
        Blurrer blurrer = new Blurrer(url -> {
            fetched.add(url);
            return new BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB);
        });
        MemoryWhoResults results = new MemoryWhoResults();
        // Ana a joué hier : sa série continue aujourd'hui.
        results.save(new WhoResults.Result(DAY.minusDays(1), "autre-univers", "u:1", "Ana", "U", 10, 0, "",
                T0.minusSeconds(86400)));
        WhoService who = new WhoService(daily, results, blurrer, clock, "UTC");

        WhoRun run = who.start(U, "u:1", "Ana");
        assertThat(who.start(U, "u:1", "Autre")).isSameAs(run); // pas de deuxième essai

        assertThat(who.image(U, "u:1", 0, 0)).isNotEmpty();
        // Palier pas encore atteint : on reçoit le palier atteint, jamais plus net.
        assertThat(who.image(U, "u:1", 0, 3)).isEqualTo(who.image(U, "u:1", 0, 0));
        assertThatThrownBy(() -> who.image(U, "u:1", 1, 0)).hasFieldOrPropertyWithValue("code", "WHO_IMAGE_LOCKED");
        assertThatThrownBy(() -> who.image(U, "u:2", 0, 0)).isInstanceOf(GameException.class);

        // Jusqu'au bout en laissant tout passer : score au classement, une seule fois.
        for (int i = 0; i < 10; i++) {
            if (i > 0) {
                who.next(U, "u:1");
            }
            who.pass(U, "u:1");
        }
        assertThat(who.find(U, "u:1").finished()).isTrue();
        assertThat(who.find(U, "u:1").rank()).isEqualTo(1);
        assertThat(results.count(DAY, U)).isEqualTo(1);
        assertThat(who.find(U, "u:1").streak()).isEqualTo(2);
        assertThat(who.find(U, "u:1").share()).contains("🔥2");
        assertThat(fetched).containsExactly("https://img.test/0.jpg");

        // Redémarrage : la partie en mémoire est perdue, le résultat enregistré interdit un 2e essai.
        WhoService restarted = new WhoService(daily, results, blurrer, clock, "UTC");
        assertThatThrownBy(() -> restarted.start(U, "u:1", "Ana")).hasFieldOrPropertyWithValue("code", "WHO_FINISHED");
        assertThat(restarted.mine(U, "u:1")).hasValueSatisfying(m -> {
            assertThat(m.rank()).isEqualTo(1);
            assertThat(m.streak()).isEqualTo(2);
        });
        assertThat(restarted.start(U, "u:2", "Bob").index()).isZero(); // un autre joueur, lui, peut jouer
    }

    @Test
    void rankingByScoreThenGoldLeftAndStreaksStopAtAGap() {
        MemoryWhoResults results = new MemoryWhoResults();
        results.save(new WhoResults.Result(DAY, U, "a", "A", "U", 100, 50, "", T0));
        results.save(new WhoResults.Result(DAY, U, "b", "B", "U", 100, 300, "", T0.plusSeconds(5)));
        int rank = results.save(new WhoResults.Result(DAY, U, "c", "C", "U", 120, 0, "", T0.plusSeconds(9)));
        results.save(new WhoResults.Result(DAY, "autre", "d", "D", "U", 999, 0, "", T0)); // autre classement

        assertThat(rank).isEqualTo(1);
        assertThat(results.top(DAY, U, 10)).extracting(e -> e.name()).containsExactly("C", "B", "A");
        assertThat(results.count(DAY, U)).isEqualTo(3);

        // Deux univers le même jour : la série ne compte ce jour qu'une fois.
        results.save(new WhoResults.Result(DAY, "autre", "a", "A", "U", 10, 0, "", T0));
        results.save(new WhoResults.Result(DAY.minusDays(1), U, "a", "A", "U", 10, 0, "", T0));
        assertThat(results.streak("a", DAY)).isEqualTo(2);

        List<LocalDate> days = List.of(DAY, DAY.minusDays(1), DAY.minusDays(2), DAY.minusDays(4));
        assertThat(WhoResults.streakOf(days, DAY)).isEqualTo(3);
        assertThat(WhoResults.streakOf(days, DAY.plusDays(1))).isZero();
    }
}
