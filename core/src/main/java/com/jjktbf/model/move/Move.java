package com.jjktbf.model.move;

import com.jjktbf.model.character.AbilityConditionData;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.progression.TechniqueMasteryResolver;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable descriptor for a single move.
 *
 * A Move defines everything about what the move IS — it does not execute itself.
 * Execution (damage calculation, effect application, interrupt resolution) is handled
 * by the CombatResolver, keeping the model free from combat logic.
 *
 * Special moves:
 *  - BASIC_PUNCH and BASIC_BLOCK are always available to every character regardless
 *    of move slots (isFreeMove = true).
 *
 * CE cost:
 *  - baseCeCost is modified at use-time by the character's CE Efficiency stat.
 *  - minCeCost / maxCeCost are hard floors/ceilings that efficiency cannot breach.
 *  - hasCeCost distinguishes a CE move costing 0 from a move with no CE cost.
 *
 * Prerequisites:
 *  - A character cannot learn this move unless all prerequisite stat thresholds are met.
 *
 * Technique restriction:
 *  - If requiredTechniqueId is non-null, only characters who possess that specific
 *    innate technique can learn or use this move. Technique moves carry no
 *    character class: any class may learn them while they possess the technique.
 */
public class Move {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Unique identifier used to reference this move from data files and character sheets. */
    private final String id;

    /** Display name. */
    private final String name;

    /**
     * Character classes of move used by learning eligibility. Empty for
     * technique moves: they are class-agnostic and learnable by any character
     * that possesses the technique.
     */
    private final Set<MoveType> moveTypes;

    /** Flavour description shown to the player. */
    private final String description;

    /** Determines the Power formula and Black Flash eligibility. */
    private final MoveCategory category;

    /** Original data tags, retained for UI, filtering, and lossless DTO round-trips. */
    private final Set<MoveTag> tags;

    /**
     * Which move pool (Combat Arts / Jujutsu Arts) this move draws its slot from.
     * Orthogonal to {@link #category} — derived from the raw PHYSICAL tag.
     * @see MovePool
     */
    private final MovePool pool;

    /** Ordered damage instances emitted at offsets from this move's unleash tick. */
    private final List<HitComponent> hitComponents;

    /** Combined component power retained for legacy callers and compact UI summaries. */
    private final int totalBasePower;

    /**
     * Base accuracy as a fraction [0.0, 1.0].
     * 1.0 = 100% (still subject to Accuracy vs Evasion roll).
     * Moves that cannot miss use a sentinel value of Double.MAX_VALUE
     * or use a Never Miss effect (the legacy boolean remains readable).
     */
    private final double baseAccuracy;

    /** Legacy compatibility flag. Canonical Never Miss tiers live in {@link #effects}. */
    private final boolean neverMiss;

    /**
     * Move potency tier (1–5). For attack moves, gates which defensive moves can
     * stop them; for defensive moves, gates which attacks they can stop. A defence
     * only applies when {@code defence.potency >= attack.potency}. Always 1 for
     * utility moves (unused). Backs the {@code potency} data field.
     */
    private final int potency;

    /**
     * If true, an action segment carrying this move cannot be cancelled by a
     * stun-current-action effect. Interrupts are unaffected. Backs the HEAVY move tag.
     */
    private final boolean heavy;

    /**
     * Size of the action segment this move occupies on the AP timeline.
     * Min: 5,  Max: ~100.
     */
    private final int apCost;

    /**
     * The AP tick within the action segment at which the move is unleashed.
     * Range: [1, apCost].
     * Unleash at tick 1 = instant/highest priority.
     * Unleash at tick == apCost = full charge.
     */
    private final int unleashPoint;

    /** Base CE cost before efficiency scaling. May be 0 when {@link #hasCeCost} is true. */
    private final int baseCeCost;

    /** Whether this move has a CE cost at all (including an intentional cost of 0). */
    private final boolean hasCeCost;

    /** Hard minimum CE cost — efficiency cannot reduce below this. */
    private final int minCeCost;

    /** Hard maximum CE cost — efficiency cannot raise above this. */
    private final int maxCeCost;
    private final boolean canBeReinforced;
    private final int reinforcementBaseCeCost;
    private final int reinforcementMinCeCost;
    private final int reinforcementMaxCeCost;
    private final ReinforcementDefenseType reinforcementDefenseType;
    private final int reinforcementDefenseValue;

    /** Defensive behavior, if any. */
    private final DefenseType defenseType;

    /** Reduction formula used when {@link #defenseType} is {@link DefenseType#BLOCK}. */
    private final BlockStyle blockStyle;

    /**
     * Duration in AP ticks of the active defense window. 0 = use move's apCost.
     * -1 = end of round. Applies to {@link DefenseType#BLOCK}, {@link DefenseType#PARRY},
     * and {@link DefenseType#DODGE}.
     */
    private final int blockDuration;

    /** Accepted MELEE/RANGED hit tags. Empty means every range, including untagged hits. */
    private final Set<MoveTag> blockRanges;

    /** Blockable elements. Every elemental tag on a hit must be present; empty means all. */
    private final Set<MoveTag> blockElementalTags;

    /** Percentage of damage reduced (0-100). 100 = full block. Used by {@link BlockStyle#PERCENTAGE}. */
    private final int blockDamageReduction;

    /** Flat damage subtracted from incoming damage. Used by {@link BlockStyle#FLAT}. */
    private final int blockFlatReduction;

    /** {@link DefenseType#DODGE}: chance (0–100%) to avoid a matching incoming attack. */
    private final int dodgeChance;

    /** {@link DefenseType#DODGE}: which attack ranges this dodge reacts to (MELEE / RANGED / BOTH). */
    private final String dodgeScope;

    /**
     * {@link DefenseType#PARRY}: AP ticks to {@link StatusEffectType#STAGGER stagger}
     * the attacker on a successful parry of a non-GUARD_BREAK attack. 0 = no stagger.
     */
    private final int parryStaggerTicks;

    /**
     * When this defence's window opens. {@link DefenseTiming#FIXED} (the
     * default) opens it at the fire tick; {@link DefenseTiming#REACTION} arms
     * at the fire tick and triggers on the next matching incoming attack.
     */
    private final DefenseTiming defenseTiming;

    /**
     * How many incoming attacks this defence may contest while its window is
     * active. 0 = unlimited (the historical behaviour: it applies to every
     * matching attack inside its window). A positive value only restricts
     * within the window — it never extends or shortens the window's duration;
     * once the cap is spent the defence simply stops applying even though its
     * window is still open.
     */
    private final int defenseUses;

    /**
     * Whose timeline this defensive move's active-defense window is conferred
     * to at fire time. {@link DefenseTargeting#SELF} (the default) preserves the
     * historical behaviour of protecting the caster; the ally modes grant a fired
     * copy of the segment to the selected/allied combatants instead. Only
     * meaningful on defensive moves.
     */
    private final DefenseTargeting defenseTargeting;

    /**
     * Number of allies protected by a {@link DefenseTargeting#MULTIPLE_ALLIES}
     * move. Ignored for the other targeting modes. Defaults to 2; must be at
     * least 2 when used.
     */
    private final int defenseTargetCount;

    /** Ordered explicit endpoint shape for moves that act on a combatant pair. */
    private final Targeting targeting;

    // On-hit status effects live per {@link HitComponent} (applied when that
    // specific component connects). There is no move-level onHitEffects field.

    /** Status effects applied to the defender when a {@link DefenseType#BLOCK} negates/reduces a hit. */
    private final List<StatusEffect> onBlockEffects;

    /** Status effects applied to the defender when a {@link DefenseType#PARRY} negates a hit. */
    private final List<StatusEffect> onParryEffects;

    /** Status effects applied to the defender when a {@link DefenseType#DODGE} avoids a hit. */
    private final List<StatusEffect> onDodgeEffects;

    /**
     * Status effects this move applies to the user on unleash (may be empty).
     * Applied by the combat engine when the move fires, for every move type
     * (damaging, defensive, and utility) — independent of whether the attack
     * later hits, misses, or is blocked.
     *
     * <p>A self-effect row may also carry a coded action (see
     * {@link StatusEffect#isCoded()}) — this is how a technique move's hardcoded
     * effect is expressed as an editable effect row instead of state on the Move.
     */
    private final List<StatusEffect> selfEffects;

