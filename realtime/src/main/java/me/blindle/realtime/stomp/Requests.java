package me.blindle.realtime.stomp;

/** Messages entrants (client → serveur). */
public final class Requests {

    private Requests() {
    }

    public record CreateRoom(String name, String presetId) {
    }

    public record StartDaily(String name, String presetId) {
    }

    public record JoinRoom(String code, String name) {
    }

    public record UpdateSettings(Integer budget, Integer rounds, Integer bidTimerSec) {
    }

    public record PlaceBid(int amount) {
    }

    public record AssignSlot(String slotId) {
    }

    public record UseJoker(String jokerId, String targetId) {
    }

    public record AddBot(String profile) {
    }

    public record CastVote(String teamOwnerId) {
    }

    public record TransferHost(String playerId) {
    }

    /**
     * Personnalisation de la room par l'hôte. {@code rules} null : règles du preset ; liste (même vide) :
     * exactement ces règles.
     */
    public record Customize(java.util.Set<String> excludedCards, java.util.Map<String, String> rarities,
                            java.util.List<me.blindle.content.Preset.RuleRef> rules) {
    }
}
