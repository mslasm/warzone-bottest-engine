package com.theaigames.game.warlight2;

import java.lang.UnsupportedOperationException;
import java.util.Random;
import com.theaigames.game.warlight2.map.Settings;

public class Battle
{
    private int attackersDestroyed;
    private int defendersDestroyed;
    private Settings settings;
    private Random gameplayRnd;

    public Battle(int attackingArmies, int defendingArmies, Random gameplayRnd, Settings settings) {
        this.settings = settings;
        this.gameplayRnd = gameplayRnd;
        doBattle(attackingArmies, defendingArmies);
    }

    public int getDestroyedAttackers() {
        return this.attackersDestroyed;
    }

    public int getDestroyedDefenders() {
        return this.defendersDestroyed;
    }

    private int getDefendersKilledFullLuck(int attackingArmies) {
        int defendersDestroyed = 0;
        for (int t = 1; t <= attackingArmies; t++) // calculate how much defending armies are destroyed with 100% luck
        {
            double rand = gameplayRnd.nextDouble();
            if (rand < settings.getOffensiveKillRatio()) // 60% chance to destroy one defending army
                defendersDestroyed++;
        }
        return defendersDestroyed;
    }

    private int getAttackersKilledFullLuck(int defendingArmies) {
        int attackerDestroyed = 0;
        for (int t = 1; t <= defendingArmies; t++) // calculate how much attacking armies are destroyed with 100% luck
        {
            double rand = gameplayRnd.nextDouble();
            if (rand < settings.getDefensiveKillRatio()) // 70% chance to destroy one attacking army
                attackersDestroyed++;
        }
        return attackerDestroyed;
    }

    private void doBattle(int attackingArmies, int defendingArmies) {
        double defendersDestroyed = attackingArmies * settings.getOffensiveKillRatio();
        double attackersDestroyed = defendingArmies * settings.getDefensiveKillRatio();

        double luck = settings.getLuckModifier();
        if (luck > 0) {
            // apply luck modifier: luck-weighted average between no-luck and full luck
            defendersDestroyed = defendersDestroyed * (1 - luck) + this.getDefendersKilledFullLuck(attackingArmies) * luck;
            attackersDestroyed = attackersDestroyed * (1 - luck) + this.getAttackersKilledFullLuck(defendingArmies) * luck;
        }

        // luck or no luck, apply rounding to the final amount of killed troops
        this.defendersDestroyed = Math.min(defendingArmies, this.roundArmies(defendersDestroyed));
        this.attackersDestroyed = this.roundArmies(attackersDestroyed);

        System.out.format("Battle: %d attacked %d, %d atackers dies, %d defenders died\n",
                attackingArmies, defendingArmies, this.attackersDestroyed, this.defendersDestroyed);
    }

    private int roundArmies(double armies) {
        switch(settings.getRoundingMode()) {
            case STRAIGHT_ROUND:
                return (int) Math.round(armies);

            case WEIGHTED_RANDOM:
                int rounded = (int) Math.floor(armies);
                double remainder = armies - rounded;
                double rand = gameplayRnd.nextDouble();
                if (rand < remainder) {
                    rounded++;
                }
                return rounded;

            default:
                throw new UnsupportedOperationException("Rounding mode " + settings.getRoundingMode() + " is not supported");
        }
    }
}
