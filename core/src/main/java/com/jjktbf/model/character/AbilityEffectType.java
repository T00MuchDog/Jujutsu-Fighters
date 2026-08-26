package com.jjktbf.model.character;

import com.jjktbf.model.character.coded.CodedAbilityRegistry;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffect;
import com.jjktbf.model.move.StatusEffectType;
import com.jjktbf.model.progression.TechniqueMasteryProgressions;
import com.jjktbf.model.progression.TechniqueMasteryResolver;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.LinkedHashSet;

import static com.jjktbf.model.character.AbilityEffectParameter.DECIMAL;
import static com.jjktbf.model.character.AbilityEffectParameter.DURATION;
import static com.jjktbf.model.character.AbilityEffectParameter.INTEGER;
import static com.jjktbf.model.character.AbilityEffectParameter.MAGNITUDE;
import static com.jjktbf.model.character.AbilityEffectParameter.PER_TICK_REMOVAL_CHANCE;
import static com.jjktbf.model.character.AbilityEffectParameter.STAT_MULTIPLIER_CURVE;
import static com.jjktbf.model.character.AbilityEffectParameter.ABILITY_ID;
import static com.jjktbf.model.character.AbilityEffectParameter.CHARACTER_ID;
import static com.jjktbf.model.character.AbilityEffectParameter.TRANSFORMATION_HP;
import static com.jjktbf.model.character.AbilityEffectParameter.RETURN_CONDITION;
import static com.jjktbf.model.character.AbilityEffectParameter.MOVE_ID;
import static com.jjktbf.model.character.AbilityEffectParameter.MOVE_SCOPE;
import static com.jjktbf.model.character.AbilityEffectParameter.STAT;
import static com.jjktbf.model.character.AbilityEffectParameter.STATUS_TYPE;
import static com.jjktbf.model.character.AbilityEffectParameter.TARGET;
import static com.jjktbf.model.character.AbilityEffectParameter.TECHNIQUE;
import static com.jjktbf.model.character.AbilityEffectParameter.TIMING;
import static com.jjktbf.model.character.AbilityEffectParameter.USES;
import static com.jjktbf.model.character.AbilityEffectParameter.BATTLE_STAT;
import static com.jjktbf.model.character.AbilityEffectParameter.CODED_FEATURE;
import static com.jjktbf.model.character.AbilityEffectParameter.CODED_ACTION;
import static com.jjktbf.model.character.AbilityEffectParameter.RESOURCE_KEY;
import static com.jjktbf.model.character.AbilityEffectParameter.RESOURCE_LABEL;
import static com.jjktbf.model.character.AbilityEffectParameter.RESOURCE_CAPACITY;
import static com.jjktbf.model.character.AbilityEffectParameter.RESOURCE_START_VALUE;
import static com.jjktbf.model.character.AbilityEffectParameter.SOURCE_RESOURCE;
import static com.jjktbf.model.character.AbilityEffectParameter.SOURCE_RESOURCE_AMOUNT;
import static com.jjktbf.model.character.AbilityEffectParameter.TARGET_RESOURCE;
import static com.jjktbf.model.character.AbilityEffectParameter.TARGET_RESOURCE_AMOUNT;
import static com.jjktbf.model.character.AbilityEffectParameter.REFRESH_GROUP;
import static com.jjktbf.model.character.AbilityEffectParameter.VALUE_MODE;
import static com.jjktbf.model.character.AbilityEffectParameter.STAT_TYPE;
import static com.jjktbf.model.character.AbilityEffectParameter.STAT_OPERATION;
import static com.jjktbf.model.character.AbilityEffectParameter.ACCURACY_DURATION;
import static com.jjktbf.model.character.AbilityEffectParameter.CE_DRAIN_MODE;
import static com.jjktbf.model.character.AbilityEffectParameter.CE_EFFICIENCY_SCALING;

/**
 * Mechanical effects that can be composed into an ability.
 *
 * <p>Each type owns its editor-facing name, explanation, required parameters,
 * defaults, data cleanup, and validation. The graphics editor consumes this
 * metadata instead of presenting every field for every effect.</p>
 */
public enum AbilityEffectType {

    STAT_ADD(
        "Add to stat",
        "Permanently adds or subtracts a flat amount from one stat.",
        STAT, INTEGER),
    STAT_MULTIPLY(
        "Multiply stat",
        "Multiplies one stat after flat changes. 1.20 means 20% higher.",
        STAT, DECIMAL),
    STAT_DIVIDE(
        "Divide stat",
        "Divides one stat after flat changes. 2.00 halves it.",
        STAT, DECIMAL),
    STAT_SET_VALUE(
        "Set stat value",
        "Sets one stat to an exact value before flat additions.",
        STAT, INTEGER),
    STAT_SET_MIN(
        "Remove stat",
        "Sets one stat to 0, displayed as N/A.",
        STAT),
    STAT_ALLOCATION_MINIMUM(
        "Set allocation minimum",
        "Sets the minimum value assignable to one base stat without affecting battle-time stat changes.",
        STAT, INTEGER),
    STAT_ALLOCATION_MAXIMUM(
        "Set allocation maximum",
        "Sets the maximum value assignable to one base stat without affecting battle-time stat changes.",
        STAT, INTEGER),
    STAT_BONUS_POINTS(
        "Change point budget",
        "Changes the character editor's point-buy budget.",
        INTEGER),

    POISON_IMMUNITY(
        "Poison immunity",
        "Marks the character as immune to poison effects."),
    SOUL_AWARE_ATTACKS(
        "Soul-aware attacks",
        "Marks the character's attacks as capable of interacting with souls."),
    CODED(
        "Coded effect",
        "Enables an allow-listed compiled mechanic that cannot be composed from generic effects.",
        CODED_FEATURE),
    CODED_MOVE_ACTION(
        "Coded move effect",
        "Runs an allow-listed compiled effect primitive when its move trigger fires.",
        CODED_ACTION, TARGET),

    CE_COST_TO_MINIMUM(
        "Minimum CE costs",
        "Forces matching moves to use their configured minimum CE cost.",
        MOVE_SCOPE),
    CE_COST_MULTIPLY(
        "Multiply CE costs",
        "Multiplies the CE cost of matching moves. 0.50 halves the cost.",
        MOVE_SCOPE, DECIMAL),
    CE_COST_ALTER(
        "Alter CE costs",
        "Scales matching move costs, then adds or subtracts a flat CE amount. Applied after the move's configured CE bounds and never below zero.",
        MOVE_SCOPE, DECIMAL, INTEGER),

    MOVE_ACCURACY_ADD(
        "Change own accuracy",
        "Adds or subtracts accuracy points when using matching moves.",
        MOVE_SCOPE, INTEGER),
    MOVE_ACCURACY_MULTIPLY(
        "Multiply own accuracy",
        "Multiplies accuracy when using matching moves.",
        MOVE_SCOPE, DECIMAL),
    OPPONENT_ACCURACY_ADD(
        "Change enemy accuracy",
        "Adds or subtracts an enemy's accuracy when they attack this character.",
        MOVE_SCOPE, INTEGER),
    OPPONENT_ACCURACY_MULTIPLY(
        "Multiply enemy accuracy",
        "Multiplies an enemy's accuracy when they attack this character.",
        MOVE_SCOPE, DECIMAL),
    NEVER_MISS(
        "Never Miss",
        "Gives matching attacks an accuracy-priority tier. Never Miss wins when its tier is equal to or higher than Never Hit.",
        MOVE_SCOPE, INTEGER),
    NEVER_HIT(
        "Never Hit",
        "Gives this character an accuracy-defense tier against matching attacks. It stops only lower-tier Never Miss attacks.",
        MOVE_SCOPE, INTEGER),

