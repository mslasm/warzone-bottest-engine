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

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import com.theaigames.game.warlight2.Player;

/**
 * Map class
 *
 * @author Jim van Eeden <jim@starapple.nl>
 */
public class Map
{
    private String name;
    private HashMap<Integer, Region> regions;
    private HashMap<Integer, SuperRegion> bonuses;

    // used for clone() only
    protected Map(String name, HashMap<Integer, Region> regions, HashMap<Integer, SuperRegion> bonuses) {
        this.name = name;
        this.regions = regions;
        this.bonuses = bonuses;

        // check that all references to region IDs are correct (region.neighbours and superRegion.subRegions)
        checkConsistency();
    }

    public String getName() {
        return this.name;
    }

    /**
     * @return : a new Map object exactly the same as this one
     */
    @Override
    public Map clone() {
        // copy regions
        HashMap<Integer, Region> regionsCopy = new HashMap<>();
        regions.forEach((regionID, region) -> regionsCopy.put(regionID, region.clone()));

        // copy superRegions
        HashMap<Integer, SuperRegion> bonusesCopy = new HashMap<>();
        bonuses.forEach((bonusID, bonus) -> bonusesCopy.put(bonusID, bonus.clone()));

        return new Map(this.getName(), regionsCopy, bonusesCopy);
    }

    /**
     * @param player
     * @return : a copy of the map as visible by a given player
     */
    public Map getVisibleMapCopyForPlayer(Player player, Settings settings) {
        Map visibleMap = this.clone();

        // first determine which regions are visible and in which way (owner, armies, both, none)
        if (settings.getFogLevel() != Settings.FogLevel.NO_FOG) {
            final Set<Integer> visibleArmiesRegions;
            final Set<Integer> visibleOwnersRegions; // note: should always a superset of visibleArmiesRegions
            switch(settings.getFogLevel()) {
            case EXTREME_FOG:
                visibleArmiesRegions = ownedRegionsByPlayer(player);    // only see armies for own territories
                visibleOwnersRegions = visibleArmiesRegions;            // only see owner for own territories
                break;
            case HEAVY_FOG:
                visibleArmiesRegions = ownedRegionsByPlayer(player);    // only see armies for own territories
                visibleOwnersRegions = visibleRegionsForPlayer(player); // see owners for all neighbours
                break;
            case LIGHT_FOG:
                visibleArmiesRegions = visibleRegionsForPlayer(player); // see armies for own and neighbours
                visibleOwnersRegions = getRegionIDs();                  // see owner for all regions
                break;
            case NORMAL_FOG:
            default:  // default to NORMAL_FOG for all unsupported fogs
                visibleArmiesRegions = visibleRegionsForPlayer(player); // see armies for own and neighbours
                visibleOwnersRegions = visibleArmiesRegions;            // see owner for own and neighbours
            }

            // apply fog, as needed
            visibleMap.regions.forEach((regionID, region) -> {
                if (!visibleArmiesRegions.contains(regionID)) {
                    region.markAsFogged(visibleOwnersRegions.contains(regionID));
                }
            });
        }

        return visibleMap;
    }

    /**
     * @param : a superRegion to check for cmplete ownership
     * @return : A string with the name of the player that fully owns the given SuperRegion, or null
     */
    public String getSuperRegionOwner(SuperRegion superRegion) {
        String playerName = null;
        for (Integer regionID : superRegion.getSubRegions()) {
            Region region = this.getRegion(regionID);
            if (playerName == null) {
                // initilaize to the owner of the first region
                playerName = region.getOwnerName();
            } else if (!playerName.equals(region.getOwnerName()))
                // if more than one owner => superRegion s not owned by any one single player
                return null;
        }
        return playerName;
    }

    /**
     * @return : a collection of all Regions in this map
     */
    public Collection<Region> getRegions() {
        return regions.values();
    }

    /**
     * @return : the set of IDs of all Regions in this map
     */
    public Set<Integer> getRegionIDs() {
        return regions.keySet();
    }

    /**
     * @return : a collection of all SuperRegions in this map
     */
    public Collection<SuperRegion> getSuperRegions() {
        return bonuses.values();
    }

    /**
     * @return : the set of IDs of all SuperRegions in this map
     */
    public Set<Integer> getSuperRegionIDs() {
        return bonuses.keySet();
    }

    /**
     * @param id : a Region id number
     * @return : the matching Region object
     */
    public Region getRegion(int id) {
        Region region = regions.get(id);
        if (region == null)
            System.err.println("Could not find region with id " + id);
        return region;
    }

    /**
     * @param id : a SuperRegion id number
     * @return : the matching SuperRegion object
     */
    public SuperRegion getSuperRegion(int id) {
        SuperRegion bonus = bonuses.get(id);
        if (bonus == null)
            System.err.println("Could not find superRegion with id " + id);
        return bonus;
    }

    /**
     * @param player
     * @return : a list of all region IDs owned by given player
     */
    public Set<Integer> ownedRegionsByPlayer(Player player) {
        Set<Integer> ownedRegionIDs = new HashSet<>();

        for (Region region : this.getRegions())
            if (region.getOwnerName().equals(player.getName()))
                ownedRegionIDs.add(region.getId());

        return ownedRegionIDs;
    }

    /**
     * Needed because fog of war
     *
     * @param player
     * @return : a list of all visible regions for given player
     */
    public Set<Integer> visibleRegionsForPlayer(Player player) {
        Set<Integer> ownedRegionIDs = ownedRegionsByPlayer(player);
        Set<Integer> visibleRegionIDs = new HashSet<>(ownedRegionIDs);

        for (Integer regionID : ownedRegionIDs) {
            for (Integer neighborID : this.getRegion(regionID).getNeighbors()) {
                visibleRegionIDs.add(neighborID);  // since this is a set nothing happens if neighbour already in the set
            }
        }
        return visibleRegionIDs;
    }

    private void checkConsistency() {
        regions.forEach((regionID, region) -> {
            if (region.getNeighbors().size() == 0) {
                throw new IllegalArgumentException("Region " + regionID + " is inaccessible (has no neighbouring regions)");
            }
            region.getNeighbors().forEach(neighbourID -> {
                if (!regions.containsKey(neighbourID)) {
                    throw new IllegalArgumentException("Region " + regionID + " neighbours a non-existing region " + neighbourID);
                }
            });
        });

        bonuses.forEach((bonusID, bonus) -> {
            if (bonus.getSubRegions().size() == 0) {
                throw new IllegalArgumentException("Bonus " + bonusID + " has no territories");
            }
            bonus.getSubRegions().forEach(subregionID -> {
                if (!regions.containsKey(subregionID)) {
                    throw new IllegalArgumentException("Bonus " + bonusID + " contains a non-existing region " + subregionID);
                }
            });
        });
    }
}
