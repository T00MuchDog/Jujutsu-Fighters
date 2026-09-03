package com.jjktbf;

import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.domain.DomainClashCalculator;
import com.jjktbf.model.domain.DomainData;
import com.jjktbf.model.domain.DomainDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainClashCalculatorTest {

    @Test
    void barrierIntegrityUsesJujutsuSkillAndCeOutputAtAThreeToOneRatio() {
        DomainDefinition domain = domain(100);

        assertEquals(360, DomainClashCalculator.barrierIntegrity(
            domain, owner("JUJUTSU", 80, 20)));
        assertEquals(240, DomainClashCalculator.barrierIntegrity(
            domain, owner("OUTPUT", 20, 80)));
    }

    @Test
    void antiDomainUsesTheSameScalingAgainstItsHigherAuthoredBase() {
        DomainDefinition antiDomain = domain(DomainData.DEFAULT_ANTI_DOMAIN_INTEGRITY);

        assertEquals(5000, DomainClashCalculator.barrierIntegrity(
            antiDomain, owner("COUNTER", 100, 100)));
    }

    private static DomainDefinition domain(int integrity) {
        DomainData data = new DomainData();
        data.id = "DOMAIN";
        data.name = "Domain";
        data.requiredTechniqueName = "Technique";
        data.internalBarrierIntegrity = integrity;
        return data.toDomain();
    }

    private static BattleCombatant owner(String id, int jujutsuSkill, int output) {
        CharacterStats stats = new CharacterStats.Builder()
            .jujutsuSkill(jujutsuSkill)
            .cursedEnergyOutput(output)
            .build();
        Character character = new SorcererCharacter(id, id, stats, null, List.of());
        return new BattleCombatant(character);
    }
}
