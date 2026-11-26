package me.blindle.realtime;

import me.blindle.content.Preset;
import me.blindle.realtime.game.GameFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catalogue des presets pour l'écran de lobby. */
@RestController
@RequestMapping("/api")
public class PresetController {

    private final GameFactory factory;

    public PresetController(GameFactory factory) {
        this.factory = factory;
    }

    public record PresetSummary(String id, String name, String description, String themeName,
                                String bidMode, String verdictMode, Preset.Settings settings) {
    }

    @GetMapping("/presets")
    public List<PresetSummary> presets() {
        return factory.presets().stream().map(p -> {
            var setup = factory.setup(p.id());
            return new PresetSummary(p.id(), p.name(), p.description(), setup.theme().name(),
                    p.bidMode(), p.verdictMode(), p.settings());
        }).toList();
    }
}