    /** Shared ability-style effect primitives used by newly-authored moves. */
    private final List<MoveEffectData> effects;

    /** Distinguishes canonical shared effects from the legacy attachment lists. */
    private final boolean unifiedEffects;

    /**
     * Stat prerequisites. Key = stat name matching CharacterStats getter convention,
     * Value = minimum required value.
     * A character cannot learn this move if any prerequisite is not met.
     */
    private final java.util.Map<String, Integer> prerequisites;

    /**
     * If non-null, the character must possess this specific innate technique
     * to learn or use this move (e.g. "BLOOD_MANIPULATION", "SHRINE").
     */
    private final String requiredTechniqueId;

    /** Cursed tool that grants this move while equipped, or null. */
    private final String requiredCursedToolId;

    /** If true, this move does not consume a move slot when assigned to a character. */
    private final boolean isFreeMove;

    /** If true, this move can only enter a character's pool through an ability grant. */
    private final boolean mustBeGranted;

    /** Maximum times this move may be placed in one round. 0 means unlimited. */
    private final int moveCap;

    /**
     * Canonical shikigami character id summoned when this move reaches its unleash
     * point. Null for non-summoning moves. The summoned combatant is created via
     * the shared runtime summon path (see CombatResolver) regardless of whether
     * the request came from a move or an ability. Works on utility and attack
     * moves alike.
     */
    private final String summonCharacterId;

    /**
     * Authoritative area-of-effect shape for an {@link MoveTag#AOE} move. Null
     * when the move is not AOE; non-null whenever the AOE tag is present (a
     * back-compat default of {@link AoeType#ALL_ENEMIES} is applied on load when
     * the field is blank). This is the single source of truth for how an AOE
     * move fans out — see {@link com.jjktbf.model.combat.MoveTargeting#forMove}.
     */
    private final AoeType aoeType;

    /**
     * Number of targets hit when {@link AoeType#MULTIPLE} AOE move. Ignored for
     * the other shapes. Defaults to 2; must be at least 2 when used.
     */
    private final int aoeTargetCount;

    /**
     * When a Defensive+Attack hybrid launches its attack portion. Null on
     * non-hybrids (a hybrid is a DEFENSIVE-category move that also carries the
     * ATTACK tag — the DEFENSIVE tag wins, so the move plays on the defensive
     * timeline).
     */
    private final AttackLaunchMode attackLaunchMode;

    /** Extra condition tree gating the hybrid attack's launch. Null = always. */
    private final AbilityConditionData attackLaunchCondition;

    /** Whether the hybrid attack's launch rolls {@link #attackLaunchChance}. */
    private final boolean attackLaunchChanceEnabled;

    /** Launch chance percent (0-100) rolled when {@link #attackLaunchChanceEnabled}. */
    private final int attackLaunchChance;

    /**
     * Referenced move id launched as the hybrid attack instead of this move's
     * own hit components. Null = custom attack (own components).
     */
    private final String attackLaunchMoveId;

    /**
     * Resolved referenced attack move, injected at build time from the
     * repository. Null when unreferenced or the id could not be resolved (the
     * launch then no-ops at runtime).
     */
    private final Move attackLaunchMove;

    // -------------------------------------------------------------------------
    // Construction via Builder
    // -------------------------------------------------------------------------

    private Move(Builder b) {
        this.id                  = b.id;
        this.name                = b.name;
        // Technique moves carry no character class regardless of what was
        // authored alongside the technique requirement.
        this.moveTypes           = java.util.Collections.unmodifiableSet(
            b.requiredTechniqueId == null
                ? EnumSet.copyOf(b.moveTypes)
                : EnumSet.noneOf(MoveType.class));
        this.description         = b.description;
        this.category            = b.category;
        this.tags                = immutableTags(b.tags, b.category);
        this.pool                = b.pool != null ? b.pool : MovePool.fromCategory(b.category);
        this.hitComponents       = buildHitComponents(b);
        this.totalBasePower      = totalBasePower(hitComponents);
        this.baseAccuracy        = b.baseAccuracy;
        this.neverMiss           = b.neverMiss;
        this.heavy               = b.heavy;
        this.potency             = b.potency;
        this.apCost              = b.apCost;
        this.unleashPoint        = b.unleashPoint;
        this.baseCeCost          = b.baseCeCost;
        this.hasCeCost           = b.hasCeCost != null ? b.hasCeCost : b.baseCeCost > 0;
        this.minCeCost           = b.minCeCost;
        this.maxCeCost           = b.maxCeCost;
        this.canBeReinforced     = b.canBeReinforced;
        this.reinforcementBaseCeCost = b.reinforcementBaseCeCost;
        this.reinforcementMinCeCost = b.reinforcementMinCeCost;
        this.reinforcementMaxCeCost = b.reinforcementMaxCeCost;
        this.reinforcementDefenseType = b.reinforcementDefenseType;
        this.reinforcementDefenseValue = b.reinforcementDefenseValue;
        this.defenseType          = b.defenseType;
        this.blockStyle           = b.blockStyle != null ? b.blockStyle : BlockStyle.PERCENTAGE;
        this.blockDuration        = b.blockDuration;
        this.blockRanges          = immutableBlockTags(b.blockRanges, MoveTag.RANGE_TAGS);
        this.blockElementalTags   = immutableBlockTags(
            b.blockElementalTags, MoveTag.ELEMENTAL_TAGS);
        this.blockDamageReduction = b.blockDamageReduction;
        this.blockFlatReduction   = b.blockFlatReduction;
        this.dodgeChance          = b.dodgeChance;
        this.dodgeScope           = b.dodgeScope;
        this.parryStaggerTicks    = b.parryStaggerTicks;
        this.defenseTiming        = b.defenseTiming != null ? b.defenseTiming : DefenseTiming.FIXED;
        this.defenseUses          = b.defenseUses;
        this.defenseTargeting     = resolveDefenseTargeting(b);
        this.defenseTargetCount   = b.defenseTargetCount;
        this.targeting        = b.targeting != null
            ? b.targeting : Targeting.DEFAULT;
        this.selfEffects         = Collections.unmodifiableList(b.selfEffects);
        this.onBlockEffects      = Collections.unmodifiableList(b.onBlockEffects);
        this.onParryEffects      = Collections.unmodifiableList(b.onParryEffects);
        this.onDodgeEffects      = Collections.unmodifiableList(b.onDodgeEffects);
        java.util.ArrayList<MoveEffectData> copiedEffects = b.moveEffects.stream()
            .filter(java.util.Objects::nonNull)
            .map(MoveEffectData::copy)
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        AbilityData.ensureEffectIds(copiedEffects);
        this.effects             = Collections.unmodifiableList(copiedEffects);
        this.unifiedEffects      = b.moveEffectsExplicit;
        this.prerequisites       = Collections.unmodifiableMap(b.prerequisites);
        this.requiredTechniqueId = b.requiredTechniqueId;
        this.requiredCursedToolId = b.requiredCursedToolId;
        this.isFreeMove          = b.isFreeMove;
        this.mustBeGranted       = b.mustBeGranted;
        this.moveCap             = b.moveCap;
        this.summonCharacterId   = b.summonCharacterId;
        this.aoeType             = resolveAoeType(b);
        this.aoeTargetCount      = b.aoeTargetCount;
        this.attackLaunchMode         = b.attackLaunchMode;
        this.attackLaunchCondition    = b.attackLaunchCondition;
        this.attackLaunchChanceEnabled = b.attackLaunchChanceEnabled;
        this.attackLaunchChance       = b.attackLaunchChance;
        this.attackLaunchMoveId       = b.attackLaunchMoveId;
        this.attackLaunchMove         = b.attackLaunchMove;
    }

    private static Set<MoveTag> immutableTags(Set<MoveTag> source, MoveCategory category) {
        EnumSet<MoveTag> copy = EnumSet.noneOf(MoveTag.class);
        if (source != null) copy.addAll(source);
        else if (category != null) copy.addAll(category.getTags());
        copy.removeAll(MoveTag.HIT_ONLY_TAGS);
        return Collections.unmodifiableSet(copy);
    }

    private static Set<MoveTag> immutableBlockTags(Set<MoveTag> source, Set<MoveTag> allowed) {
        if (source == null || source.isEmpty()) return Set.of();
        EnumSet<MoveTag> copy = EnumSet.copyOf(source);
        if (!allowed.containsAll(copy)) {
            throw new IllegalArgumentException("Invalid block coverage tags: " + source);
        }
        return Collections.unmodifiableSet(copy);
    }

