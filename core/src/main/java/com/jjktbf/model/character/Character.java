package com.jjktbf.model.character;

import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MovePool;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.weapon.WeaponType;

import java.util.*;

/**
 * Abstract base for all combat-capable characters.
 *
 * Holds:
 *  - Identity and classification
 *  - Immutable base stats (CharacterStats)
 *  - Derived combat stats (CombatStats), computed on construction
 *  - The character's move pool
 *  - Innate technique name (null if none, e.g. "Shrine", "Blood Manipulation")
 *
 * Does NOT hold mutable battle state — that lives in BattleCombatant.
 *
 * Technique gating:
 *   Moves with a requiredTechniqueId are only accessible to characters whose
 *   innateTechniqueName matches (case-insensitive). Characters with no innate
 *   technique (innateTechniqueName == null) cannot use any technique-restricted move.
 *   Technique moves carry no character class: any character type that possesses
 *   the technique may learn them.
 */
public abstract class Character extends Entity {

    private final CharacterStats baseStats;
    private final CombatStats    combatStats;
    private final CharacterType  type;

    /**
     * The human-readable name of this character's innate cursed technique.
     * e.g. "Shrine", "Blood Manipulation", "Infinite Void".
     * Null if the character has no innate technique.
     * Matched case-insensitively against Move.requiredTechniqueId.
     * Will be replaced by a Technique ID reference once the Technique class is implemented.
     */
    private final String innateTechniqueName;

    /** The ordered pool of every move this character has learned. */
    private final List<Move> learnedMoves;

    /**
     * The learned moves equipped for this battle. Kept under the historical
     * {@code knownMoves} name because battle systems consume this list.
     */
    private final List<Move> knownMoves;
    private final List<Ability> abilities;
    private final java.util.Set<String> accessibleTechniques;
    /** Domain definition IDs unlocked through technique-tree Domain nodes. */
    private java.util.Set<String> accessibleDomainIds = Set.of();
    private final java.util.Set<String> moveSetSlotExemptIds;

    /**
     * Everything this character has equipped: base weapons plus cursed tools.
     * A move carrying a weapon-type tag
     * ({@link MoveTag#WEAPON_TAGS}) can only be learned by a character whose
     * {@link Equipment#weaponTypes()} covers that type.
     */
    private final Equipment equipment;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    protected Character(
        String         id,
        String         name,
        CharacterType  type,
        CharacterStats baseStats,
        String         innateTechniqueName,
        List<Move>     knownMoves
    ) {
        this(id, name, type, baseStats, innateTechniqueName, knownMoves, List.of());
    }

    protected Character(
        String         id,
        String         name,
        CharacterType  type,
        CharacterStats baseStats,
        String         innateTechniqueName,
        List<Move>     knownMoves,
        List<Ability>  abilities
    ) {
        this(id, name, type, baseStats, innateTechniqueName, knownMoves, abilities,
             accessibleTechniquesOf(innateTechniqueName, abilities), Equipment.NONE);
    }

    /**
     * Full construction with an explicit set of accessible technique names and
     * equipment.
     *
     * <p>A character "has access to" technique T if it is their
     * {@code innateTechniqueName} OR an applied ability's
     * {@code UNLOCK_TECHNIQUE} effect grants it. The caller resolves this set
     * (typically {@link CharacterData#toCharacter}); the default constructors
     * compute it via {@link #accessibleTechniquesOf} from the ability list.
     *
     * <p>Move validation checks membership against this set instead of a single
     * {@code equalsIgnoreCase} against the innate name — which is what makes
     * UNLOCK_TECHNIQUE (and, by extension, Copy) functional.
     *
     * <p>Equipped cursed tools contribute their weapon types to the equipped-
     * weapon gate. Only moves explicitly assigned to a tool are added
     * automatically, and those moves remain subject to normal requirements.
     *
     * @param equipment the character's weapons and cursed tools (may be
     *                  {@link Equipment#NONE})
     */
    protected Character(
        String         id,
        String         name,
        CharacterType  type,
        CharacterStats baseStats,
        String         innateTechniqueName,
        List<Move>     knownMoves,
        List<Ability>  abilities,
        java.util.Set<String> accessibleTechniques,
        Equipment      equipment
    ) {
        this(id, name, type, baseStats, innateTechniqueName,
            knownMoves, knownMoves, abilities, accessibleTechniques, equipment);
    }

