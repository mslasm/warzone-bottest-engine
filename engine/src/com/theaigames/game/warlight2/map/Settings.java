package com.theaigames.game.warlight2.map;

public class Settings {
    
    private final int DEFAULT_BASE_ARMIES_PER_TURN = 5;
    private final int DEFAULT_NEUTRAL_ARMIES = 2;
    private final int DEFAULT_WASTELAND_SIZE = 10;
    private final int DEFAULT_NUMBER_OF_WASTELANDS = 1;
    
    public Settings() {
    }
    
    public int getBaseArmiesPerTurn() {
        return this.DEFAULT_BASE_ARMIES_PER_TURN;
    }
    
    public int getNeutralArmies() {
        return this.DEFAULT_NEUTRAL_ARMIES;
    }
    
    public int getWastelandSize() {
        return this.DEFAULT_WASTELAND_SIZE;
    }
    
    public int getNumberOfWastelands() {
        return this.DEFAULT_NUMBER_OF_WASTELANDS;
    }
}
