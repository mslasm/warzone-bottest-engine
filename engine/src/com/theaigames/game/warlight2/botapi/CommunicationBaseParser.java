package com.theaigames.game.warlight2.botapi;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import com.theaigames.game.warlight2.Player;
import com.theaigames.game.warlight2.map.Settings;
import com.theaigames.game.warlight2.move.AttackTransferMove;
import com.theaigames.game.warlight2.move.Move;
import com.theaigames.game.warlight2.move.PlaceArmiesMove;

/**
 * Includes implementations of some helper parser methods, useful by different implementations
 * of the communication protocol.
 */
public abstract class CommunicationBaseParser
{
    protected Settings settings;

    protected CommunicationBaseParser(Settings settings) {
        this.settings = settings;
    }

    /**
     * A single method to log all communication parse errors.
     */
    protected void logParseError(Player player, String format, Object ...args) {
        System.out.format("[%s] " + format, player.getName(), args);
    }

    /**
     * Returns a string with all values separatd by the given separator.
     *
     * @param items a list of items
     * @param separator a separator
     * @return
     */
    protected <T> String asSeparatedString(Collection<T> items, String separator) {
        return items.stream()
                .map(n -> String.valueOf(n))
                .collect(Collectors.joining(separator));
    }

    /**
     * @return : a string with all newlines and tabs replaced by spaces.
     */
    protected String removeNewlines(String str) {
        return str.replaceAll("[\\t\\n\\r]+"," ");
    }

    /**
     * Attempts to parse an integer form the provided string.
     *
     * @return : parsed integer, or null
     */
    protected Integer parseIntFromInput(String input, String itemDescription, Player player) {
        try {
            return Integer.parseInt(input);
        } catch (Exception e) {
            this.logParseError(player, "non-integer value of [%s] : [%s]%n", itemDescription, input);
            return null;
        }
    }

    //===================================================================================
    // some more specific methods:

    /**
     * @param botPicks   : the picks sent by the bot in the format "n n n n ...", where n is an integer region ID
     * @param validPicks : a set of valid picks
     * @param maxPicks   : maximum number of picks returned. Only the first so many valid picks will be returned
     * @return           : a list of valid region IDs parsed from bot reply. Note that order is important.
     */
    protected List<Integer> parsePicks(Player player, String botPicks, Collection<Integer> validPicks, int maxPicks) {
        List<Integer> pickedRegions = new LinkedList<>();

        String[] parts = botPicks.split(" ");
        if (parts.length > maxPicks) {
            logParseError(player, "[pick] string has too many picks (max is %d): [%d]%n", maxPicks, botPicks);
        }

        for (int i = 0; i < Math.min(parts.length, maxPicks); i++) {
            try {
                int id = Integer.parseInt(parts[i]);
                if (validPicks.contains(id)) {
                    if (!pickedRegions.contains(id)) {
                        pickedRegions.add(id);
                    } else {
                        logParseError(player, "[pick] region id [%d] is selected more than once%n", id);
                    }
                } else {
                    logParseError(player, "[pick] picked region id [%d] is not in the set of available picks%n", id);
                }
            } catch (Exception e) {
                logParseError(player, "[pick] picked region id is not an integer: [%s]%n", parts[i]);
                return null;
            }
        }
        return pickedRegions;
    }

    /**
     * Parses sequence of moves given by player
     *
     * @param input  : input string
     * @param player : player who gave the input
     * @return : list of moves
     */
    protected List<? extends Move> parseMoves(String input, Player player) {
        LinkedList<Move> moves = new LinkedList<>();

        try {
            input = input.trim();
            if (input.length() <= 1)
                return moves;

            String[] split = input.split(",");

            for (int i = 0; i < split.length; i++) {
                if (i > settings.getMaxMovesPerPlayerPerTurn()) {
                    logParseError(player, "maximum number of moves reached (max %d moves are allowed)%n",
                            settings.getMaxMovesPerPlayerPerTurn());
                    break;
                }
                Move move = parseMove(split[i], player);
                if (move != null)
                    moves.add(move);
            }
        } catch (Exception e) {
            logParseError(player, "move input is null%n");
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
            logParseError(player, "incorrect player name [%s] or move format incorrect: [%s]%n", split[0], input);
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

        logParseError(player, "move format incorrect: [%s]%n", input);
        return null;
    }

}