    /**
     * Full construction with separate learned and battle move pools. A null
     * {@code moveSet} selects the first learned moves that fit each slot budget.
     */
    protected Character(
        String         id,
        String         name,
        CharacterType  type,
        CharacterStats baseStats,
        String         innateTechniqueName,
        List<Move>     learnedMoves,
        List<Move>     moveSet,
        List<Ability>  abilities,
        java.util.Set<String> accessibleTechniques,
        Equipment      equipment
    ) {
        super(id, name);
        Objects.requireNonNull(type,      "CharacterType cannot be null");
        Objects.requireNonNull(baseStats, "CharacterStats cannot be null");

        Equipment resolvedEquipment = equipment == null ? Equipment.NONE : equipment;
        List<Ability> effectiveAbilities = abilities;

        this.type               = type;
        this.baseStats          = baseStats;
        AbilityApplicator.AbilityFlags passiveFlags =
            AbilityApplicator.apply(baseStats, effectiveAbilities).flags;
        this.combatStats        = new CombatStats(baseStats, passiveFlags.jujutsuArtSlots);
        this.innateTechniqueName = innateTechniqueName;
        this.equipment          = resolvedEquipment;
        this.accessibleTechniques = accessibleTechniques == null
            ? Set.of() : Set.copyOf(accessibleTechniques);
        GrantedMoves granted = withEquipmentMoves(
            availableMoveIdsOf(effectiveAbilities), resolvedEquipment);
        Set<String> lockedMoveTags = lockedMoveTagsOf(effectiveAbilities);
        List<Move> validatedLearnedMoves = validateAndBuildMoveList(
            learnedMoves,
            type, baseStats, this.accessibleTechniques,
            granted, lockedMoveTags, resolvedEquipment);
        java.util.Set<String> learnedMoveIds = learnedMoves == null ? java.util.Set.of()
            : learnedMoves.stream().filter(java.util.Objects::nonNull).map(Move::getId)
                .collect(java.util.stream.Collectors.toSet());
        for (Move candidate : resolvedEquipment.grantedMoves()) {
            if (candidate == null || learnedMoveIds.contains(candidate.getId())) continue;
            List<Move> withCandidate = new ArrayList<>(validatedLearnedMoves);
            withCandidate.add(candidate);
            try {
                validatedLearnedMoves = validateAndBuildMoveList(
                    withCandidate, type, baseStats, this.accessibleTechniques,
                    granted, lockedMoveTags, resolvedEquipment);
            } catch (IllegalArgumentException ignored) {
                // Automatic tool grants only become known after ordinary move
                // requirements pass; an unmet requirement does not invalidate
                // the character or the equipped tool.
            }
        }
        validatedLearnedMoves = filterMovesByAssignedCodedFeatures(
            validatedLearnedMoves, effectiveAbilities);
        validateCodedMoveReferences(validatedLearnedMoves);
        this.learnedMoves = Collections.unmodifiableList(validatedLearnedMoves);
        Set<String> slotExemptIds = new HashSet<>(granted.bypass());
        slotExemptIds.addAll(granted.automatic());
        this.moveSetSlotExemptIds = Collections.unmodifiableSet(slotExemptIds);

        List<Move> requestedMoveSet = moveSet == null ? null
            : filterMovesByAssignedCodedFeatures(moveSet, effectiveAbilities);
        List<Move> selectedMoves = requestedMoveSet == null
            ? SlotBudgetEnforcer.defaultMoveSet(
                this.learnedMoves, combatStats, moveSetSlotExemptIds)
            : resolveMoveSet(requestedMoveSet, this.learnedMoves, granted.automatic());
        selectedMoves = validateAndBuildMoveList(
            selectedMoves, type, baseStats, this.accessibleTechniques,
            granted, lockedMoveTags, resolvedEquipment);
        selectedMoves = filterMovesByAssignedCodedFeatures(selectedMoves, effectiveAbilities);
        validateCodedMoveReferences(selectedMoves);
        SlotBudgetEnforcer.validateMoveSet(
            selectedMoves, combatStats, moveSetSlotExemptIds);
        this.knownMoves = Collections.unmodifiableList(selectedMoves);
        this.abilities          = effectiveAbilities != null
            ? Collections.unmodifiableList(new ArrayList<>(effectiveAbilities)) : List.of();
    }

