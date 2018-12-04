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

import java.io.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

import java.lang.Thread;

import com.theaigames.engine.Engine;
import com.theaigames.engine.Logic;
import com.theaigames.engine.io.IOPlayer;

import com.theaigames.game.warlight2.map.Map;
import com.theaigames.game.warlight2.map.MapJSON;
import com.theaigames.game.warlight2.map.Settings;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Warlight2 class
 *
 * Main class for Warlight2
 *
 * @author Jim van Eeden <jim@starapple.nl>
 */

public class Warlight2 implements Logic
{
    private String gameID;

    private Settings settings;

    private String playerName1, playerName2;
    private final String mapFile;
    private final String settingsFile;

    private Processor processor;
    private Player player1, player2;
    private int maxRounds;

    private Random mapGenerationRnd = new Random();
    private Random gameplayRnd = new Random();

    public Warlight2(String gameID, int randomMapSeed, int randomGameSeed, String mapFile, String settingsFile,
            String playerName1, String playerName2) {
        if (randomMapSeed > 0) {
            System.out.format("Using wasteland generation/available regions seed: %d\n", randomMapSeed);
            this.mapGenerationRnd.setSeed(randomMapSeed);
        }
        if (randomGameSeed > 0) {
            System.out.format("Using turn order/battle seed: %d\n", randomGameSeed);
            this.gameplayRnd.setSeed(randomGameSeed);
        }

        this.gameID = gameID;
        this.mapFile = mapFile;
        this.settingsFile = settingsFile;

        this.playerName1 = playerName1;
        this.playerName2 = playerName2;

        System.out.format("Starting game ID = [%s]%n", this.gameID);
    }

    /**
     * sets up everything that's needed before a round can be played
     *
     * @param players : list of bots that have already been initialized
     */
    @Override
    public void setupGame(ArrayList<IOPlayer> players)
            throws IncorrectPlayerCountException, IOException, JSONException
    {
        System.out.println("Setting up game...");

        // Determine array size is two players
        if (players.size() != 2) {
            throw new IncorrectPlayerCountException("Should be two players");
        }

        if (new File(this.settingsFile).isFile()) {
            // file exists: read setting form file
            this.settings = new Settings(getJSONFromFile(this.settingsFile));
        } else {
            this.settings = new Settings();
        }

        this.player1 = new Player(playerName1, players.get(0), this.settings);
        this.player2 = new Player(playerName2, players.get(1), this.settings);

        // init the base (no wastelands, no armies) map from the file
        Map baseMap = MapJSON.createMap(new JSONObject(getRAWFileContents(this.mapFile)));

        this.maxRounds = settings.getMaxRounds(baseMap.getRegions().size());

        System.out.println("Customizing the map (wastelands, pickable regions)...");
        this.processor = new Processor(baseMap, this.settings, this.gameplayRnd, this.mapGenerationRnd, player1, player2);

        System.out.println("Starting game...");
        this.processor.getPicksAndInitGame();
    }

    /**
     * play one round of the game
     *
     * @param roundNumber : round number
     */
    @Override
    public void playRound(int roundNumber) {
        System.out.println("------ Playing round #" + roundNumber + " ------");

        player1.getBot().addToDump(String.format("Round %d\n", roundNumber));
        player2.getBot().addToDump(String.format("Round %d\n", roundNumber));

        this.processor.playRound(roundNumber);
    }

    /**
     * @return : True when the game is over
     */
    @Override
    public boolean isGameWon() {
        if (this.processor.getWinner() != null || this.processor.getRoundNr() > this.maxRounds) {
            return true;
        }
        return false;
    }

    /**
     * Reads the contents of a given file into a string
     *
     * @return : string representation of the map
     * @throws IOException
     */
    private String getRAWFileContents(String fileName) throws IOException {
        File file = new File(fileName);
        StringBuilder fileContents = new StringBuilder((int) file.length());
        Scanner scanner = new Scanner(file);
        String lineSeparator = System.getProperty("line.separator");

        try {
            while (scanner.hasNextLine()) {
                fileContents.append(scanner.nextLine() + lineSeparator);
            }
            return fileContents.toString();
        } finally {
            scanner.close();
        }
    }

    private JSONObject getJSONFromFile(String fileName) throws IOException, JSONException {
        String fileContents = this.getRAWFileContents(fileName);
        JSONObject result = new JSONObject(fileContents);
        return result;
    }

    /**
     * close the bot processes, save, exit program
     */
    @Override
    public void finish() throws Exception {
        this.player1.getBot().finish();
        this.player2.getBot().finish();
        Thread.sleep(100);

        // write everything
        try {
            this.saveGame();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Done.");

        System.exit(0);
    }

    /**
     * Does everything that is needed to store the output of a game
     */
    public void saveGame() {

        Player winner = this.processor.getWinner();
        //int score = this.processor.getRoundNr() - 1;

        if (winner != null) {
            System.out.println("winner: " + winner.getName());
        } else {
            System.out.println("winner: draw");
        }

        System.out.println("Saving the game...");
        // do stuff here if you want to save results
    }

    /**
     * main
     *
     * @param args : game id, the map file and the settings file, along with the commands that
     *               start the bot processes
     * @throws Exception
     */
    public static void main(String args[]) throws Exception {
        String gameID = args[0];
        int randomMapSeed = Integer.parseInt(args[1]);
        int randomGameSeed = Integer.parseInt(args[2]);
        String mapFile = args[3];
        String settingsFile = args[4];
        String bot1Cmd = args[5];
        String bot2Cmd = args[6];

        String bot1Name = "player1";
        String bot2Name = "player2";

        // Construct engine
        Engine engine = new Engine();

        // Set logic
        engine.setLogic(new Warlight2(gameID, randomMapSeed, randomGameSeed, mapFile, settingsFile, bot1Name, bot2Name));

        // Add players
        engine.addPlayer(bot1Cmd, bot1Name);
        engine.addPlayer(bot2Cmd, bot2Name);

        engine.start();
    }
}
