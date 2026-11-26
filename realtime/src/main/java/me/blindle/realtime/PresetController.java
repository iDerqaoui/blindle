package me.blindle.realtime;

import jakarta.servlet.http.HttpServletRequest;
import me.blindle.content.Channel;
import me.blindle.content.Preset;
import me.blindle.realtime.channel.ChannelAccess;
import me.blindle.realtime.channel.ChannelAuthenticator;
import me.blindle.realtime.view.Views;
import org.springframework.http.server.ServletServerHttpRequest;
import me.blindle.modes.bot.BotCatalog;
import me.blindle.realtime.game.GameFactory;
import me.blindle.realtime.solo.DailyChallenge;
import me.blindle.realtime.solo.DailyLeaderboard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Catalogue des presets et des profils de bots, et défis du jour avec leur classement. */
@RestController
@RequestMapping("/api")
public class PresetController {

    private static final int LEADERBOARD_SIZE = 10;
    private static final int FACES = 6;

    private final GameFactory factory;
    private final DailyChallenge daily;
    private final DailyLeaderboard leaderboard;
    private final BotCatalog bots;
    private final ChannelAccess access;
    private final ChannelAuthenticator channels;

    public PresetController(GameFactory factory, DailyChallenge daily, DailyLeaderboard leaderboard,
                            BotCatalog bots, ChannelAccess access, ChannelAuthenticator channels) {
        this.access = access;
        this.channels = channels;
        this.bots = bots;
        this.factory = factory;
        this.daily = daily;
        this.leaderboard = leaderboard;
    }

    public record PresetSummary(String id, String name, String description, String themeName,
                                String bidMode, String verdictMode, boolean daily, Preset.Settings settings,
                                String disclaimer, Views.ArtView art, int cards, List<String> faces) {
    }

    public record DailySummary(String presetId, String name, String description, String themeName, String date,
                               int players, List<DailyLeaderboard.Entry> leaderboard) {
    }

    /** Presets jouables sur le canal du demandeur, déduit de son authentification. */
    @GetMapping("/presets")
    public List<PresetSummary> presets(HttpServletRequest request) {
        Channel channel = channelOf(request);
        return factory.presets().stream().filter(p -> access.allows(p, channel)).map(p -> {
            var setup = factory.setup(p.id());
            return new PresetSummary(p.id(), p.name(), p.description(), setup.theme().name(),
                    p.bidMode(), p.verdictMode(), p.daily(), p.settings(), setup.theme().rights().disclaimer(),
                    art(setup.theme()), setup.theme().cards().size(), faces(setup.theme()));
        }).toList();
    }

    public record BotProfile(String profile, String label) {
    }

    @GetMapping("/bots")
    public List<BotProfile> bots() {
        return bots.all().stream().map(b -> new BotProfile(b.profile(), b.label())).toList();
    }

    @GetMapping("/daily")
    public List<DailySummary> daily(HttpServletRequest request) {
        Channel channel = channelOf(request);
        LocalDate today = daily.today();
        return daily.dailyPresets().stream().filter(p -> access.allows(p, channel)).map(p -> new DailySummary(p.id(), p.name(), p.description(),
                factory.setup(p.id()).theme().name(), today.toString(), leaderboard.size(today, p.id()),
                leaderboard.top(today, p.id(), LEADERBOARD_SIZE))).toList();
    }

    /** Images des premières cartes du thème (les plus populaires d'abord) : médaillons de l'accueil. */
    private static List<String> faces(me.blindle.content.Theme theme) {
        return theme.cards().stream().map(c -> c.image()).filter(java.util.Objects::nonNull).limit(FACES).toList();
    }

    private static Views.ArtView art(me.blindle.content.Theme theme) {
        return theme.art() == null ? null
                : new Views.ArtView(theme.art().cover(), theme.art().banner(), theme.art().color());
    }

    private Channel channelOf(HttpServletRequest request) {
        return channels.channelOf(new ServletServerHttpRequest(request));
    }
}
