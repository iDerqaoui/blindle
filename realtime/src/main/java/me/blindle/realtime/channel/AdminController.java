package me.blindle.realtime.channel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Retrait à chaud d'un thème d'un canal (§8). Protégé par le jeton {@code blindle.admin.token},
 * envoyé dans l'en-tête {@code X-Admin-Token} ; sans jeton configuré, ces routes répondent 404.
 */
@RestController
@RequestMapping("/api/admin/themes")
public class AdminController {

    private static final String HEADER = "X-Admin-Token";

    private final ChannelAccess access;
    private final byte[] token;

    public AdminController(ChannelAccess access, @Value("${blindle.admin.token:}") String token) {
        this.access = access;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping
    public Map<String, ChannelAccess.ThemeChannels> status(
            @RequestHeader(value = HEADER, required = false) String given) {
        authorize(given);
        return access.status();
    }

    @PostMapping("/{themeId}/channels/{channel}/block")
    public Map<String, ChannelAccess.ThemeChannels> block(@PathVariable String themeId, @PathVariable String channel,
            @RequestHeader(value = HEADER, required = false) String given) {
        authorize(given);
        run(() -> access.block(themeId, ChannelAccess.channel(channel)));
        return access.status();
    }

    @DeleteMapping("/{themeId}/channels/{channel}/block")
    public Map<String, ChannelAccess.ThemeChannels> unblock(@PathVariable String themeId, @PathVariable String channel,
            @RequestHeader(value = HEADER, required = false) String given) {
        authorize(given);
        run(() -> access.unblock(themeId, ChannelAccess.channel(channel)));
        return access.status();
    }

    private void authorize(String given) {
        if (token.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (given == null || !MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    private static void run(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
