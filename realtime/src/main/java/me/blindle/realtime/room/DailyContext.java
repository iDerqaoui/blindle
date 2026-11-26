package me.blindle.realtime.room;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Ce qui fait d'une room un défi du jour.
 *
 * @param date   jour du défi
 * @param submit enregistre le résultat de la partie terminée au classement et renvoie le rang obtenu
 */
public record DailyContext(LocalDate date, ToIntFunction<RoomSnapshot> submit) {

    public DailyContext {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(submit, "submit");
    }
}
