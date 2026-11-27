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
 * Défi du jour « Who's That? Auction » (REST), un par univers anime : {@code /api/daily/who/{theme}/…}.
 * Le joueur est le compte connecté s'il y en a un, sinon un cookie de navigateur : un essai par jour et
 * par univers. Tout est jugé par le serveur.
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

    /** Un univers dans la liste des défis du jour : déjà joué ou non, nombre de joueurs. */
    public record UniverseCard(String theme, String universe, String color, String cover, int players,
                               WhoService.Mine mine) {
    }

    /**
     * Accueil du défi d'un univers. {@code started} : une partie est en cours en mémoire ; {@code mine} : mon résultat du
     * jour, s'il est enregistré (rejouer est alors impossible, même après un redémarrage).
     */
    public record Summary(boolean available, String date, String theme, String universe, String color, boolean started,
                          boolean finished, int players, List<DailyLeaderboard.Entry> leaderboard,
                          WhoService.Mine mine) {
    }

    /**
     * État de ma partie. {@code live} est null entre deux cartes ; {@code last} est la dernière carte
     * révélée ; {@code outcomes}, {@code share} et {@code leaderboard} n'arrivent qu'en fin de partie.
     */
    public record RunView(String date, String theme, String universe, String color, int total, int index, int budget,
                          int slots, List<Integer> prices, long serverTime,
                          LiveView live, OutcomeView last, List<OutcomeView> team, boolean finished,
                          int score, Integer rank, int streak, String share, List<OutcomeView> outcomes,
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

    /** Défis du jour : un par univers, avec mon résultat là où j'ai déjà joué. */
    @GetMapping
    public List<UniverseCard> universes(HttpServletRequest req) {
        String key = keyOrNull(req);
        return who.daily().eligibleThemes().stream().map(t -> new UniverseCard(t.id(), t.name(),
                t.art() == null ? null : t.art().color(), t.art() == null ? null : t.art().cover(),
                who.players(t.id()), who.mine(t.id(), key).orElse(null))).toList();
    }

    @GetMapping("/{theme}")
    public Summary summary(@PathVariable String theme, HttpServletRequest req) {
        WhoLineup lineup = who.lineup(theme);
        String key = keyOrNull(req);
        WhoRun run = who.find(theme, key);
        WhoService.Mine mine = who.mine(theme, key).orElse(null);
        return new Summary(true, lineup.date().toString(), theme, lineup.universe(), lineup.color(), run != null,
                mine != null || (run != null && run.finished()), who.players(theme), who.top(theme, TOP), mine);
    }

    @PostMapping("/{theme}/start")
    public RunView start(@PathVariable String theme, @RequestBody Start body, HttpServletRequest req,
                         HttpServletResponse res) {
        return view(who.start(theme, key(req, res), body == null ? null : body.name()));
    }

    @GetMapping("/{theme}/run")
    public RunView run(@PathVariable String theme, HttpServletRequest req) {
        WhoRun run = who.find(theme, keyOrNull(req));
        if (run == null) {
            throw new GameException("WHO_NOT_STARTED", "Lance d'abord le défi du jour");
        }
        return view(run);
    }

    @PostMapping("/{theme}/buy")
    public RunView buy(@PathVariable String theme, HttpServletRequest req, HttpServletResponse res) {
        String key = key(req, res);
        who.buy(theme, key);
        return view(who.find(theme, key));
    }

    /** « Call it! » : nom juste = achat ×2, nom faux = image plus nette (et plus chère). */
    @PostMapping("/{theme}/guess")
    public RunView guess(@PathVariable String theme, @RequestBody Guess body, HttpServletRequest req,
                         HttpServletResponse res) {
        String key = key(req, res);
        who.guess(theme, key, body == null ? null : body.name());
        return view(who.find(theme, key));
    }

    @PostMapping("/{theme}/pass")
    public RunView pass(@PathVariable String theme, HttpServletRequest req, HttpServletResponse res) {
        String key = key(req, res);
        who.pass(theme, key);
        return view(who.find(theme, key));
    }

    @PostMapping("/{theme}/next")
    public RunView next(@PathVariable String theme, HttpServletRequest req, HttpServletResponse res) {
        String key = key(req, res);
        who.next(theme, key);
        return view(who.find(theme, key));
    }

    /** Image floutée de la carte en jeu, au palier demandé sans dépasser celui atteint. */
    @GetMapping(value = "/{theme}/image/{index}/{level}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable String theme, @PathVariable int index, @PathVariable int level,
                                        HttpServletRequest req) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .body(who.image(theme, keyOrNull(req), index, level));
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
                    "/api/daily/who/" + lineup.themeId() + "/image/" + run.index() + "/", run.wrongGuesses());
        }
        List<Outcome> outcomes = run.outcomes();
        OutcomeView last = outcomes.isEmpty() ? null : outcome(outcomes.get(outcomes.size() - 1));
        List<OutcomeView> team = outcomes.stream().filter(Outcome::bought).map(WhoController::outcome).toList();
        boolean finished = run.finished();
        List<Integer> prices = Arrays.stream(WhoRun.PRICES).boxed().toList();
        return new RunView(lineup.date().toString(), lineup.themeId(), lineup.universe(), lineup.color(),
                lineup.cards().size(),
                run.index(), run.budget(), WhoRun.SLOTS, prices, now.toEpochMilli(), liveView, last, team, finished,
                run.score(), run.rank(), run.streak(), finished ? run.share() : null,
                finished ? outcomes.stream().map(WhoController::outcome).toList() : null,
                finished ? who.top(lineup.themeId(), TOP) : null, lineup.roster());
    }

    private static OutcomeView outcome(Outcome o) {
        var c = o.card();
        return new OutcomeView(o.index(), c.name(), c.image(), c.rarity(), c.universe(),
                RarityPoints.POINTS.getOrDefault(c.rarity(), 0), o.bought(), o.price(), o.level(), o.called(),
                o.jackpot(), o.skipped(), o.misses());
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
        cookie.setMaxAge(400 * 24 * 3600); // la série de jours suit le navigateur
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
