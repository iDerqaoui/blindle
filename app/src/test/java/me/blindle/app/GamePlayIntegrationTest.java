package me.blindle.app;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trois clients STOMP jouent une partie complète du preset manga-classique :
 * à chaque round, un joueur désigné mise le plancher, les autres passent ; le gagnant
 * place la carte ; chacun vote pour son voisin.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GamePlayIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final List<StompSession> sessions = new ArrayList<>();
    private final List<String> violations = Collections.synchronizedList(new ArrayList<>());
    private final List<Object> errors = Collections.synchronizedList(new ArrayList<>());

    @AfterEach
    void close() {
        sessions.forEach(StompSession::disconnect);
    }

    @Test
    void presetsAreListed() {
        List<?> presets = rest.getForObject("/api/presets", List.class);
        assertThat(presets).anySatisfy(p -> assertThat(((Map<?, ?>) p).get("id")).isEqualTo("manga-classique"));
        List<?> bots = rest.getForObject("/api/bots", List.class);
        assertThat(bots.stream().map(b -> (String) ((Map<?, ?>) b).get("profile")).toList())
                .containsExactlyInAnyOrder("aggressive", "stingy", "fan", "bluffer", "strategist");
    }

    @Test
    void threePlayersPlayAFullGame() throws Exception {
        CountDownLatch finished = new CountDownLatch(3);
        Player alice = new Player("Alice", finished);
        Player bilal = new Player("Bilal", finished);
        Player chloe = new Player("Chloé", finished);

        alice.send("/app/room.create", Map.of("name", "Alice", "presetId", "manga-classique"));
        String code = alice.code.get(5, TimeUnit.SECONDS);
        alice.watch(code);
        for (Player p : List.of(bilal, chloe)) {
            p.send("/app/room.join", Map.of("code", code, "name", p.name));
            assertThat(p.code.get(5, TimeUnit.SECONDS)).isEqualTo(code);
            p.watch(code);
        }
        Thread.sleep(200);
        alice.send("/app/room/" + code + "/start", Map.of());

        assertThat(finished.await(30, TimeUnit.SECONDS)).as("partie terminée").isTrue();
        assertThat(errors).isEmpty();
        assertThat(violations).isEmpty();

        Map<?, ?> end = alice.lastRoom;
        assertThat(end.get("phase")).isEqualTo("FINISHED");
        assertThat(end.get("round")).isEqualTo(10);
        Map<?, ?> result = (Map<?, ?>) end.get("result");
        assertThat((List<?>) result.get("ranking")).hasSize(3);
        List<?> players = (List<?>) end.get("players");
        int cards = players.stream().mapToInt(p -> ((List<?>) ((Map<?, ?>) p).get("team")).size()).sum();
        assertThat(cards).isEqualTo(10);
    }

    @Test
    void dailyChallengeIsPlayedSoloRankedAndShareable() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        Player alice = new Player("Alice", finished);

        alice.send("/app/room.create", Map.of("name", "Alice", "presetId", "defi-manga"));
        Thread.sleep(300);
        assertThat(errors).anySatisfy(e -> assertThat(((Map<?, ?>) e).get("code")).isEqualTo("DAILY_IS_SOLO"));
        errors.clear();

        alice.send("/app/daily.start", Map.of("name", "Alice", "presetId", "defi-manga"));
        alice.watch(alice.code.get(5, TimeUnit.SECONDS));

        assertThat(finished.await(30, TimeUnit.SECONDS)).as("défi terminé").isTrue();
        assertThat(errors).isEmpty();
        assertThat(violations).isEmpty();
        Map<?, ?> end = alice.lastRoom;
        assertThat(end.get("bidMode")).isEqualTo("market");
        Map<?, ?> daily = (Map<?, ?>) end.get("daily");
        assertThat(daily.get("rank")).isEqualTo(1);
        assertThat((String) daily.get("share")).startsWith("Blindle · Défi du jour").endsWith("blindle.app");

        List<?> summaries = rest.getForObject("/api/daily", List.class);
        Map<?, ?> manga = (Map<?, ?>) summaries.stream()
                .filter(d -> "defi-manga".equals(((Map<?, ?>) d).get("presetId"))).findFirst().orElseThrow();
        assertThat(manga.get("players")).isEqualTo(1);
        assertThat((List<?>) manga.get("leaderboard"))
                .singleElement().satisfies(e -> assertThat(((Map<?, ?>) e).get("name")).isEqualTo("Alice"));
    }

    /** Client de test qui joue automatiquement à partir des vues reçues. */
    private final class Player {
        final String name;
        final CompletableFuture<String> code = new CompletableFuture<>();
        final StompSession session;
        final CountDownLatch finished;
        final Set<String> done = new HashSet<>();
        String myId;
        long version = -1;
        Map<?, ?> lastRoom;

        Player(String name, CountDownLatch finished) throws Exception {
            this.name = name;
            this.finished = finished;
            WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
            client.setMessageConverter(new MappingJackson2MessageConverter());
            this.session = client.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
            }).get(5, TimeUnit.SECONDS);
            sessions.add(session);
            session.subscribe("/user/queue/room", handler(this::onMessage));
            session.subscribe("/user/queue/errors", handler(errors::add));
            Thread.sleep(100);
        }

        void watch(String roomCode) throws InterruptedException {
            session.subscribe("/topic/room/" + roomCode, handler(this::onMessage));
            Thread.sleep(100);
            send("/app/room/" + roomCode + "/sync", Map.of());
        }

        void send(String destination, Object payload) {
            session.send(destination, payload);
        }

        synchronized void onMessage(Object payload) {
            Map<?, ?> msg = (Map<?, ?>) payload;
            if ("player".equals(msg.get("type"))) {
                myId = (String) msg.get("playerId");
                code.complete((String) msg.get("code"));
                return;
            }
            long v = ((Number) msg.get("version")).longValue();
            if (v < version || myId == null) {
                return;
            }
            version = v;
            lastRoom = msg;
            react(msg);
        }

        private void react(Map<?, ?> room) {
            String phase = (String) room.get("phase");
            String roomCode = (String) room.get("code");
            List<?> players = (List<?>) room.get("players");
            int me = indexOf(players, myId);
            int round = ((Number) room.get("round")).intValue();
            Map<?, ?> lot = (Map<?, ?>) room.get("lot");
            switch (phase) {
                case "BIDDING" -> {
                    if (lot.get("bids") != null) {
                        violations.add("offres visibles en BIDDING");
                    }
                    if (lot.get("cards") != null) {
                        violations.add("carte visible avant révélation");
                    }
                    String key = "bid:" + round + ":" + lot.get("hintLevel");
                    if (done.add(key)) {
                        if (round % 3 == me) {
                            send("/app/room/" + roomCode + "/bid", Map.of("amount", lot.get("floorPrice")));
                        } else {
                            send("/app/room/" + roomCode + "/pass", Map.of());
                        }
                    }
                }
                case "REVEALED" -> {
                    if (myId.equals(lot.get("winnerId")) && done.add("slot:" + round)) {
                        Map<?, ?> mine = (Map<?, ?>) players.get(me);
                        Set<Object> used = new HashSet<>();
                        for (Object e : (List<?>) mine.get("team")) {
                            used.add(((Map<?, ?>) e).get("slotId"));
                        }
                        String slot = ((List<?>) room.get("slots")).stream()
                                .map(s -> (String) ((Map<?, ?>) s).get("id"))
                                .filter(id -> !used.contains(id)).findFirst().orElseThrow();
                        send("/app/room/" + roomCode + "/slot", Map.of("slotId", slot));
                    }
                }
                case "VERDICT" -> {
                    if (done.add("vote")) {
                        Map<?, ?> next = (Map<?, ?>) players.get((me + 1) % players.size());
                        send("/app/room/" + roomCode + "/vote", Map.of("teamOwnerId", next.get("id")));
                    }
                }
                case "FINISHED" -> {
                    if (done.add("finished")) {
                        finished.countDown();
                    }
                }
                default -> {
                }
            }
        }

        private int indexOf(List<?> players, String id) {
            for (int i = 0; i < players.size(); i++) {
                if (id.equals(((Map<?, ?>) players.get(i)).get("id"))) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static StompFrameHandler handler(java.util.function.Consumer<Object> consumer) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                consumer.accept(payload);
            }
        };
    }
}
