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

package com.theaigames.game.warlight2.move;

/**
 * Move class
 *
 * Superclass to all move classes
 *
 * @author Jim van Eeden <jim@starapple.nl>
 */

public abstract class Move {

	private String playerName;  // name of the player that did this move
	private String illegalMove; // the value of the error message if move is illegal (including ""), or null if move is legal

	/**
	 * @param playerName : Sets the name of the Player that this Move belongs to
	 */
	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}

	/**
	 * @param problemDescription : error message of this move, can be null/blank
	 */
	public void markAsIllegal(String problemDescription) {
		this.illegalMove = (problemDescription == null) ? "" : problemDescription;
	}

	/**
	 * @return : The Player's name that this Move belongs to
	 */
	public String getPlayerName() {
		return playerName;
	}

	public boolean isLegalMove() {
	    return illegalMove == null;
	}

	/**
	 * @return : The error message of this Move, or null if move is legal
	 */
	public String getIllegalDescription() {
		return illegalMove;
	}

	public abstract String getString();
}
