package com.theaigames.game.warlight2.map;

import java.util.Map;
import com.google.common.collect.ImmutableMap;
import org.json.JSONObject;

public class Settings
{
    // note: default settings are similar to "1v1 SmallEarth - one wasteland"
    // note: also lists all currently supported fields
    private static final String DEFAULT_SETTINGS_JSON_STRING = "{" +
        "'OffensiveKillRate'               : 60,              " +
        "'DefensiveKillRate'               : 70,              " +
        "'LuckModifier'                    : 0.18,            " +
        "'RoundingMode'                    : 'StraightRound', " +
        "'TerritoryLimit'                  : 3,               " +
        "'DistributionMode'                : -1,              " +
        "'InitialPlayerArmiesPerTerritory' : 5,               " +
        "'InitialNonDistributionArmies'    : 2,               " +
        "'InitialNeutralsInDistribution'   : 4,               " +
        "'Wastelands' : {                                     " +
        "   'NumberOfWastelands'           : 7,               " +
        "   'WastelandSize'                : 10               " +
        "},                                                   " +
        "'MinimumArmyBonus'                : 5,               " +
        "'BonusArmyPer'                    : 0,               " +
        "'MoveOrder'                       : 'Cycle',         " +
        "'Fog'                             : 'Foggy',         " +
        "'FirstPlayer'                     : '1',             " +   // custom: "random", "1" or "2" - which player gets first pick (and moves second on turn1)
        "'RoundsUntilDraw'                 : 60               " +   // custom: 0 == use own heuristic based on map size
        "}";

    private static final JSONObject DEFAULT_SETTINGS_JSON = new JSONObject(DEFAULT_SETTINGS_JSON_STRING.replaceAll("'", "\""));

    // time control is different for bots and humans, so not using WarZone settings
    private final long DEFAULT_TIMEBANK_MAX = 10000l;
    private final long DEFAULT_INITIAL_TIMEBANK = DEFAULT_TIMEBANK_MAX;
    private final long DEFAULT_EXTRA_TIME_PER_MOVE = 500l;  // 5 seconds

    // max number of moves per turn - just a run-away check for now
    private final int MAX_MOVES_BY_PLAYER_PER_TURN = 100;

    //===================================================================================
    // enums used in the code, and their mappings to JSON values:

    public enum DistributionMode { RANDOM_WARLORD, RANDOM_CITIES, FULL };
    private static final Map<Integer, DistributionMode> JSON_DISTRIBUTION_MODES = ImmutableMap.of(
        -1, DistributionMode.RANDOM_WARLORD,
        -2, DistributionMode.RANDOM_CITIES,
        -3, DistributionMode.FULL);

    public enum MoveOrder { CYCLE, RANDOM };
    private static final Map<String, MoveOrder> JSON_MOVE_ORDERS = ImmutableMap.of(
        "Cycle",       MoveOrder.CYCLE,
        "Random",      MoveOrder.RANDOM,
        "NoLuckCycle", MoveOrder.CYCLE);  // note: for testing bot purposes cycle and no-luck cycle are the same

    public enum RoundingMode { STRAIGHT_ROUND, WEIGHTED_RANDOM };
    private static final Map<String, RoundingMode> JSON_ROUNDING_MODES = ImmutableMap.of(
        "StraightRound",  RoundingMode.STRAIGHT_ROUND,
        "WeightedRandom", RoundingMode.WEIGHTED_RANDOM);

    public enum FogLevel { NO_FOG, LIGHT_FOG, NORMAL_FOG, HEAVY_FOG, EXTREME_FOG };
    private static final Map<String, FogLevel> JSON_FOG_LEVELS = ImmutableMap.of(
        "NoFog",      FogLevel.NO_FOG,         // all visible
        "LightFog",   FogLevel.LIGHT_FOG,      // all owners visible, armies only for neighbouring regions
        "Foggy",      FogLevel.NORMAL_FOG,     // both owners and armis only for neighbouring regions
        "VeryFoggy",  FogLevel.HEAVY_FOG,      // armies never visible, owners only for neighbouring regions
        "ExtremeFog", FogLevel.EXTREME_FOG);   // neither armies nor owners are visible

    public enum FirstPlayer { RANDOM, PLAYER_1, PLAYER_2 };
    private static final Map<String, FirstPlayer> JSON_FIRST_PLAYER = ImmutableMap.of(
            "Random", FirstPlayer.RANDOM,
            "1",      FirstPlayer.PLAYER_1,
            "2",      FirstPlayer.PLAYER_2);