    DAMAGE_MULTIPLY(
        "Multiply damage",
        "Multiplies damage dealt by matching moves.",
        MOVE_SCOPE, DECIMAL),
    MOVE_BASE_POWER_MULTIPLY(
        "Multiply move base power",
        "Multiplies the configured base power of matching moves before power and defense are applied.",
        MOVE_SCOPE, DECIMAL),
    MOVE_BASE_POWER_SCALE_BY_STAT(
        "Scale move base power by stat",
        "Scales matching moves' configured base power along a piecewise-linear curve from the current scaled stat: the configured minimum multiplier at 10, x1 at 80, and the configured maximum multiplier at scaled stat maximum.",
        STAT, MOVE_SCOPE, STAT_MULTIPLIER_CURVE),
    INCOMING_DAMAGE_MULTIPLY(
        "Multiply incoming damage",
        "Multiplies damage taken from matching moves. 0.95 reduces it by 5%.",
        MOVE_SCOPE, DECIMAL),
    GRANT_MOVE(
        "Grant move",
        "Adds one move to the character's available moves, bypassing all requirements.",
        MOVE_ID),
    GRANT_ABILITY(
        "Grant ability",
        "Adds one ability to the character's available abilities. It must still be assigned normally.",
        ABILITY_ID),
    UNLOCK_MOVE(
        "Unlock move",
        "Adds one move to the character's available moves. It must still be learned normally.",
        MOVE_ID),
    BF_CHANCE_ADD(
        "Change Black Flash chance",
        "Adds or subtracts Black Flash chance. Enter 5 for five percentage points.",
        DECIMAL),
    UNLOCK_TECHNIQUE(
        "Unlock technique",
        "Lets the character learn and use moves belonging to another technique.",
        TECHNIQUE),
    MODIFY_DEFENSE(
        "Multiply defense",
        "Multiplies the character's effective defense.",
        DECIMAL),
    DEFENSE_FROM_DURABILITY(
        "Defense from durability",
        "Replaces the normal defense formula with scaled Durability multiplied by this value.",
        DECIMAL),
    MODIFY_AP_BAR(
        "Change AP bar",
        "Adds or subtracts a flat amount from the character's AP bar.",
        INTEGER),
    AUTO_STATUS_APPLY(
        "Apply status automatically",
        "Applies a supported status at fight start, round start, or after a hit.",
        STATUS_TYPE, TARGET, TIMING, DURATION, MAGNITUDE, PER_TICK_REMOVAL_CHANCE),
    LOCK_MOVE_TAG(
        "Lock own move tag",
        "Prevents this character from selecting moves with one tag.",
        MOVE_SCOPE),
    SET_JUJUTSU_ART_SLOTS(
        "Set Jujutsu Art slots",
        "Overrides the number of Jujutsu Art slots available to the character.",
        INTEGER),
    COST_CE_PER_ROUND(
        "Round-start CE cost",
        "Drains CE before planning each round. Other passive effects remain active at 0 CE.",
        INTEGER),
    MAX_ACTIVE_SUMMONS(
        "Maximum active summons",
        "Caps the number of direct active and pending shikigami summons. Multiple caps use the lowest value.",
        INTEGER),
    SUMMON_CE_UPKEEP_PER_ACTIVE_TICK(
        "Summon CE upkeep per active tick",
        "Adds this flat CE upkeep rate to the shikigami while it is actively summoned.",
        DECIMAL),

    HEAL_HP(
        "Heal HP",
        "Immediately restores either a flat amount or a percentage of maximum HP.",
        TARGET, VALUE_MODE, INTEGER, DECIMAL),
    RESTORE_CE(
        "Restore CE",
        "Immediately restores either a flat amount or a percentage of maximum Cursed Energy.",
        TARGET, VALUE_MODE, INTEGER, DECIMAL),
    DRAIN_CE(
        "Drain CE",
        "Immediately removes CE or drains it on each resolution tick for a configured duration.",
        TARGET, CE_DRAIN_MODE, VALUE_MODE, INTEGER, DECIMAL, DURATION,
        CE_EFFICIENCY_SCALING),
    DEAL_DIRECT_DAMAGE(
        "Deal direct damage",
        "Immediately deals either fixed damage or a percentage of maximum HP, bypassing accuracy and defense.",
        TARGET, VALUE_MODE, INTEGER, DECIMAL),
    INSTANT_KILL(
        "Instant kill",
        "Immediately reduces the target to 0 HP unless fatal-hit protection is active.",
        TARGET),

    APPLY_STATUS(
        "Apply status",
        "Applies any status when the ability activates.",
        STATUS_TYPE, TARGET, DURATION, MAGNITUDE, PER_TICK_REMOVAL_CHANCE),
    REMOVE_STATUS(
        "Remove status",
        "Removes every instance of one status from the target.",
        STATUS_TYPE, TARGET),
    CLEAR_STATUSES(
        "Clear all statuses",
        "Removes every active status from the target.",
        TARGET),

    TIMED_STAT_MODIFIER(
        "Timed stat modifier",
        "Changes or multiplies a core or battle stat for the configured rounds and ticks.",
        STAT_TYPE, STAT_OPERATION, VALUE_MODE, STAT, BATTLE_STAT,
        TARGET, INTEGER, DECIMAL, DURATION, REFRESH_GROUP),
    TEMP_STAT_SET_VALUE(
        "Timed character stat set",
        "Sets a character stat to an exact value for the configured rounds and ticks.",
        STAT, TARGET, INTEGER, DURATION, REFRESH_GROUP),
    BATTLE_STAT_ODDS_MULTIPLY(
        "Multiply battle-stat odds",
        "Permanently multiplies the odds of a probability battle stat. A factor of 2 doubles odds without directly doubling probability.",
        BATTLE_STAT, DECIMAL),

    IGNORE_DAMAGE(
        "Ignore incoming damage",
        "Negates damaging instances. Uses = 1 for one hit or -1 for every hit during the duration.",
        TARGET, USES, DURATION),
    DAMAGE_SHIELD(
        "Damage shield",
        "Absorbs up to a fixed total amount of incoming damage during the duration.",
        TARGET, INTEGER, DURATION),
    SURVIVE_FATAL_DAMAGE(
        "Survive fatal damage",
        "Leaves the target at 1 HP when damage would defeat them.",
        TARGET, USES, DURATION),
    APPLY_NEVER_MISS(
        "Apply Never Miss tier",
        "Applies Never Miss to matching attacks for the next attack or a configured duration. Tier 0 skips only the normal accuracy roll; tiers 1-5 also contest Never Hit and dodges.",
        TARGET, MOVE_SCOPE, INTEGER, ACCURACY_DURATION, DURATION),
    APPLY_NEVER_HIT(
        "Apply Never Hit tier",
        "Applies a Never Hit tier against the next incoming attack or for a configured duration.",
        TARGET, INTEGER, ACCURACY_DURATION, DURATION),
    GUARANTEE_NEXT_BLACK_FLASH(
        "Guarantee next Black Flash",
        "Makes the target's next Black-Flash-eligible hit become a Black Flash.",
        TARGET, USES, DURATION),
    CANCEL_NEXT_MOVE(
        "Cancel next move",
        "Stuns the target's next move when it begins execution.",
        TARGET, USES, DURATION),
    STUN_CURRENT_ACTION(
        "Stun current action",
        "Cancels the target's active, not-yet-fired action on the current tick. HEAVY actions resist this effect.",
        TARGET),
    TEMP_LOCK_MOVE_TAG(
        "Temporarily lock move tag",
        "Prevents the target from planning moves with one tag for the duration.",
        TARGET, MOVE_SCOPE, DURATION),
    TAUNT(
        "Taunt",
        "Draws enemies' single-target MELEE attacks onto the target for the configured rounds and ticks. Area-of-effect attacks are unaffected.",
        TARGET, DURATION),
    EXCHANGE_ATTACK_TARGETS(
        "Exchange attack targets",
        "Transposes the selected pair for matching future single-target attacks. Area-of-effect attacks are unaffected.",
        MOVE_SCOPE, DURATION, USES),
    DEFINE_BOUNDED_RESOURCE(
        "Define bounded resource",
        "Creates a named player-visible resource with a capacity and initial value.",
        RESOURCE_KEY, RESOURCE_LABEL, RESOURCE_CAPACITY, RESOURCE_START_VALUE),
    TRANSACT_BOUNDED_RESOURCE(
        "Change bounded resources",
        "Atomically consumes one named resource and adds to another. Either side may be omitted for a pure gain or spend.",
        TARGET, SOURCE_RESOURCE, SOURCE_RESOURCE_AMOUNT,
        TARGET_RESOURCE, TARGET_RESOURCE_AMOUNT),
    MOVE_UNAVAILABLE_WHILE_OWNED_SUMMON_ACTIVE(
        "Block while shikigami is active",
        "Prevents this move from being used while the selected owned shikigami is active on the field.",
        CHARACTER_ID),
    SUMMON_CHARACTER(
        "Summon character",
        "Summons a shikigami combatant onto the owner's team when activated.",
        CHARACTER_ID),
    TRANSFORM_CHARACTER(
        "Transform character",
        "Changes the target into another authored character form while preserving its battle identity. An optional condition returns it to its original form.",
        CHARACTER_ID, TARGET, TRANSFORMATION_HP, RETURN_CONDITION),
    DESUMMON_OWNED_SHIKIGAMI(
        "Desummon owned shikigami",
        "Voluntarily dismisses every direct shikigami summon owned by this combatant and their descendants."),
    DESUMMON_TARGET_SHIKIGAMI(
        "Desummon target shikigami",
        "Voluntarily dismisses the targeted shikigami combatant.",
        TARGET);

