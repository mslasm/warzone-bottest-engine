// Copyright 2015 theaigames.com (developers@theaigames.com)

//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at

//        http://www.apache.org/licenses/LICENSE-2.0

//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//
//    For the full copyright and license information, please view the LICENSE
//    file that was distributed with this source code.

package com.theaigames.game.warlight2;

import java.util.LinkedList;
import java.util.Collection;
import java.util.Set;
import java.util.List;
import java.util.Iterator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Random;

import com.theaigames.game.warlight2.map.Map;
import com.theaigames.game.warlight2.map.MapJSON;
import com.theaigames.game.warlight2.map.Settings;
import com.theaigames.game.warlight2.map.Region;
import com.theaigames.game.warlight2.map.SuperRegion;
import com.theaigames.game.warlight2.move.AttackTransferMove;
import com.theaigames.game.warlight2.move.Move;
import com.theaigames.game.warlight2.move.MoveQueue;
import com.theaigames.game.warlight2.move.PlaceArmiesMove;

/**
 * Processor class
 *
 * @author Jim van Eeden <jim@starapple.nl>
 */

public class Processor
{
    // TODO: replace with Map<String, Player>
    private Player player1;
    private Player player2;

    private Map map;
    private Settings settings;
    private Random gameplayRnd;
    private Random mapGenerationRnd;

    private BotCommunication communication;

    private int maxRounds;
    private int roundNr;

    private LinkedList<Move> opponentMovesPlayer1;
    private LinkedList<Move> opponentMovesPlayer2;

    private MoveQueue moveQueue;

    private Set<Integer> wastelands;
    private Set<Integer> pickableStartingRegions;

    private HashMap<Player, List<Integer>> playerPickedRegions;
    private HashMap<Player, Set<Integer>> playerStartingRegions;
    private Player firstPickPlayer;

    public Processor(Map initMap, Settings settings, Random gameplayRnd, Random mapGenerationRnd,
            Player player1, Player player2) {
        this.map = initMap;
        this.settings = settings;
        this.gameplayRnd = gameplayRnd;
        this.mapGenerationRnd = mapGenerationRnd;

        this.maxRounds = settings.getMaxRounds(map.getRegions().size());

        // setup troops for neutral players according to game settings
        setupNeutrals(this.map);

        // setup watelands
        this.wastelands = setupWastelands(this.map);

        // select pickable starting regions
        this.pickableStartingRegions = distributeStartingRegions(this.map, this.wastelands);

        this.player1 = player1;
        this.player2 = player2;
        moveQueue = new MoveQueue(player1, player2);

        this.playerPickedRegions = new HashMap<>();
        this.playerStartingRegions = new HashMap<>();

        this.communication = new BotCommunicationV1(settings);

        opponentMovesPlayer1 = new LinkedList<Move>();
        opponentMovesPlayer2 = new LinkedList<Move>();
    }

    /**
     * Make every region neutral with the number of armies defined in the settings.
     */
    private void setupNeutrals(Map map) {
        for (Region region : map.getRegions()) {
            region.setArmies(settings.getNeutralArmies());
            region.setPlayerName(Region.OWNER_NEUTRAL);
        }
    }

    /**
     * Distributs wastelands according to the settings.
     *
     * @param map : the map which will be used to distribute wastelands
     * @return : the set of wastelands, so that they are excluded from the list of pickable regions
     *           (note: wasteland size may be the same as default neutral size, so size itself
     *                  can not be used to distinguish a wasteland from a neutral)
     */
    private Set<Integer> setupWastelands(Map map) {
        Set<Integer> wastelands = new HashSet<>();

        if (settings.getNumberOfWastelands() > 0) {
            // number of wastlands is at most the number of regions minus the number of players
            int numWastelands = settings.getNumberOfWastelands() < map.getRegions().size()
                    ? settings.getNumberOfWastelands()
                    : map.getRegions().size() - this.getNumPlayers();
            for (int i = 0; i < numWastelands; i++) {
                Region wastelandTarget = getRandomObjectFromCollection(map.getRegions(), mapGenerationRnd);
                if (wastelands.contains(wastelandTarget.getId())) {
                    // already a wasteland
                    i--;
                    continue;
                }
                wastelandTarget.setArmies(settings.getWastelandSize());
                wastelands.add(wastelandTarget.getId());
            }
        }
        return wastelands;
    }

