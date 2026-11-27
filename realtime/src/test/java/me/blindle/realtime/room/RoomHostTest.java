package me.blindle.realtime.room;

import me.blindle.content.Preset;
import me.blindle.core.Phase;
import me.blindle.modes.bot.StingyBot;
import me.blindle.realtime.RealtimeConfig.RoomSettings;
import me.blindle.realtime.game.GameSetup;
import me.blindle.realtime.game.RoomOverrides;
import me.blindle.realtime.room.RoomTest.ManualTimers;
import me.blindle.realtime.room.RoomTest.Recorder;
import me.blindle.realtime.view.ViewProjector;
import me.blindle.realtime.view.Views;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static me.blindle.realtime.room.RoomTest.FACTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Passation d'hôte et personnalisation d'une room par son hôte. */
class RoomHostTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneOffset.UTC);
    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room("HOST", 7, FACTORY.setup("manga-classique"), new RoomSettings(2, 20, 60),
                new ManualTimers(), new Recorder(), clock);
        room.join("a", "Alice");
        room.join("b", "Bilal");
        room.join("c", "Chloé");
    }

    // ------------------------------------------------------------------ passation

    /** Fin du délai de reconnexion. */
    private void graceOver() {
        room.sweep(clock.instant().plus(Room.RECONNECT_GRACE).plusSeconds(1));
    }

    @Test
    void lobbyDisconnectRemovesPlayerAndPassesHostAfterTheGrace() {
        room.disconnect("a");
        assertThat(room.snapshot().state().players()).hasSize(3); // encore dans le délai
        assertThat(room.snapshot().hostId()).isEqualTo("a");

        graceOver();

        var snap = room.snapshot();
        assertThat(snap.state().players()).extracting(p -> p.id()).containsExactly("b", "c");
        assertThat(snap.hostId()).isEqualTo("b");
        room.start("b");
        assertThat(room.snapshot().state().phase()).isEqualTo(Phase.BIDDING);
    }

    @Test
    void inGameDisconnectKeepsPlayerAndSkipsDisconnectedForHost() {
        room.start("a");
        room.disconnect("b");
        room.disconnect("a");
        graceOver();

        var snap = room.snapshot();
        assertThat(snap.state().players()).hasSize(3);
        assertThat(snap.hostId()).isEqualTo("c");
        var view = ViewProjector.room(snap);
        assertThat(view.players()).filteredOn(p -> p.id().equals("a")).allMatch(p -> !p.connected());
        assertThat(view.players()).filteredOn(p -> p.id().equals("c")).allMatch(p -> p.host() && p.connected());
    }

    @Test
    void hostPassesInCircleAndNeverToBots() {
        room.addBot("a", new StingyBot());
        room.start("a");
        room.transferHost("a", "c");
        room.disconnect("b");
        room.disconnect("c");
        graceOver();

        // Après « c » vient le bot, puis on reprend au début : « a ».
        assertThat(room.snapshot().hostId()).isEqualTo("a");
        room.disconnect("a");
        graceOver();
        assertThat(room.snapshot().hostId()).isNull();

        room.sync("b"); // « b » revient : la room retrouve un hôte
        assertThat(room.snapshot().hostId()).isEqualTo("b");
    }

    @Test
    void reloadingWithinTheGraceKeepsSeatAndHost() {
        room.disconnect("a");
        room.sync("a"); // reconnecté avec son jeton : même identité
        graceOver();

        var snap = room.snapshot();
        assertThat(snap.hostId()).isEqualTo("a");
        assertThat(snap.state().players()).hasSize(3);
        assertThat(ViewProjector.room(snap).players()).allMatch(Views.PlayerSummary::connected);
    }

    @Test
    void inGameReturningPlayerFindsTeamAndBudget() {
        room.start("a");
        room.disconnect("b");
        graceOver();
        int budget = room.snapshot().state().player("b").orElseThrow().budget();

        room.sync("b");

        assertThat(room.snapshot().disconnected()).doesNotContain("b");
        assertThat(room.snapshot().state().player("b").orElseThrow().budget()).isEqualTo(budget);
        assertThat(room.snapshot().hostId()).isEqualTo("a");
    }

    @Test
    void transferHostOnlyToConnectedHuman() {
        assertThatThrownBy(() -> room.transferHost("b", "c")).hasFieldOrPropertyWithValue("code", "NOT_HOST");
        room.start("a");
        room.disconnect("c");
        assertThatThrownBy(() -> room.transferHost("a", "c")).hasFieldOrPropertyWithValue("code", "BAD_HOST");

        room.transferHost("a", "b");

        assertThat(room.snapshot().hostId()).isEqualTo("b");
        assertThat(ViewProjector.player(room.snapshot(), "b").host()).isTrue();
    }

    // ------------------------------------------------------------------ personnalisation

    @Test
    void hostExcludesCardsAndChangesRulesForThisRoomOnly() {
        GameSetup base = room.setup();
        String removed = base.deck().get(0).id();
        var overrides = new RoomOverrides(Set.of(removed), Map.of(),
                List.of(new Preset.RuleRef("budget-cap", Map.of()),
                        new Preset.RuleRef("rising-floor", Map.of("base", 10, "stepPerHint", 20))));

        room.customize("a", FACTORY.customized("manga-classique", overrides));
        room.start("a");

        var s = room.snapshot().state();
        assertThat(s.cards()).doesNotContainKey(removed).hasSize(base.deck().size() - 1);
        assertThat(s.lot().floorPrice()).isEqualTo(10);
        var view = ViewProjector.room(room.snapshot());
        assertThat(view.customized()).isTrue();
        assertThat(view.rules()).containsExactly("budget-cap", "rising-floor");
        // La base (preset) n'a pas bougé : une nouvelle room repart du preset.
        assertThat(FACTORY.setup("manga-classique").deck()).hasSize(base.deck().size());
    }

    @Test
    void roundsShrinkToTheCustomDeck() {
        List<String> ids = room.setup().deck().stream().map(c -> c.id()).toList();
        Set<String> excluded = new HashSet<>(ids.subList(2, ids.size()));

        room.customize("a", FACTORY.customized("manga-classique", new RoomOverrides(excluded, Map.of(), null)));

        assertThat(room.snapshot().state().settings().rounds()).isEqualTo(2);
        assertThat(room.snapshot().state().players()).hasSize(3);
    }

    @Test
    void rarityOverrideOnlyAcceptsRaritiesOfTheTheme() {
        String id = room.setup().deck().get(0).id();
        room.customize("a", FACTORY.customized("manga-classique",
                new RoomOverrides(Set.of(), Map.of(id, "legendary"), null)));
        assertThat(room.snapshot().state().cards().get(id).rarity()).isEqualTo("legendary");

        assertThatThrownBy(() -> FACTORY.customized("manga-classique",
                new RoomOverrides(Set.of(), Map.of(id, "mythic"), null)))
                .hasFieldOrPropertyWithValue("code", "BAD_ROOM_SETUP");
    }

    @Test
    void invalidRulesAreRefusedLikeInAPreset() {
        // discard-fee exige le mode « drawer » ; manga-classique se joue en enchères scellées.
        assertThatThrownBy(() -> FACTORY.customized("manga-classique", new RoomOverrides(Set.of(), Map.of(),
                List.of(new Preset.RuleRef("discard-fee", Map.of())))))
                .hasFieldOrPropertyWithValue("code", "BAD_ROOM_SETUP");
        assertThatThrownBy(() -> FACTORY.customized("manga-classique", new RoomOverrides(Set.of(), Map.of(),
                List.of(new Preset.RuleRef("rising-floor", Map.of("base", "cher"))))))
                .hasFieldOrPropertyWithValue("code", "BAD_ROOM_SETUP");
        assertThatThrownBy(() -> FACTORY.customized("manga-classique", new RoomOverrides(Set.of(), Map.of(),
                List.of(new Preset.RuleRef("nope", Map.of())))))
                .hasFieldOrPropertyWithValue("code", "BAD_ROOM_SETUP");
    }

    @Test
    void onlyHostCustomizesAndOnlyInLobby() {
        GameSetup custom = FACTORY.customized("manga-classique", new RoomOverrides(Set.of(), Map.of(), List.of()));
        assertThatThrownBy(() -> room.customize("b", custom)).hasFieldOrPropertyWithValue("code", "NOT_HOST");
        room.start("a");
        assertThatThrownBy(() -> room.customize("a", custom)).hasFieldOrPropertyWithValue("code", "WRONG_PHASE");
    }

    @Test
    void catalogShowsCardsRaritiesAndRuleDefaults() {
        var catalog = ViewProjector.catalog("HOST", room.setup(), FACTORY.catalog());

        assertThat(catalog.rarities()).contains("common", "legendary");
        assertThat(catalog.cards()).allMatch(c -> c.included() || c.trap());
        assertThat(catalog.rules()).filteredOn(r -> r.type().equals("rising-floor"))
                .singleElement().matches(r -> r.active() && r.params().get("base").equals(50));
        assertThat(catalog.rules()).filteredOn(r -> r.type().equals("anti-snipe"))
                .singleElement().matches(r -> !r.active() && r.defaults().containsKey("extendSeconds"));
    }
}