    /**
     * Effects that require an active ability condition to run at battle time.
     * This explicit set replaces the former ordinal-based check so adding a new
     * activation-required effect (e.g. SUMMON_CHARACTER) does not silently shift
     * the ordinal boundary and reclassify earlier effects.
     */
    private static final java.util.Set<AbilityEffectType> ACTIVATION_REQUIRED =
        java.util.EnumSet.of(
            HEAL_HP, RESTORE_CE, DRAIN_CE, DEAL_DIRECT_DAMAGE,
            INSTANT_KILL, APPLY_STATUS, REMOVE_STATUS, CLEAR_STATUSES,
            TIMED_STAT_MODIFIER, TEMP_STAT_SET_VALUE,
            IGNORE_DAMAGE, DAMAGE_SHIELD, SURVIVE_FATAL_DAMAGE,
            APPLY_NEVER_MISS, APPLY_NEVER_HIT, GUARANTEE_NEXT_BLACK_FLASH,
             CANCEL_NEXT_MOVE, STUN_CURRENT_ACTION, TEMP_LOCK_MOVE_TAG, TAUNT,
             EXCHANGE_ATTACK_TARGETS, TRANSACT_BOUNDED_RESOURCE,
             SUMMON_CHARACTER,
             TRANSFORM_CHARACTER,
            DESUMMON_OWNED_SHIKIGAMI, DESUMMON_TARGET_SHIKIGAMI,
            CODED_MOVE_ACTION);

    private static final Set<StatusEffectType> SUPPORTED_AUTO_STATUSES =
        Collections.unmodifiableSet(EnumSet.allOf(StatusEffectType.class));

    private final String displayName;
    private final String description;
    private final EnumSet<AbilityEffectParameter> parameters;

    public enum ValueMode {
        FLAT("Flat amount"),
        PERCENT("Percentage");

        public final String label;
        ValueMode(String label) { this.label = label; }
    }

    public enum StatType {
        CORE("Core stat"),
        BATTLE("Battle stat");

        public final String label;
        StatType(String label) { this.label = label; }
    }

    public enum StatOperation {
        CHANGE("Change"),
        MULTIPLY("Multiply");

        public final String label;
        StatOperation(String label) { this.label = label; }
    }

    public enum AccuracyDuration {
        NEXT_ATTACK("Next attack"),
        DURATION("Rounds / ticks");

        public final String label;
        AccuracyDuration(String label) { this.label = label; }
    }

    public enum CeDrainMode {
        INSTANT("Instant"),
        OVER_TIME("Over time");

        public final String label;
        CeDrainMode(String label) { this.label = label; }
    }

