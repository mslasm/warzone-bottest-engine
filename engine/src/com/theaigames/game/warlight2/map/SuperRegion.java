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
 * SuperRegion class
 *
 * @author Jim van Eeden <jim@starapple.nl>
 */
public class SuperRegion implements Comparable<SuperRegion>
{
    private final int id;
    private final String name;
    private final int armiesReward;
    private final Set<Integer> subRegions;

    // should be constructed either via clone() or via MapJSON factory methods
    protected SuperRegion(int id, String name, int armiesReward, Set<Integer> subRegions) {
        this.id = id;
        this.name = name;
        this.armiesReward = armiesReward;
        this.subRegions = subRegions;  // note: a new copy is never created, the same set is re-used in all clones
    }

    @Override
    public SuperRegion clone() {
        SuperRegion newBonus = new SuperRegion(this.getId(), this.getName(), this.getArmiesReward(), this.getSubRegions());
        return newBonus;
    }

    /**
     * @return : The id of this SuperRegion
     */
    public int getId() {
        return id;
    }

    /**
     * @return : The name of this SuperRegion
     */
    public String getName() {
        return name;
    }

    /**
     * @return : The number of armies a Player is rewarded when he fully owns this SuperRegion
     */
    public int getArmiesReward() {
        return armiesReward;
    }

    /**
     * @return : A list with the Regions that are part of this SuperRegion
     */
    public Set<Integer> getSubRegions() {
        return subRegions;
    }

    /**
     * Used for sorting superRegions by id
     */
    @Override
    public int compareTo(SuperRegion sr) {
        if (this.id > sr.id)
            return 1;
        if (this.id == sr.id)
            return 0;
        return -1;
    }

    @Override
    public int hashCode() {
        return this.id;
    }
}