    public Set<Integer> distributeStartingRegions(Map map, Set<Integer> wastelands) {
        Set<Integer> pickableRegions = new HashSet<>();

        switch(settings.getDistributionMode()) {
        case RANDOM_WARLORD:
            // get one random region from each superRegion (or none if the one selected is a wasteland)
            for (SuperRegion superRegion : map.getSuperRegions()) {
                int regionID = getRandomObjectFromCollection(superRegion.getSubRegions(), mapGenerationRnd);
                if (!wastelands.contains(regionID)) {
                    pickableRegions.add(regionID);
                }
            }
            break;
        case RANDOM_CITIES:
            // get all regions except one (and all wastelands) from each superRegion
            for (SuperRegion superRegion : map.getSuperRegions()) {
                int excludeRegionID = getRandomObjectFromCollection(superRegion.getSubRegions(), mapGenerationRnd);
                for (Integer regionID : superRegion.getSubRegions()) {
                    if (!wastelands.contains(regionID) && excludeRegionID != regionID) {
                        pickableRegions.add(regionID);
                    }
                }
            }
            break;
        case FULL:
            for (Integer regionID : map.getRegionIDs()) {
                if (!wastelands.contains(regionID)) {
                    pickableRegions.add(regionID);
                }
            }
            break;
        default:
            throw new RuntimeException("Unsupported distribution mode");
        }

        if (pickableRegions.size() < this.getNumPlayers() * settings.getNumberOfStartingTerritories()) {
            throw new IllegalArgumentException("There are not enough starting regions on this map when using these settings");
        }

        return pickableRegions;
    }

    public int getNumPlayers() {
        return 2;
    }

    public Set<Integer> getPickableStartingRegions() {
        return this.pickableStartingRegions;
    }

    public void getPicksAndInitGame() {
        communication.sendSettings(player1, player2, this.maxRounds);
        communication.sendSettings(player2, player1, this.maxRounds);

        communication.sendBaseMapInfo(player1, map);
        communication.sendBaseMapInfo(player2, map);

        queryAndDistributeStartingRegions(); // decide the player's starting regions
    }

    /**
     * Gets prefered picks from both players and distributes starting regions based on the picks. If
     * a player provides less picks tha necessary a random starting ocation wil be assigned.
     *
     * Starting territories are assigned in the ABBAABB... fashion, always starting from player 1
     */
    public void queryAndDistributeStartingRegions() {

        int numStartingTerritories = settings.getNumberOfStartingTerritories();

        this.playerPickedRegions.put(player1,
                communication.sendPickInfoAndRequestStartingPicks(player1, numStartingTerritories, this.pickableStartingRegions));

        this.playerPickedRegions.put(player2,
                communication.sendPickInfoAndRequestStartingPicks(player2, numStartingTerritories, this.pickableStartingRegions));

        // iterators to go through the picks in the order they are selected
        HashMap<Player, Iterator<Integer>> playerPicksItr = new HashMap<>();
        playerPicksItr.put(player1, this.playerPickedRegions.get(player1).iterator());
        playerPicksItr.put(player2, this.playerPickedRegions.get(player2).iterator());

        this.playerStartingRegions.put(player1, new HashSet<>());
        this.playerStartingRegions.put(player2, new HashSet<>());

        this.firstPickPlayer = decideWhoGetsFirstPick();  // based on the settings can be either random or given player

        Player secondPickPlayer = (this.firstPickPlayer == player1) ? player2 : player1;

        int iter = 0;
        // iterate between two players, assigning territories in the A BB AA BB... pattern,
        // until both players get the required number of starting regions
        while (!playerDonePicking(player1) || !playerDonePicking(player2)) {

            // player selected to pick first picks on iterations  0, 2, 4, etc. (or all the time after other player is done)
            // player selected to pick second picks on iterations 1, 3, 5, etc. (or all the time after other player is done)
            Player picksThisIteration = (playerDonePicking(secondPickPlayer) ||
                                         ((iter % 2 == 0) && !playerDonePicking(firstPickPlayer))) ? firstPickPlayer : secondPickPlayer;

            Iterator<Integer> remainingPicks = playerPicksItr.get(picksThisIteration);

            // in the A BB AA BB scheme on 1st iteration A gets one pick, on all other iterations players get 2 picks
            // (except if they have only 1 territory left unassigned)
            int maxPicksToAssign = (iter == 0) ? 1 : Math.min(2, numUnassignedStartingTerritories(picksThisIteration));

            for (int p = 0; p < maxPicksToAssign; p++) {
                boolean teritorySelected = false;
                while (!teritorySelected) {
                    Integer nextPick = remainingPicks.hasNext()
                            ? remainingPicks.next()
                            : getRandomObjectFromCollection(getPickableStartingRegions(), mapGenerationRnd);

                    Region selectedRegion = map.getRegion(nextPick);

                    if (selectedRegion.isNeutral()) {
                        // mark the region as belonging to the player on the map, and set initial armies
                        selectedRegion.setPlayerName(picksThisIteration.getName());
                        selectedRegion.setArmies(settings.getInitilPlayerArmies());

                        // for the record only, add to the list of player's starting regions
                        playerStartingRegions.get(picksThisIteration).add(nextPick);

                        System.out.format("Player [%s] received starting territory [%d] (%s)%n",
                                picksThisIteration.getName(), nextPick, selectedRegion.getName());

                        teritorySelected = true;
                    }
                }
            }
            iter++;
        }

        System.out.format("All starting territories have been assigned%n");

        /*
        // FIXME: remove
        // start of the output for after the picking phase
        fullPlayedGame.add(new MoveResult(null, map.getMapCopy()));
        player1PlayedGame.add(new MoveResult(null, map.getVisibleMapCopyForPlayer(player1, settings)));
        player2PlayedGame.add(new MoveResult(null, map.getVisibleMapCopyForPlayer(player2, settings)));
        fullPlayedGame.add(null);
        player1PlayedGame.add(null);
        player2PlayedGame.add(null);
        */
    }

