package com.theaigames.game.warlight2;

import java.util.Collection;
import java.util.List;

import com.theaigames.game.warlight2.map.Map;
import com.theaigames.game.warlight2.move.Move;
import com.theaigames.game.warlight2.move.AttackTransferMove;
import com.theaigames.game.warlight2.move.PlaceArmiesMove;

/**
 * A set of methods for communication between the engine and the bot - all
 * communication is assumed to go through one of the methods.
 *
 * Player classes are assumed to perform the actual sending and receiving, this interface
 * provides an API for serializing and de-serializing game data before it is sent over a
 * communication channel.
 */
public interface BotCommunication
{
    /**
     * Sends all game settings to the given player.
     *
     * This data should only be sent once at the start of the game.
     *
     * @param player      : player to send settings to
     * @param otherPlayer : the other player (so that players know who they play against)
     * @param maxRounds   : the maximum numberof rounds the game will be played for until declaring a draw/a winner
     */
    public void sendSettings(Player player, Player otherPlayer, int maxRounds);

    /**
     * Sends base map info (regions, connections) to the given player.
     * Only base map structure will be send, not amount of units or region owners or wastelands.
     *
     * This data should only be sent once at the start of the game.
     *
     * @param player : player to send info to
     * @param map    : map to be sent
     */
    public void sendBaseMapInfo(Player player, Map map);

    /**
     * Sends the list of available starting regions to pick from and the number of territories that
     * the player wil get to start from. After that queries and returns the selected picks from the player
     * (only valid selections are included, and at number of selections is lmited to the
     * maximum allowed number).
     *
     * This communication is supposed to be only performed once at the start of the game.
     *
     * FIXME: this method makes pick processing sequential, it makes more sense to send all info to all players first
     *        and let all players think/pick in parrallel. However this is simpler and local debugging is easier, so OK for now
     *
     * @param player                  : player to send info to and request picks from
     * @param numberOfStartingRegions : number of regions each player will start with
     * @param pickableRegions         : the set of starting picks
     * @return : a list of (valid) regions picked by the player
     */
    public List<Integer> sendPickInfoAndRequestStartingPicks(Player player, int numberOfStartingRegions, Collection<Integer> pickableRegions);

    /**
     * Sends all the new data available before each turn (observed moves during the previous turn,
     * map state at the end of previous turn, number of armies available to be deployed).
     *
     * This update is supposed ot be sent before each turn.
     *
     * @param player              : player to send info to
     * @param visibleMoves        : all moves performed last turn visible by this player
     * @param visibleMapForPlayer : the state of the map as observed by the player at the start of this turn
     */
    public void sendTurnStartUpdate(Player player, List<Move> visibleMoves, Map visibleMapForPlayer);

    /**
     * Asks the bot for his placeArmiesMoves and returns the answer
     *
     * @param player : player to query for placement orders
     * @return : the bot's output
     */
    public List<PlaceArmiesMove> requestPlaceArmiesMoves(Player player);

    /**
     * Asks the bot for this attackTransferMoves and returns the answer
     *
     * @param player : player to query for move/transfer orders
     * @return : the bot's output
     */
    public List<AttackTransferMove> requestAttackTransferMoves(Player player);
}
