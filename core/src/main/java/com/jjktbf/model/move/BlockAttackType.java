package com.jjktbf.model.move;

/** The three attack categories used by block and parry coverage. */
public enum BlockAttackType {
    PHYSICAL("Physical"),
    PHYSICAL_CURSED_ENERGY("Physical + Cursed Energy"),
    CURSED_ENERGY("Cursed Energy");

    private final String displayName;

    BlockAttackType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Technique tags count as cursed energy for defensive coverage. */
    public static BlockAttackType from(HitComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("An incoming hit component is required.");
        }
        boolean physical = component.hasTag(MoveTag.PHYSICAL);
        boolean cursedEnergy = component.hasTag(MoveTag.CURSED_ENERGY)
            || component.hasTag(MoveTag.INNATE_TECHNIQUE)
            || component.hasTag(MoveTag.NON_INNATE_TECHNIQUE);
        if (physical && cursedEnergy) return PHYSICAL_CURSED_ENERGY;
        if (physical) return PHYSICAL;
        return CURSED_ENERGY;
    }
}
