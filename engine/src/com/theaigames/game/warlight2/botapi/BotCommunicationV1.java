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
public class BotCommunicationV1 implements BotCommunication
{
    private Settings settings;

    public BotCommunicationV1(Settings settings) {
        this.settings = settings;
    }

    //===================================================================================

    @Override
    public void sendSettings(Player player, Player otherPlayer, int maxRounds) {
        player.sendInfo("settings timebank " + settings.getMaxTimebank());
        player.sendInfo("settings time_per_move " + settings.getExtraTimePerMove());
        player.sendInfo("settings max_rounds " + maxRounds);
        player.sendInfo("settings your_bot " + player.getName());
        player.sendInfo("settings opponent_bot " + otherPlayer.getName());
        player.sendInfo("settings all_settings_json " + asString(settings.getSettingsJSON()));
    }

    @Override
    public void sendBaseMapInfo(Player player, Map map) {
        String mapJSONString = asString(MapJSON.getMapJSON(map));

        String mapInfoString = "setup_map " + mapJSONString;

        player.sendInfo(mapInfoString);
    }

    //===================================================================================

    @Override
    public List<Integer> sendPickInfoAndRequestStartingPicks(Player player, int numberOfStartingRegions, Collection<Integer> pickableRegions) {
        // each bot can submit as many as (numberOfStartingTerritories * 2) picks
        // (since all players may pick the same, so with e.g. 3 picks player2 will end up with picks 2, 3 and 6)
        int maxSubmittedPicks = numberOfStartingRegions * 2;

        String pickAmountString       = "settings starting_regions_amount " + numberOfStartingRegions;
        String maxNumberOfPicksString = "settings maximum_number_of_picks " + maxSubmittedPicks;

        player.sendInfo(pickAmountString);
        player.sendInfo(maxNumberOfPicksString);

        String startingRegionsString = "settings starting_regions";
        for (Integer regionID : pickableRegions) {
            startingRegionsString = startingRegionsString.concat(" " + regionID);
        }
        player.sendInfo(startingRegionsString);

        String output = "pick_starting_region " + player.getTimeBank();
        player.sendInfo(output);

        String response = player.getResponse();

        List<Integer> pickedRegions = parsePicks(response, pickableRegions, maxSubmittedPicks);

        System.out.format("Player [%s] selected the following valid regions: %s%n",
                player.getName(), Arrays.toString(pickedRegions.toArray()));

        return pickedRegions;
    }

    /**
     * @param botPicks   : the picks sent by the bot in the format "n n n n ...", where n is an integer region ID
     * @param validPicks : a set of valid picks
     * @param maxPicks   : maximum number of picks returned. Only the first so many valid picks will be returned
     * @return           : a list of valid region IDs parsed from bot reply. Note that order is important.
     */
    private List<Integer> parsePicks(String botPicks, Collection<Integer> validPicks, int maxPicks) {
        List<Integer> pickedRegions = new LinkedList<>();

        String[] parts = botPicks.split(" ");
        if (parts.length > maxPicks) {
            System.out.format("[pick] Pick string has too many picks (max is %d): [%d]%n", maxPicks, botPicks);
        }

        for (int i = 0; i < Math.min(parts.length, maxPicks); i++) {
            try {
                int id = Integer.parseInt(parts[i]);
                if (validPicks.contains(id)) {
                    if (!pickedRegions.contains(id)) {
                        pickedRegions.add(id);
                    } else {
                        System.out.format("[pick] Region id [%d] is selected more than once%n", id);
                    }
                } else {
                    System.out.format("[pick] Picked region id [%d] is not in the set of available picks%n", id);
                }
            } catch (Exception e) {
                System.out.format("[pick] Picked region id is not an integer: [%s]%n", parts[i]);
                return null;
            }
        }
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
        String updateStartingArmiesString = "settings starting_armies";

        updateStartingArmiesString = updateStartingArmiesString.concat(" " + player.getArmiesLeft());

        player.sendInfo(updateStartingArmiesString);
    }

    /**
     * Informs the player about how his visible map looks now
     *
     * @param player : player to send the info to
     */
    private void sendUpdateMapInfo(Player player, Map visibleMapForPlayer) {
        String standingsJSON = asString(MapJSON.getStandingsJSON(visibleMapForPlayer));

        String updateMapString = "update_map " + standingsJSON;

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

    /**
     * Parses sequence of moves given by player
     *
     * @param input  : input string
     * @param player : player who gave the input
     * @return : list of moves
     */
    private List<? extends Move> parseMoves(String input, Player player) {
        LinkedList<Move> moves = new LinkedList<>();

        try {
            input = input.trim();
            if (input.length() <= 1)
                return moves;

            String[] split = input.split(",");

            for (int i = 0; i < split.length; i++) {
                if (i > settings.getMaxMovesPerPlayerPerTurn()) {
                    System.out.format("Maximum number of moves reached by player [%s], max %d moves are allowed%n",
                            player.getName(), settings.getMaxMovesPerPlayerPerTurn());
                    break;
                }
                Move move = parseMove(split[i], player);
                if (move != null)
                    moves.add(move);
            }
        } catch (Exception e) {
            player.getBot().addToDump("Move input is null\n");
        }
        return moves;
    }

    /**
     * Parses a move from input string given by player
     *
     * @param input  : input string
     * @param player : player who gave the input
     * @return : parsed move
     */
    private Move parseMove(String input, Player player) {
        String[] split = input.trim().split(" ");

        if (!split[0].equals(player.getName())) {
            System.out.format("Incorrect player name [%s] or move format incorrect in [%s]%n", split[0], input);
            return null;
        }

        if (split[1].equals("place_armies")) {
            Integer region = parseIntFromInput(split[2], "region", player);
            Integer armies = parseIntFromInput(split[3], "armies", player);

            if (region != null && armies != null)
                return new PlaceArmiesMove(player.getName(), region, armies);
            return null;
        } else if (split[1].equals("attack/transfer")) {
            Integer fromRegion = parseIntFromInput(split[2], "from region", player);
            Integer toRegion   = parseIntFromInput(split[3], "to region", player);
            Integer armies     = parseIntFromInput(split[4], "armies", player);

            if (fromRegion != null && toRegion != null && armies != null)
                return new AttackTransferMove(player.getName(), fromRegion, toRegion, armies);
            return null;
        }

        System.out.format("Input [%s] move format incorrect for player [%s]%n", input, player.getName());
        return null;
    }

    private Integer parseIntFromInput(String input, String itemDescription, Player player) {
        try {
            return Integer.parseInt(input);
        } catch (Exception e) {
            System.out.format("Player [%s] provided a non-integer value for [%s] : %s%n", player.getName(), itemDescription);
            return null;
        }
    }

    //===================================================================================

    /**
     * @param json : a JSONObject or a JSONArray
     * @return : a JSON converted to a single string
     */
    private <T> String asString(T json) {
        // TODO: maybe: base64 encode? does not matter, current JSONs are simple and do not have important newlines
        return json.toString().replaceAll("[\\t\\n\\r]+"," "); // remove all newlines and extra spaces just in case
    }
}