    /**
     * Resolve the authoritative {@link AoeType} from the builder state. A move
     * without the {@link MoveTag#AOE} tag never carries an AOE type. A move with
     * the AOE tag but no authored type falls back to {@link AoeType#ALL_ENEMIES}
     * (or {@link AoeType#ALL_OTHERS} when the legacy {@link MoveTag#FRIENDLY_FIRE}
     * tag is present), preserving the pre-AoeType behaviour for old data.
     */
    private static AoeType resolveAoeType(Builder b) {
        Set<MoveTag> effective = b.tags != null ? b.tags
            : (b.category != null ? b.category.getTags() : EnumSet.noneOf(MoveTag.class));
        boolean aoe = effective.contains(MoveTag.AOE);
        if (!aoe) return null;
        if (b.aoeType != null) return b.aoeType;
        return effective.contains(MoveTag.FRIENDLY_FIRE) ? AoeType.ALL_OTHERS : AoeType.ALL_ENEMIES;
    }

    /**
     * Resolve the authoritative {@link DefenseTargeting} from the builder state.
     * A non-defensive move never carries a targeting mode (always SELF); a
     * defensive move with no authored targeting also defaults to SELF, preserving
     * the pre-DefenseTargeting behaviour for old data.
     */
    private static DefenseTargeting resolveDefenseTargeting(Builder b) {
        if (b.category != MoveCategory.DEFENSIVE) return DefenseTargeting.SELF;
        return b.defenseTargeting != null ? b.defenseTargeting : DefenseTargeting.SELF;
    }

    private static List<HitComponent> buildHitComponents(Builder builder) {
        if (builder.hitComponentsExplicit) {
            Set<MoveTag> inherited = builder.legacyHitTags();
            if (inherited.isEmpty()) return List.copyOf(builder.hitComponents);
            return builder.hitComponents.stream()
                .map(component -> withAdditionalTags(component, inherited))
                .toList();
        }
        if (builder.category == MoveCategory.UTILITY) {
            return List.of();
        }
        if (builder.category == MoveCategory.DEFENSIVE) {
            // A hybrid may author its custom attack the legacy way (move-level
            // base power): synthesize the fallback component from the hybrid's
            // damage-nature tags. A pure defence has no attack at all.
            if (!builder.synthesizesLegacyComponent()) return List.of();
            return List.of(new HitComponent(
                builder.basePower, builder.legacyComponentTags(
                    builder.legacyHybridDamageTags()), 0, false, true,
                builder.baseAccuracy, builder.onHitEffects));
        }
        // Legacy single-component path: seed the synthesized fallback component
        // with the builder-level on-hit effects and accuracy so existing
        // basePower-based authoring keeps working.
        return List.of(new HitComponent(
            builder.basePower, builder.legacyComponentTags(builder.category.getTags()),
            0, false, true,
            builder.baseAccuracy, builder.onHitEffects));
    }

    private static HitComponent withAdditionalTags(
        HitComponent component,
        Set<MoveTag> additional
    ) {
        EnumSet<MoveTag> tags = EnumSet.copyOf(component.getTags());
        tags.addAll(additional);
        return new HitComponent(
            component.getBasePower(), tags, component.getDelayTicks(),
            component.requiresPreviousConnection(), component.isAvoidable(),
            component.getBaseAccuracy(), component.getOnHitEffects(),
            component.isReinforcementEligible(), component.getReinforcementBonusPower());
    }

    private static int totalBasePower(List<HitComponent> components) {
        long total = 0;
        for (HitComponent component : components) total += component.getBasePower();
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("combined component basePower exceeds integer range");
        }
        return (int) total;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getId()                         { return id; }
    public String getName()                       { return name; }
    /** Primary type retained for callers that only need grouping or legacy behavior. */
    public MoveType getMoveType()                 { return moveTypes.iterator().next(); }
    public Set<MoveType> getMoveTypes()            { return moveTypes; }
    public String getDescription()                { return description; }
    public MoveCategory getCategory()             { return category; }
    public Set<MoveTag> getTags()                 { return tags; }
    public MovePool getPool()                     { return pool; }
    public int getBasePower()                     { return totalBasePower; }
    public int getTotalBasePower()                { return totalBasePower; }
    public List<HitComponent> getHitComponents()  { return hitComponents; }
    public int getMaxHitDelayTicks() {
        return hitComponents.stream().mapToInt(HitComponent::getDelayTicks).max().orElse(0);
    }
    public double getBaseAccuracy()               { return baseAccuracy; }
    public boolean isNeverMiss()                  { return neverMiss || getNeverMissTier() > 0; }
    public boolean hasLegacyNeverMiss()           { return neverMiss; }
    public int getNeverMissTier()                 { return getNeverMissTier(0); }
    public int getNeverMissTier(int mastery) {
        return accuracyPriorityTier(AbilityEffectType.NEVER_MISS, mastery);
    }
    public int getNeverHitTier()                  { return getNeverHitTier(0); }
    public int getNeverHitTier(int mastery) {
        return accuracyPriorityTier(AbilityEffectType.NEVER_HIT, mastery);
    }
    /** True when at least one hit component breaks guards. */
    public boolean isGuardBreak()                 {
        return hitComponents.stream().anyMatch(HitComponent::isGuardBreak);
    }
    public boolean isHeavy()                      { return heavy; }
    public int getPotency()                       { return potency; }
    /**
     * Every weapon-type tag this move carries. A character needs at least one
     * matching weapon equipped (base weapon or cursed tool) to learn it.
     */
    public Set<MoveTag> weaponTags()               { return MoveTag.weaponTagsIn(getTags()); }
    /**
     * First weapon tag, retained for callers that only display one tag.
     */
    public MoveTag weaponTag()                    { return MoveTag.weaponTagIn(getTags()); }
    /** True when this move can be performed with one or more weapon types. */
    public boolean hasWeaponTag()                 { return !weaponTags().isEmpty(); }
    public int getApCost()                        { return apCost; }
    public int getUnleashPoint()                  { return unleashPoint; }
    public int getBaseCeCost()                    { return baseCeCost; }
    public boolean hasCeCost()                     { return hasCeCost; }
    public int getMinCeCost()                     { return minCeCost; }
    public int getMaxCeCost()                     { return maxCeCost; }
    public boolean canBeReinforced()               { return canBeReinforced; }
    public int getReinforcementBaseCeCost()        { return reinforcementBaseCeCost; }
    public int getReinforcementMinCeCost()         { return reinforcementMinCeCost; }
    public int getReinforcementMaxCeCost()         { return reinforcementMaxCeCost; }
    public ReinforcementDefenseType getReinforcementDefenseType() {
        return reinforcementDefenseType;
    }
    public int getReinforcementDefenseValue()      { return reinforcementDefenseValue; }
    public DefenseType getDefenseType()           { return defenseType; }
    public BlockStyle getBlockStyle()             { return blockStyle; }
    public int getBlockDuration()                 { return blockDuration; }
    public Set<MoveTag> getBlockRanges()          { return blockRanges; }
    public Set<MoveTag> getBlockElementalTags()   { return blockElementalTags; }
    public int getBlockDamageReduction()          { return blockDamageReduction; }
    public int getBlockFlatReduction()            { return blockFlatReduction; }
    public int getDodgeChance()                   { return dodgeChance; }
    public String getDodgeScope()                 { return dodgeScope; }
    public int getParryStaggerTicks()             { return parryStaggerTicks; }
    public DefenseTiming getDefenseTiming()       { return defenseTiming; }
    /** True for a REACTION-timing defence (arms at fire, triggers on an incoming attack). */
    public boolean isReactionDefense()            { return defenseTiming == DefenseTiming.REACTION; }
    /** Activations allowed inside the defence window; 0 = unlimited. */
    public int getDefenseUses()                   { return defenseUses; }
    /** Whose timeline this defensive move's window is conferred to (SELF for non-defensive moves). */
    public DefenseTargeting getDefenseTargeting() { return defenseTargeting; }
    /** Ally count for {@link DefenseTargeting#MULTIPLE_ALLIES}; ignored otherwise. */
    public int getDefenseTargetCount()            { return defenseTargetCount; }
    /** Pair endpoint selection shape; {@link Targeting#DEFAULT} by default. */
    public Targeting getTargeting() { return targeting; }
    /**
     * On-hit status effects aggregated across all hit components (in authored
     * order). On-hit effects live per {@link HitComponent}; this convenience
     * flattens them for callers that do not need per-hit attribution.
     */
    public List<StatusEffect> getOnHitEffects()   {
        if (hitComponents.isEmpty()) return List.of();
        java.util.List<StatusEffect> all = new java.util.ArrayList<>();
        for (HitComponent component : hitComponents) {
            all.addAll(component.getOnHitEffects());
        }
        return Collections.unmodifiableList(all);
    }
    public List<StatusEffect> getSelfEffects()    { return selfEffects; }
    public List<StatusEffect> getOnBlockEffects() { return onBlockEffects; }
    public List<StatusEffect> getOnParryEffects() { return onParryEffects; }
    public List<StatusEffect> getOnDodgeEffects() { return onDodgeEffects; }
    /** Deep copies preserve this descriptor's immutability despite mutable JSON DTO rows. */
    public List<MoveEffectData> getEffects()       {
        return effects.stream().map(MoveEffectData::copy).toList();
    }
    public boolean usesUnifiedEffects()            { return unifiedEffects; }
    public List<MoveEffectData> effectsFor(MoveEffectTrigger trigger, int componentIndex) {
        if (!unifiedEffects || trigger == null) return List.of();
        return effects.stream()
            .filter(effect -> effect.matches(trigger, componentIndex))
            .map(MoveEffectData::copy)
            .toList();
    }

