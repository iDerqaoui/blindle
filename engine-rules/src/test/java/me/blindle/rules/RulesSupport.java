package me.blindle.rules;

import me.blindle.core.BidResolver;
import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.model.Bid;
import me.blindle.core.model.GameState;
import me.blindle.core.model.LotResult;
import me.blindle.core.rule.Rule;
import me.blindle.core.rule.RuleEngine;
import me.blindle.core.rule.RuleParams;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Outils communs aux tests de règles. */
final class RulesSupport {

    static final Instant DEADLINE = Instant.parse("2026-01-01T00:00:30Z");

    /** La plus haute offre gagne, au prix offert. */
    static final BidResolver HIGHEST = (lot, random) -> lot.bids().stream()
            .max((a, b) -> Integer.compare(a.amount(), b.amount()))
            .map(b -> new LotResult(b.playerId(), b.amount()));

    private RulesSupport() {
    }

    static GameEngine engine(Rule rule, Map<String, ?> params) {
        return new GameEngine(new RuleEngine(List.of(new RuleEngine.ActiveRule(rule, RuleParams.of(params)))));
    }

    /** Partie démarrée, premier lot en enchères. */
    static GameState bidding(GameEngine engine, long seed) {
        GameState s = engine.start(Fixtures.lobby(engine, seed, 3)).state();
        s = engine.revealFirstHint(engine.nextRound(s).state()).state();
        return engine.openBidding(s, DEADLINE).state();
    }

    /** Le joueur remporte le lot courant au prix donné et place la carte dans le slot. */
    static GameState win(GameEngine engine, GameState s, String playerId, int price, String slotId) {
        s = engine.placeBid(s, new Bid(playerId, price)).state();
        s = engine.revealCard(engine.closeBidding(s, HIGHEST).state()).state();
        return engine.assignSlot(s, playerId, slotId, s.lot().cards().get(0).id()).state();
    }

    /** Depuis SLOT : round suivant ouvert aux enchères. */
    static GameState nextBidding(GameEngine engine, GameState s) {
        s = engine.revealFirstHint(engine.nextRound(s).state()).state();
        return engine.openBidding(s, DEADLINE).state();
    }
}
