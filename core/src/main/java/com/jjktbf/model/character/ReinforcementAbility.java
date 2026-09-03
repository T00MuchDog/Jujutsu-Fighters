package com.jjktbf.model.character;

/** Stable identity of the generic permission to reinforce authored moves. */
public final class ReinforcementAbility {
    public static final String ID = "000059";
    public static final String NAME = "Cursed-Energy Reinforcement";

    private ReinforcementAbility() { }

    public static boolean is(Ability ability) {
        return ability != null && (ID.equals(ability.getId())
            || NAME.equalsIgnoreCase(ability.getName()));
    }

    public static boolean is(AbilityData ability) {
        return ability != null && (ID.equals(ability.id)
            || NAME.equalsIgnoreCase(ability.name));
    }
}
