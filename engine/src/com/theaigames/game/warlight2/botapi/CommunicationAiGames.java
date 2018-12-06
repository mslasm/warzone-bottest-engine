package com.theaigames.game.warlight2.botapi;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;

import java.util.Arrays;
import java.util.List;
import java.util.LinkedList;

import com.theaigames.game.warlight2.BotCommunication;
import com.theaigames.game.warlight2.Player;
import com.theaigames.game.warlight2.map.Map;
import com.theaigames.game.warlight2.map.Region;
import com.theaigames.game.warlight2.map.SuperRegion;
import com.theaigames.game.warlight2.map.Settings;
import com.theaigames.game.warlight2.move.AttackTransferMove;
import com.theaigames.game.warlight2.move.Move;
import com.theaigames.game.warlight2.move.PlaceArmiesMove;

/**
 * This implementation of the interface uses the original protocol of theaigames.com warlight2 engine,
 * with one exception: when picking, opponents picks are not excluded and opponents picks are not sent
 * when picking is done.
 */
public class CommunicationAiGames extends CommunicationBaseParser implements BotCommunication
{
    public CommunicationAiGames(Settings settings, Map map) {
        super(settings);

        if (settings.getNeutralArmies() != 2 || settings.getWastelandSize() != 6) {
            throw new IllegalArgumentException("theaigames.com protocol does not suport sending map details " +
                                               "and thus neutral regions should only have 2 armies and wastelands 6 armies");
        }

        if (settings.getDefensiveKillRatio() != 0.7 || settings.getOffensiveKillRatio() != 0.6) {
            throw new IllegalArgumentException("theaigames.com protocol does not support non-default kill ratios");
        }

        if (map.hasOverlappingBonuses()) {
            throw new IllegalArgumentException("theaigames.com protocol does not support overlapping bonuses");
        }

        if (map.hasRegionsNotInABonus()) {
            throw new IllegalArgumentException("theaigames.com protocol does not support regions without a bonus");
        }
    }

    //===================================================================================

    @Override
    public void sendSettings(Player player, Player otherPlayer, int maxRounds) {
        player.sendInfo("settings timebank " + settings.getMaxTimebank());
        player.sendInfo("settings time_per_move " + settings.getExtraTimePerMove());
        player.sendInfo("settings max_rounds " + maxRounds);
        player.sendInfo("settings your_bot " + player.getName());
        player.sendInfo("settings opponent_bot " + otherPlayer.getName());
    }

    @Override
    public void sendBaseMapInfo(Player player, Map map) {
        sendSuperRegionsString(player, map);
        sendRegionsString(player, map);
        sendNeighborsString(player, map);
        sendWastelandsString(player, map);
    }

    private void sendSuperRegionsString(Player player, Map map) {
        String superRegionsString = "setup_map super_regions";
        for (SuperRegion superRegion : map.getSuperRegions()) {
            int id = superRegion.getId();
            int reward = superRegion.getArmiesReward();
            superRegionsString = superRegionsString.concat(" " + id + " " + reward);
        }
        player.sendInfo(superRegionsString);
    }

    private void sendRegionsString(Player player, Map map) {
        String regionsString = "setup_map regions";
        for (Region region : map.getRegions()) {
            int id = region.getId();
            // we assume each region belongs to exactly one bonus
            int superRegionId = map.getRegionBonuses(region).iterator().next();
            regionsString = regionsString.concat(" " + id + " " + superRegionId);
        }
        player.sendInfo(regionsString);
    }

    private void sendNeighborsString(Player player, Map map) {
        String neighborsString = "setup_map neighbors";
        for (Region region : map.getRegions()) {
            int id = region.getId();
            String neighbors = "";
            for (Integer neighbor : region.getNeighbors()) {
                // as per theaigames.com web site: connectivity is only given in one way: 'region id' < 'neighbour id'
                if (neighbor > id) {
                    neighbors = neighbors.concat("," + neighbor);
                }
            }
            if (neighbors.length() != 0) {
                neighbors = neighbors.replaceFirst(",", " ");
                neighborsString = neighborsString.concat(" " + id + neighbors);
            }
        }
        player.sendInfo(neighborsString);
    }

    private void sendWastelandsString(Player player, Map map) {
        String wastelandsString = "setup_map wastelands";
        for (Region region : map.getRegions()) {
            if (region.getArmies() > 2) {
                int id = region.getId();
                wastelandsString = wastelandsString.concat(" " + id);
            }
        }
        player.sendInfo(wastelandsString);
    }