    //===================================================================================
    // basic sanity checks for parameter values:

    private static final Map<String, Double> PARAMETER_MIN_VALUES = ImmutableMap.of(
        "TerritoryLimit",    1.0,  // at least 1 territory
        "OffensiveKillRate", 1.0   // at least 1%, or there is no game
        );
    private static final Map<String, Double> PARAMETER_MAX_VALUES = ImmutableMap.of(
        "LuckModifier",        1.0,  // at most 100% luck
        "OffensiveKillRate", 100.0,  // at most 100% kill rate
        "DefensiveKillRate", 100.0   // at most 100% kill rate
        );

    // TODO: one-army-stand-guard, multi-attack, bonus-army-per-territory
    // TODO: commanders, local deployments, no-split
    // TODO: cards (reinf, airlift, sanction, spy, bomb, order priority/delay)
    // TODO: team games - any settings?
    // TODO: attack-only, transfer-only moves?

    JSONObject settingsJSON;

    public Settings() {
        //System.out.println("using settings string: -----------");
        //System.out.println(DEFAULT_SETTINGS_JSON_STRING.replaceAll("'", "\""));
        //System.out.println("----------------------------------");
        this.settingsJSON = DEFAULT_SETTINGS_JSON;
    }

    // TODO: improve performance, parse JSON on load and not when a value is needed
    public Settings(JSONObject settingsJSON) {
        this.settingsJSON = new JSONObject();
        // initiallize all suported values: use provided, if present, otherwise use default
        for (String key : DEFAULT_SETTINGS_JSON.keySet()) {
            this.settingsJSON.put(key, settingsJSON.has(key) ? settingsJSON.get(key) : DEFAULT_SETTINGS_JSON.get(key));
            checkValueRange(key);
        }
    }

    /**
     * @return current settings in the JSON format (for communication to the bot)
     */
    public JSONObject getSettingsJSON() {
        return this.settingsJSON;
    }

    /**
     * Determines how much rounds a game can take before it's a draw (or a winner is
     * determined despite both players still being in the game), depending on map size
     *
     * @param mapSize : the number of regions in the map being played
     * @return : the maximum number of rounds for this game
     */
    public int getMaxRounds(int mapSize) {
        int roundsInSettings = settingsJSON.getInt("RoundsUntilDraw");
        return (roundsInSettings > 0)
                ? roundsInSettings                    // use provided value
                : (int) Math.max(50, mapSize * 2.5);  // compute: minimum of 50, otherwise 2.5 times the number of regions
    }

    // TODO: depends on map size?
    public int getMaxMovesPerPlayerPerTurn() {
        return this.MAX_MOVES_BY_PLAYER_PER_TURN;
    }

    public FirstPlayer getFirstPlayerPolicty() {
        FirstPlayer first = JSON_FIRST_PLAYER.get(settingsJSON.getString("FirstPlayer"));
        return (first == null) ? FirstPlayer.PLAYER_1 : first;
    }

    public int getNumberOfStartingTerritories() {
        return settingsJSON.getInt("TerritoryLimit");
    }

    public FogLevel getFogLevel() {
        FogLevel fog = JSON_FOG_LEVELS.get(settingsJSON.getString("Fog"));
        return (fog == null) ? FogLevel.NORMAL_FOG : fog;
    }

    public int getBaseArmiesPerTurn() {
        return settingsJSON.getInt("MinimumArmyBonus");
    }

    public int getBonuseArmyPerTerritories() {
        return settingsJSON.getInt("BonusArmyPer");  // TODO: not supported yet
    }

    public int getInitilPlayerArmies() {
        return settingsJSON.getInt("InitialPlayerArmiesPerTerritory");
    }

    public int getNeutralArmies() {
        return settingsJSON.getInt("InitialNonDistributionArmies");
    }

    public int getNeutralArmiesInDistribution() {
        return settingsJSON.getInt("InitialNeutralsInDistribution");
    }

    public int getWastelandSize() {
        JSONObject wastelandInfo = settingsJSON.getJSONObject("Wastelands");
        return wastelandInfo.getInt("WastelandSize");
    }

    public int getNumberOfWastelands() {
        JSONObject wastelandInfo = settingsJSON.getJSONObject("Wastelands");
        return wastelandInfo.getInt("NumberOfWastelands");
    }

