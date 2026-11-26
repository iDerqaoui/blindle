package me.blindle.realtime;

import me.blindle.content.Preset;
import me.blindle.realtime.game.GameFactory;
import me.blindle.realtime.solo.DailyChallenge;
import me.blindle.realtime.solo.DailyLeaderboard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Catalogue des presets pour l'écran d'accueil, et défis du jour avec leur classement. */
@RestController
@RequestMapping("/api")
public class PresetController {

    private static final int LEADERBOARD_SIZE = 10;

    private final GameFactory factory;
    private final DailyChallenge daily;
    private final DailyLeaderboard leaderboard;

    public PresetController(GameFactory factory, DailyChallenge daily, DailyLeaderboard leaderboard) {
        this.factory = factory;
        this.daily = daily;
        this.leaderboard = leaderboard;
    }

    public record PresetSummary(String id, String name, String description, String themeName,
                                String bidMode, String verdictMode, boolean daily, Preset.Settings settings) {
    }

    public record DailySummary(String presetId, String name, String description, String themeName, String date,
                               int players, List<DailyLeaderboard.Entry> leaderboard) {
    }

    @GetMapping("/presets")
    public List<PresetSummary> presets() {
        return factory.presets().stream().map(p -> {
            var setup = factory.setup(p.id());
            return new PresetSummary(p.id(), p.name(), p.description(), setup.theme().name(),
                    p.bidMode(), p.verdictMode(), p.daily(), p.settings());
        }).toList();
    }

    @GetMapping("/daily")
    public List<DailySummary> daily() {
        LocalDate today = daily.today();
        return daily.dailyPresets().stream().map(p -> new DailySummary(p.id(), p.name(), p.description(),
                factory.setup(p.id()).theme().name(), today.toString(), leaderboard.size(today, p.id()),
                leaderboard.top(today, p.id(), LEADERBOARD_SIZE))).toList();
    }
}
