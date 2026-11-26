package me.blindle.realtime.solo;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classement du défi du jour, en mémoire (MVP) : par jour et par preset, meilleur score d'abord,
 * à égalité le premier arrivé. Seuls le jour courant et la veille sont conservés.
 */
@Component
public class DailyLeaderboard {

    public static final int MAX_ENTRIES = 1000;

    public record Entry(String name, int score) {
    }

    private record Key(LocalDate date, String presetId) {
    }

    private final Map<Key, List<Entry>> boards = new HashMap<>();

    /** Enregistre un score et renvoie le rang obtenu (1 = premier). */
    public synchronized int submit(LocalDate date, String presetId, Entry entry) {
        boards.keySet().removeIf(k -> k.date().isBefore(date.minusDays(1)));
        List<Entry> board = boards.computeIfAbsent(new Key(date, presetId), k -> new ArrayList<>());
        int index = 0;
        while (index < board.size() && board.get(index).score() >= entry.score()) {
            index++;
        }
        board.add(index, entry);
        if (board.size() > MAX_ENTRIES) {
            board.remove(board.size() - 1);
        }
        return index + 1;
    }

    public synchronized List<Entry> top(LocalDate date, String presetId, int limit) {
        List<Entry> board = boards.getOrDefault(new Key(date, presetId), List.of());
        return List.copyOf(board.subList(0, Math.min(limit, board.size())));
    }

    public synchronized int size(LocalDate date, String presetId) {
        return boards.getOrDefault(new Key(date, presetId), List.of()).size();
    }
}
