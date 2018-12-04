package com.theaigames.game.warlight2.map;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**********************************************************
Sample map JSON (same as WarZone official server JSONs):

   {
      "name" : "Sample map",
      "territories" : [ {
        "name" : "Territory A",
        "id" : "1",
        "connectedTo" : [ 2, 3 ]
      }, {
        "name" : "Territory B",
        "id" : "2",
        "connectedTo" : [ 1 ]
      }, {
        "name" : "Territory C",
        "id" : "2",
        "connectedTo" : [ 1 ]
      } ],
      "bonuses" : [ {
        "id" : "1",
        "name" : "First",
        "value" : "2",
        "territoryIDs" : [ 1 ]
      }, {
        "id" : "2",
        "name" : "Second",
        "value" : "2",
        "territoryIDs" : [ 3 ]
      }, {
        "id" : "3",
        "name" : "Superbonus",
        "value" : "1",
        "territoryIDs" : [ 1, 2, 3 ]
      } ]
    }
***********************************************************/
public final class MapJSON
{
    // creates a map from JSON, assigning each region 0 armies and neutral owner
    public static Map createMap(JSONObject mapJSON) {
        String name = mapJSON.getString("name");

        JSONArray territories = mapJSON.getJSONArray("territories");
        HashMap<Integer, Region> regions = new HashMap<>(MapJSON.getOptimalInitialHashCapacity(territories.length()));
        for (int i = 0; i < territories.length(); i++) {
            JSONObject regionJSON = territories.getJSONObject(i);
            Region region = MapJSON.createRegion(regionJSON);
            regions.put(region.getId(), region);
        }

        JSONArray bonuses = mapJSON.getJSONArray("bonuses");
        HashMap<Integer, SuperRegion> superRegions = new HashMap<>(MapJSON.getOptimalInitialHashCapacity(bonuses.length()));
        for (int i = 0; i < bonuses.length(); i++) {
            JSONObject superRegionJSON = bonuses.getJSONObject(i);
            SuperRegion superRegion = MapJSON.createSuperRegion(superRegionJSON);
            superRegions.put(superRegion.getId(), superRegion);
        }

        return new Map(name, regions, superRegions);
    }

    public static JSONObject getMapJSON(Map map) {
        JSONObject mapJSON = new JSONObject();
        mapJSON.put("name", map.getName());

        JSONArray territories = new JSONArray();
        map.getRegions().forEach(region -> territories.put(MapJSON.getRegionJSON(region)));
        mapJSON.put("territories", territories);

        JSONArray bonuses = new JSONArray();
        map.getSuperRegions().forEach(superRegion -> bonuses.put(MapJSON.getSuperRegionJSON(superRegion)));
        mapJSON.put("bonuses", bonuses);

        return mapJSON;
    }

    public static JSONArray getStandingsJSON(Map map) {
        JSONArray standing = new JSONArray();
        map.getRegions().forEach(region -> standing.put(MapJSON.getStandingsJSON(region)));
        return standing;
    }

    //-------------------------------------------------------------------

    // Region JSON (compatible with WarZone map JSONs):
    //   {
    //     "id"          : "1",
    //     "name"        : "Territory A",
    //     "connectedTo" : [ 2, 3 ]
    //   }
    private static Region createRegion(JSONObject regionJSON) {
        int id = regionJSON.getInt("id");
        String name = regionJSON.getString("name");

        JSONArray connections = regionJSON.getJSONArray("connectedTo");
        Set<Integer> neighbours = new HashSet<>(MapJSON.getOptimalInitialHashCapacity(connections.length()));
        for (int i = 0; i < connections.length(); i++) {
            int neighbour = connections.getInt(i);
            if (!neighbours.add(neighbour)) {
                throw new IllegalArgumentException("A region " + neighbour + " is listed twice as a neighbour for " + id);
            }
        }
        return new Region(id, name, neighbours, 0, Region.OWNER_NEUTRAL);
    }

    private static JSONObject getRegionJSON(Region region) {
        JSONObject result = new JSONObject();
        result.put("id", region.getId());
        result.put("name", region.getName());
        result.put("connectedTo", new JSONArray(region.getNeighbors()));
        return result;
    }

    // GameStanding JSON (compatible with WarZone "standing" JSON):
    //   {
    //     "terrID"   : 1,
    //     "ownedBy"  : "Neutral", // or playerName,
    //     "armies"   : 1,         // -1 when not known
    //     "fogLevel" : "Visible"  // or "Fog"  TODO: check what WarZone uses
    //   }
    private static JSONObject getStandingsJSON(Region region) {
        JSONObject result = new JSONObject();
        result.put("terrID", region.getId());
        result.put("armies", region.getArmies());
        result.put("ownedBy", region.getOwnerName());
        result.put("fogLevel", region.isFogged() ? "Fog" : "Visible");
        return result;
    }

    //-------------------------------------------------------------------

    // SuperRegion JSON (compatible with WarZone map JSONs):
    //   {
    //     "id"           : "1",
    //     "name"         : "Bonus 1",
    //     "value"        : "1",
    //     "territoryIDs" : [ 1, 2, 3 ]
    //   }
    private static SuperRegion createSuperRegion(JSONObject bonusJSON) {
        int id = bonusJSON.getInt("id");
        String name = bonusJSON.getString("name");
        int armiesReward = bonusJSON.getInt("value");

        JSONArray territories = bonusJSON.getJSONArray("territoryIDs");
        Set<Integer> subRegions = new HashSet<>(MapJSON.getOptimalInitialHashCapacity(territories.length()));
        for (int i = 0; i < territories.length(); i++) {
            int subRegion = territories.getInt(i);
            if (!subRegions.add(subRegion)) {
                throw new IllegalArgumentException("A region " + subRegion + " is listed twice as a sub-region for bonus " + id);
            }
        }
        return new SuperRegion(id, name, armiesReward, subRegions);
    }

    private static JSONObject getSuperRegionJSON(SuperRegion superRegion) {
        JSONObject result = new JSONObject();
        result.put("id", superRegion.getId());
        result.put("name", superRegion.getName());
        result.put("value", superRegion.getArmiesReward());
        result.put("territoryIDs", new JSONArray(superRegion.getSubRegions()));
        return result;
    }

    private static int getOptimalInitialHashCapacity(int numElements) {
        return Math.max((int) (numElements/.75f) + 1, 16);
    }
}