    private static List<Move> resolveMoveSet(
        List<Move> requestedMoves,
        List<Move> learnedMoves,
        Set<String> automaticMoveIds
    ) {
        Map<String, Move> learnedById = new LinkedHashMap<>();
        for (Move move : learnedMoves) learnedById.put(move.getId(), move);
        Set<String> selectedIds = new HashSet<>();
        for (Move requested : requestedMoves) {
            if (requested == null || !selectedIds.add(requested.getId())) {
                throw new IllegalArgumentException("Move set cannot contain null or duplicate moves");
            }
            Move learned = learnedById.get(requested.getId());
            if (learned == null) {
                throw new IllegalArgumentException(
                    "Move set contains unlearned move " + requested.getId());
            }
        }
        selectedIds.addAll(automaticMoveIds);
        List<Move> selected = learnedMoves.stream()
            .filter(move -> selectedIds.contains(move.getId()))
            .toList();
        return selected;
    }

    /**
     * Fold tool-bestowed moves into the automatic grant set. Automatic grants
     * satisfy {@code mustBeGranted} and do not consume assignment slots, but do
     * not bypass class, weapon, lock, stat, or technique requirements.
     */
    private static GrantedMoves withEquipmentMoves(GrantedMoves granted, Equipment equipment) {
        if (granted == null) granted = GrantedMoves.EMPTY;
        if (equipment.grantedMoveIds().isEmpty()) return granted;
        java.util.Set<String> automatic = new java.util.HashSet<>(granted.automatic());
        automatic.addAll(equipment.grantedMoveIds());
        return new GrantedMoves(granted.bypass(), granted.plain(), automatic);
    }

    private static void validateCodedMoveReferences(List<Move> moves) {
        Set<String> knownIds = new HashSet<>();
        for (Move move : moves) knownIds.add(move.getId());
        for (Move move : moves) {
            for (var effect : codedMoveEffects(move)) {
                String target = effect.getCodedTarget();
                if (target == null || !target.matches("\\d{6}")) continue;
                if (!knownIds.contains(target)) {
                    throw new IllegalArgumentException(
                        "Move '" + move.getName() + "' references unknown move " + target);
                }
            }
        }
    }

    private static List<Move> filterMovesByAssignedCodedFeatures(
        List<Move> moves,
        List<Ability> abilities
    ) {
        boolean hasRatioReinforcement = abilities != null && abilities.stream()
            .filter(java.util.Objects::nonNull)
            .flatMap(ability -> ability.getEffects().stream())
            .anyMatch(effect -> effect != null && effect.isCoded()
                && com.jjktbf.model.character.coded.RatioAbility.KEY.equalsIgnoreCase(
                    effect.codedAbilityKey)
                && com.jjktbf.model.character.coded.RatioAbility.REINFORCEMENT_RATIO
                    .equalsIgnoreCase(effect.codedFeature));
        if (hasRatioReinforcement) return moves;
        return moves.stream().filter(move -> move == null || !codedMoveEffects(move).stream()
            .anyMatch(effect -> effect != null && effect.isCoded()
                && com.jjktbf.model.character.coded.RatioAbility.KEY.equalsIgnoreCase(
                    effect.getCodedAbilityKey())
                && com.jjktbf.model.character.coded.RatioAbility.RATIO_EFFECT.equalsIgnoreCase(
                    effect.getCodedAction())
                && com.jjktbf.model.character.coded.RatioAbility.CREATE_STACKS.equalsIgnoreCase(
                    effect.getCodedTarget())))
            .toList();
    }

    private static List<com.jjktbf.model.move.StatusEffect> moveEffects(Move move) {
        List<com.jjktbf.model.move.StatusEffect> effects = new ArrayList<>(move.getSelfEffects());
        effects.addAll(move.getOnHitEffects());
        effects.addAll(move.getOnBlockEffects());
        effects.addAll(move.getOnParryEffects());
        effects.addAll(move.getOnDodgeEffects());
        return effects;
    }

