package me.blindle.realtime.solo.who;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.blindle.core.GameException;
import me.blindle.modes.RarityPoints;
import me.blindle.realtime.solo.DailyLeaderboard;
import me.blindle.realtime.solo.who.WhoRun.Outcome;
import me.blindle.realtime.view.Views;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Défi du jour « Who's That? Auction » (REST). Le joueur est le compte connecté s'il y en a un,
 * sinon un cookie de navigateur : un essai par jour. Tout est jugé à l'heure serveur.
 */
@RestController
@RequestMapping("/api/daily/who")
public class WhoController {

    static final String COOKIE = "blindle_who";
    private static final int TOP = 10;

    private final WhoService who;

    public WhoController(WhoService who) {
        this.who = who;
    }

    // ------------------------------------------------------------------ vues

    /** Accueil : univers du jour, déjà joué ou non, classement. */
    public record Summary(boolean available, String date, String universe, String color, boolean started,
                          boolean finished, int players, List<DailyLeaderboard.Entry> leaderboard) {
    }

    /**
     * État de ma partie. {@code live} est null entre deux cartes ; {@code last} est la dernière carte
     * révélée ; {@code outcomes}, {@code share} et {@code leaderboard} n'arrivent qu'en fin de partie.
     */
    public record RunView(String date, String universe, String color, int total, int index, int budget,
                          int slots, List<Integer> prices, long serverTime,
                          LiveView live, OutcomeView last, List<OutcomeView> team, boolean finished,
                          int score, Integer rank, String share, List<OutcomeView> outcomes,
                          List<DailyLeaderboard.Entry> leaderboard, List<String> roster) {
    }

    /**
     * Carte en jeu : ni nom ni image d'origine, seulement l'adresse des paliers.
     * {@code wrong} : mauvais noms déjà proposés (chacun a avancé l'image d'un palier).
     */
    public record LiveView(int index, int level, int imageLevel, int price, String image, List<String> wrong) {
    }

    public record OutcomeView(int index, String name, String image, String rarity, String universe, int points,
                              boolean bought, int price, int level, boolean called, boolean jackpot,
                              boolean skipped, int misses) {
    }

    public record Start(String name) {
    }

    public record Guess(String name) {
    }

    // ------------------------------------------------------------------ endpoints

    @GetMapping
    public Summary summary(HttpServletRequest req) {
        if (!who.daily().available()) {
            return new Summary(false, who.today().toString(), null, null, false, false, 0, List.of());
        }
        WhoLineup lineup = who.daily().lineup(who.today());
        WhoRun run = who.find(keyOrNull(req));
        return new Summary(true, lineup.date().toString(), lineup.universe(), lineup.color(), run != null,
                run != null && run.finished(), who.leaderboard().size(who.today(), WhoService.LEADERBOARD_ID),
                who.top(TOP));
    }

    @PostMapping("/start")
    public RunView start(@RequestBody Start body, HttpServletRequest req, HttpServletResponse res) {
        return view(who.start(key(req, res), body == null ? null : body.name()));
    }

    @GetMapping("/run")
    public RunView run(HttpServletRequest req) {
        WhoRun run = who.find(keyOrNull(req));
        if (run == null) {
            throw new GameException("WHO_NOT_STARTED", "Lance d'abord le défi du jour");
        }
        return view(run);
    }

    @PostMapping("/buy")
    public RunView buy(HttpServletRequest req, HttpServletResponse res) {
        String key = key(req, res);
        who.buy(key);
        return view(who.find(key));
    }

    /** « Call it! » : nom juste = achat ×2, nom faux = image plus nette (et plus chère). */
    @PostMapping("/guess")
    public RunView guess(@RequestBody Guess body, HttpServletRequest req, HttpServletResponse res) {
        String key = key(req, res);
        who.guess(key, body == null ? null : body.name());
        return view(who.find(key));
    }

    @PostMapping("/pass")
    public RunView pass(HttpServletRequest req, HttpServletResponse res) {
        String key = key(req, res);
        who.pass(key);
        return view(who.find(key));
    }

    @PostMapping("/next")
    public RunView next(HttpServletRequest req, HttpServletResponse res) {
        String key = key(req, res);
        who.next(key);
        return view(who.find(key));
    }