    private Player decideWhoGetsFirstPick() {
        double rand = this.gameplayRnd.nextDouble();
        if (settings.getFirstPlayerPolicty() == Settings.FirstPlayer.PLAYER_1 ||
            (settings.getFirstPlayerPolicty() == Settings.FirstPlayer.RANDOM && rand < 0.5)) {
            return player1;
        } else {
            return player2;
        }
    }

    private boolean playerDonePicking(Player player) {
        return numUnassignedStartingTerritories(player) == 0;
    }

    private int numUnassignedStartingTerritories(Player player) {
        return settings.getNumberOfStartingTerritories() - playerStartingRegions.get(player).size();
    }

    /**
     * Plays one round of the game
     *
     * @param roundNumber
     */
    public void playRound(int roundNumber) {
        this.roundNr = roundNumber;

        //System.out.println("Standings at round start: ----------");
        //System.out.println(MapJSON.getStandingsJSON(map).toString());
        //System.out.println("------------------------------------");

        recalculateStartingArmies();  // calculate how much armies the players get at the start of the
                                      // round (depending on owned SuperRegions and territories)

        communication.sendTurnStartUpdate(player1, opponentMovesPlayer1, map.getVisibleMapCopyForPlayer(player1, settings));
        communication.sendTurnStartUpdate(player2, opponentMovesPlayer2, map.getVisibleMapCopyForPlayer(player2, settings));

        // FIXME: replce global queues with a new queue for each turn
        //        (for the moveQueue, possibly separate queues for PlaceArmies and MoveAttack moves)
        opponentMovesPlayer1.clear();
        opponentMovesPlayer2.clear();
        moveQueue.clear();

        getPlaceArmyMoves(player1);
        getPlaceArmyMoves(player2);

        executePlaceArmies();

        getAttackTransferMoves(player1);
        getAttackTransferMoves(player2);

        executeAttackTransfer();

        roundNr++;
    }

    /**
     * Queries the player for deployments, and places the orders received into the move queue.
     *
     * @param player : player to ask for deployments
     */
    private void getPlaceArmyMoves(Player player) {
        List<PlaceArmiesMove> deployments = communication.requestPlaceArmiesMoves(player);

        for (PlaceArmiesMove move : deployments) {
            queuePlaceArmies(move);
        }
    }

    /**
     * Queries the player for attack/transfer moves, and places the orders received into the move queue.
     *
     * @param player : player to ask for moves and transfers
     */
    private void getAttackTransferMoves(Player player) {
        List<AttackTransferMove> orders = communication.requestAttackTransferMoves(player);

        for (AttackTransferMove move : orders) {
            queueAttackTransfer(move);
        }
    }