    private static List<com.jjktbf.model.move.StatusEffect> codedMoveEffects(Move move) {
        if (move == null) return List.of();
        if (!move.usesUnifiedEffects()) {
            return moveEffects(move).stream()
                .filter(com.jjktbf.model.move.StatusEffect::isCoded)
                .toList();
        }
        return move.getEffects().stream()
            .filter(effect -> AbilityEffectType.CODED_MOVE_ACTION.name()
                .equalsIgnoreCase(effect.type))
            .map(com.jjktbf.model.move.MoveEffectData::toCodedStatusEffect)
            .toList();
    }

    /**
     * Resolve the set of technique names a character can use moves from: their
     * innate technique plus any technique granted by an {@code UNLOCK_TECHNIQUE}
     * ability effect. Case-insensitive (names are lower-cased on insertion).
     */
    static java.util.Set<String> accessibleTechniquesOf(
            String innateTechniqueName, List<Ability> abilities) {
        java.util.Set<String> set = new java.util.HashSet<>();
        if (innateTechniqueName != null && !innateTechniqueName.isBlank()) {
            set.add(innateTechniqueName.toLowerCase());
        }
        if (abilities != null) {
            for (Ability a : abilities) {
                if (!a.isPassive()) continue;
                var effects = a.getEffects();
                if (effects == null) continue;
                for (var e : effects) {
                    if (com.jjktbf.model.character.AbilityEffectType.UNLOCK_TECHNIQUE.name().equalsIgnoreCase(e.type)
                        && e.stringValue != null && !e.stringValue.isBlank()) {
                        set.add(e.stringValue.toLowerCase());
                    }
                }
            }
        }
        return set;
    }

    /**
     * Moves made available by passive ability effects, split by how they were
     * granted. {@link GrantedMoves#bypass} moves come from {@code GRANT_MOVE}
     * and bypass all learning requirements; {@link GrantedMoves#plain} moves
     * come from {@code UNLOCK_MOVE} and still require the character to meet
     * prerequisites when assigned.
     */
    private static GrantedMoves availableMoveIdsOf(List<Ability> abilities) {
        java.util.Set<String> bypass = new java.util.HashSet<>();
        java.util.Set<String> plain  = new java.util.HashSet<>();
        if (abilities == null) return new GrantedMoves(bypass, plain, Set.of());
        for (Ability ability : abilities) {
            if (!ability.isPassive()) continue;
            for (AbilityEffectData effect : ability.getEffects()) {
                if (effect.moveId == null || effect.moveId.isBlank()) continue;
                if (AbilityEffectType.GRANT_MOVE.name().equalsIgnoreCase(effect.type)) {
                    bypass.add(effect.moveId);
                } else if (AbilityEffectType.UNLOCK_MOVE.name().equalsIgnoreCase(effect.type)) {
                    plain.add(effect.moveId);
                }
            }
        }
        return new GrantedMoves(bypass, plain, Set.of());
    }

    /** Available moves grouped by ability bypass, ordinary unlock, and automatic tool grant. */
    record GrantedMoves(Set<String> bypass, Set<String> plain, Set<String> automatic) {
        static final GrantedMoves EMPTY = new GrantedMoves(Set.of(), Set.of(), Set.of());
        GrantedMoves {
            bypass = bypass == null ? Set.of() : bypass;
            plain  = plain  == null ? Set.of() : plain;
            automatic = automatic == null ? Set.of() : automatic;
        }
        /** True when the move is available through either grant path. */
        boolean contains(String moveId) {
            return moveId != null && (bypass.contains(moveId) || plain.contains(moveId)
                || automatic.contains(moveId));
        }
    }

    private static java.util.Set<String> lockedMoveTagsOf(List<Ability> abilities) {
        java.util.Set<String> tags = new java.util.HashSet<>();
        if (abilities == null) return tags;
        for (Ability ability : abilities) {
            if (!ability.isPassive()) continue;
            for (AbilityEffectData effect : ability.getEffects()) {
                if (AbilityEffectType.LOCK_MOVE_TAG.name().equalsIgnoreCase(effect.type)
                    && effect.moveTag != null && !effect.moveTag.isBlank()) {
                    tags.add(effect.moveTag);
                }
            }
        }
        return tags;
    }

    // -------------------------------------------------------------------------
    // Move validation
    // -------------------------------------------------------------------------