    private int accuracyPriorityTier(
        AbilityEffectType expected,
        int mastery
    ) {
        int tier = 0;
        for (MoveEffectData effect : effects) {
            if (!expected.name().equalsIgnoreCase(effect.type)
                || !MoveEffectTrigger.ACCURACY_CHECK.name().equalsIgnoreCase(effect.trigger)) {
                continue;
            }
            com.jjktbf.model.character.AbilityEffectData resolved =
                TechniqueMasteryResolver.resolve(effect, mastery);
            tier = Math.max(tier, resolved.intValue == null ? 0 : resolved.intValue);
        }
        return tier;
    }
    public java.util.Map<String, Integer> getPrerequisites() { return prerequisites; }
    public String getRequiredTechniqueId()        { return requiredTechniqueId; }
    /** True when this move belongs to a cursed technique and carries no class. */
    public boolean isTechniqueMove()              { return requiredTechniqueId != null; }
    public String getRequiredCursedToolId()       { return requiredCursedToolId; }
    public boolean isFreeMove()                    { return isFreeMove; }
    public boolean mustBeGranted()                 { return mustBeGranted; }
    public int getMoveCap()                        { return moveCap; }
    /** First shikigami summoned by this move, including canonical effect rows. */
    public String getSummonCharacterId() {
        if (unifiedEffects) {
            return effects.stream()
                .filter(effect -> "SUMMON_CHARACTER".equalsIgnoreCase(effect.type))
                .map(effect -> effect.characterId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst().orElse(null);
        }
        return summonCharacterId;
    }
    /** True when the move composition contains a summon effect. */
    public boolean summonsCharacter() {
        String id = getSummonCharacterId();
        return id != null && !id.isBlank();
    }

    /**
     * Authoritative AOE shape for this move. Null when the move is not AOE;
     * never null when {@link #isAoe()} is true (a default is applied on build).
     */
    public AoeType getAoeType()                    { return aoeType; }
    /**
     * Number of targets hit when {@link #getAoeType()} is {@link AoeType#MULTIPLE}.
     * Defaults to 2; meaningless for the other AOE shapes and for non-AOE moves.
     */
    public int getAoeTargetCount()                 { return aoeTargetCount; }

    /** When a Defensive+Attack hybrid launches its attack. Null on non-hybrids. */
    public AttackLaunchMode getAttackLaunchMode()  { return attackLaunchMode; }
    /** Extra condition tree gating the hybrid attack's launch. Null = always. */
    public AbilityConditionData getAttackLaunchCondition() { return attackLaunchCondition; }
    /** Whether the hybrid attack's launch rolls {@link #getAttackLaunchChance()}. */
    public boolean isAttackLaunchChanceEnabled()   { return attackLaunchChanceEnabled; }
    /** Launch chance percent (0-100) rolled when the chance gate is enabled. */
    public int getAttackLaunchChance()             { return attackLaunchChance; }
    /** Referenced move id launched as the hybrid attack. Null = custom attack. */
    public String getAttackLaunchMoveId()          { return attackLaunchMoveId; }
    /** Resolved referenced attack move; null when unreferenced or unresolved. */
    public Move getAttackLaunchMove()              { return attackLaunchMove; }
    /** True iff this is a Defensive+Attack hybrid (DEFENSIVE category + ATTACK tag). */
    public boolean isDefenceAttackHybrid() {
        return category == MoveCategory.DEFENSIVE && tags.contains(MoveTag.ATTACK);
    }
    /** True iff the hybrid launches its attack after its defence resolves an incoming attack. */
    public boolean launchesAttackOnDefence() {
        return isDefenceAttackHybrid() && attackLaunchMode == AttackLaunchMode.ON_DEFENCE;
    }
    /** True iff the hybrid launches its attack at its own firing tick. */
    public boolean launchesAttackOnFire() {
        return isDefenceAttackHybrid() && attackLaunchMode == AttackLaunchMode.ON_FIRE;
    }
    /** True iff the attack portion is a referenced move rather than own hit components. */
    public boolean referencesAttackMove() {
        return attackLaunchMoveId != null && !attackLaunchMoveId.isBlank();
    }

    public boolean isBlackFlashEligible() {
        return hitComponents.stream().anyMatch(HitComponent::isBlackFlashEligible);
    }

    public boolean hasTag(String tagName) {
        if (tagName == null || tagName.isBlank()) return true;
        String normalized = tagName.trim().toUpperCase();
        if ("ATTACK".equals(normalized)) {
            return tags.contains(MoveTag.ATTACK)
                || !hitComponents.isEmpty();
        }
        if ("GUARD_BREAK".equals(normalized)) return isGuardBreak();
        if ("INTANGIBLE".equals(normalized)) return isIntangible();
        if ("MELEE".equals(normalized)) return isMelee();
        if ("RANGED".equals(normalized)) return isRanged();
        if ("HEAVY".equals(normalized)) return heavy;
        if ("CURSED_ENERGY".equals(normalized)) {
            return tags.contains(MoveTag.CURSED_ENERGY)
                || category == MoveCategory.CURSED_ENERGY
                || category == MoveCategory.PHYSICAL_CURSED_ENERGY
                || category == MoveCategory.INNATE_TECHNIQUE
                || category == MoveCategory.NON_INNATE_TECHNIQUE
                || category == MoveCategory.PHYSICAL_INNATE_TECHNIQUE
                || category == MoveCategory.PHYSICAL_NON_INNATE_TECHNIQUE
                || category == MoveCategory.INNATE_NON_INNATE_TECHNIQUE
                || category == MoveCategory.PHYSICAL_INNATE_NON_INNATE_TECHNIQUE;
        }
        try {
            MoveTag tag = MoveTag.valueOf(normalized);
            return tags.contains(tag) || category.getTags().contains(tag)
                || MoveTag.HIT_ONLY_TAGS.contains(tag)
                    && hitComponents.stream().anyMatch(component -> component.hasTag(tag));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Whether any hit carries the {@link MoveTag#MELEE} range subcategory.
     *
     * <p>Range is authored per hit. This aggregate query supports planning, AI,
     * and summaries that need to know whether the move has any melee hit.
     */
    public boolean isMelee() {
        return hitComponents.stream().anyMatch(HitComponent::isMelee);
    }

    /**
     * Whether any hit carries the {@link MoveTag#RANGED} range subcategory.
     */
    public boolean isRanged() {
        return hitComponents.stream().anyMatch(HitComponent::isRanged);
    }

    /**
     * Whether this move targets an area rather than one combatant. Moves without
     * {@link MoveTag#AOE} are single-target by default.
     */
    public boolean isAoe() {
        return tags.contains(MoveTag.AOE);
    }

    /** Whether this move targets exactly one combatant. */
    public boolean isSingleTarget() {
        return !isAoe();
    }

    /**
     * Whether this move carries the {@link MoveTag#FRIENDLY_FIRE} modifier.
     * Only meaningful together with {@link MoveTag#AOE} (validated on construction).
     */
    public boolean isFriendlyFire() {
        return tags.contains(MoveTag.FRIENDLY_FIRE);
    }

    /**
     * Whether at least one hit bypasses parries and all block reduction.
     */
    public boolean isIntangible() {
        return hitComponents.stream().anyMatch(HitComponent::isIntangible);
    }

    /**
     * Whether this move is a hostile attack that targets an enemy (or enemies).
     * A move is hostile iff it is an attack (the ATTACK tag heuristic) — i.e. it
     * has hit components or the ATTACK tag. Defensive, self-only utility, and
     * summon-only moves are not hostile and require no target selection.
     *
     * <p>A counter-attack hybrid (launch mode ON_DEFENCE) is the exception: its
     * attack targets whoever it just defended against, so planning needs no
     * target selection for the attack portion.</p>
     */
    public boolean isHostile() {
        if (launchesAttackOnDefence()) return onFireEnemyRows();
        if (hasTag("ATTACK")) return true;
        return onFireEnemyRows();
    }

    private boolean onFireEnemyRows() {
        return unifiedEffects && effects.stream()
            .filter(effect -> MoveEffectTrigger.ON_FIRE.name()
                .equalsIgnoreCase(effect.trigger))
            .anyMatch(effect -> "ENEMY".equalsIgnoreCase(effect.target)
                || "BOTH".equalsIgnoreCase(effect.target));
    }

    /** Whether this block or parry covers the first hit of an incoming move. */
    public boolean blocksAttack(Move incoming) {
        if (incoming == null || incoming.hitComponents.isEmpty()) return false;
        return blocksAttack(incoming, incoming.hitComponents.get(0));
    }

    /**
     * Whether this block or parry covers one incoming hit. Attack category is an
     * exact one-of-three match; every authored range and elemental tag on the hit
     * must be accepted by its corresponding defense dimension.
     */
    public boolean blocksAttack(Move incoming, HitComponent component) {
        if (incoming == null) return false;
        HitComponent incomingComponent = component != null ? component
            : incoming.hitComponents.isEmpty() ? null : incoming.hitComponents.get(0);
        if (incomingComponent == null) return false;
        return incoming.coveredByBlockProfile(
            blockRanges, blockElementalTags, incomingComponent);
    }

    private boolean coveredByBlockProfile(
        Set<MoveTag> acceptedRanges,
        Set<MoveTag> acceptedElements,
        HitComponent component
    ) {
        EnumSet<MoveTag> incomingRanges = EnumSet.noneOf(MoveTag.class);
        incomingRanges.addAll(component.getTags());
        incomingRanges.retainAll(MoveTag.RANGE_TAGS);
        if (!acceptedRanges.isEmpty()
            && (incomingRanges.isEmpty() || !acceptedRanges.containsAll(incomingRanges))) {
            return false;
        }

        EnumSet<MoveTag> incomingElements = EnumSet.noneOf(MoveTag.class);
        incomingElements.addAll(component.getTags());
        incomingElements.retainAll(MoveTag.ELEMENTAL_TAGS);
        return acceptedElements.isEmpty() || acceptedElements.containsAll(incomingElements);
    }

    public boolean isDefensive() {
        return category == MoveCategory.DEFENSIVE;
    }

    /** True iff this move is a {@link DefenseType#BLOCK}. */
    public boolean isBlock() {
        return defenseType == DefenseType.BLOCK;
    }

    /** True iff this move is a {@link DefenseType#PARRY}. */
    public boolean isParry() {
        return defenseType == DefenseType.PARRY;
    }

    /** True iff this move is a {@link DefenseType#DODGE}. */
    public boolean isDodge() {
        return defenseType == DefenseType.DODGE;
    }

    /**
     * True iff this move carries an active defense window that the timeline must
     * track (BLOCK, PARRY, or DODGE). SHIELD has no behaviour yet. Replaces the
     * former {@code isActiveBlock()}.
     */
    public boolean isActiveDefense() {
        return defenseType == DefenseType.BLOCK
            || defenseType == DefenseType.PARRY
            || defenseType == DefenseType.DODGE;
    }

    /**
     * Whether this dodge reacts to the given incoming attack's range. A DODGE
     * move with scope BOTH reacts to anything; MELEE/RANGED react only to the
     * matching range. Non-dodge moves return false.
     */
    public boolean dodgeAppliesTo(Move incoming) {
        HitComponent component = incoming == null || incoming.getHitComponents().isEmpty()
            ? null : incoming.getHitComponents().get(0);
        return dodgeAppliesTo(incoming, component);
    }

    /** Whether this dodge reacts to the current incoming hit's range. */
    public boolean dodgeAppliesTo(Move incoming, HitComponent component) {
        if (defenseType != DefenseType.DODGE || incoming == null) return false;
        String scope = dodgeScope == null ? "BOTH" : dodgeScope.trim().toUpperCase();
        return switch (scope) {
            case "MELEE"  -> component == null ? incoming.isMelee() : component.isMelee();
            case "RANGED" -> component == null ? incoming.isRanged() : component.isRanged();
            default       -> true; // BOTH
        };
    }

    /**
     * Whether a successful parry of the given incoming attack should stagger the
     * attacker: the move must be a PARRY, the incoming attack must carry neither
     * GUARD_BREAK nor RANGED, and {@code parryStaggerTicks} must be positive.
     */
    public boolean parryStaggersAttacker(Move incoming) {
        HitComponent component = incoming == null || incoming.getHitComponents().isEmpty()
            ? null : incoming.getHitComponents().get(0);
        return parryStaggersAttacker(incoming, component);
    }

    /**
     * Component-aware guard-break and range check for parry stagger. A RANGED
     * attack never staggers its user when parried — its wielder is out of
     * reach; a perfect read reflects it instead (see DamageCalculator).
     */
    public boolean parryStaggersAttacker(Move incoming, HitComponent component) {
        if (defenseType != DefenseType.PARRY) return false;
        if (component != null ? component.isGuardBreak()
            : incoming != null && incoming.isGuardBreak()) return false;
        boolean ranged = component != null ? component.isRanged()
            : incoming != null && incoming.isRanged();
        if (ranged) return false;
        return parryStaggerTicks > 0;
    }

    /**
     * Apply this move's block reduction to an incoming raw damage value.
     *
     * Returns the modified damage. Damage is never reduced below 1.
     * If this is a full PERCENTAGE block (100%), returns 0 to signal a complete block.
     * Callers should treat a return value of 0 as BLOCKED outcome.
     *
     * Should only be called if {@link #isBlock()} is true.
     */
    public int applyBlockTo(int rawDamage) {
        return (int) Math.round(applyBlockTo((double) rawDamage));
    }

    public double applyBlockTo(double incomingDamage) {
        return applyBlockTo(incomingDamage, 1.0);
    }

    /** Apply the authored block amount after conditional effectiveness modifiers. */
    public double applyBlockTo(double incomingDamage, double effectivenessMultiplier) {
        if (defenseType != DefenseType.BLOCK) return incomingDamage;
        double multiplier = Double.isFinite(effectivenessMultiplier)
            ? Math.max(0.0, effectivenessMultiplier) : Double.MAX_VALUE;
        return switch (blockStyle) {
            case PERCENTAGE -> {
                double reduction = Math.min(100.0, blockDamageReduction * multiplier);
                if (reduction >= 100.0) yield 0;
                yield Math.max(1.0, incomingDamage * (100.0 - reduction) / 100.0);
            }
            case FLAT -> Math.max(1.0, incomingDamage - blockFlatReduction * multiplier);
        };
    }

    @Override
    public String toString() {
        return String.format("Move{%s [%s] hits=%d AP=%d unleash=%d CE=%d}",
            name, category, hitComponents.size(), apCost, unleashPoint, baseCeCost);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static class Builder {
        private String id;
        private String name                  = "";
        private Set<MoveType> moveTypes      = EnumSet.of(MoveType.SORCERER);
        private String description           = "";
        private MoveCategory category        = MoveCategory.PHYSICAL;
        private Set<MoveTag> tags;
        private MovePool pool;
        private int basePower                = 0;
        private List<HitComponent> hitComponents = List.of();
        private boolean hitComponentsExplicit = false;
        private double baseAccuracy          = 1.0;
        private boolean neverMiss            = false;
        private boolean guardBreak           = false;
        private boolean heavy                = false;
        private int potency                  = 1;
        private int apCost                   = 10;
        private int unleashPoint             = 10;
        private int baseCeCost               = 0;
        private Boolean hasCeCost             = null;
        private int minCeCost                = 0;
        private int maxCeCost                = 0;
        private boolean canBeReinforced       = false;
        private int reinforcementBaseCeCost  = 0;
        private int reinforcementMinCeCost   = 0;
        private int reinforcementMaxCeCost   = 0;
        private ReinforcementDefenseType reinforcementDefenseType = ReinforcementDefenseType.NONE;
        private int reinforcementDefenseValue = 0;
        private DefenseType defenseType        = DefenseType.NONE;
        private BlockStyle blockStyle          = BlockStyle.PERCENTAGE;
        private int blockDuration              = 0;
        private Set<MoveTag> blockRanges = Set.of();
        private Set<MoveTag> blockElementalTags = Set.of();
        private int blockDamageReduction       = 100;
        private int blockFlatReduction         = 0;
        private int dodgeChance                = 0;
        private String dodgeScope              = "BOTH";
        private int parryStaggerTicks          = 0;
        private DefenseTiming defenseTiming    = DefenseTiming.FIXED;
        private int defenseUses                = 0;
        private DefenseTargeting defenseTargeting = DefenseTargeting.SELF;
        private int defenseTargetCount          = 2;
        private Targeting targeting = Targeting.DEFAULT;
        /**
         * On-hit effects for the legacy single-component authoring path. When a
         * move is built with {@link #basePower} (no explicit {@link #hitComponents}),
         * these seed the synthesized fallback component. Explicit hit components
         * carry their own effects and ignore this value.
         */
        private List<StatusEffect> onHitEffects = List.of();
        private List<StatusEffect> selfEffects  = List.of();
        private List<StatusEffect> onBlockEffects = List.of();
        private List<StatusEffect> onParryEffects = List.of();
        private List<StatusEffect> onDodgeEffects = List.of();
        private List<MoveEffectData> moveEffects = List.of();
        private boolean moveEffectsExplicit = false;
        private java.util.Map<String, Integer> prerequisites = java.util.Map.of();
        private String requiredTechniqueId   = null;
        private String requiredCursedToolId  = null;
        private boolean isFreeMove           = false;
        private boolean mustBeGranted        = false;
        private int moveCap                  = 0;
        private String summonCharacterId     = null;
        private AoeType aoeType              = null;
        private int aoeTargetCount           = 2;
        private AttackLaunchMode attackLaunchMode = null;
        private AbilityConditionData attackLaunchCondition = null;
        private boolean attackLaunchChanceEnabled = false;
        private int attackLaunchChance       = 100;
        private String attackLaunchMoveId    = null;
        private Move attackLaunchMove        = null;

        public Builder(String id) { this.id = id; }

        public Builder name(String v)                      { this.name = v; return this; }
        public Builder moveType(MoveType v)                {
            this.moveTypes = EnumSet.of(v == null ? MoveType.SORCERER : v);
            return this;
        }
        public Builder moveTypes(Set<MoveType> v)          {
            // Null keeps the sorcerer default; an explicitly empty set is the
            // technique-move representation (no character class).
            this.moveTypes = v == null ? EnumSet.of(MoveType.SORCERER)
                : v.isEmpty() ? EnumSet.noneOf(MoveType.class)
                : EnumSet.copyOf(v);
            return this;
        }
        public Builder description(String v)               { this.description = v; return this; }
        public Builder category(MoveCategory v)            { this.category = v; return this; }
        public Builder tags(Set<MoveTag> v)                { this.tags = v == null ? null : Set.copyOf(v); return this; }
        public Builder pool(MovePool v)                    { this.pool = v; return this; }
        public Builder basePower(int v)                    { this.basePower = v; return this; }
        public Builder hitComponents(List<HitComponent> v) {
            this.hitComponents = v == null ? List.of() : List.copyOf(v);
            this.hitComponentsExplicit = true;
            return this;
        }
        public Builder baseAccuracy(double v)              { this.baseAccuracy = v; return this; }
        public Builder neverMiss(boolean v)                { this.neverMiss = v; return this; }
        public Builder guardBreak(boolean v)               { this.guardBreak = v; return this; }
        public Builder heavy(boolean v)                    { this.heavy = v; return this; }
        public Builder potency(int v)                      { this.potency = v; return this; }
        public Builder apCost(int v)                       { this.apCost = v; return this; }
        public Builder unleashPoint(int v)                 { this.unleashPoint = v; return this; }
        public Builder baseCeCost(int v)                   { this.baseCeCost = v; return this; }
        public Builder hasCeCost(boolean v)                { this.hasCeCost = v; return this; }
        public Builder minCeCost(int v)                    { this.minCeCost = v; return this; }
        public Builder maxCeCost(int v)                    { this.maxCeCost = v; return this; }
        public Builder canBeReinforced(boolean v)          { this.canBeReinforced = v; return this; }
        public Builder reinforcementCeCosts(int base, int min, int max) {
            this.reinforcementBaseCeCost = base;
            this.reinforcementMinCeCost = min;
            this.reinforcementMaxCeCost = max;
            return this;
        }
        public Builder reinforcementDefense(ReinforcementDefenseType type, int value) {
            this.reinforcementDefenseType = type == null
                ? ReinforcementDefenseType.NONE : type;
            this.reinforcementDefenseValue = value;
            return this;
        }
        public Builder defenseType(DefenseType v)          { this.defenseType = v; return this; }
        public Builder blockStyle(BlockStyle v)            { this.blockStyle = v; return this; }
        public Builder blockDuration(int v)                { this.blockDuration = v; return this; }
        public Builder blockRanges(Set<MoveTag> v) {
            this.blockRanges = v == null ? Set.of() : Set.copyOf(v);
            return this;
        }
        public Builder blockElementalTags(Set<MoveTag> v) {
            this.blockElementalTags = v == null ? Set.of() : Set.copyOf(v);
            return this;
        }
        public Builder blockDamageReduction(int v)         { this.blockDamageReduction = v; return this; }
        public Builder blockFlatReduction(int v)           { this.blockFlatReduction = v; return this; }
        public Builder dodgeChance(int v)                  { this.dodgeChance = v; return this; }
        public Builder dodgeScope(String v)                { this.dodgeScope = v; return this; }
        public Builder parryStaggerTicks(int v)            { this.parryStaggerTicks = v; return this; }
        public Builder defenseTiming(DefenseTiming v)      { this.defenseTiming = v; return this; }
        public Builder defenseUses(int v)                  { this.defenseUses = v; return this; }
        /** Set whose timeline this defensive move's window is conferred to. Only meaningful on defensive moves. */
        public Builder defenseTargeting(DefenseTargeting v) { this.defenseTargeting = v; return this; }
        /** Set the ally count for {@link DefenseTargeting#MULTIPLE_ALLIES}. Must be ≥ 2 when used. */
        public Builder defenseTargetCount(int v)           { this.defenseTargetCount = v; return this; }
        /** Set the ordered explicit endpoints selected for this move. */
        public Builder targeting(Targeting v) {
            this.targeting = v == null ? Targeting.DEFAULT : v;
            return this;
        }
        /**
         * On-hit effects for the legacy single-component path only (seeds the
         * synthesized fallback component). For multi-hit moves, author effects
         * directly on each {@link HitComponent}.
         */
        public Builder onHitEffects(List<StatusEffect> v)  { this.onHitEffects = v; return this; }
        public Builder selfEffects(List<StatusEffect> v)   { this.selfEffects = v; return this; }
        public Builder onBlockEffects(List<StatusEffect> v){ this.onBlockEffects = v; return this; }
        public Builder onParryEffects(List<StatusEffect> v){ this.onParryEffects = v; return this; }
        public Builder onDodgeEffects(List<StatusEffect> v){ this.onDodgeEffects = v; return this; }
        public Builder effects(List<MoveEffectData> v) {
            this.moveEffects = v == null ? List.of() : v.stream()
                .filter(java.util.Objects::nonNull)
                .map(MoveEffectData::copy)
                .toList();
            this.moveEffectsExplicit = true;
            return this;
        }
        public Builder prerequisites(java.util.Map<String, Integer> v) { this.prerequisites = v; return this; }
        public Builder requiredTechniqueId(String v)       {
            this.requiredTechniqueId = v == null || v.isBlank() ? null : v.trim();
            return this;
        }
        public Builder requiredCursedToolId(String v)      { this.requiredCursedToolId = v; return this; }
        public Builder freeMove(boolean v)                 { this.isFreeMove = v; return this; }
        public Builder mustBeGranted(boolean v)            { this.mustBeGranted = v; return this; }
        public Builder moveCap(int v)                       { this.moveCap = v; return this; }
        /** Set the shikigami character id summoned at this move's unleash point. */
        public Builder summonCharacterId(String v)          { this.summonCharacterId = v; return this; }
        /** Set the authoritative AOE shape. Only meaningful together with the AOE tag. */
        public Builder aoeType(AoeType v)                   { this.aoeType = v; return this; }
        /** Set the target count for {@link AoeType#MULTIPLE}. Must be ≥ 2 when used. */
        public Builder aoeTargetCount(int v)                { this.aoeTargetCount = v; return this; }
        /** Set when a Defensive+Attack hybrid launches its attack. */
        public Builder attackLaunchMode(AttackLaunchMode v) { this.attackLaunchMode = v; return this; }
        /** Set the extra condition tree gating the hybrid attack's launch. */
        public Builder attackLaunchCondition(AbilityConditionData v) {
            this.attackLaunchCondition = v == null ? null : v.copy();
            return this;
        }
        /** Set the optional launch chance roll (percent clamped to [0, 100]). */
        public Builder attackLaunchChance(boolean enabled, int percent) {
            this.attackLaunchChanceEnabled = enabled;
            this.attackLaunchChance = Math.max(0, Math.min(100, percent));
            return this;
        }
        /** Set the referenced move id launched as the hybrid attack. */
        public Builder attackLaunchMoveId(String v)         { this.attackLaunchMoveId = v; return this; }
        /** Inject the resolved referenced attack move (built from the repository). */
        public Builder attackLaunchMove(Move v)             { this.attackLaunchMove = v; return this; }

        public Move build() {
            if (id == null || id.isBlank()) throw new IllegalStateException("Move id is required");
            if (unleashPoint < 1 || unleashPoint > apCost)
                throw new IllegalStateException("unleashPoint must be in [1, apCost]");
            if (moveCap < 0)
                throw new IllegalStateException("moveCap must be non-negative");
            if (requiredTechniqueId == null && moveTypes.isEmpty())
                throw new IllegalStateException(
                    "Move requires at least one move type unless it belongs to a technique (name='"
                        + name + "')");

            Set<MoveTag> effectiveTags = tags != null ? tags : category.getTags();
            if (moveTypes.contains(MoveType.CURSED_SPIRIT)
                && (tags == null || !tags.contains(MoveTag.CURSED_ENERGY))) {
                throw new IllegalStateException(
                    "Cursed Spirit moves must explicitly include CURSED_ENERGY (name='"
                        + name + "')");
            }
            if (effectiveTags.contains(MoveTag.FRIENDLY_FIRE)
                && !effectiveTags.contains(MoveTag.AOE)) {
                throw new IllegalStateException("FRIENDLY_FIRE requires AOE");
            }

            // AOE type is only meaningful on AOE moves; reject it elsewhere so an
            // authoring slip (type set without the tag) can't silently take effect.
            boolean isAoe = effectiveTags.contains(MoveTag.AOE);
            if (!isAoe && aoeType != null) {
                throw new IllegalStateException("aoeType may only be set on an AOE move (name='" + name + "')");
            }
            if (aoeType == AoeType.MULTIPLE && aoeTargetCount < 2) {
                throw new IllegalStateException(
                    "MULTIPLE AOE type requires aoeTargetCount >= 2 (name='" + name + "')");
            }

            // Defensive targeting is only meaningful on defensive moves; reject
            // an authored ally mode elsewhere so a slip can't silently take effect.
            if (category != MoveCategory.DEFENSIVE && defenseTargeting != DefenseTargeting.SELF) {
                throw new IllegalStateException(
                    "defenseTargeting may only be set on a defensive move (name='" + name + "')");
            }
            if (defenseTargeting == DefenseTargeting.MULTIPLE_ALLIES && defenseTargetCount < 2) {
                throw new IllegalStateException(
                    "MULTIPLE_ALLIES defense targeting requires defenseTargetCount >= 2 (name='" + name + "')");
            }

            // Defence timing and uses only take effect on an active defence;
            // reject them elsewhere so an authoring slip can't silently apply.
            if (defenseUses < 0) {
                throw new IllegalStateException(
                    "defenseUses must be non-negative (name='" + name + "')");
            }
            if (category != MoveCategory.DEFENSIVE
                && (defenseTiming != DefenseTiming.FIXED || defenseUses != 0)) {
                throw new IllegalStateException(
                    "defenseTiming/defenseUses may only be set on a defensive move (name='" + name + "')");
            }

            validateAttackLaunch();
            validateHitComponents();
            validateTargeting();
            validateMoveEffects();

            // Potency lives on attack and defensive moves (gates which defences
            // stop which attacks). Utility moves don't participate, so clamp to 1.
            if (category == MoveCategory.UTILITY) {
                potency = 1;
            } else if (potency < 1) {
                potency = 1;
            }

            // Technique-tag invariant: moves bearing the INNATE_TECHNIQUE or
            // NON_INNATE_TECHNIQUE tag MUST declare their governing mastery stat
            // as a prerequisite (even if 0), and innate-technique moves MUST name
            // their technique. This is what lets a Technique's progression be
            // discovered and mastery-sorted at runtime, and keeps the editor's
            // save validation honest (it routes through this builder).
            if (category != null) {
                var tags = this.tags != null ? this.tags : category.getTags();
                boolean isInnate    = tags.contains(MoveTag.INNATE_TECHNIQUE);
                boolean isNonInnate = tags.contains(MoveTag.NON_INNATE_TECHNIQUE);
                if (isInnate) {
                    if (requiredTechniqueId == null || requiredTechniqueId.isBlank()) {
                        throw new IllegalStateException(
                            "Innate-technique moves must set a requiredTechniqueId (name='" + name + "')");
                    }
                    if (!hasStatPrereq(prerequisites, "cursedtechniquemastery", "ctm")) {
                        throw new IllegalStateException(
                            "Innate-technique moves must declare a cursedTechniqueMastery prerequisite (name='" + name + "')");
                    }
                }
                if (isNonInnate) {
                    if (!hasStatPrereq(prerequisites, "jujutsuskill", "js")) {
                        throw new IllegalStateException(
                            "Non-innate-technique moves must declare a jujutsuSkill prerequisite (name='" + name + "')");
                    }
                }
            }

            return new Move(this);
        }

        private void validateMoveEffects() {
            if (!moveEffectsExplicit) return;
            java.util.ArrayList<MoveEffectData> rows = moveEffects.stream()
                .map(MoveEffectData::copy)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            AbilityData.ensureEffectIds(rows);
            java.util.Set<String> ids = new java.util.HashSet<>();
            int hitCount = hitComponentsExplicit
                ? hitComponents.size()
                : synthesizesLegacyComponent() ? 1 : 0;
            boolean masteryEligible = effectiveTags().contains(MoveTag.INNATE_TECHNIQUE);
            for (int index = 0; index < rows.size(); index++) {
                MoveEffectData effect = rows.get(index);
                if (!ids.add(effect.effectId)) {
                    throw new IllegalStateException(
                        "Move effects need unique stable effect IDs (name='" + name + "')");
                }
                String error = effect.validationError(hitCount, masteryEligible);
                if (error != null) {
                    throw new IllegalStateException("Invalid move effect " + (index + 1)
                        + " (name='" + name + "'): " + error);
                }
                MoveEffectTrigger trigger = effect.resolvedTrigger();
                AbilityEffectType effectType = AbilityEffectType.fromName(effect.type);
                if (effectType == AbilityEffectType.EXCHANGE_ATTACK_TARGETS
                    && targeting == Targeting.DEFAULT) {
                    throw new IllegalStateException(
                        "Exchange attack targets requires pair targeting (name='" + name + "')");
                }
                if (trigger == MoveEffectTrigger.ACCURACY_CHECK
                    && effectType == AbilityEffectType.NEVER_MISS && hitCount == 0) {
                    throw new IllegalStateException(
                        "Never Miss requires an attacking move (name='" + name + "')");
                }
                if (trigger == MoveEffectTrigger.ACCURACY_CHECK
                    && effectType == AbilityEffectType.NEVER_HIT
                    && defenseType != DefenseType.DODGE) {
                    throw new IllegalStateException(
                        "Never Hit requires a dodge move (name='" + name + "')");
                }
                if (trigger == MoveEffectTrigger.ON_HIT && hitCount == 0) {
                    throw new IllegalStateException(
                        "On-hit effects require an attacking move (name='" + name + "')");
                }
                if (trigger == MoveEffectTrigger.ON_BLOCK && defenseType != DefenseType.BLOCK) {
                    throw new IllegalStateException(
                        "On-block effects require a block move (name='" + name + "')");
                }
                if (trigger == MoveEffectTrigger.BLOCK_CALCULATION
                    && defenseType != DefenseType.BLOCK) {
                    throw new IllegalStateException(
                        "Block modifiers require a block move (name='" + name + "')");
                }
                if (trigger == MoveEffectTrigger.ON_PARRY && defenseType != DefenseType.PARRY) {
                    throw new IllegalStateException(
                        "On-parry effects require a parry move (name='" + name + "')");
                }
                if (trigger == MoveEffectTrigger.ON_DODGE && defenseType != DefenseType.DODGE) {
                    throw new IllegalStateException(
                        "On-dodge effects require a dodge move (name='" + name + "')");
                }
            }
        }

        private void validateTargeting() {
            if (targeting != Targeting.DEFAULT
                && category != MoveCategory.UTILITY
                && category != MoveCategory.DEFENSIVE) {
                throw new IllegalStateException(
                    "Pair targeting is only supported on utility and defensive moves (name='"
                        + name + "')");
            }
            if (targeting != Targeting.DEFAULT
                && category == MoveCategory.DEFENSIVE
                && effectiveTags().contains(MoveTag.ATTACK)) {
                throw new IllegalStateException(
                    "Pair-targeted defensive moves cannot launch attacks (name='" + name + "')");
            }
        }

        private Set<MoveTag> effectiveTags() {
            return tags != null ? tags : category.getTags();
        }

        /**
         * True when the legacy single-component path synthesizes a fallback
         * component for this move: every damaging move, and — since hybrids
         * carry an attack — a Defensive+Attack hybrid authored with move-level
         * base power instead of explicit hit components.
         */
        private boolean synthesizesLegacyComponent() {
            if (hitComponentsExplicit) return false;
            if (category == MoveCategory.UTILITY) return false;
            if (category == MoveCategory.DEFENSIVE) {
                return effectiveTags().contains(MoveTag.ATTACK)
                    && basePower > 0
                    && !legacyHybridDamageTags().isEmpty();
            }
            return true;
        }

        /** Damage-nature tags of a legacy-authored hybrid's synthesized component. */
        private EnumSet<MoveTag> legacyHybridDamageTags() {
            EnumSet<MoveTag> damageTags = EnumSet.noneOf(MoveTag.class);
            for (MoveTag tag : effectiveTags()) {
                if (MoveTag.TYPE_TAGS.contains(tag)) damageTags.add(tag);
            }
            return damageTags;
        }

        /** Hit-only tags accepted from legacy move-level authoring. */
        private EnumSet<MoveTag> legacyHitTags() {
            EnumSet<MoveTag> hitTags = EnumSet.noneOf(MoveTag.class);
            for (MoveTag tag : effectiveTags()) {
                if (MoveTag.HIT_ONLY_TAGS.contains(tag)) hitTags.add(tag);
            }
            if (guardBreak) hitTags.add(MoveTag.GUARD_BREAK);
            return hitTags;
        }

        private EnumSet<MoveTag> legacyComponentTags(Set<MoveTag> typeTags) {
            EnumSet<MoveTag> componentTags = EnumSet.copyOf(typeTags);
            componentTags.addAll(legacyHitTags());
            return componentTags;
        }

        /**
         * A Defensive+Attack hybrid (DEFENSIVE category + ATTACK tag) must say
         * when its attack launches, and only hybrids may carry launch settings.
         */
        private void validateAttackLaunch() {
            boolean hybrid = category == MoveCategory.DEFENSIVE
                && effectiveTags().contains(MoveTag.ATTACK);
            boolean authored = attackLaunchMode != null
                || attackLaunchCondition != null
                || attackLaunchChanceEnabled
                || (attackLaunchMoveId != null && !attackLaunchMoveId.isBlank());
            if (!hybrid) {
                if (authored) {
                    throw new IllegalStateException(
                        "Attack launch settings require a Defensive+Attack hybrid (name='"
                        + name + "')");
                }
                return;
            }
            if (attackLaunchMode == null) {
                throw new IllegalStateException(
                    "A Defensive+Attack hybrid must choose an attack launch mode (name='"
                    + name + "')");
            }
        }

        private void validateHitComponents() {
            boolean hybrid = category == MoveCategory.DEFENSIVE
                && effectiveTags().contains(MoveTag.ATTACK);
            if (hybrid) {
                boolean references = attackLaunchMoveId != null && !attackLaunchMoveId.isBlank();
                boolean hasComponents = hitComponentsExplicit
                    ? !hitComponents.isEmpty()
                    : synthesizesLegacyComponent();
                if (references && hasComponents) {
                    throw new IllegalStateException(
                        "A hybrid cannot both reference a move and define hit components (name='"
                        + name + "')");
                }
                if (!references && !hasComponents) {
                    throw new IllegalStateException(
                        "A Defensive+Attack hybrid must define hit components or reference a move (name='"
                        + name + "')");
                }
            }
            if (!hitComponentsExplicit) return;
            boolean damaging = category != MoveCategory.UTILITY
                && category != MoveCategory.DEFENSIVE;
            if (!damaging && !hybrid && !hitComponents.isEmpty()) {
                throw new IllegalStateException(
                    "Only attacking moves may define hit components (name='" + name + "')");
            }
            if (damaging && hitComponents.isEmpty()) {
                throw new IllegalStateException(
                    "Attacking moves must define at least one hit component (name='" + name + "')");
            }
            if (!damaging && !hybrid) return;

            EnumSet<MoveTag> componentTags = EnumSet.noneOf(MoveTag.class);
            for (int index = 0; index < hitComponents.size(); index++) {
                HitComponent component = hitComponents.get(index);
                if (component == null || component.getBasePower() < 0) {
                    throw new IllegalStateException(
                        "Hit components must have nonnegative Base Power (name='" + name + "')");
                }
                if (index == 0 && component.requiresPreviousConnection()) {
                    throw new IllegalStateException(
                        "The first hit component cannot require a previous connection (name='"
                        + name + "')");
                }
                if (index > 0 && component.requiresPreviousConnection()
                    && component.getDelayTicks() < hitComponents.get(index - 1).getDelayTicks()) {
                    throw new IllegalStateException(
                        "A dependent hit cannot occur before its prerequisite (name='"
                        + name + "')");
                }
                componentTags.addAll(component.getTypeTags());
            }
            // A hybrid's category is DEFENSIVE (no damage tags of its own), so
            // its components are checked against the move's damage-nature tags.
            if (hybrid) {
                EnumSet<MoveTag> expected = EnumSet.noneOf(MoveTag.class);
                for (MoveTag tag : effectiveTags()) {
                    if (MoveTag.TYPE_TAGS.contains(tag)) expected.add(tag);
                }
                if (!componentTags.equals(expected)) {
                    throw new IllegalStateException(
                        "Hybrid damage tags must match the union of its hit-component tags (name='"
                        + name + "')");
                }
            } else if (!componentTags.equals(category.getTags())) {
                throw new IllegalStateException(
                    "Move damage tags must match the union of its hit-component tags (name='"
                    + name + "')");
            }
        }

        /**
         * Case/underscore/whitespace-insensitive check that a prerequisite map
         * contains one of the candidate stat names (canonical or alias).
         */
        private static boolean hasStatPrereq(java.util.Map<String, Integer> prereqs,
                                             String canonical, String alias) {
            String canon = normalise(canonical);
            String ali   = normalise(alias);
            for (String key : prereqs.keySet()) {
                String k = normalise(key);
                if (k.equals(canon) || k.equals(ali)) return true;
            }
            return false;
        }

        private static String normalise(String s) {
            return s == null ? "" : s.toLowerCase().replace("_", "").replace(" ", "");
        }
    }
}