    /**
     * Queues the placeArmies moves given by the player Checks if the moves are legal
     *
     * @param plm : placeArmiesMove to be queued
     */
    private void queuePlaceArmies(PlaceArmiesMove plm) {
        // should not ever happen
        if (plm == null) {
            System.err.println("Error on place_armies input.");
            return;
        }

        Region region = map.getRegion(plm.getRegion());
        Player player = getPlayer(plm.getPlayerName());
        int armies = plm.getArmies();

        // check legality
        if (region == null)  {
            plm.setIllegalMove(" place-armies " + "for non-existing region " + plm.getRegion());
        } else if (player == null) {
            plm.setIllegalMove(" place-armies " + "for non-existing player " + plm.getPlayerName());
        } else if (region.ownedByPlayer(player.getName())) {
            if (armies < 1) {
                plm.setIllegalMove(" place-armies " + "cannot place less than 1 army");
            } else {
                if (armies > player.getArmiesLeft()) // player wants to place more armies than he has left
                    plm.setArmies(player.getArmiesLeft()); // place all armies he has left
                if (player.getArmiesLeft() <= 0)
                    plm.setIllegalMove(" place-armies " + "no armies left to place");

                player.setArmiesLeft(player.getArmiesLeft() - plm.getArmies());
            }
        } else {
            plm.setIllegalMove(plm.getRegion() + " place-armies " + " not owned");
        }

        moveQueue.addMove(plm);
    }

    /**
     * Queues the attackTransfer moves given by the player Does the first checks for legality
     *
     * @param atm : attackTransferMove to be queued
     */
    private void queueAttackTransfer(AttackTransferMove atm) {
        // should not ever happen
        if (atm == null) {
            System.err.println("Error on attack/transfer input.");
            return;
        }

        Region fromRegion = map.getRegion(atm.getFromRegion());
        Region toRegion = map.getRegion(atm.getToRegion());
        Player player = getPlayer(atm.getPlayerName());
        int armies = atm.getArmies();

        // check legality
        if (fromRegion == null)  {
            atm.setIllegalMove(" attack/transfer " + " from non-existing region " + atm.getFromRegion());
        } else if (toRegion == null) {
            atm.setIllegalMove(" attack/transfer " + " to non-existing region " + atm.getToRegion());
        } else if (player == null) {
            atm.setIllegalMove(" attack/transfer " + " for non-existing player " + atm.getPlayerName());
        }else if (fromRegion.ownedByPlayer(player.getName())) {
            if (fromRegion.isNeighbor(toRegion)) {
                if (armies < 1)
                    atm.setIllegalMove(" attack/transfer " + "cannot use less than 1 army");
            } else
                atm.setIllegalMove(atm.getToRegion() + " attack/transfer " + "not a neighbor");
        } else
            atm.setIllegalMove(atm.getFromRegion() + " attack/transfer " + "not owned");

        moveQueue.addMove(atm);
    }

    /**
     * Executes all placeArmies move currently in the queue Moves have already been checked if they are legal Also stores the
     * moves for the visualizer
     */
    private void executePlaceArmies() {
        for (PlaceArmiesMove move : moveQueue.placeArmiesMoves) {
            Region region = map.getRegion(move.getRegion());
            if (region == null)
                continue;

            if (move.getIllegalMove().equals("")) { // the move is not illegal
                region.setArmies(region.getArmies() + move.getArmies());
            }

            if (map.visibleRegionsForPlayer(player1).contains(region.getId())) {
                if (move.getPlayerName().equals(player2.getName()))
                    opponentMovesPlayer1.add(move); // for the opponent_moves output
            }
            if (map.visibleRegionsForPlayer(player2).contains(region.getId())) {
                if (move.getPlayerName().equals(player1.getName()))
                    opponentMovesPlayer2.add(move); // for the opponent_moves output
            }
        }
    }

