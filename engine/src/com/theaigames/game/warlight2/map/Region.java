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

package com.theaigames.game.warlight2.map;

import java.util.Set;

/**
 * Region class
 *
 * @author Jim van Eeden <jim@starapple.nl>
 */

public class Region implements Comparable<Region>
{
    public final static String OWNER_NEUTRAL = "neutral";
    public final static String OWNER_FOG = "fog";
    public final static int ARMIES_FOGGED = -1;

    private final int id;
    private final String name;
    private final Set<Integer> neighbors;

    private int armies;
    private String ownerName;

    // should be constructed either via clone() or via MapJSON factory methods
    protected Region(int id, String name, Set<Integer> neighbours, int armies, String ownerName) {
        this.id = id;
        this.name = name;
        this.neighbors = neighbours;  // note: a new copy is never created, the same set is re-used in all clones
        this.armies = armies;
        this.ownerName = ownerName;
    }

    @Override
    public Region clone() {
        Region newRegion = new Region(this.getId(), this.getName(), this.neighbors, this.armies, this.ownerName);
        return newRegion;
    }

    /**
     * @param region : a Region object
     * @return : True if this Region is a neighbor of given Region, false otherwise
     */
    public boolean isNeighbor(Region region) {
        return isNeighbor(region.getId());
    }

    public boolean isNeighbor(int regionID) {
        if (neighbors.contains(regionID))
            return true;
        return false;
    }

    /**
     * @param : playerName A string with a player's name
     * @return : True if this region is owned by given playerName, false otherwise
     */
    public boolean ownedByPlayer(String playerName) {
        if (this.ownerName.equals(playerName))
            return true;
        return false;
    }

    /**
     * @return : If the region is owned by the neutral player
     *           note: fogged regions will not be reported as neutral.
     */
    public boolean isNeutral() {
        return this.ownerName.equals(OWNER_NEUTRAL);
    }

    /**
     * @param armies : Sets the number of armies that are on this Region
     */
    public void setArmies(int armies) {
        this.armies = armies;
    }

    /**
     * @param playerName : Sets the Name of the player that this Region belongs to
     */
    public void setPlayerName(String playerName) {
        this.ownerName = playerName;
    }

    /**
     * @return : The id of this Region
     */
    public int getId() {
        return id;
    }

    /**
     * @return : The name of this Region
     */
    public String getName() {
        return name;
    }

    /**
     * @return : The number of armies on this region; ARMIES_FOGGED means "unknown"
     */
    public int getArmies() {
        return armies;
    }

    /**
     * @return : A string with the name of the player that owns this region
     */
    public String getOwnerName() {
        return ownerName;
    }

    /**
     * @param ownerVisible : fog may have different levels: none visible or owner visible
     */
    public void markAsFogged(boolean ownerVisible) {
        if (!ownerVisible) {
            this.ownerName = OWNER_FOG;
        }
        this.armies = ARMIES_FOGGED;
    }

    /**
     * @return : If this region is fogged.
     */
    public boolean isFogged() {
        return OWNER_FOG.equals(this.getOwnerName()) || ARMIES_FOGGED == this.getArmies();
    }

    /**
     * @return : If owner is fogged. A region may be fogged but owner still known (e.g. "Light Fog")
     */
    public boolean isOwnerFogged() {
        return OWNER_FOG.equals(this.getOwnerName());
    }

    /**
     * @return : A list of this Region's neighboring Regions
     */
    public Set<Integer> getNeighbors() {
        return neighbors;
    }

    /**
     * Used for sorting by id
     */
    @Override
    public int compareTo(Region r) {
        if (this.id > r.id)
            return 1;
        if (this.id == r.id)
            return 0;
        return -1;
    }

    @Override
    public int hashCode() {
        return this.id;
    }
}
