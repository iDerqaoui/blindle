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
import me.blindle.realtime.view.Views;
import me.blindle.rules.AntiSnipeRule;
import me.blindle.rules.BudgetCapRule;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mode « Anime Blind Bid » de bout en bout, sur un univers synthétique (aucune image réelle). */
class BlindBidRoomTest {

    @TempDir
    static Path content;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC);
    private RoomTest.ManualTimers timers;
    private Room room;

    static GameFactory factory() throws IOException {
        Files.createDirectories(content.resolve("themes"));
        Files.createDirectories(content.resolve("presets"));
        String cards = IntStream.rangeClosed(1, 12).mapToObj(i -> """
                { "id": "h%d", "name": "Héros %d", "rarity": "rare", "stats": { "popularity": %d },
                  "image": "https://img.test/h%d.png",
                  "hints": [ { "level": 1, "type": "text", "text": "x" }, { "level": 2, "type": "initial", "value": "H" } ] }
                """.formatted(i, i, 40 + i, i)).collect(Collectors.joining(","));
        Files.writeString(content.resolve("themes/univers.json"), """
                { "id": "univers", "name": "Univers", "art": { "cover": "https://img.test/cover.png", "color": "#e49335" },
                  "rights": { "profile": "private", "channels": ["web"], "visibility": "private", "imagePolicy": "any" },
                  "defaultSlots": [ { "id": "slot-1", "label": "Carte 1" }, { "id": "slot-2", "label": "Carte 2" } ],
                  "attributes": ["popularity"], "cards": [ %s ] }
                """.formatted(cards));
        Files.writeString(content.resolve("presets/blind.json"), """
                { "id": "blind", "name": "Blind", "theme": "univers", "bidMode": "drawer", "verdictMode": "vote",
                  "settings": { "budget": 1000, "rounds": 12, "teamSize": 2, "hintLevels": 1, "bidTimerSec": 20 },
                  "rules": [ { "ref": "budget-cap" }, { "ref": "anti-snipe", "params": { "extendSeconds": 5 } } ] }
                """);
        return new GameFactory(new ContentRegistry(content.toString()),
                new RuleCatalog(List.of(new BudgetCapRule(), new AntiSnipeRule())),
                new ModeCatalog(List.of(new DrawerBidMode()), List.of(new VoteVerdictMode())));
    }

    @BeforeEach
    void setUp() throws IOException {
        timers = new RoomTest.ManualTimers();
        room = new Room("BLND", 3, factory().setup("blind"), new RoomSettings(2, 20, 60), timers,
                new RoomTest.Recorder(), clock);
        room.join("a", "Alice");
        room.join("b", "Bilal");
        room.start("a");
    }

    @Test
    void onlyTheDrawerSeesTheCard() {
        RoomSnapshot snap = room.snapshot();
        Views.RoomView pub = ViewProjector.room(snap);
        assertThat(pub.drawerId()).isEqualTo("a");
        assertThat(pub.lot().cards()).isNull();
        assertThat(pub.lot().hints()).isEmpty();
        assertThat(pub.art().color()).isEqualTo("#e49335");

        Views.CardView drawn = ViewProjector.player(snap, "a").drawnCard();
        assertThat(drawn).isNotNull();
        assertThat(drawn.image()).startsWith("https://img.test/");
        assertThat(ViewProjector.player(snap, "b").drawnCard()).isNull();
    }

    @Test
    void drawerOpensOthersOverbidAndTheCardGoesToTheBoard() {
        assertThatThrownBy(() -> room.bid("b", 50)).hasFieldOrPropertyWithValue("code", "DRAWER_FIRST");
        room.bid("a", 50);
        room.bid("b", 120);
        room.pass("a");

        GameState s = room.snapshot().state();
        assertThat(s.phase()).isEqualTo(Phase.REVEALED);
        assertThat(ViewProjector.room(room.snapshot()).lot().cards()).hasSize(1);
        timers.fire(); // pause de révélation, puis pose automatique

        s = room.snapshot().state();
        assertThat(s.player("b").orElseThrow().team()).hasSize(1);
        assertThat(s.player("b").orElseThrow().budget()).isEqualTo(880);
        assertThat(s.phase()).isEqualTo(Phase.BIDDING);
        assertThat(ViewProjector.room(room.snapshot()).drawerId()).isEqualTo("b"); // le piocheur tourne
    }

    @Test
    void cardNobodyWantsGoesToTheTrashVisibleToAll() {
        room.pass("a");
        room.pass("b");

        Views.RoomView view = ViewProjector.room(room.snapshot());
        assertThat(view.trash()).hasSize(1);
        assertThat(view.trash().get(0).image()).isNotNull();
        assertThat(view.round()).isEqualTo(2);
    }

    @Test
    void fullGameUntilVote() {
        for (int guard = 0; guard < 100 && room.snapshot().state().phase() != Phase.VERDICT; guard++) {
            GameState s = room.snapshot().state();
            if (s.phase() == Phase.REVEALED) {
                timers.fire();
                continue;
            }
            String drawer = room.snapshot().setup().bidMode().drawerId(s);
            String other = drawer.equals("a") ? "b" : "a";
            room.bid(drawer, 100);
            if (room.snapshot().state().phase() == Phase.BIDDING) {
                room.pass(other);
            }
        }
        GameState s = room.snapshot().state();
        assertThat(s.phase()).isEqualTo(Phase.VERDICT);
        assertThat(s.players()).allSatisfy(p -> assertThat(p.team()).hasSize(2));
        assertThat(s.roundIndex()).isEqualTo(4); // fini dès que les deux équipes sont pleines
    }
}
