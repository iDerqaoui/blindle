package me.blindle.realtime.room;

import me.blindle.content.ContentRegistry;
import me.blindle.core.Phase;
import me.blindle.core.model.GameState;
import me.blindle.modes.DrawerBidMode;
import me.blindle.modes.ModeCatalog;
import me.blindle.modes.VoteVerdictMode;
import me.blindle.realtime.RealtimeConfig.RoomSettings;
import me.blindle.realtime.game.GameFactory;
import me.blindle.realtime.view.ViewProjector;
import me.blindle.rules.BudgetCapRule;
import me.blindle.rules.DiscardFeeRule;
import me.blindle.rules.RuleCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Économie du mode Blind Bid (2026-11-27) : on ne gagne jamais d'or. Acheter coûte le prix,
 * passer ne coûte rien, le piocheur qui laisse filer une carte invendue paie cher.
 */
class BlindBidGoldTest {

    @TempDir
    static Path content;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneOffset.UTC);
    private RoomTest.ManualTimers timers;
    private Room room;

    static GameFactory factory() throws IOException {
        Files.createDirectories(content.resolve("themes"));
        Files.createDirectories(content.resolve("presets"));
        String cards = IntStream.rangeClosed(1, 8).mapToObj(i -> """
                { "id": "h%d", "name": "Héros %d", "rarity": "rare",
                  "hints": [ { "level": 1, "type": "text", "text": "x" }, { "level": 2, "type": "initial", "value": "H" } ] }
                """.formatted(i, i)).collect(Collectors.joining(","));
        Files.writeString(content.resolve("themes/univers.json"), """
                { "id": "univers", "name": "Univers",
                  "rights": { "profile": "private", "channels": ["web"], "visibility": "private", "imagePolicy": "any" },
                  "defaultSlots": [ { "id": "slot-1", "label": "Carte 1" }, { "id": "slot-2", "label": "Carte 2" } ],
                  "cards": [ %s ] }
                """.formatted(cards));
        Files.writeString(content.resolve("presets/blind.json"), """
                { "id": "blind", "name": "Blind", "theme": "univers", "bidMode": "drawer", "verdictMode": "vote",
                  "settings": { "budget": 1000, "rounds": 8, "teamSize": 2, "hintLevels": 1, "bidTimerSec": 20 },
                  "rules": [ { "ref": "budget-cap" }, { "ref": "discard-fee" } ] }
                """);
        return new GameFactory(new ContentRegistry(content.toString()),
                new RuleCatalog(List.of(new BudgetCapRule(), new DiscardFeeRule())),
                new ModeCatalog(List.of(new DrawerBidMode()), List.of(new VoteVerdictMode())));
    }

    @BeforeEach
    void setUp() throws IOException {
        timers = new RoomTest.ManualTimers();
        room = new Room("GOLD", 3, factory().setup("blind"), new RoomSettings(2, 20, 60), timers,
                new RoomTest.Recorder(), clock);
        room.join("a", "Alice");
        room.join("b", "Bilal");
        room.start("a");
    }

    @Test
    void buyerPaysTheBankAndTheDrawerEarnsNothing() {
        room.bid("a", 50);
        room.bid("b", 120);
        room.pass("a");

        GameState s = room.snapshot().state();
        assertThat(s.player("b").orElseThrow().budget()).isEqualTo(880);
        assertThat(s.player("a").orElseThrow().budget()).isEqualTo(1000);
    }

    @Test
    void drawerWhoKeepsTheCardPaysItsOpening() {
        room.bid("a", 70);
        room.pass("b");

        assertThat(room.snapshot().state().player("a").orElseThrow().budget()).isEqualTo(930);
        assertThat(room.snapshot().state().player("b").orElseThrow().budget()).isEqualTo(1000);
    }

    @Test
    void lettingACardGoCostsTheDrawerDearlyAndPassingIsFree() {
        room.pass("a");
        room.pass("b");

        GameState s = room.snapshot().state();
        assertThat(s.discarded()).hasSize(1);
        assertThat(s.player("a").orElseThrow().budget()).isEqualTo(1000 - DiscardFeeRule.DEFAULT_AMOUNT);
        assertThat(s.player("b").orElseThrow().budget()).isEqualTo(1000);
        assertThat(s.phase()).isEqualTo(Phase.BIDDING);
    }

    @Test
    void roomViewCarriesTheRealFee() {
        assertThat(ViewProjector.room(room.snapshot()).ruleParams().get("discard-fee"))
                .containsEntry("amount", DiscardFeeRule.DEFAULT_AMOUNT);
    }
}
