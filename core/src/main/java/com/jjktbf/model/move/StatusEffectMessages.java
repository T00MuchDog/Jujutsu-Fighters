package com.jjktbf.model.move;

/**
 * Human-readable battle-log lines for action-blocking status effects.
 *
 * <p>Stat changes and ordinary expiry are already represented by the HUD, so
 * only states that visibly prevent actions receive narration.
 */
public final class StatusEffectMessages {

    private StatusEffectMessages() {}

    /** Ordinary expiry is implied by the HUD. */
    public static String expiryMessage(String characterName, StatusEffectType type) {
        String character = characterName == null || characterName.isBlank()
            ? "Someone" : characterName;
        if (type == StatusEffectType.FROZEN) return character + " broke out of Frozen!";
        return "";
    }

    /** Describe a newly applied action-blocking status. */
    public static String applicationMessage(
        String sourceName,
        String targetName,
        StatusEffectType type,
        boolean sourceIsTarget
    ) {
        String target = targetName == null || targetName.isBlank() ? "Someone" : targetName;
        if (type == StatusEffectType.SLEEP) return target + " fell asleep!";
        if (type == StatusEffectType.STAGGER) return target + " was staggered!";
        if (type == StatusEffectType.RESTRAINED) return target + " was restrained!";
        if (type == StatusEffectType.WET) return target + " became Wet!";
        if (type == StatusEffectType.FROZEN) return target + " was Frozen!";
        if (type == StatusEffectType.BURNED) return target + " was Burned!";
        if (type == StatusEffectType.BLEED) return target + " suffered a bleeding wound!";
        if (type == StatusEffectType.FATIGUED) return target + " became Fatigued!";
        if (type == StatusEffectType.CURSED_SPEECH_WARD) {
            return target + " covers their ears in cursed energy!";
        }
        if (type == StatusEffectType.CURSED_ENERGY_PARASITE) {
            return target + " was implanted with a cursed-energy parasite!";
        }
        return "";
    }
}
