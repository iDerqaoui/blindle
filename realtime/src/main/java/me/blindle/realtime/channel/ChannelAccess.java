package me.blindle.realtime.channel;

import me.blindle.content.Channel;
import me.blindle.content.ContentRegistry;
import me.blindle.content.Preset;
import me.blindle.content.Theme;
import me.blindle.core.GameException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Qui peut jouer quoi, sur quel canal (§8). Un preset se joue sur ses canaux (ou ceux de son thème),
 * moins les canaux dont le thème a été retiré à chaud (signalement d'un ayant droit).
 * <p>
 * Les retraits sont en mémoire (MVP) ; {@code blindle.rights.blocked=theme:canal,…} les rétablit au
 * démarrage. Un retrait empêche de voir le preset, de créer une room et de la rejoindre ; les
 * parties déjà lancées vont à leur terme.
 */
@Component
public class ChannelAccess {

    private final ContentRegistry content;
    private final Map<String, Set<Channel>> blocked = new ConcurrentHashMap<>();

    public ChannelAccess(ContentRegistry content, @Value("${blindle.rights.blocked:}") List<String> initial) {
        this.content = content;
        for (String entry : initial) {
            if (entry.isBlank()) {
                continue;
            }
            String[] parts = entry.strip().split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("blindle.rights.blocked : « thème:canal » attendu, reçu " + entry);
            }
            block(parts[0], channel(parts[1]));
        }
    }

    /** Canaux où le preset se joue en ce moment. */
    public Set<Channel> channels(Preset preset) {
        Theme theme = theme(preset.theme());
        Set<Channel> out = EnumSet.noneOf(Channel.class);
        out.addAll(preset.channels(theme));
        out.removeAll(blocked.getOrDefault(theme.id(), Set.of()));
        return out;
    }

    public boolean allows(Preset preset, Channel channel) {
        return channels(preset).contains(channel);
    }

    /** Lève {@code CHANNEL_FORBIDDEN} si le preset n'est pas jouable sur ce canal. */
    public void require(Preset preset, Channel channel) {
        if (!allows(preset, channel)) {
            throw new GameException("CHANNEL_FORBIDDEN",
                    "« " + preset.name() + " » n'est pas disponible sur " + channel.id());
        }
    }

    /** Retire à chaud un thème d'un canal. */
    public void block(String themeId, Channel channel) {
        theme(themeId);
        blocked.computeIfAbsent(themeId, k -> ConcurrentHashMap.newKeySet()).add(channel);
    }

    /** Rétablit un thème sur un canal (dans la limite de ses droits déclarés). */
    public void unblock(String themeId, Channel channel) {
        theme(themeId);
        blocked.getOrDefault(themeId, Set.of()).remove(channel);
    }

    /** État de chaque thème : canaux déclarés et canaux retirés. */
    public Map<String, ThemeChannels> status() {
        Map<String, ThemeChannels> out = new TreeMap<>();
        for (Theme t : content.themes()) {
            out.put(t.id(), new ThemeChannels(t.rights().channels(),
                    blocked.getOrDefault(t.id(), Set.of()).stream().map(Channel::id).sorted().toList()));
        }
        return out;
    }

    public record ThemeChannels(List<String> declared, List<String> blocked) {
    }

    public static Channel channel(String id) {
        return Channel.of(id).orElseThrow(() -> new IllegalArgumentException("Canal inconnu : " + id));
    }

    private Theme theme(String id) {
        return content.theme(id).orElseThrow(() -> new IllegalArgumentException("Thème inconnu : " + id));
    }
}