    /** Image floutée de la carte en jeu, au palier demandé sans dépasser celui atteint. */
    @GetMapping(value = "/image/{index}/{level}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable int index, @PathVariable int level, HttpServletRequest req) {
        String key = keyOrNull(req);
        if (key == null) {
            throw new GameException("WHO_NOT_STARTED", "Lance d'abord le défi du jour");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .body(who.image(key, index, level));
    }

    @ExceptionHandler(GameException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Views.ErrorMessage onError(GameException e) {
        return Views.ErrorMessage.of(e.code(), e.getMessage());
    }

    // ------------------------------------------------------------------ construction des vues

    RunView view(WhoRun run) {
        Instant now = who.now();
        WhoLineup lineup = run.lineup();
        boolean live = run.live(now);
        LiveView liveView = null;
        if (live) {
            liveView = new LiveView(run.index(), run.level(now), run.imageLevel(now), run.price(now),
                    "/api/daily/who/image/" + run.index() + "/", run.wrongGuesses());
        }
        List<Outcome> outcomes = run.outcomes();
        OutcomeView last = outcomes.isEmpty() ? null : outcome(outcomes.get(outcomes.size() - 1));
        List<OutcomeView> team = outcomes.stream().filter(Outcome::bought).map(WhoController::outcome).toList();
        boolean finished = run.finished();
        List<Integer> prices = Arrays.stream(WhoRun.PRICES).boxed().toList();
        return new RunView(lineup.date().toString(), lineup.universe(), lineup.color(), lineup.cards().size(),
                run.index(), run.budget(), WhoRun.SLOTS, prices, now.toEpochMilli(), liveView, last, team, finished,
                run.score(), run.rank(), finished ? share(run) : null,
                finished ? outcomes.stream().map(WhoController::outcome).toList() : null,
                finished ? who.top(TOP) : null, lineup.roster());
    }

    private static OutcomeView outcome(Outcome o) {
        var c = o.card();
        return new OutcomeView(o.index(), c.name(), c.image(), c.rarity(), c.universe(),
                RarityPoints.POINTS.getOrDefault(c.rarity(), 0), o.bought(), o.price(), o.level(), o.called(),
                o.jackpot(), o.skipped(), o.misses());
    }

    /** Grille à partager, sans spoiler : une case par carte. */
    static String share(WhoRun run) {
        StringBuilder grid = new StringBuilder();
        int called = 0;
        boolean jackpot = false;
        for (Outcome o : run.outcomes()) {
            if (!o.bought()) {
                grid.append("⬛");
                continue;
            }
            grid.append(switch (o.card().rarity()) {
                case "legendary" -> "🟨";
                case "epic" -> "🟪";
                case "rare" -> "🟦";
                default -> "⬜";
            });
            if (o.called()) {
                grid.append("⚡");
                called++;
            }
            jackpot |= o.jackpot();
        }
        List<String> tail = new ArrayList<>();
        tail.add(run.score() + " pts");
        if (called > 0) {
            tail.add("⚡" + called);
        }
        if (jackpot) {
            tail.add("🎰");
        }
        return "Blindle · Who's That? · " + run.lineup().universe() + " · " + run.lineup().date() + "\n"
                + grid + "\n" + String.join(" · ", tail);
    }

    // ------------------------------------------------------------------ identité

    /** Compte connecté, sinon cookie du navigateur (créé au besoin). */
    private static String key(HttpServletRequest req, HttpServletResponse res) {
        String key = keyOrNull(req);
        if (key != null) {
            return key;
        }
        String id = UUID.randomUUID().toString();
        Cookie cookie = new Cookie(COOKIE, id);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(3 * 24 * 3600);
        cookie.setAttribute("SameSite", "Lax");
        cookie.setSecure(req.isSecure());
        res.addCookie(cookie);
        return "c:" + id;
    }

    private static String keyOrNull(HttpServletRequest req) {
        if (req.getUserPrincipal() != null) {
            return "u:" + req.getUserPrincipal().getName();
        }
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if (COOKIE.equals(c.getName()) && c.getValue() != null && c.getValue().matches("[0-9a-f-]{36}")) {
                    return "c:" + c.getValue();
                }
            }
        }
        return null;
    }
}