    // random warlords/cities: this will generally mean that Superbonuses will have one territory for each minor bonus,
    // plus an additional territory; included in distribution for warlords, and excluded in distribution for cities.
    public DistributionMode getDistributionMode() {
        DistributionMode mode = JSON_DISTRIBUTION_MODES.get(settingsJSON.getInt("DistributionMode"));
        return (mode == null) ? DistributionMode.FULL : mode;
    }

    public MoveOrder getMoveOrder() {
        MoveOrder order = JSON_MOVE_ORDERS.get(settingsJSON.getString("MoveOrder"));
        return (order == null) ? MoveOrder.CYCLE : order;
    }

    public double getDefensiveKillRatio() {
        return settingsJSON.getDouble("DefensiveKillRate")/100.0;
    }

    public double getOffensiveKillRatio() {
        return settingsJSON.getDouble("OffensiveKillRate")/100.0;
    }

    public double getLuckModifier() {
        return settingsJSON.getDouble("LuckModifier");
    }

    public RoundingMode getRoundingMode() {
        RoundingMode mode = JSON_ROUNDING_MODES.get(settingsJSON.getString("RoundingMode"));
        return (mode == null) ? RoundingMode.STRAIGHT_ROUND : mode;
    }

    public long getInitialTimebank() {
        return this.DEFAULT_INITIAL_TIMEBANK;
    }

    public long getMaxTimebank() {
        return this.DEFAULT_TIMEBANK_MAX;
    }

    public long getExtraTimePerMove() {
        return this.DEFAULT_EXTRA_TIME_PER_MOVE;
    }

    private void checkValueRange(String key) {
        if (PARAMETER_MIN_VALUES.containsKey(key)) {
            double value = this.settingsJSON.getDouble(key);
            double minSuported = PARAMETER_MIN_VALUES.get(key);
            if (value < minSuported)
                throw new IllegalArgumentException("Setting " + key + " has value " + value + " below min supported " + minSuported);
        }
        if (PARAMETER_MAX_VALUES.containsKey(key)) {
            double value = this.settingsJSON.getDouble(key);
            double maxSuported = PARAMETER_MAX_VALUES.get(key);
            if (value > maxSuported)
                throw new IllegalArgumentException("Setting " + key + " has value " + value + " above max supported " + maxSuported);
        }
    }

    /**
     * Sample settings JSON, as generated by the WarZone server:
     {
        "OffensiveKillRate" : 60,
        "DefensiveKillRate" : 70,
        "LuckModifier" : 0.18,             // no luck : 0.0
        "RoundingMode" : "StraightRound",  // or "WeightedRandom"

        "TerritoryLimit" : 3,
        "DistributionMode" : -1,           // -1 == random warlords, -2 = random cities, -3 = full
        "InitialPlayerArmiesPerTerritory" : 5,
        "InitialNonDistributionArmies" : 2,
        "InitialNeutralsInDistribution" : 4,
        "Wastelands" : {
          "NumberOfWastelands" : 7,
          "WastelandSize" : 10
        },
        "MinimumArmyBonus" : 5,
        "BonusArmyPer" : 0,

        "MoveOrder" : "Cycle"    // "Random", "NoLuckCycle"
        "Fog" : "Foggy",         // "NoFog", "LightFog,", "ModerateFog", "Foggy", "VeryFoggy", "ExtremeFog"
        "MultiAttack" : false,
        "Commanders" : false,
        "OneArmyStandsGuard" : true,
        "DirectBoot" : 4320.0,   // in minutes
        "LocalDeployments" : false,
        "NoSplit" : false,

        "ReinforcementCard" : {
          "NumPieces" : 4,
          "InitialPieces" : 0,
          "MinimumPiecesPerTurn" : 1,
          "Weight" : 1.0,
          "Mode" : 0,
          "FixedArmies" : 5
        },
        "SpyCard" : "none",
        "OrderPriorityCard" : {
          "NumPieces" : 6,
          "InitialPieces" : 0,
          "MinimumPiecesPerTurn" : 1,
          "Weight" : 1.0
        },
        "OrderDelayCard" : {
          "NumPieces" : 5,
          "InitialPieces" : 0,
          "MinimumPiecesPerTurn" : 1,
          "Weight" : 1.0
        },
        "AirliftCard" : "none",
        "SanctionsCard" : "none",
        "ReconnaissanceCard" : "none",
        "SurveillanceCard" : "none",
        "BlockadeCard" : "none",
        "BombCard" : "none",
        "MaxCardsHold" : 3,
        "NumberOfCardsToReceiveEachTurn" : 3
     }
     */
}
