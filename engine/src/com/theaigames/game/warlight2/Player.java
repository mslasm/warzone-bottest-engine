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

import java.io.IOException;

import com.theaigames.engine.io.IOPlayer;
import com.theaigames.game.warlight2.map.Settings;

/**
 * Player class
 *
 * This class stores all the information about the player and handles
 * communication between bot and engine.
 *
 * @author Jim van Eeden <jim@starapple.nl>
 */

public class Player
{
    private String name;
    private IOPlayer bot;
    private int armiesLeft;    //variable armies that can be added, changes with superRegions fully owned and moves already placed.
    private long timeBank;
    private Settings settings;

    public Player(String name, IOPlayer bot, Settings settings) {
        this.name = name;
        this.bot = bot;
        this.timeBank = settings.getInitialTimebank();
        this.armiesLeft = 0;
        this.settings = settings;
    }

    /**
     * @param n Sets the number of armies this player has left to place
     */
    public void setArmiesLeft(int n) {
        armiesLeft = n;
    }

    /**
     * @return The String name of this Player
     */
    public String getName() {
        return name;
    }

    /**
     * @return The time left in this player's time bank
     */
    public long getTimeBank() {
        return timeBank;
    }

    /**
     * @return The Bot object of this Player
     */
    public IOPlayer getBot() {
        return bot;
    }

    /**
     * @return The number of armies this Player has left to place on the map
     */
    public int getArmiesLeft() {
        return armiesLeft;
    }

    /**
     * sets the time bank directly
     */
    public void setTimeBank(long time) {
        this.timeBank = time;
    }

    /**
     * updates the time bank for this player, cannot get bigger than maximal time bank or smaller than zero
     *
     * @param time : time consumed from the time bank
     */
    public void updateTimeBank(long time) {
        this.timeBank = Math.max(this.timeBank - time, 0);
        this.timeBank = Math.min(this.timeBank + settings.getExtraTimePerMove(), settings.getMaxTimebank());
    }

    /**
     * Sends given string to bot
     *
     * @param info
     */
    public void sendInfo(String info) {
        try {
            this.bot.process(info, "input");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Waits for some input from the bot and returns the received string.
     *
     * @return the bot's output
     */
    public String getResponse() {
        long startTime = System.currentTimeMillis();

        String response = this.bot.getResponse(this.getTimeBank());

        long timeElapsed = System.currentTimeMillis() - startTime;
        updateTimeBank(timeElapsed);

        return response;
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }
}