    AbilityEffectType(String displayName, String description, AbilityEffectParameter... parameters) {
        this.displayName = displayName;
        this.description = description;
        this.parameters = EnumSet.noneOf(AbilityEffectParameter.class);
        Collections.addAll(this.parameters, parameters);
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public boolean uses(AbilityEffectParameter parameter) {
        return parameters.contains(parameter);
    }

    /** Whether this row uses a parameter after its selected sub-options are applied. */
    public boolean uses(AbilityEffectParameter parameter, AbilityEffectData effect) {
        if (!uses(parameter)) return false;
        if (effect == null) return true;
        if (isAmountModeEffect()) {
            ValueMode mode = selectedValueMode(effect);
            if (parameter == INTEGER) return mode == ValueMode.FLAT;
            if (parameter == DECIMAL) return mode == ValueMode.PERCENT;
        }
        if (this == TIMED_STAT_MODIFIER) {
            StatType selectedStat = selectedStatType(effect);
            StatOperation operation = selectedStatOperation(effect);
            ValueMode mode = selectedValueMode(effect);
            if (parameter == STAT) return selectedStat == StatType.CORE;
            if (parameter == BATTLE_STAT) return selectedStat == StatType.BATTLE;
            if (parameter == VALUE_MODE) return operation == StatOperation.CHANGE;
            if (parameter == INTEGER) {
                return selectedStat == StatType.CORE
                    && operation == StatOperation.CHANGE && mode == ValueMode.FLAT;
            }
            if (parameter == DECIMAL) return !uses(INTEGER, effect);
        }
        if ((this == APPLY_NEVER_MISS || this == APPLY_NEVER_HIT)
            && parameter == DURATION) {
            return selectedAccuracyDuration(effect) == AccuracyDuration.DURATION;
        }
        if (this == DRAIN_CE
            && (parameter == DURATION || parameter == CE_EFFICIENCY_SCALING)) {
            return selectedCeDrainMode(effect) == CeDrainMode.OVER_TIME;
        }
        return true;
    }

    public Set<AbilityEffectParameter> parameters() {
        return Collections.unmodifiableSet(parameters);
    }

    public static Set<StatusEffectType> supportedAutoStatuses() {
        return SUPPORTED_AUTO_STATUSES;
    }

    public static AbilityEffectType fromName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Effect type is required.");
        }
        return valueOf(name.trim().toUpperCase());
    }

    /** Create a new effect with useful, non-neutral defaults where possible. */
    public AbilityEffectData createDefault() {
        AbilityEffectData effect = new AbilityEffectData();
        reset(effect);
        return effect;
    }

    /** Create a move attachment using this same immutable effect primitive. */
    public com.jjktbf.model.move.MoveEffectData createDefaultMoveEffect() {
        com.jjktbf.model.move.MoveEffectData effect =
            new com.jjktbf.model.move.MoveEffectData();
        reset(effect);
        return effect;
    }

    /** Replace all effect parameters with this type's defaults. */
    public void reset(AbilityEffectData effect) {
        effect.type = name();
        effect.codedAbilityKey = null;
        effect.codedFeature = null;
        effect.codedAction = null;
        effect.codedTarget = null;
        effect.codedStackCount = null;
        effect.codedParameters = null;
        effect.stat = null;
        effect.intValue = null;
        effect.doubleValue = null;
        effect.valueMode = null;
        effect.statType = null;
        effect.statOperation = null;
        effect.accuracyDuration = null;
        effect.ceDrainMode = null;
        effect.minimumStatMultiplier = null;
        effect.maximumStatMultiplier = null;
        effect.moveTag = null;
        effect.moveId = null;
        effect.abilityId = null;
        effect.characterId = null;
        effect.transformationHpMode = null;
        effect.returnCondition = null;
        effect.stringValue = null;
        effect.target = null;
        effect.timing = null;
        effect.durationRounds = null;
        effect.durationTicks = null;
        effect.magnitude = null;
        effect.perTickRemovalChance = null;
        effect.uses = null;
        effect.refreshGroup = null;
        effect.resourceKey = null;
        effect.resourceLabel = null;
        effect.resourceCapacity = null;
        effect.resourceStartValue = null;
        effect.sourceResourceKey = null;
        effect.sourceResourceAmount = null;
        effect.targetResourceKey = null;
        effect.targetResourceAmount = null;
        effect.masteryProgression = null;
        effect.ceEfficiencyProgression = null;

        if (uses(STAT)) effect.stat = StatKey.VITALITY.fieldName;
        if (uses(TARGET)) effect.target = AbilityEffectTarget.SELF.name();
        if (uses(DURATION, effect)) effect.durationTicks = 0;

        switch (this) {
            case CODED -> {
                CodedAbilityRegistry.AbilityFeature feature =
                    CodedAbilityRegistry.abilityFeatures().get(0);
                effect.codedAbilityKey = feature.key();
                effect.codedFeature = feature.feature();
            }
            case CODED_MOVE_ACTION -> {
                CodedAbilityRegistry.EffectAction action =
                    CodedAbilityRegistry.effectActions().get(0);
                effect.codedAbilityKey = action.key();
                effect.codedAction = action.action();
                CodedAbilityRegistry.prepareMoveEffect(effect);
            }
            case STAT_ADD -> effect.intValue = 10;
            case STAT_MULTIPLY -> effect.doubleValue = 1.10;
            case STAT_DIVIDE -> effect.doubleValue = 2.0;
            case STAT_SET_VALUE -> effect.intValue = CharacterStats.BASELINE;
            case STAT_ALLOCATION_MINIMUM, STAT_ALLOCATION_MAXIMUM ->
                effect.intValue = CharacterStats.BASELINE;
            case STAT_BONUS_POINTS -> effect.intValue = 10;
            case CE_COST_ALTER -> {
                effect.doubleValue = 0.50;
                effect.intValue = 0;
            }
            case CE_COST_MULTIPLY, MOVE_ACCURACY_MULTIPLY,
                 OPPONENT_ACCURACY_MULTIPLY, DAMAGE_MULTIPLY, MOVE_BASE_POWER_MULTIPLY,
                 INCOMING_DAMAGE_MULTIPLY, MODIFY_DEFENSE -> effect.doubleValue = 1.10;
            case MOVE_BASE_POWER_SCALE_BY_STAT -> {
                effect.minimumStatMultiplier = 0.5;
                effect.maximumStatMultiplier = 2.0;
            }
            case DEFENSE_FROM_DURABILITY -> effect.doubleValue = 4.0 / 3.0;
            case MOVE_ACCURACY_ADD, OPPONENT_ACCURACY_ADD -> effect.intValue = 10;
            case NEVER_MISS, NEVER_HIT -> effect.intValue = 1;
            case BF_CHANCE_ADD -> effect.doubleValue = 0.05;
            case MODIFY_AP_BAR -> effect.intValue = 10;
            case AUTO_STATUS_APPLY -> {
                effect.stringValue = StatusEffectType.STRENGTH_INCREASE.name();
                effect.target = AbilityEffectTarget.SELF.name();
                effect.timing = AbilityEffectTiming.FIGHT_START.name();
                effect.durationRounds = -1;
                effect.magnitude = 10.0;
            }
            case LOCK_MOVE_TAG -> effect.moveTag = MoveTag.PHYSICAL.name();
            case SET_JUJUTSU_ART_SLOTS -> effect.intValue = 0;
            case COST_CE_PER_ROUND -> effect.intValue = 5;
            case MAX_ACTIVE_SUMMONS -> effect.intValue = 1;
            case SUMMON_CE_UPKEEP_PER_ACTIVE_TICK -> effect.doubleValue = 0.1;
            case HEAL_HP, RESTORE_CE, DRAIN_CE, DEAL_DIRECT_DAMAGE -> {
                effect.valueMode = ValueMode.FLAT.name();
                effect.intValue = 10;
                if (this == DRAIN_CE) effect.ceDrainMode = CeDrainMode.INSTANT.name();
            }
            case APPLY_STATUS -> {
                effect.stringValue = StatusEffectType.STRENGTH_DECREASE.name();
                effect.target = AbilityEffectTarget.ENEMY.name();
                effect.durationRounds = 1;
                effect.magnitude = 10.0;
            }
            case REMOVE_STATUS -> {
                effect.stringValue = StatusEffectType.STRENGTH_DECREASE.name();
                effect.target = AbilityEffectTarget.SELF.name();
            }
            case CLEAR_STATUSES, INSTANT_KILL, DESUMMON_TARGET_SHIKIGAMI ->
                effect.target = AbilityEffectTarget.ENEMY.name();
            case TIMED_STAT_MODIFIER -> {
                effect.statType = StatType.CORE.name();
                effect.statOperation = StatOperation.CHANGE.name();
                effect.valueMode = ValueMode.FLAT.name();
                effect.intValue = 10;
                timedDefaults(effect);
            }
            case TEMP_STAT_SET_VALUE -> {
                effect.intValue = CharacterStats.BASELINE;
                timedDefaults(effect);
            }
            case BATTLE_STAT_ODDS_MULTIPLY -> {
                effect.stringValue = BattleStatKey.BLACK_FLASH_CHANCE.name();
                effect.doubleValue = 2.0;
            }
            case APPLY_NEVER_MISS, APPLY_NEVER_HIT -> {
                effect.target = AbilityEffectTarget.SELF.name();
                effect.intValue = 1;
                effect.accuracyDuration = AccuracyDuration.NEXT_ATTACK.name();
                effect.durationRounds = null;
                effect.durationTicks = null;
            }
            case IGNORE_DAMAGE, SURVIVE_FATAL_DAMAGE,
                 GUARANTEE_NEXT_BLACK_FLASH, CANCEL_NEXT_MOVE -> {
                effect.target = AbilityEffectTarget.SELF.name();
                effect.uses = 1;
                effect.durationRounds = -1;
            }
            case DAMAGE_SHIELD -> {
                effect.target = AbilityEffectTarget.SELF.name();
                effect.intValue = 10;
                effect.durationRounds = -1;
            }
            case TEMP_LOCK_MOVE_TAG -> {
                effect.target = AbilityEffectTarget.ENEMY.name();
                effect.moveTag = MoveTag.CURSED_ENERGY.name();
                effect.durationRounds = 1;
            }
            case TAUNT -> {
                effect.target = AbilityEffectTarget.SELF.name();
                effect.durationRounds = 0;
                effect.durationTicks = 20;
            }
            case EXCHANGE_ATTACK_TARGETS -> {
                effect.durationRounds = 0;
                effect.durationTicks = 10;
                effect.uses = 1;
            }
            case DEFINE_BOUNDED_RESOURCE -> {
                effect.resourceKey = "RESOURCE";
                effect.resourceLabel = "Resource";
                effect.resourceCapacity = 3;
                effect.resourceStartValue = 0;
            }
            case TRANSACT_BOUNDED_RESOURCE -> {
                effect.target = AbilityEffectTarget.SELF.name();
                effect.sourceResourceAmount = 0;
                effect.targetResourceKey = "RESOURCE";
                effect.targetResourceAmount = 1;
            }
            case SUMMON_CHARACTER -> {
                // No target needed — the summon joins the owner's team.
                effect.characterId = null;
            }
            case TRANSFORM_CHARACTER -> {
                effect.characterId = null;
                effect.target = AbilityEffectTarget.SELF.name();
                effect.transformationHpMode = TransformationHpMode.FULL.name();
            }
            default -> { }
        }
    }

    /** Fill missing relevant values and discard fields belonging to another type. */
    public void prepare(AbilityEffectData effect) {
        AbilityEffectData defaults = createDefault();
        effect.type = name();
        if (uses(VALUE_MODE) && isBlank(effect.valueMode)) {
            effect.valueMode = defaults.valueMode;
        }
        if (uses(STAT_TYPE) && isBlank(effect.statType)) effect.statType = defaults.statType;
        if (uses(STAT_OPERATION) && isBlank(effect.statOperation)) {
            effect.statOperation = defaults.statOperation;
        }
        if (uses(ACCURACY_DURATION) && isBlank(effect.accuracyDuration)) {
            effect.accuracyDuration = defaults.accuracyDuration;
        }
        if (uses(CE_DRAIN_MODE) && isBlank(effect.ceDrainMode)) {
            effect.ceDrainMode = defaults.ceDrainMode;
        }
        if (uses(STATUS_TYPE) && uses(MAGNITUDE) && !isBlank(effect.stringValue)) {
            String storedType = effect.stringValue;
            try {
                StatusEffectType status = StatusEffectType.fromName(
                    storedType, effect.magnitude != null ? effect.magnitude : 0.0);
                effect.stringValue = status.name();
                if (effect.magnitude != null) {
                    effect.magnitude = StatusEffectType.normalizeStoredMagnitude(
                        storedType, effect.magnitude);
                }
            } catch (IllegalArgumentException ignored) { }
        }
        clearUnusedFields(effect);
        if (uses(CODED_FEATURE) && (isBlank(effect.codedAbilityKey)
            || isBlank(effect.codedFeature))) {
            effect.codedAbilityKey = defaults.codedAbilityKey;
            effect.codedFeature = defaults.codedFeature;
        }
        if (uses(CODED_FEATURE)) CodedAbilityRegistry.prepareAbilityParameters(effect);
        if (uses(CODED_ACTION) && (isBlank(effect.codedAbilityKey)
            || isBlank(effect.codedAction))) {
            effect.codedAbilityKey = defaults.codedAbilityKey;
            effect.codedAction = defaults.codedAction;
        }
        if (uses(CODED_ACTION)) CodedAbilityRegistry.prepareMoveEffect(effect);
        if (uses(STAT, effect) && isBlank(effect.stat)) effect.stat = defaults.stat;
        if (uses(INTEGER, effect) && effect.intValue == null) {
            effect.intValue = defaultInteger(effect);
        }
        if (uses(DECIMAL, effect) && effect.doubleValue == null) {
            effect.doubleValue = defaultDecimal(effect);
        }
        if (uses(STAT_MULTIPLIER_CURVE)) {
            if (effect.minimumStatMultiplier == null) {
                effect.minimumStatMultiplier = defaults.minimumStatMultiplier;
            }
            if (effect.maximumStatMultiplier == null) {
                effect.maximumStatMultiplier = defaults.maximumStatMultiplier;
            }
        }
        if (uses(MOVE_SCOPE) && (this == LOCK_MOVE_TAG || this == TEMP_LOCK_MOVE_TAG)
            && isBlank(effect.moveTag)) {
            effect.moveTag = defaults.moveTag;
        }
        if ((uses(TECHNIQUE) || uses(STATUS_TYPE)) && isBlank(effect.stringValue)) {
            effect.stringValue = defaults.stringValue;
        }
        if (uses(TARGET) && isBlank(effect.target)) effect.target = defaults.target;
        if (uses(TRANSFORMATION_HP) && isBlank(effect.transformationHpMode)) {
            effect.transformationHpMode = defaults.transformationHpMode;
        }
        if (uses(TIMING) && isBlank(effect.timing)) effect.timing = defaults.timing;
        if (uses(DURATION, effect) && effect.durationRounds == null) {
            effect.durationRounds = defaults.durationRounds != null ? defaults.durationRounds : 1;
        }
        if (uses(DURATION, effect) && effect.durationTicks == null) effect.durationTicks = 0;
        if (uses(MAGNITUDE) && effect.magnitude == null) effect.magnitude = defaults.magnitude;
        if (uses(PER_TICK_REMOVAL_CHANCE) && effect.perTickRemovalChance == null) {
            effect.perTickRemovalChance = defaultPerTickRemovalChance(effect.stringValue);
        }
        if (uses(STATUS_TYPE) && uses(MAGNITUDE)) {
            try {
                if (!StatusEffectType.fromName(effect.stringValue).usesMagnitude()) {
                    effect.magnitude = 0.0;
                }
            } catch (IllegalArgumentException ignored) { }
        }
        if (uses(USES) && effect.uses == null) effect.uses = defaults.uses;
        if (uses(BATTLE_STAT, effect) && isBlank(effect.stringValue)) {
            effect.stringValue = BattleStatKey.MAX_AP.name();
        }
        if (uses(RESOURCE_KEY) && isBlank(effect.resourceKey)) effect.resourceKey = defaults.resourceKey;
        if (uses(RESOURCE_LABEL) && isBlank(effect.resourceLabel)) effect.resourceLabel = defaults.resourceLabel;
        if (uses(RESOURCE_CAPACITY) && effect.resourceCapacity == null) {
            effect.resourceCapacity = defaults.resourceCapacity;
        }
        if (uses(RESOURCE_START_VALUE) && effect.resourceStartValue == null) {
            effect.resourceStartValue = defaults.resourceStartValue;
        }
        if (uses(SOURCE_RESOURCE_AMOUNT) && effect.sourceResourceAmount == null) {
            effect.sourceResourceAmount = defaults.sourceResourceAmount;
        }
        if (uses(TARGET_RESOURCE_AMOUNT) && effect.targetResourceAmount == null) {
            effect.targetResourceAmount = defaults.targetResourceAmount;
        }
    }

    /** Remove stale values so persisted JSON contains only parameters this type reads. */
    public void clearUnusedFields(AbilityEffectData effect) {
        if (!uses(CODED_FEATURE)) {
            effect.codedFeature = null;
            if (!uses(CODED_ACTION)) effect.codedAbilityKey = null;
        }
        if (!uses(CODED_ACTION)) {
            effect.codedAction = null;
            effect.codedTarget = null;
            effect.codedStackCount = null;
            if (!uses(CODED_FEATURE)) effect.codedParameters = null;
        }
        if (!uses(STAT, effect)) effect.stat = null;
        if (!uses(INTEGER, effect)) effect.intValue = null;
        if (!uses(DECIMAL, effect)) effect.doubleValue = null;
        if (!uses(VALUE_MODE, effect)) effect.valueMode = null;
        if (!uses(STAT_TYPE)) effect.statType = null;
        if (!uses(STAT_OPERATION)) effect.statOperation = null;
        if (!uses(ACCURACY_DURATION)) effect.accuracyDuration = null;
        if (!uses(CE_DRAIN_MODE)) effect.ceDrainMode = null;
        if (!uses(STAT_MULTIPLIER_CURVE)) {
            effect.minimumStatMultiplier = null;
            effect.maximumStatMultiplier = null;
        }
        if (!uses(MOVE_SCOPE)) effect.moveTag = null;
        if (!uses(MOVE_ID)) effect.moveId = null;
        if (!uses(ABILITY_ID)) effect.abilityId = null;
        if (!uses(CHARACTER_ID)) effect.characterId = null;
        if (!uses(TRANSFORMATION_HP)) effect.transformationHpMode = null;
        if (!uses(RETURN_CONDITION)) effect.returnCondition = null;
        if (!uses(TECHNIQUE) && !uses(STATUS_TYPE) && !uses(BATTLE_STAT, effect)) effect.stringValue = null;
        if (!uses(TARGET)) effect.target = null;
        if (!uses(TIMING)) effect.timing = null;
        boolean migratedAccuracyUses = (this == APPLY_NEVER_MISS || this == APPLY_NEVER_HIT)
            && selectedAccuracyDuration(effect) == AccuracyDuration.NEXT_ATTACK
            && effect.uses != null && effect.uses > 0;
        if (!uses(DURATION, effect) && !migratedAccuracyUses) {
            effect.durationRounds = null;
            effect.durationTicks = null;
        }
        if (!uses(MAGNITUDE)) effect.magnitude = null;
        if (!uses(PER_TICK_REMOVAL_CHANCE)) effect.perTickRemovalChance = null;
        if (!uses(USES) && !migratedAccuracyUses) effect.uses = null;
        if (!uses(REFRESH_GROUP)) effect.refreshGroup = null;
        if (!uses(RESOURCE_KEY)) effect.resourceKey = null;
        if (!uses(RESOURCE_LABEL)) effect.resourceLabel = null;
        if (!uses(RESOURCE_CAPACITY)) effect.resourceCapacity = null;
        if (!uses(RESOURCE_START_VALUE)) effect.resourceStartValue = null;
        if (!uses(SOURCE_RESOURCE)) effect.sourceResourceKey = null;
        if (!uses(SOURCE_RESOURCE_AMOUNT)) effect.sourceResourceAmount = null;
        if (!uses(TARGET_RESOURCE)) effect.targetResourceKey = null;
        if (!uses(TARGET_RESOURCE_AMOUNT)) effect.targetResourceAmount = null;
        if (!uses(BATTLE_STAT, effect) && !uses(TECHNIQUE) && !uses(STATUS_TYPE)) effect.stringValue = null;
        Set<String> allowedProgressions = masteryProgressionFields(effect);
        if (effect.masteryProgression != null) {
            effect.masteryProgression = effect.masteryProgression.entrySet().stream()
                .filter(entry -> allowedProgressions.contains(entry.getKey()))
                .collect(java.util.stream.Collectors.toMap(
                    java.util.Map.Entry::getKey,
                    java.util.Map.Entry::getValue,
                    (left, right) -> left,
                    java.util.LinkedHashMap::new));
            if (effect.masteryProgression.isEmpty()) effect.masteryProgression = null;
        }
        Set<String> allowedEfficiencyProgressions = ceEfficiencyProgressionFields(effect);
        if (effect.ceEfficiencyProgression != null) {
            effect.ceEfficiencyProgression = effect.ceEfficiencyProgression.entrySet().stream()
                .filter(entry -> allowedEfficiencyProgressions.contains(entry.getKey()))
                .collect(java.util.stream.Collectors.toMap(
                    java.util.Map.Entry::getKey,
                    java.util.Map.Entry::getValue,
                    (left, right) -> left,
                    java.util.LinkedHashMap::new));
            if (effect.ceEfficiencyProgression.isEmpty()) effect.ceEfficiencyProgression = null;
        }
    }

    /** Return a user-facing validation error, or {@code null} when valid. */
    public String validationError(AbilityEffectData effect) {
        if (effect == null) return "Effect is missing.";

        if (uses(VALUE_MODE, effect)) {
            try { valueMode(effect); }
            catch (Exception ex) { return "Choose flat amount or maximum percentage."; }
        }
        if (uses(STAT_TYPE)) {
            try { statType(effect); }
            catch (Exception ex) { return "Choose a core or battle stat."; }
        }
        if (uses(STAT_OPERATION)) {
            try { statOperation(effect); }
            catch (Exception ex) { return "Choose change or multiply."; }
        }
        if (uses(ACCURACY_DURATION)) {
            try { accuracyDuration(effect); }
            catch (Exception ex) { return "Choose next attack or a duration."; }
        }
        if (uses(CE_DRAIN_MODE)) {
            try { ceDrainMode(effect); }
            catch (Exception ex) { return "Choose instant or over-time drainage."; }
        }
        if (uses(STAT, effect)) {
            try {
                StatKey.fromString(effect.stat);
            } catch (Exception ex) {
                return "Choose a valid stat.";
            }
        }
        if (uses(INTEGER, effect) && effect.intValue == null) return "Enter an integer value.";
        if (uses(DECIMAL, effect) && !isFinite(effect.doubleValue)) return "Enter a valid decimal value.";
        if (uses(STAT_MULTIPLIER_CURVE)
            && (!isFinite(effect.minimumStatMultiplier)
                || !isFinite(effect.maximumStatMultiplier))) {
            return "Enter valid minimum- and maximum-stat multipliers.";
        }
        if (uses(MOVE_SCOPE) && !isBlank(effect.moveTag)) {
            try {
                MoveTag.valueOf(effect.moveTag);
            } catch (Exception ex) {
                return "Choose a valid move tag.";
            }
        }
        if ((this == LOCK_MOVE_TAG || this == TEMP_LOCK_MOVE_TAG) && isBlank(effect.moveTag)) {
            return "Choose a move tag to lock.";
        }
        if (uses(MOVE_ID) && isBlank(effect.moveId)) return "Choose a move.";
        if (uses(ABILITY_ID) && isBlank(effect.abilityId)) return "Choose an ability to grant.";
        if (uses(CHARACTER_ID) && isBlank(effect.characterId)) {
            return this == SUMMON_CHARACTER
                ? "Choose a shikigami to summon."
                : this == TRANSFORM_CHARACTER
                    ? "Choose a character form." : "Choose a shikigami.";
        }
        if (uses(TRANSFORMATION_HP)) {
            try {
                TransformationHpMode.fromName(effect.transformationHpMode);
            } catch (Exception ex) {
                return "Choose how HP is set for the new form.";
            }
        }
        if (uses(RETURN_CONDITION) && effect.returnCondition != null) {
            String conditionError = AbilityConditionType.validationError(effect.returnCondition);
            if (conditionError != null) return "Return condition: " + conditionError;
        }
        if (uses(TECHNIQUE) && isBlank(effect.stringValue)) return "Choose a technique.";

        StatusEffectType status = null;
        if (uses(STATUS_TYPE)) {
            try {
                status = StatusEffectType.fromName(effect.stringValue);
            } catch (Exception ex) {
                return "Choose a valid status.";
            }
        }
        if (uses(TARGET)) {
            try {
                AbilityEffectTarget.valueOf(effect.target);
            } catch (Exception ex) {
                return "Choose a valid effect target.";
            }
        }
        if (uses(TIMING)) {
            try {
                AbilityEffectTiming.valueOf(effect.timing);
            } catch (Exception ex) {
                return "Choose when the status should be applied.";
            }
        }
        if (uses(DURATION, effect)) {
            if (effect.durationRounds == null) return "Enter a round duration.";
            int ticks = effect.durationTicks != null ? effect.durationTicks : 0;
            try {
                StatusEffect.validateDuration(status, effect.durationRounds, ticks);
            } catch (IllegalArgumentException ignored) {
                return status != null && status.requiresTickDuration()
                    ? "Stagger must use 0 rounds and at least 1 AP tick."
                    : status != null && status.requiresRoundDuration()
                        ? "Poison must use a positive round duration or be permanent, with 0 AP ticks."
                    : "Use -1 rounds and 0 ticks for permanent, or enter at least one round or tick.";
            }
            if (AbilityEffectTiming.ROUND_START.name().equals(effect.timing)
                && (status == null || !status.requiresTickDuration())
                && (effect.durationRounds != 1 || ticks != 0)) {
                return "A ROUND_START status must last exactly 1 round and 0 ticks so it refreshes without stacking.";
            }
        }
        if (uses(MAGNITUDE) && (!isFinite(effect.magnitude) || effect.magnitude < 0)) {
            return "Enter a non-negative status amount.";
        }
        if (uses(PER_TICK_REMOVAL_CHANCE) && effect.perTickRemovalChance != null
            && (!isFinite(effect.perTickRemovalChance)
                || effect.perTickRemovalChance < 0.0 || effect.perTickRemovalChance > 1.0)) {
            return "Per-tick removal chance must be between 0% and 100%.";
        }
        if (uses(USES) && (effect.uses == null || (effect.uses != -1 && effect.uses < 1))) {
            return "Uses must be -1 (unlimited) or at least 1.";
        }
        if (uses(BATTLE_STAT, effect)) {
            try {
                BattleStatKey stat = BattleStatKey.fromString(effect.stringValue);
                if (this == BATTLE_STAT_ODDS_MULTIPLY && !stat.isProbability()) {
                    return "Choose a probability battle stat.";
                }
            }
            catch (Exception ex) { return "Choose a valid battle stat."; }
        }
        if (uses(RESOURCE_KEY) && isBlank(effect.resourceKey)) return "Enter a resource key.";
        if (uses(RESOURCE_LABEL) && isBlank(effect.resourceLabel)) return "Enter a resource label.";
        if (uses(RESOURCE_CAPACITY)
            && (effect.resourceCapacity == null || effect.resourceCapacity < 1)) {
            return "Resource capacity must be at least 1.";
        }
        if (uses(RESOURCE_START_VALUE)
            && (effect.resourceStartValue == null || effect.resourceStartValue < 0
                || (effect.resourceCapacity != null
                    && effect.resourceStartValue > effect.resourceCapacity))) {
            return "Resource start value must be between 0 and its capacity.";
        }
        if (uses(SOURCE_RESOURCE_AMOUNT)
            && (effect.sourceResourceAmount == null || effect.sourceResourceAmount < 0)) {
            return "Source resource amount cannot be negative.";
        }
        if (uses(TARGET_RESOURCE_AMOUNT)
            && (effect.targetResourceAmount == null || effect.targetResourceAmount < 0)) {
            return "Target resource amount cannot be negative.";
        }
        if (this == TRANSACT_BOUNDED_RESOURCE) {
            boolean hasSource = !isBlank(effect.sourceResourceKey)
                && effect.sourceResourceAmount != null && effect.sourceResourceAmount > 0;
            boolean hasTarget = !isBlank(effect.targetResourceKey)
                && effect.targetResourceAmount != null && effect.targetResourceAmount > 0;
            if (!hasSource && !hasTarget) return "Configure a resource gain or spend.";
            if (effect.sourceResourceAmount != null && effect.sourceResourceAmount > 0
                && isBlank(effect.sourceResourceKey)) return "Enter the source resource key.";
            if (effect.targetResourceAmount != null && effect.targetResourceAmount > 0
                && isBlank(effect.targetResourceKey)) return "Enter the target resource key.";
        }
        if (uses(CODED_FEATURE) && !CodedAbilityRegistry.supportsAbilityEffect(
            effect.codedAbilityKey, effect.codedFeature)) {
            return "Choose a supported coded effect.";
        }
        if (uses(CODED_FEATURE)) {
            String codedParameterError =
                CodedAbilityRegistry.abilityParameterValidationError(effect);
            if (codedParameterError != null) return codedParameterError;
        }
        if (uses(CODED_ACTION) && !CodedAbilityRegistry.supportsEffect(
            effect.codedAbilityKey, effect.codedAction,
            effect.codedTarget, effect.codedStackCount)) {
            return "Choose a supported coded move effect.";
        }
        if (uses(CODED_ACTION)) {
            String codedParameterError = CodedAbilityRegistry.effectParameterValidationError(
                effect.codedAbilityKey, effect.codedAction,
                effect.codedTarget, effect.codedParameters);
            if (codedParameterError != null) return codedParameterError;
        }

        String literalError = switch (this) {
            case STAT_ADD, STAT_BONUS_POINTS,
                 MOVE_ACCURACY_ADD, OPPONENT_ACCURACY_ADD,
                   MODIFY_AP_BAR -> effect.intValue == 0 ? "Enter a non-zero amount." : null;
            case STAT_SET_VALUE, TEMP_STAT_SET_VALUE -> effect.intValue < 0 ? "Stat value cannot be negative." : null;
            case STAT_ALLOCATION_MINIMUM, STAT_ALLOCATION_MAXIMUM ->
                effect.intValue < CharacterStats.MIN_STAT || effect.intValue > CharacterStats.MAX_STAT
                    ? "Allocation bound must be between " + CharacterStats.MIN_STAT
                        + " and " + CharacterStats.MAX_STAT + "."
                    : null;
            case STAT_MULTIPLY, CE_COST_MULTIPLY, MOVE_ACCURACY_MULTIPLY,
                  OPPONENT_ACCURACY_MULTIPLY, DAMAGE_MULTIPLY, MOVE_BASE_POWER_MULTIPLY,
                  INCOMING_DAMAGE_MULTIPLY, MODIFY_DEFENSE, DEFENSE_FROM_DURABILITY,
                  BATTLE_STAT_ODDS_MULTIPLY ->
                effect.doubleValue <= 0 || effect.doubleValue == 1.0
                    ? "Enter a positive multiplier other than 1.0." : null;
            case CE_COST_ALTER -> effect.doubleValue < 0
                || (effect.doubleValue == 1.0 && effect.intValue == 0)
                    ? "Use a non-negative multiplier or a non-zero CE change."
                    : null;
            case STAT_DIVIDE -> effect.doubleValue <= 0 || effect.doubleValue == 1.0
                ? "Enter a positive divisor other than 1.0." : null;
            case BF_CHANCE_ADD -> effect.doubleValue == 0.0
                || effect.doubleValue < -1.0 || effect.doubleValue > 1.0
                    ? "Chance change must be non-zero and between -100% and 100%." : null;
            case AUTO_STATUS_APPLY -> status != null && status.usesMagnitude() && effect.magnitude == 0.0
                ? "Enter a non-zero status magnitude." : null;
            case COST_CE_PER_ROUND -> effect.intValue <= 0
                ? "Round-start CE cost must be greater than 0." : null;
            case MAX_ACTIVE_SUMMONS -> effect.intValue <= 0
                ? "Maximum active summons must be greater than 0." : null;
            case SUMMON_CE_UPKEEP_PER_ACTIVE_TICK -> effect.doubleValue <= 0
                ? "Summon upkeep must be greater than 0." : null;
            case SET_JUJUTSU_ART_SLOTS -> effect.intValue < 0
                || effect.intValue > CombatStats.MAX_ART_SLOTS
                ? "Jujutsu Art slots must be between 0 and "
                    + CombatStats.MAX_ART_SLOTS + "." : null;
            case NEVER_MISS, NEVER_HIT, APPLY_NEVER_HIT ->
                effect.intValue < 1 || effect.intValue > 5
                ? "Accuracy priority tier must be between 1 and 5." : null;
            case APPLY_NEVER_MISS -> effect.intValue < 0 || effect.intValue > 5
                ? "Never Miss tier must be between 0 and 5." : null;
            case HEAL_HP, RESTORE_CE, DRAIN_CE, DEAL_DIRECT_DAMAGE ->
                amountValidationError(effect);
            case DAMAGE_SHIELD -> effect.intValue <= 0
                ? "Amount must be greater than 0." : null;
            case TIMED_STAT_MODIFIER -> timedStatValidationError(effect);
            case MOVE_BASE_POWER_SCALE_BY_STAT ->
                effect.minimumStatMultiplier <= 0.0 || effect.maximumStatMultiplier <= 0.0
                    ? "Stat-scaled multipliers must be greater than 0."
                    : effect.minimumStatMultiplier == 1.0
                        && effect.maximumStatMultiplier == 1.0
                        ? "At least one stat-scaled multiplier must differ from 1.0." : null;
            default -> null;
        };
        if (literalError != null) return literalError;

        String progressionError = TechniqueMasteryProgressions.validationError(
            effect.masteryProgression, masteryProgressionFields(effect));
        if (progressionError != null) return progressionError;
        if (effect.masteryProgression != null && !effect.masteryProgression.isEmpty()) {
            for (int mastery = 0; mastery <= CharacterStats.MAX_STAT; mastery++) {
                AbilityEffectData resolved;
                try {
                    resolved = TechniqueMasteryResolver.resolve(effect, mastery);
                } catch (RuntimeException exception) {
                    return "Invalid mastery progression at CTM " + mastery + ": "
                        + exception.getMessage();
                }
                resolved.masteryProgression = null;
                resolved.ceEfficiencyProgression = null;
                String error = validationError(resolved);
                if (error != null) {
                    return "At CTM " + mastery + ": " + error;
                }
            }
        }
        String efficiencyProgressionError = TechniqueMasteryProgressions.validationError(
            effect.ceEfficiencyProgression, ceEfficiencyProgressionFields(effect),
            TechniqueMasteryProgressions.CE_EFFICIENCY_VARIABLE,
            "Cursed Energy Efficiency");
        if (efficiencyProgressionError != null) return efficiencyProgressionError;
        if (effect.ceEfficiencyProgression != null && !effect.ceEfficiencyProgression.isEmpty()) {
            Set<String> overlappingFields = new LinkedHashSet<>(
                ceEfficiencyProgressionFields(effect));
            if (effect.masteryProgression != null) {
                overlappingFields.retainAll(effect.masteryProgression.keySet());
            } else {
                overlappingFields.clear();
            }
            if (!overlappingFields.isEmpty()) {
                return "Scale the drain value with either CTM or Cursed Energy Efficiency, not both.";
            }
            for (int efficiency = 0; efficiency <= CharacterStats.MAX_STAT; efficiency++) {
                AbilityEffectData resolved;
                try {
                    resolved = TechniqueMasteryResolver.resolve(
                        effect, effect.ceEfficiencyProgression, efficiency,
                        TechniqueMasteryProgressions.CE_EFFICIENCY_VARIABLE);
                } catch (RuntimeException exception) {
                    return "Invalid Cursed Energy Efficiency progression at " + efficiency + ": "
                        + exception.getMessage();
                }
                resolved.masteryProgression = null;
                resolved.ceEfficiencyProgression = null;
                String error = validationError(resolved);
                if (error != null) {
                    return "At Cursed Energy Efficiency " + efficiency + ": " + error;
                }
            }
        }
        return null;
    }

    /** Numeric fields that may derive their value from CTM for this effect row. */
    public Set<String> masteryProgressionFields(AbilityEffectData effect) {
        if (this == STAT_ALLOCATION_MINIMUM || this == STAT_ALLOCATION_MAXIMUM
            || this == STAT_BONUS_POINTS || this == SET_JUJUTSU_ART_SLOTS) return Set.of();
        Set<String> fields = new LinkedHashSet<>();
        if (uses(INTEGER, effect)) fields.add(TechniqueMasteryProgressions.INT_VALUE);
        if (uses(DECIMAL, effect)) fields.add(TechniqueMasteryProgressions.DOUBLE_VALUE);
        boolean migratedAccuracyUses = (this == APPLY_NEVER_MISS || this == APPLY_NEVER_HIT)
            && selectedAccuracyDuration(effect) == AccuracyDuration.NEXT_ATTACK
            && effect != null && effect.uses != null && effect.uses > 0;
        if (uses(DURATION, effect) || migratedAccuracyUses) {
            fields.add(TechniqueMasteryProgressions.DURATION_ROUNDS);
            fields.add(TechniqueMasteryProgressions.DURATION_TICKS);
        }
        if (uses(MAGNITUDE)) fields.add(TechniqueMasteryProgressions.MAGNITUDE);
        if (uses(PER_TICK_REMOVAL_CHANCE)) {
            fields.add(TechniqueMasteryProgressions.PER_TICK_REMOVAL_CHANCE);
        }
        if (uses(USES) || migratedAccuracyUses) {
            fields.add(TechniqueMasteryProgressions.USES);
        }
        if (uses(CODED_FEATURE) && effect != null && effect.codedParameters != null) {
            fields.addAll(effect.codedParameters.keySet());
        }
        if (uses(CODED_ACTION) && effect != null) {
            if (effect.codedStackCount != null) {
                fields.add(TechniqueMasteryProgressions.CODED_STACK_COUNT);
            }
            if (effect.codedParameters != null) fields.addAll(effect.codedParameters.keySet());
        }
        if (uses(RESOURCE_CAPACITY)) {
            fields.add(TechniqueMasteryProgressions.RESOURCE_CAPACITY);
        }
        if (uses(RESOURCE_START_VALUE)) {
            fields.add(TechniqueMasteryProgressions.RESOURCE_START_VALUE);
        }
        if (uses(SOURCE_RESOURCE_AMOUNT)) {
            fields.add(TechniqueMasteryProgressions.SOURCE_RESOURCE_AMOUNT);
        }
        if (uses(TARGET_RESOURCE_AMOUNT)) {
            fields.add(TechniqueMasteryProgressions.TARGET_RESOURCE_AMOUNT);
        }
        return Collections.unmodifiableSet(fields);
    }

    /** Numeric fields that may derive their value from Cursed Energy Efficiency. */
    public Set<String> ceEfficiencyProgressionFields(AbilityEffectData effect) {
        if (this != DRAIN_CE || selectedCeDrainMode(effect) != CeDrainMode.OVER_TIME) {
            return Set.of();
        }
        return uses(INTEGER, effect)
            ? Set.of(TechniqueMasteryProgressions.INT_VALUE)
            : Set.of(TechniqueMasteryProgressions.DOUBLE_VALUE);
    }

    /** True when an effect needs an active ability condition to run at battle time. */
    public boolean requiresActivation() {
        return ACTIVATION_REQUIRED.contains(this);
    }

    /** True for effects resolved while a passive ability is assigned. */
    public boolean isPassiveOnly() {
        return switch (this) {
            case STAT_ALLOCATION_MINIMUM, STAT_ALLOCATION_MAXIMUM, STAT_BONUS_POINTS,
                  POISON_IMMUNITY, SOUL_AWARE_ATTACKS,
                   GRANT_MOVE, GRANT_ABILITY, UNLOCK_MOVE,
                   UNLOCK_TECHNIQUE, AUTO_STATUS_APPLY, DEFENSE_FROM_DURABILITY,
                   SET_JUJUTSU_ART_SLOTS, MAX_ACTIVE_SUMMONS,
                   SUMMON_CE_UPKEEP_PER_ACTIVE_TICK, NEVER_MISS, NEVER_HIT,
                   BATTLE_STAT_ODDS_MULTIPLY, DEFINE_BOUNDED_RESOURCE -> true;
            default -> false;
        };
    }

    /** Effect primitives that may be activated from a move effect row. */
    public boolean isMoveEffect() {
        return requiresActivation() || isAccuracyPriority() || isMoveAvailabilityConstraint();
    }

    /** Move constraints queried before placement and again when the move fires. */
    public boolean isMoveAvailabilityConstraint() {
        return this == MOVE_UNAVAILABLE_WHILE_OWNED_SUMMON_ACTIVE;
    }

    /** Accuracy-priority primitives are queried during hit resolution rather than fired. */
    public boolean isAccuracyPriority() {
        return this == NEVER_MISS || this == NEVER_HIT;
    }

    public boolean isMoveOnly() {
        return this == CODED_MOVE_ACTION || this == EXCHANGE_ATTACK_TARGETS
            || isMoveAvailabilityConstraint();
    }

    private static void timedDefaults(AbilityEffectData effect) {
        effect.target = AbilityEffectTarget.SELF.name();
        effect.durationRounds = 1;
    }

    public boolean isAmountModeEffect() {
        return this == HEAL_HP || this == RESTORE_CE || this == DRAIN_CE
            || this == DEAL_DIRECT_DAMAGE;
    }

    public static ValueMode valueMode(AbilityEffectData effect) {
        return effect == null || isBlank(effect.valueMode)
            ? ValueMode.FLAT : ValueMode.valueOf(effect.valueMode);
    }

    public static StatType statType(AbilityEffectData effect) {
        return effect == null || isBlank(effect.statType)
            ? StatType.CORE : StatType.valueOf(effect.statType);
    }

    public static StatOperation statOperation(AbilityEffectData effect) {
        return effect == null || isBlank(effect.statOperation)
            ? StatOperation.CHANGE : StatOperation.valueOf(effect.statOperation);
    }

    public static AccuracyDuration accuracyDuration(AbilityEffectData effect) {
        return effect == null || isBlank(effect.accuracyDuration)
            ? AccuracyDuration.NEXT_ATTACK
            : AccuracyDuration.valueOf(effect.accuracyDuration);
    }

    public static CeDrainMode ceDrainMode(AbilityEffectData effect) {
        return effect == null || isBlank(effect.ceDrainMode)
            ? CeDrainMode.INSTANT : CeDrainMode.valueOf(effect.ceDrainMode);
    }

    /** Selector values for resilient editor rendering before validation. */
    public static ValueMode selectedValueMode(AbilityEffectData effect) {
        return selectedOrDefault(
            effect == null ? null : effect.valueMode, ValueMode.FLAT, ValueMode.class);
    }

    public static StatType selectedStatType(AbilityEffectData effect) {
        return selectedOrDefault(
            effect == null ? null : effect.statType, StatType.CORE, StatType.class);
    }

    public static StatOperation selectedStatOperation(AbilityEffectData effect) {
        return selectedOrDefault(effect == null ? null : effect.statOperation,
            StatOperation.CHANGE, StatOperation.class);
    }

    public static AccuracyDuration selectedAccuracyDuration(AbilityEffectData effect) {
        return selectedOrDefault(effect == null ? null : effect.accuracyDuration,
            AccuracyDuration.NEXT_ATTACK, AccuracyDuration.class);
    }

    public static CeDrainMode selectedCeDrainMode(AbilityEffectData effect) {
        return selectedOrDefault(effect == null ? null : effect.ceDrainMode,
            CeDrainMode.INSTANT, CeDrainMode.class);
    }

    private static <T extends Enum<T>> T selectedOrDefault(
        String value,
        T defaultValue,
        Class<T> enumType
    ) {
        if (isBlank(value)) return defaultValue;
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException ex) {
            return defaultValue;
        }
    }

    /** True when the selected decimal is authored and displayed as a percentage. */
    public boolean isPercentageValue(AbilityEffectData effect) {
        if (isAmountModeEffect()) return valueMode(effect) == ValueMode.PERCENT;
        return this == TIMED_STAT_MODIFIER
            && statOperation(effect) == StatOperation.CHANGE
            && valueMode(effect) == ValueMode.PERCENT;
    }

    /** Battle-stat flat changes retain decimal points in CTM progression. */
    public boolean storesDecimalAsPoints(AbilityEffectData effect) {
        return this == TIMED_STAT_MODIFIER
            && statType(effect) == StatType.BATTLE
            && statOperation(effect) == StatOperation.CHANGE
            && valueMode(effect) == ValueMode.FLAT;
    }

    private Integer defaultInteger(AbilityEffectData effect) {
        if (this == TIMED_STAT_MODIFIER) return 10;
        return createDefault().intValue;
    }

    private Double defaultDecimal(AbilityEffectData effect) {
        if (isAmountModeEffect()) return 0.10;
        if (this == TIMED_STAT_MODIFIER) {
            if (statOperation(effect) == StatOperation.MULTIPLY) return 1.10;
            return valueMode(effect) == ValueMode.PERCENT ? 0.20 : 10.0;
        }
        return createDefault().doubleValue;
    }

    private String amountValidationError(AbilityEffectData effect) {
        if (valueMode(effect) == ValueMode.FLAT) {
            return effect.intValue <= 0 ? "Amount must be greater than 0." : null;
        }
        return effect.doubleValue <= 0 || effect.doubleValue > 1
            ? "Percentage must be greater than 0% and no more than 100%." : null;
    }

    private static String timedStatValidationError(AbilityEffectData effect) {
        if (statOperation(effect) == StatOperation.MULTIPLY) {
            return effect.doubleValue <= 0 || effect.doubleValue == 1.0
                ? "Enter a positive multiplier other than 1.0." : null;
        }
        if (valueMode(effect) == ValueMode.PERCENT) {
            return effect.doubleValue == 0 || effect.doubleValue <= -1.0
                ? "Enter a non-zero percentage greater than -100%." : null;
        }
        double change = statType(effect) == StatType.CORE
            ? effect.intValue : effect.doubleValue;
        return change == 0.0 ? "Enter a non-zero amount." : null;
    }

    private static boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private static double defaultPerTickRemovalChance(String statusName) {
        try {
            return StatusEffectType.fromName(statusName).defaultPerTickRemovalChance();
        } catch (IllegalArgumentException ignored) {
            return 0.0;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
