package me.blindle.modes;

import me.blindle.core.GameException;
import me.blindle.core.GameRandom;
import me.blindle.core.model.Bid;
import me.blindle.core.model.GameState;
import me.blindle.core.model.LotResult;
import me.blindle.core.model.LotState;
import me.blindle.core.model.PlayerState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@code drawer} (« Blind Bid ») : à chaque round, un joueur pioche la carte — lui seul la voit —
 * et ouvre l'enchère. Les autres, à l'aveugle, surenchérissent ou laissent. Offres visibles ;
 * chaque offre dépasse la meilleure ; le meneur ne peut pas laisser.
 * <p>
 * Le piocheur tourne dans l'ordre des joueurs (round 1 : premier joueur), en sautant ceux dont
 * l'équipe est pleine. Palier clos quand le piocheur a joué et que tous les autres ont laissé,
 * sinon à l'heure de fin. Personne n'a misé : la carte part à la poubelle (défausse publique).
 * La carte gagnée est posée d'office dans l'équipe du gagnant.
 */
@Component
public class DrawerBidMode implements BidMode {

    public static final String ID = "drawer";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean bidsVisible() {
        return true;
    }

    @Override
    public boolean autoPlace() {
        return true;
    }

    @Override
    public String drawerId(GameState state) {
        if (state.lot() == null || state.players().isEmpty()) {
            return null;
        }
        List<PlayerState> players = state.players();
        List<String> eligible = state.eligibleBidders();
        int start = Math.floorMod(state.roundIndex() - 1, players.size());
        for (int i = 0; i < players.size(); i++) {
            String id = players.get((start + i) % players.size()).id();
            if (eligible.contains(id)) {
                return id;
            }
        }
        return null;
    }

    @Override
    public void validateBid(GameState state, Bid bid) {
        requireDrawerFirst(state, bid.playerId());
        Optional<Bid> leader = OpenBidMode.leader(state.lot());
        if (leader.isPresent() && bid.amount() <= leader.get().amount()) {
            throw new GameException("BID_TOO_LOW", "Il faut dépasser " + leader.get().amount());
        }
    }

    @Override
    public void validatePass(GameState state, String playerId) {
        requireDrawerFirst(state, playerId);
        if (OpenBidMode.leader(state.lot()).filter(b -> b.playerId().equals(playerId)).isPresent()) {
            throw new GameException("LEADER_CANNOT_PASS", "Tu mènes l'enchère : tu ne peux pas laisser");
        }
    }

    private void requireDrawerFirst(GameState state, String playerId) {
        String drawer = drawerId(state);
        if (drawer != null && !drawer.equals(playerId) && !state.lot().hasActed(drawer)) {
            throw new GameException("DRAWER_FIRST", "Le piocheur ouvre l'enchère");
        }
    }

    @Override
    public boolean isComplete(GameState state) {
        LotState lot = state.lot();
        if (lot == null) {
            return false;
        }
        String drawer = drawerId(state);
        if (drawer == null) {
            return true; // plus personne ne peut miser
        }
        if (!lot.hasActed(drawer)) {
            return false;
        }
        String leaderId = OpenBidMode.leader(lot).map(Bid::playerId).orElse(null);
        return state.eligibleBidders().stream()
                .filter(id -> !id.equals(leaderId))
                .allMatch(lot.passes()::contains);
    }

    @Override
    public Optional<LotResult> resolve(LotState lot, GameRandom random) {
        return OpenBidMode.leader(lot).map(b -> new LotResult(b.playerId(), b.amount()));
    }
}