    /**
     * Executes all attackTransfer moves currently in the queue
     * Does a lot of legality checks and determines whether it is an attack or a transfer.
     * Also stores the moves for the visualizer
     */
    private void executeAttackTransfer() {
        Map mapAtTurnStart = map.clone(); // this is the map as players saw it when they issued orders

        Set<Integer> visibleRegionsPlayer1OldMap = mapAtTurnStart.visibleRegionsForPlayer(player1);
        Set<Integer> visibleRegionsPlayer2OldMap = mapAtTurnStart.visibleRegionsForPlayer(player2);

        // for each attack/transfer from region with ID X to a region with ID Y has an element "X_Y",
        // to make sure an armies are never moved/transferred twice between the same regions on a single turn
        Set<String> usedTransfers = new HashSet<>();

        int moveNr = 1;
        Boolean previousMoveWasIllegal = false;
        String previousMovePlayer = "";
        while (moveQueue.hasNextAttackTransferMove()) {
            AttackTransferMove move = moveQueue.getNextAttackTransferMove(moveNr, previousMovePlayer,
                    previousMoveWasIllegal);

            Region fromRegion = map.getRegion(move.getFromRegion());
            Region toRegion   = map.getRegion(move.getToRegion());

            if (move.getIllegalMove().equals("")) // the move is not illegal
            {
                Region oldFromRegion = mapAtTurnStart.getRegion(move.getFromRegion());
                Region oldToRegion   = mapAtTurnStart.getRegion(move.getToRegion());
                Player player        = getPlayer(move.getPlayerName());

                if (fromRegion.ownedByPlayer(player.getName())) // check if the fromRegion still belongs to this player
                {
                    if (!usedTransfers.contains(fromRegion.getId() + "_" + toRegion.getId())) // each turn there can only be one
                                                                                              // attack/transfer between two regions
                    {
                        if (oldFromRegion.getArmies() > 1) // there are still armies that can be used
                        {
                            if (oldFromRegion.getArmies() < fromRegion.getArmies()
                                    && oldFromRegion.getArmies() - 1 < move.getArmies()) // not enough armies on fromRegion
                                                                                         // at the start of the round?
                                move.setArmies(oldFromRegion.getArmies() - 1); // move the maximal number.
                            else if (oldFromRegion.getArmies() >= fromRegion.getArmies()
                                    && fromRegion.getArmies() - 1 < move.getArmies()) // not enough armies on fromRegion
                                                                                      // currently?
                                move.setArmies(fromRegion.getArmies() - 1); // move the maximal number.

                            oldFromRegion.setArmies(oldFromRegion.getArmies() - move.getArmies()); // update
                                                                                                   // oldFromRegion so
                                                                                                   // new armies cannot
                                                                                                   // be used yet

                            if (toRegion.ownedByPlayer(player.getName())) // transfer
                            {
                                if (fromRegion.getArmies() > 1) {
                                    fromRegion.setArmies(fromRegion.getArmies() - move.getArmies());
                                    toRegion.setArmies(toRegion.getArmies() + move.getArmies());
                                    usedTransfers.add(fromRegion.getId() + "_" + toRegion.getId());
                                } else
                                    move.setIllegalMove(move.getFromRegion() + " transfer " + "only has 1 army");
                            } else // attack
                            {
                                int armiesDestroyed = doAttack(move);
                                if (armiesDestroyed == 0) { // attack was succes
                                    oldToRegion.setArmies(1); // region was taken, so cannot be used anymore, even if
                                                             // it's taken back.
                                } else if (armiesDestroyed > 0) { // attack failed
                                    oldToRegion.setArmies(oldToRegion.getArmies() - armiesDestroyed); // armies
                                                                                                      // destroyed and
                                                                                                      // replaced cannot
                                                                                                      // be used again
                                                                                                      // this turn
                                }
                                usedTransfers.add(fromRegion.getId() + "_" + toRegion.getId());
                            }
                        } else
                            move.setIllegalMove(move.getFromRegion() + " attack/transfer "
                                    + "has used all available armies");
                    } else
                        move.setIllegalMove(move.getFromRegion() + " attack/transfer "
                                + "has already attacked/transfered to this region");
                } else
                    move.setIllegalMove(move.getFromRegion() + " attack/transfer " + "was taken this round");
            }

            Set<Integer> visibleRegionsPlayer1Map = map.visibleRegionsForPlayer(player1);
            Set<Integer> visibleRegionsPlayer2Map = map.visibleRegionsForPlayer(player2);

            if (visibleRegionsPlayer1Map.contains(fromRegion.getId())
                    || visibleRegionsPlayer1Map.contains(toRegion.getId())
                    || visibleRegionsPlayer1OldMap.contains(toRegion.getId())) {
                if (move.getPlayerName().equals(player2.getName()))
                    opponentMovesPlayer1.add(move); // for the opponent_moves output
            }
            if (visibleRegionsPlayer2Map.contains(fromRegion.getId())
                    || visibleRegionsPlayer2Map.contains(toRegion.getId())
                    || visibleRegionsPlayer2OldMap.contains(toRegion.getId())) {
                if (move.getPlayerName().equals(player1.getName()))
                    opponentMovesPlayer2.add(move); // for the opponent_moves output
            }

            visibleRegionsPlayer1OldMap = visibleRegionsPlayer1Map;
            visibleRegionsPlayer2OldMap = visibleRegionsPlayer2Map;

            // set some stuff to know what next move to get
            if (move.getIllegalMove().equals("")) {
                previousMoveWasIllegal = false;
                moveNr++;
            } else {
                previousMoveWasIllegal = true;
            }
            previousMovePlayer = move.getPlayerName();
        }
    }

