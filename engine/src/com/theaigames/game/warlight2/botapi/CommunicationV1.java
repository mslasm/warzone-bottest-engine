package com.theaigames.game.warlight2.botapi;

import java.util.Collection;
import java.util.stream.Collectors;

import java.util.Arrays;
import java.util.List;
import java.util.LinkedList;

import com.theaigames.game.warlight2.BotCommunication;
import com.theaigames.game.warlight2.Player;
import com.theaigames.game.warlight2.map.Map;
import com.theaigames.game.warlight2.map.MapJSON;
import com.theaigames.game.warlight2.map.Settings;
import com.theaigames.game.warlight2.move.AttackTransferMove;
import com.theaigames.game.warlight2.move.Move;
import com.theaigames.game.warlight2.move.PlaceArmiesMove;

/**
 * This implementation of the interface uses a protocol somewhat similar to the one
 * originally used by theaigames.com, with a few differences:
 *
 *  1) base map info is sent as a single JSON string
 *     (in the format used by QueryGame API on the real WarZone server, as in the "map" JSON key)
 *
 *  2) map updates are also sent as a JSON string
 *     (in the format used by QueryGame API on the real WarZone server, as in the "standingsXX" JSON key)
 *
 *  3) all picks are requested at once, and opponent picks are not sent to the bot
 *     (as it is on the real server)
 *
 *  4) all visible moves are reported, not just opponent moves
 *
 *  5) in addition to some basic settings sent separately, a full settings JSON is sent at the beginning of a game
 *     (in the format used by QueryGame API on the real WarZone server, as in the "settings" JSON key,
 *     possibly with custom extensions used to control specifics of local bot games)
 *
 *  The goal is to keep changes to a minimum, but provide a more realistic environment
 *  (e.g. this map format allows overlapping bonuses and more fog levels, while not knowing
 *  opponent's picks is an essential part of the game)
 */
public class CommunicationV1 extends CommunicationBaseParser implements BotCommunication
{
    public CommunicationV1(Settings settings) {
        super(settings);
    }

    //===================================================================================

    @Override
    public void sendSettings(Player player, Player otherPlayer, int maxRounds) {
        player.sendInfo("settings timebank " + settings.getMaxTimebank());
        player.sendInfo("settings time_per_move " + settings.getExtraTimePerMove());
        player.sendInfo("settings max_rounds " + maxRounds);
        player.sendInfo("settings your_bot " + player.getName());
        player.sendInfo("settings opponent_bot " + otherPlayer.getName());
        player.sendInfo("settings all_settings_json " + removeNewlines(settings.getSettingsJSON().toString()));
    }

    @Override
    public void sendBaseMapInfo(Player player, Map map) {
        String mapJSONString = removeNewlines(MapJSON.getMapJSON(map).toString());

        String mapInfoString = "setup_map " + mapJSONString;

        player.sendInfo(mapInfoString);
    }

    //===================================================================================

    @Override
    public List<Integer> sendPickInfoAndRequestStartingPicks(Player player, int numberOfStartingRegions, Collection<Integer> pickableRegions) {
        // each bot can submit as many as (numberOfStartingTerritories * 2) picks
        // (since all players may pick the same, so with e.g. 3 picks player2 will end up with picks 2, 3 and 6)
        int maxSubmittedPicks = numberOfStartingRegions * 2;

        player.sendInfo("settings starting_regions_amount " + numberOfStartingRegions);
        player.sendInfo("settings maximum_number_of_picks " + maxSubmittedPicks);

        player.sendInfo("settings starting_regions " + asSeparatedString(pickableRegions, " "));

        player.sendInfo("pick_starting_region " + player.getTimeBank());

        String response = player.getResponse();

        List<Integer> pickedRegions = parsePicks(player, response, pickableRegions, maxSubmittedPicks);

        System.out.format("Player [%s] selected the following valid regions: %s%n",
                player.getName(), Arrays.toString(pickedRegions.toArray()));

        return pickedRegions;
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
        String standingsJSON = removeNewlines(MapJSON.getStandingsJSON(visibleMapForPlayer).toString());

        player.sendInfo("update_map " + standingsJSON);
    }

    /**
     * Informs the player about all the observed moves from the previous turn.
     *
     * @param player : player to send the info to
     */
    private void sendVisibleMovesInfo(Player player, List<Move> visibleMoves) {
        String visibleMovesString = "visible_moves ";

        for (Move move : visibleMoves) {
            if (move.getIllegalMove().equals("")) {
                visibleMovesString = visibleMovesString.concat(move.getString() + " ");
            }
        }
        visibleMovesString = visibleMovesString.substring(0, visibleMovesString.length() - 1);

        player.sendInfo(visibleMovesString);
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