    private static List<Move> validateAndBuildMoveList(
        List<Move>     moves,
        CharacterType  characterType,
        CharacterStats cs,
        java.util.Set<String> accessibleTechniques,
        GrantedMoves   granted,
        java.util.Set<String> lockedMoveTags,
        Equipment      equipment
    ) {
        if (moves == null) return List.of();
        if (granted == null) granted = GrantedMoves.EMPTY;
        Equipment resolvedEquipment = equipment == null ? Equipment.NONE : equipment;

        List<Move> validated = new ArrayList<>();

        for (Move move : moves) {
            // A move is "granted" when any passive ability makes it available.
            // Only GRANT_MOVE grants bypass requirements; UNLOCK_MOVE still
            // enforces them.
            boolean moveAvailable = granted.contains(move.getId());
            boolean bypass        = granted.bypass().contains(move.getId());

            // Character-class eligibility is absolute for classed moves: an
            // ability may waive ordinary learning requirements, but cannot
            // change what kind of move the character is capable of learning.
            // Technique moves carry no class — technique possession alone
            // decides who may learn them (checked below).
            if (!move.isTechniqueMove()
                    && move.getMoveTypes().stream().noneMatch(characterType::canLearn)) {
                throw new IllegalArgumentException(
                    "Character type " + characterType + " cannot learn "
                        + move.getMoveTypes() + " move '" + move.getName() + "'");
            }

            if (move.mustBeGranted() && !moveAvailable) {
                throw new IllegalArgumentException(
                    "Move '" + move.getName() + "' must be granted by an ability");
            }

            // --- 0. Weapon requirement ---
            // A move carrying weapon-type tags needs at least one matching weapon
            // equipped (base weapon or cursed tool). A GRANT_MOVE-granted move
            // bypasses this like the other restrictions, so an ability can
            // still bestow a weapon technique.
            Set<MoveTag> requiredWeapons = move.weaponTags();
            if (!bypass && !requiredWeapons.isEmpty()
                    && !resolvedEquipment.supportsWeaponTags(requiredWeapons)) {
                throw new IllegalArgumentException(
                    "Move '" + move.getName() + "' requires one of its weapon types, "
                        + "which this character does not have");
            }

            if (!bypass && lockedMoveTags != null
                && lockedMoveTags.stream().anyMatch(move::hasTag)) {
                throw new IllegalArgumentException(
                    "Ability restrictions prevent learning move '" + move.getName() + "'");
            }

            // --- 1. Prerequisite stats ---
            // A matching cursed tool supplies the supernatural side of its
            // weapon moves, but physical and combat requirements still apply.
            if (!bypass) {
                for (Map.Entry<String, Integer> prereq : move.getPrerequisites().entrySet()) {
                    StatKey stat = StatKey.fromString(prereq.getKey());
                    if (resolvedEquipment.waivesJujutsuPrerequisite(
                        requiredWeapons, stat)) continue;
                    int actual = stat.get(cs);
                    if (actual < prereq.getValue()) {
                        throw new IllegalArgumentException(
                            "Character does not meet prerequisite for move '" + move.getName()
                            + "': needs " + prereq.getKey() + " >= " + prereq.getValue()
                            + " but has " + actual
                        );
                    }
                }
            }

            // --- 2. Technique restriction ---
            // A move is usable if its required technique is the character's
            // innate technique OR was granted by an UNLOCK_TECHNIQUE ability
            // effect (e.g. Six Eyes → Limitless, or a Copy ability). Case-insensitive.
            if (!bypass && move.getRequiredTechniqueId() != null) {
                if (accessibleTechniques == null
                    || !accessibleTechniques.contains(move.getRequiredTechniqueId().toLowerCase())) {
                    throw new IllegalArgumentException(
                        "Character does not possess required technique '"
                        + move.getRequiredTechniqueId()
                        + "' for move '" + move.getName() + "'"
                    );
                }
            }

            validated.add(move);
        }

        return validated;
    }

