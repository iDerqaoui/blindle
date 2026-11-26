package me.blindle.core.event;

/** Points d'accroche des règles. Un hook correspond à un type de {@link GameEvent}. */
public enum GameHook {
    ON_GAME_START,
    ON_ROUND_START,
    ON_HINT_REVEAL,
    ON_BID_PLACED,
    ON_LOT_WON,
    ON_CARD_REVEALED,
    ON_SLOT_ASSIGNED,
    ON_JOKER_USED,
    ON_ROUND_END,
    ON_VERDICT
}