    /**
     * Processes the result of an attack see wiki.warlight.net/index.php/Combat_Basics
     *
     * @param move : attackTransfer move
     * @return : amount of defenders destroyed, used for correcting coming moves
     */
    private int doAttack(AttackTransferMove move) {
        Region fromRegion = map.getRegion(move.getFromRegion());
        Region toRegion = map.getRegion(move.getToRegion());
        int attackingArmies;
        int defendingArmies = toRegion.getArmies();

        int defendersDestroyed = 0;
        int attackersDestroyed = 0;

        if (fromRegion.getArmies() > 1) {
            if (fromRegion.getArmies() - 1 >= move.getArmies()) // are there enough armies on fromRegion?
                attackingArmies = move.getArmies();
            else
                attackingArmies = fromRegion.getArmies() - 1;

            Battle battle = new Battle(attackingArmies, defendingArmies, this.gameplayRnd, this.settings);
            attackersDestroyed = battle.getDestroyedAttackers();
            defendersDestroyed = battle.getDestroyedDefenders();

            if (attackersDestroyed >= attackingArmies) {
                if (defendersDestroyed >= defendingArmies)
                    defendersDestroyed = defendingArmies - 1;

                attackersDestroyed = attackingArmies;
            }

            // process result of attack
            if (defendersDestroyed >= defendingArmies) // attack success
            {
                fromRegion.setArmies(fromRegion.getArmies() - attackingArmies);
                toRegion.setPlayerName(move.getPlayerName());
                toRegion.setArmies(attackingArmies - attackersDestroyed);
                return 0;

            } else // attack fail
            {
                fromRegion.setArmies(fromRegion.getArmies() - attackersDestroyed);
                toRegion.setArmies(toRegion.getArmies() - defendersDestroyed);
                return defendersDestroyed;
            }
        } else
            move.setIllegalMove(move.getFromRegion() + " attack " + "only has 1 army");

        return -1;
    }

    /**
     * @return : the winner of the game, null if the game is not over
     */
    public Player getWinner() {
        if (map.ownedRegionsByPlayer(player1).isEmpty())
            return player2;
        else if (map.ownedRegionsByPlayer(player2).isEmpty())
            return player1;
        else
            return null;
    }

    /**
     * Calculates how many armies each player is able to place on the map for the next round
     */
    public void recalculateStartingArmies() {
        player1.setArmiesLeft(settings.getBaseArmiesPerTurn());
        player2.setArmiesLeft(settings.getBaseArmiesPerTurn());

        for (SuperRegion superRegion : map.getSuperRegions()) {
            Player player = getPlayer(map.getSuperRegionOwner(superRegion));
            if (player != null)
                player.setArmiesLeft(player.getArmiesLeft() + superRegion.getArmiesReward());
        }
    }

    /**
     * @param playerName : string of player's name
     * @return : Player object who has given name
     */
    private Player getPlayer(String playerName) {
        if (player1.getName().equals(playerName))
            return player1;
        else if (player2.getName().equals(playerName))
            return player2;
        else
            return null;
    }

    /**
     * A helper method to get a random object from a collection of objects.
     *
     * (note: not very efficient, but is suposedly only used a few times during game setup)
     *
     * @param collection : a collection to pick an object from
     * @param rnd        : the random number generator to be used
     * @return           : a random object from the collection
     */
    private <T> T getRandomObjectFromCollection(Collection<T> collection, Random rnd) {
        // the algorithm assumes all IDs are sequential, from 1 to number_of_regions
        double rand = rnd.nextDouble();
        int index = (int) (rand * collection.size());
        int i = 0;
        for (T element : collection) {
            if (i++ == index) {
                return element;
            }
        }
        throw new RuntimeException("Error selecting a random object from a collection");
    }

    /**
     * @return : current round number
     */
    public int getRoundNr() {
        return roundNr;
    }
}