    /** Package-accessible and public delegate — routes through CharacterStats.getByName(). */
    public static int getStatByName(CharacterStats cs, String statName) {
        return cs.getByName(statName);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public CharacterStats  getBaseStats()           { return baseStats; }
    public CombatStats     getCombatStats()          { return combatStats; }
    public CharacterType   getType()                 { return type; }
    public String          getInnateTechniqueName()  { return innateTechniqueName; }
    /** Every move learned by the character, in authored order. */
    public List<Move>      getLearnedMoves()         { return learnedMoves; }
    /** Deterministic legal loadout used by quick setup and recommendation UI. */
    public List<Move>      getRecommendedMoveSet() {
        return SlotBudgetEnforcer.defaultMoveSet(
            learnedMoves, combatStats, moveSetSlotExemptIds);
    }
    /** Moves equipped for the current battle. */
    public List<Move>      getMoveSet()              { return knownMoves; }
    /** Historical battle-facing alias for {@link #getMoveSet()}. */
    public List<Move>      getKnownMoves()           { return knownMoves; }
    public List<Ability>   getAbilities()            { return abilities; }
    public Set<String>     getAccessibleDomainIds()   { return accessibleDomainIds; }
    public boolean         canUseTechnique(String techniqueName) {
        return techniqueName != null
            && accessibleTechniques.contains(techniqueName.trim().toLowerCase());
    }
    public boolean         canEstablishDomain(String domainId) {
        return domainId != null && accessibleDomainIds.contains(domainId);
    }
    public boolean         hasInnateTechnique()      { return innateTechniqueName != null; }
    /** The character's weapons and cursed tools (never null). */
    public Equipment       getEquipment()            { return equipment; }

    /** Whether this move consumes one of the current character's move-set slots. */
    public boolean consumesMoveSetSlot(Move move) {
        return SlotBudgetEnforcer.consumesSlot(move, moveSetSlotExemptIds);
    }

    /**
     * Returns an immutable copy configured with the requested move-set IDs.
     * Selected moves retain learned order; automatic equipment moves remain equipped.
     */
    public Character withMoveSet(List<String> moveIds) {
        Objects.requireNonNull(moveIds, "Move set cannot be null");
        Map<String, Move> learnedById = new LinkedHashMap<>();
        for (Move move : learnedMoves) learnedById.put(move.getId(), move);
        List<Move> requested = new ArrayList<>(moveIds.size());
        Set<String> seen = new HashSet<>();
        for (String moveId : moveIds) {
            if (moveId == null || moveId.isBlank() || !seen.add(moveId)) {
                throw new IllegalArgumentException(
                    "Move set IDs cannot be blank or duplicated");
            }
            Move move = learnedById.get(moveId);
            if (move == null) {
                throw new IllegalArgumentException("Move " + moveId + " has not been learned");
            }
            requested.add(move);
        }
        Character copy = switch (type) {
            case SHIKIGAMI -> new ShikigamiCharacter(
                getId(), getName(), baseStats, innateTechniqueName,
                learnedMoves, requested, abilities, accessibleTechniques,
                equipment, getBaseCeDrainPerTick());
            case CURSED_SPIRIT -> new CursedSpiritCharacter(
                getId(), getName(), baseStats, innateTechniqueName,
                learnedMoves, requested, abilities, accessibleTechniques, equipment);
            case CURSED_CORPSE -> new CursedCorpseCharacter(
                getId(), getName(), baseStats, innateTechniqueName,
                learnedMoves, requested, abilities, accessibleTechniques, equipment);
            case SORCERER -> new SorcererCharacter(
                getId(), getName(), baseStats, innateTechniqueName,
                learnedMoves, requested, abilities, accessibleTechniques, equipment);
        };
        copy.accessibleDomainIds = accessibleDomainIds;
        return copy;
    }

    /** Returns an immutable character copy with the supplied unlocked Domain IDs. */
    public Character withAccessibleDomains(Collection<String> domainIds) {
        Character copy = withMoveSet(knownMoves.stream().map(Move::getId).toList());
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (domainIds != null) {
            domainIds.stream().filter(Objects::nonNull).map(String::trim)
                .filter(id -> !id.isEmpty()).forEach(normalized::add);
        }
        copy.accessibleDomainIds = Collections.unmodifiableSet(normalized);
        return copy;
    }

    /** Base CE charged to a summoner per active tick; non-shikigami default to zero. */
    public double          getBaseCeDrainPerTick()     { return 0.0; }

}