    //===================================================================================

    @Override
    public List<Integer> sendPickInfoAndRequestStartingPicks(Player player,
            int numberOfStartingRegions, Collection<Integer> pickableRegions) {

        int maxSubmittedPicks = numberOfStartingRegions * 2;

        player.sendInfo("settings starting_regions " + asSeparatedString(pickableRegions, " "));

        player.sendInfo("settings starting_pick_amount " + maxSubmittedPicks);

        List<Integer> picks = new LinkedList<>();

        // simulate picking regions one-by-one
        Set<Integer> remainingPickableRegions = new HashSet<>(pickableRegions);
        for (int i = 0; i < maxSubmittedPicks; i++) {
            player.sendInfo("pick_starting_region " + player.getTimeBank() + " " + asSeparatedString(remainingPickableRegions, " "));
            String response = player.getResponse();
            List<Integer> pickedRegions = parsePicks(player, response, remainingPickableRegions, maxSubmittedPicks);
            if (pickedRegions.size() == 1) {
                int pickedRegionID = pickedRegions.get(0);
                picks.add(pickedRegionID);
                // to emulate real theaigames.com protocol exclude already picked regions from the list of available picks
                remainingPickableRegions.remove(pickedRegionID);
            } else {
                logParseError(player, "did not receive exactly one pick per turn as specified by the protocol");
            }
        }

        System.out.format("Player [%s] selected the following valid regions: %s%n",
                player.getName(), Arrays.toString(picks.toArray()));
        return picks;
    }

    //===================================================================================

    @Override
    public void sendTurnStartUpdate(Player player, List<Move> visibleMoves, Map visibleMapForPlayer) {
        sendStartingArmiesInfo(player);
        sendUpdateMapInfo(player, visibleMapForPlayer);
        sendVisibleMovesInfo(player, visibleMoves);
    }

    /**
     * Informs the player about how much armies he can place at the start next round
     *
     * @param player : player to send the info to
     */
    private void sendStartingArmiesInfo(Player player) {
        player.sendInfo("settings starting_armies " + player.getArmiesLeft());
    }

    /**
     * Informs the player about how his visible map looks now
     *
     * @param player : player to send the info to
     */
    private void sendUpdateMapInfo(Player player, Map visibleMapForPlayer) {
        String updateMapString = "update_map";
        for (Region region : visibleMapForPlayer.getRegions()) {
            if (!region.isFogged()) {
                updateMapString += " " + region.getId() + " " + region.getOwnerName() + " " + region.getArmies();
            }
        }
        player.sendInfo(updateMapString);
    }

    /**
     * Informs the player about all the observed moves from the previous turn.
     *
     * @param player : player to send the info to
     */
    private void sendVisibleMovesInfo(Player player, List<Move> visibleMoves) {
        String opponentMovesString = "opponent_moves ";

        for (Move move : visibleMoves) {
            if (move.getPlayerName().equals(player.getName())) {
                // theaigames.com protocol only sends opponent moves
                continue;
            }
            if (move.getIllegalMove().equals("")) {
                opponentMovesString = opponentMovesString.concat(move.getString() + " ");
            }
        }

        opponentMovesString = opponentMovesString.substring(0, opponentMovesString.length() - 1);

        player.sendInfo(opponentMovesString);
    }

    //===================================================================================

    @Override
    public List<PlaceArmiesMove> requestPlaceArmiesMoves(Player player) {
        return this.<PlaceArmiesMove>requestMoves("place_armies", player);
    }

    @Override
    public List<AttackTransferMove> requestAttackTransferMoves(Player player) {
        return this.<AttackTransferMove>requestMoves("attack/transfer", player);
    }

    /**
     * Prompts the given player to return some moves, and returns a typed list of moves.
     *
     * FIXME: rewrite the inherited parseMoves() code to avoid unchecked type casts
     */
    private <T> List<T> requestMoves(String prompt, Player player) {
        player.sendInfo("go " + prompt + " " + player.getTimeBank());

        String response = player.getResponse();

        List<? extends Move> parsedMoves = parseMoves(response, player);

        // convert to specifically deployment orders
        @SuppressWarnings("unchecked")
        List<T> typedMoves = parsedMoves.stream()
                .map(m -> (T) m).collect(Collectors.toCollection(LinkedList::new));

        return typedMoves;
    }
}
