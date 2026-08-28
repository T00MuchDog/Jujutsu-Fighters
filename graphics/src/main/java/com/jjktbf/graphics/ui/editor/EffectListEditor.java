package com.jjktbf.graphics.ui.editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.jjktbf.graphics.audio.SoundCue;
import com.jjktbf.graphics.ui.ContentSizedDialog;
import com.jjktbf.graphics.ui.DynamicSelectBox;
import com.jjktbf.graphics.ui.profile.UiProfile;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityConditionType;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectParameter;
import com.jjktbf.model.character.AbilityEffectTarget;
import com.jjktbf.model.character.AbilityEffectTiming;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.BattleStatKey;
import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.character.CharacterType;
import com.jjktbf.model.character.StatKey;
import com.jjktbf.model.character.TransformationHpMode;
import com.jjktbf.model.character.coded.CodedAbilityRegistry;
import com.jjktbf.model.character.coded.CursedSpeechAbility;
import com.jjktbf.model.character.coded.RatioAbility;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffectType;
import com.jjktbf.model.technique.InnateTechniqueData;
import com.jjktbf.model.progression.TechniqueMasteryProgressions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Effect list and effect-specific modal editor used by the ability editor. */
public class EffectListEditor extends Table {

    private static final String ALL_MOVES = "All moves";
    private static final String SELECT_MOVE = "[select a move]";
    private static final String SELECT_ABILITY = "[select an ability]";
    private static final String SELECT_TECHNIQUE = "[select a technique]";
    private static final String SELECT_CHARACTER = "[select a shikigami]";
    private static final String SELECT_FORM = "[select a character form]";
    private static final String NO_MOVES = "[no moves available]";
    private static final String NO_ABILITIES = "[no abilities available]";
    private static final String NO_TECHNIQUES = "[no techniques available]";
    private static final String NO_CHARACTERS = "[no shikigami available]";
    private static final String NO_FORMS = "[no character forms available]";

    private final Skin skin;
    private final List<AbilityEffectData> effects;
    private final List<MoveData> moves;
    private final List<AbilityData> abilities;
    private final List<InnateTechniqueData> techniques;
    private final List<CharacterData> characters;
    private final Runnable onDirty;
    private final Runnable requestRebuild;
    private final Consumer<SoundCue> soundPlayer;
    private final Container<Actor> listContainer;
    private final boolean masteryEligible;
    private final boolean passiveAbility;
    private final List<AbilityEffectType> availableTypes;
    private final boolean moveEffectEditor;
    private final UiProfile uiProfile;
    private final boolean windowsLayout;

    public EffectListEditor(
        List<AbilityEffectData> effects,
        List<MoveData> moves,
        List<AbilityData> abilities,
        List<InnateTechniqueData> techniques,
        List<CharacterData> characters,
        Runnable onDirty,
        Runnable requestRebuild,
        Consumer<SoundCue> soundPlayer,
        boolean masteryEligible,
        boolean passiveAbility,
        UiProfile uiProfile,
        Skin skin
    ) {
        this(effects, moves, abilities, techniques, characters, onDirty,
            requestRebuild, soundPlayer, masteryEligible, passiveAbility,
            java.util.Arrays.stream(AbilityEffectType.values())
                .filter(type -> !type.isMoveOnly())
                .toList(), false, uiProfile, skin);
    }

    public EffectListEditor(
        List<AbilityEffectData> effects,
        List<MoveData> moves,
        List<AbilityData> abilities,
        List<InnateTechniqueData> techniques,
        List<CharacterData> characters,
        Runnable onDirty,
        Runnable requestRebuild,
        Consumer<SoundCue> soundPlayer,
        boolean masteryEligible,
        boolean passiveAbility,
        List<AbilityEffectType> availableTypes,
        boolean moveEffectEditor,
        UiProfile uiProfile,
        Skin skin
    ) {
        super(skin);
        this.skin = skin;
        this.effects = effects == null ? new ArrayList<>() : effects;
        this.moves = moves == null ? List.of() : moves;
        this.abilities = abilities == null ? List.of() : abilities;
        this.techniques = techniques == null ? List.of() : techniques;
        this.characters = characters == null ? List.of() : characters;
        this.onDirty = onDirty;
        this.requestRebuild = requestRebuild;
        this.soundPlayer = soundPlayer == null ? cue -> { } : soundPlayer;
        this.masteryEligible = masteryEligible;
        this.passiveAbility = passiveAbility;
        this.availableTypes = availableTypes == null || availableTypes.isEmpty()
            ? List.of(AbilityEffectType.APPLY_STATUS) : List.copyOf(availableTypes);
        this.moveEffectEditor = moveEffectEditor;
        this.uiProfile = uiProfile;
        this.windowsLayout = uiProfile == UiProfile.WINDOWS;

        listContainer = new Container<>();
        listContainer.fill(true, false);
        add(listContainer).growX().row();

        TextButton addButton = new TextButton("+ Add effect", skin);
        addButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                EffectListEditor.this.soundPlayer.accept(SoundCue.UI_CONFIRM);
                openEditor(-1);
            }
        });
        add(addButton).left().padTop(4).row();

        rebuildList();
    }

    private void rebuildList() {
        Table list = new Table(skin);
        list.defaults().left().pad(3);
        if (effects.isEmpty()) {
            Label empty = new Label("(none)", skin, "small");
            empty.setColor(skin.get("text-dim", Color.class));
            list.add(empty).row();
        } else {
            for (int i = 0; i < effects.size(); i++) {
                final int index = i;
                AbilityEffectData effect = effects.get(index);
                list.add(new Label(describe(effect), skin, "small")).left().growX();

                TextButton edit = new TextButton("Edit", skin);
                edit.addListener(new ChangeListener() {
                    @Override public void changed(ChangeEvent event, Actor actor) {
                        soundPlayer.accept(SoundCue.UI_CONFIRM);
                        openEditor(index);
                    }
                });
                TextButton remove = new TextButton("X", skin);
                remove.addListener(new ChangeListener() {
                    @Override public void changed(ChangeEvent event, Actor actor) {
                        effects.remove(index);
                        soundPlayer.accept(SoundCue.UI_DELETE);
                        dirtyAndRebuild();
                    }
                });
                list.add(edit).padLeft(4);
                list.add(remove).padLeft(4).row();
            }
        }
        listContainer.setActor(list);
    }

    /** Open a working-copy editor. A cancelled add/edit never mutates the draft. */
    private void openEditor(int index) {
        boolean adding = index < 0;
        AbilityEffectData working = adding
            ? availableTypes.get(0).createDefault()
            : effects.get(index).copy();
        AbilityEffectType initialType = safeType(working.type);
        initialType.prepare(working);

        SelectBox<String> typeBox = new DynamicSelectBox<>(skin, uiProfile);
        typeBox.setItems(effectTypeLabels());
        typeBox.setSelected(initialType.displayName());

        Label hint = new Label(initialType.description(), skin, "small");
        hint.setColor(skin.get("text-dim", Color.class));
        hint.setWrap(true);

        Label error = new Label("", skin, "small");
        error.setColor(skin.get("text-error", Color.class));
        error.setWrap(true);

        Container<Actor> fieldsContainer = new Container<>();
        fieldsContainer.fill(true, false);
        final Runnable[] rebuildFields = new Runnable[1];
        rebuildFields[0] = () -> fieldsContainer.setActor(
            buildFields(working, typeFromLabel(typeBox.getSelected()), rebuildFields[0]));
        rebuildFields[0].run();

        typeBox.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                AbilityEffectType selected = typeFromLabel(typeBox.getSelected());
                selected.reset(working);
                hint.setText(selected.description());
                error.setText("");
                rebuildFields[0].run();
            }
        });

        ContentSizedDialog dialog = new ContentSizedDialog(
            adding ? "Add Effect" : "Edit Effect",
            skin,
            uiProfile);
        Table content = dialog.getContentTable();
        content.defaults().pad(4).left().growX();
        content.add(new Label("Effect", skin)).padRight(8);
        content.add(typeBox).growX().row();
        addDialogLabel(content, hint);
        ScrollPane fieldsScroll = verticalScrollPane(fieldsContainer);
        content.add(fieldsScroll).colspan(2).minHeight(windowsLayout ? 180f : 120f)
            .maxHeight(effectDialogScrollHeight()).growX().row();
        addDialogLabel(content, error);

        TextButton done = new TextButton("Done", skin);
        done.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                AbilityEffectType selected = typeFromLabel(typeBox.getSelected());
                selected.clearUnusedFields(working);
                String validationError = selected.validationError(working);
                if (validationError != null) {
                    error.setText(validationError);
                    soundPlayer.accept(SoundCue.UI_DENIED);
                    return;
                }
                if ((selected == AbilityEffectType.GRANT_MOVE
                    || selected == AbilityEffectType.UNLOCK_MOVE)
                    && moves.stream().noneMatch(move -> working.moveId.equals(move.id))) {
                    error.setText("Choose a move that still exists.");
                    soundPlayer.accept(SoundCue.UI_DENIED);
                    return;
                }
                if (selected == AbilityEffectType.GRANT_ABILITY
                    && abilities.stream().noneMatch(ability ->
                        working.abilityId.equals(ability.id))) {
                    error.setText("Choose an ability that still exists.");
                    soundPlayer.accept(SoundCue.UI_DENIED);
                    return;
                }
                if (selected == AbilityEffectType.UNLOCK_TECHNIQUE
                    && techniques.stream().noneMatch(technique ->
                        working.stringValue.equalsIgnoreCase(technique.name))) {
                    error.setText("Choose a technique that still exists.");
                    soundPlayer.accept(SoundCue.UI_DENIED);
                    return;
                }
                if (selected.uses(AbilityEffectParameter.CHARACTER_ID)
                    && !(selected == AbilityEffectType.TRANSFORM_CHARACTER
                        ? isCharacterReference(characters, working.characterId)
                        : isShikigamiReference(characters, working.characterId))) {
                    error.setText(selected == AbilityEffectType.TRANSFORM_CHARACTER
                        ? "Choose a character form that still exists."
                        : "Choose a Shikigami that still exists.");
                    soundPlayer.accept(SoundCue.UI_DENIED);
                    return;
                }
                if (adding) effects.add(working.copy());
                else effects.set(index, working.copy());
                soundPlayer.accept(SoundCue.UI_CONFIRM);
                dialog.hide();
                dirtyAndRebuild();
            }
        });
        TextButton cancel = new TextButton("Cancel", skin);
        cancel.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                soundPlayer.accept(SoundCue.UI_BACK);
                dialog.hide();
            }
        });
        dialog.getButtonTable().add(done).pad(4);
        dialog.getButtonTable().add(cancel).pad(4);
        dialog.show(getStage());
    }

    private ScrollPane verticalScrollPane(Actor content) {
        ScrollPane scroll = new AxisLockedScrollPane(content, skin);
        scroll.setScrollingDisabled(true, false);
        scroll.setFadeScrollBars(false);
        scroll.setOverscroll(false, false);
        scroll.setForceScroll(false, false);
        return scroll;
    }

    private float effectDialogScrollHeight() {
        float viewportHeight = getStage() == null
            ? windowsLayout ? 1080f : 720f
            : getStage().getHeight();
        if (windowsLayout) {
            return Math.max(120f, Math.min(840f, viewportHeight - 240f));
        }
        return Math.max(180f, Math.min(560f, viewportHeight - 220f));
    }

    private void addDialogLabel(Table content, Label label) {
        if (windowsLayout) {
            content.add(label).colspan(2).minWidth(0f).prefWidth(660f)
                .maxWidth(660f).growX().row();
        } else {
            content.add(label).colspan(2).width(440f).growX().row();
        }
    }

    private Actor buildFields(
        AbilityEffectData effect,
        AbilityEffectType type,
        Runnable refreshFields
    ) {
        Table fields = new Table(skin);
        fields.defaults().pad(4).left().growX();
        boolean tickOnlyStatus = isTickOnlyStatus(effect, type);
        boolean roundOnlyStatus = isRoundOnlyStatus(effect, type);
        TextField durationField = type.uses(AbilityEffectParameter.DURATION, effect)
            && !tickOnlyStatus
            ? integerField(effect.durationRounds) : null;
        TextField durationTicksField = type.uses(AbilityEffectParameter.DURATION, effect)
            && !roundOnlyStatus ? nonNegativeIntegerField(effect.durationTicks) : null;
        TextField perTickRemovalChanceField = type.uses(
            AbilityEffectParameter.PER_TICK_REMOVAL_CHANCE)
            ? nonNegativeDecimalField(effect.perTickRemovalChance == null
                ? null : effect.perTickRemovalChance * 100.0) : null;

        if (type.uses(AbilityEffectParameter.CODED_FEATURE)) {
            List<CodedAbilityRegistry.AbilityFeature> features =
                CodedAbilityRegistry.abilityFeatures();
            CodedAbilityRegistry.AbilityFeature selected = codedFeature(effect);
            SelectBox<String> featureBox = new DynamicSelectBox<>(skin, uiProfile);
            featureBox.setItems(features.stream()
                .map(CodedAbilityRegistry.AbilityFeature::label)
                .toArray(String[]::new));
            featureBox.setSelected(selected.label());
            effect.codedAbilityKey = selected.key();
            effect.codedFeature = selected.feature();
            CodedAbilityRegistry.prepareAbilityParameters(effect);
            featureBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    CodedAbilityRegistry.AbilityFeature feature = features.stream()
                        .filter(candidate -> candidate.label().equals(featureBox.getSelected()))
                        .findFirst().orElse(features.get(0));
                    effect.codedAbilityKey = feature.key();
                    effect.codedFeature = feature.feature();
                    CodedAbilityRegistry.prepareAbilityParameters(effect);
                    refreshFields.run();
                }
            });
            addRow(fields, "Coded effect", featureBox);
            for (CodedAbilityRegistry.CodedParameter parameter
                : CodedAbilityRegistry.abilityParameters(
                    effect.codedAbilityKey, effect.codedFeature)) {
                TextField value = integerField(effect.codedParameters.get(parameter.key()));
                value.addListener(new ChangeListener() {
                    @Override public void changed(ChangeEvent event, Actor actor) {
                        Integer parsed = parseInteger(value.getText());
                        if (parsed != null) effect.codedParameters.put(parameter.key(), parsed);
                    }
                });
                addRow(fields, parameter.label(), value);
                addMasteryProgression(fields, effect, parameter.key(),
                    () -> effect.codedParameters.getOrDefault(
                        parameter.key(), parameter.defaultValue()));
            }
        }

        if (type.uses(AbilityEffectParameter.CODED_ACTION)) {
            List<CodedAbilityRegistry.EffectAction> actions =
                CodedAbilityRegistry.effectActions();
            CodedAbilityRegistry.EffectAction selected = codedAction(effect);
            SelectBox<String> actionBox = new DynamicSelectBox<>(skin, uiProfile);
            actionBox.setItems(actions.stream()
                .map(CodedAbilityRegistry.EffectAction::label)
                .toArray(String[]::new));
            actionBox.setSelected(selected.label());
            effect.codedAbilityKey = selected.key();
            effect.codedAction = selected.action();
            CodedAbilityRegistry.prepareMoveEffect(effect);
            actionBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    CodedAbilityRegistry.EffectAction action = actions.stream()
                        .filter(candidate -> candidate.label().equals(actionBox.getSelected()))
                        .findFirst().orElse(actions.get(0));
                    effect.codedAbilityKey = action.key();
                    effect.codedAction = action.action();
                    effect.codedTarget = null;
                    effect.codedStackCount = null;
                    effect.codedParameters = null;
                    effect.masteryProgression = null;
                    CodedAbilityRegistry.prepareMoveEffect(effect);
                    refreshFields.run();
                }
            });
            addRow(fields, "Coded effect", actionBox);
            addCodedMoveActionFields(fields, effect, refreshFields);
        }

        if (type.uses(AbilityEffectParameter.STAT_TYPE)) {
            SelectBox<String> statTypeBox = new DynamicSelectBox<>(skin, uiProfile);
            statTypeBox.setItems(java.util.Arrays.stream(AbilityEffectType.StatType.values())
                .map(value -> value.label).toArray(String[]::new));
            statTypeBox.setSelected(AbilityEffectType.selectedStatType(effect).label);
            statTypeBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    AbilityEffectType.StatType selected = java.util.Arrays.stream(
                            AbilityEffectType.StatType.values())
                        .filter(value -> value.label.equals(statTypeBox.getSelected()))
                        .findFirst().orElse(AbilityEffectType.StatType.CORE);
                    effect.statType = selected.name();
                    effect.stat = selected == AbilityEffectType.StatType.CORE
                        ? StatKey.VITALITY.fieldName : null;
                    effect.stringValue = selected == AbilityEffectType.StatType.BATTLE
                        ? BattleStatKey.MAX_AP.name() : null;
                    effect.intValue = null;
                    effect.doubleValue = null;
                    type.prepare(effect);
                    refreshFields.run();
                }
            });
            addRow(fields, "Stat type", statTypeBox);
        }

        if (type.uses(AbilityEffectParameter.STAT_OPERATION)) {
            SelectBox<String> operationBox = new DynamicSelectBox<>(skin, uiProfile);
            operationBox.setItems(java.util.Arrays.stream(AbilityEffectType.StatOperation.values())
                .map(value -> value.label).toArray(String[]::new));
            operationBox.setSelected(AbilityEffectType.selectedStatOperation(effect).label);
            operationBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    AbilityEffectType.StatOperation selected = java.util.Arrays.stream(
                            AbilityEffectType.StatOperation.values())
                        .filter(value -> value.label.equals(operationBox.getSelected()))
                        .findFirst().orElse(AbilityEffectType.StatOperation.CHANGE);
                    effect.statOperation = selected.name();
                    effect.valueMode = selected == AbilityEffectType.StatOperation.CHANGE
                        ? AbilityEffectType.ValueMode.FLAT.name() : null;
                    effect.intValue = null;
                    effect.doubleValue = null;
                    type.prepare(effect);
                    refreshFields.run();
                }
            });
            addRow(fields, "Operation", operationBox);
        }

        if (type.uses(AbilityEffectParameter.CE_DRAIN_MODE)) {
            SelectBox<String> drainModeBox = new DynamicSelectBox<>(skin, uiProfile);
            drainModeBox.setItems(java.util.Arrays.stream(AbilityEffectType.CeDrainMode.values())
                .map(value -> value.label).toArray(String[]::new));
            drainModeBox.setSelected(AbilityEffectType.selectedCeDrainMode(effect).label);
            drainModeBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    AbilityEffectType.CeDrainMode selected = java.util.Arrays.stream(
                            AbilityEffectType.CeDrainMode.values())
                        .filter(value -> value.label.equals(drainModeBox.getSelected()))
                        .findFirst().orElse(AbilityEffectType.CeDrainMode.INSTANT);
                    effect.ceDrainMode = selected.name();
                    if (selected == AbilityEffectType.CeDrainMode.OVER_TIME) {
                        if (effect.durationRounds == null) effect.durationRounds = 1;
                        if (effect.durationTicks == null) effect.durationTicks = 0;
                    } else {
                        effect.durationRounds = null;
                        effect.durationTicks = null;
                        effect.ceEfficiencyProgression = null;
                    }
                    type.prepare(effect);
                    refreshFields.run();
                }
            });
            addRow(fields, "Drain timing", drainModeBox);
        }

        if (type.uses(AbilityEffectParameter.VALUE_MODE, effect)) {
            SelectBox<String> valueModeBox = new DynamicSelectBox<>(skin, uiProfile);
            String percentageLabel = type == AbilityEffectType.TIMED_STAT_MODIFIER
                ? "Scaled percentage" : "Maximum percentage";
            valueModeBox.setItems(AbilityEffectType.ValueMode.FLAT.label, percentageLabel);
            valueModeBox.setSelected(AbilityEffectType.selectedValueMode(effect)
                == AbilityEffectType.ValueMode.FLAT
                    ? AbilityEffectType.ValueMode.FLAT.label : percentageLabel);
            valueModeBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.valueMode = AbilityEffectType.ValueMode.FLAT.label.equals(
                        valueModeBox.getSelected())
                            ? AbilityEffectType.ValueMode.FLAT.name()
                            : AbilityEffectType.ValueMode.PERCENT.name();
                    effect.intValue = null;
                    effect.doubleValue = null;
                    type.prepare(effect);
                    refreshFields.run();
                }
            });
            addRow(fields, "Value type", valueModeBox);
        }

        if (type.uses(AbilityEffectParameter.ACCURACY_DURATION)) {
            SelectBox<String> durationModeBox = new DynamicSelectBox<>(skin, uiProfile);
            durationModeBox.setItems(java.util.Arrays.stream(
                    AbilityEffectType.AccuracyDuration.values())
                .map(value -> value.label).toArray(String[]::new));
            durationModeBox.setSelected(
                AbilityEffectType.selectedAccuracyDuration(effect).label);
            durationModeBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    AbilityEffectType.AccuracyDuration selected = java.util.Arrays.stream(
                            AbilityEffectType.AccuracyDuration.values())
                        .filter(value -> value.label.equals(durationModeBox.getSelected()))
                        .findFirst().orElse(AbilityEffectType.AccuracyDuration.NEXT_ATTACK);
                    effect.accuracyDuration = selected.name();
                    effect.durationRounds = selected == AbilityEffectType.AccuracyDuration.DURATION
                        ? 1 : null;
                    effect.durationTicks = selected == AbilityEffectType.AccuracyDuration.DURATION
                        ? 0 : null;
                    refreshFields.run();
                }
            });
            addRow(fields, "Applies for", durationModeBox);
        }

        if (type.uses(AbilityEffectParameter.STAT, effect)) {
            SelectBox<String> statBox = new DynamicSelectBox<>(skin, uiProfile);
            statBox.setItems(statLabels());
            statBox.setSelected(statLabel(effect.stat));
            effect.stat = statFromLabel(statBox.getSelected()).fieldName;
            statBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.stat = statFromLabel(statBox.getSelected()).fieldName;
                }
            });
            addRow(fields, "Stat", statBox);
        }

        if (type.uses(AbilityEffectParameter.BATTLE_STAT, effect)) {
            SelectBox<String> statBox = new DynamicSelectBox<>(skin, uiProfile);
            statBox.setItems(java.util.Arrays.stream(BattleStatKey.values())
                .map(stat -> stat.label).toArray(String[]::new));
            statBox.setSelected(battleStatLabel(effect.stringValue));
            effect.stringValue = battleStatFromLabel(statBox.getSelected()).name();
            statBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.stringValue = battleStatFromLabel(statBox.getSelected()).name();
                }
            });
            addRow(fields, "Battle stat", statBox);
        }

        if (type.uses(AbilityEffectParameter.MOVE_SCOPE)) {
            SelectBox<String> scopeBox = new DynamicSelectBox<>(skin, uiProfile);
            scopeBox.setItems(moveScopeLabels(type != AbilityEffectType.LOCK_MOVE_TAG));
            scopeBox.setSelected(moveScopeLabel(effect.moveTag));
            effect.moveTag = ALL_MOVES.equals(scopeBox.getSelected())
                ? null : tagFromLabel(scopeBox.getSelected()).name();
            scopeBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.moveTag = ALL_MOVES.equals(scopeBox.getSelected())
                        ? null : tagFromLabel(scopeBox.getSelected()).name();
                }
            });
            addRow(fields, type == AbilityEffectType.LOCK_MOVE_TAG ? "Move tag" : "Affected moves", scopeBox);
        }

        if (type.uses(AbilityEffectParameter.INTEGER, effect)) {
            TextField integer = integerField(effect.intValue);
            integer.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.intValue = parseInteger(integer.getText());
                }
            });
            addRow(fields, integerLabel(type, effect), integer);
            addMasteryProgression(fields, effect, TechniqueMasteryProgressions.INT_VALUE,
                () -> effect.intValue == null ? 0 : effect.intValue);
            addCeEfficiencyProgression(fields, effect, TechniqueMasteryProgressions.INT_VALUE,
                () -> effect.intValue == null ? 0 : effect.intValue);
        }

        if (type.uses(AbilityEffectParameter.DECIMAL, effect)) {
            boolean percentage = isPercentage(type, effect);
            Double displayed = percentage && effect.doubleValue != null
                ? effect.doubleValue * 100.0 : effect.doubleValue;
            TextField decimal = decimalField(displayed);
            decimal.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    Double value = parseDouble(decimal.getText());
                    effect.doubleValue = percentage && value != null ? value / 100.0 : value;
                }
            });
            addRow(fields, decimalLabel(type, effect), decimal);
            addMasteryProgression(fields, effect, TechniqueMasteryProgressions.DOUBLE_VALUE,
                () -> masteryDecimalLiteral(type, effect, effect.doubleValue));
            addCeEfficiencyProgression(fields, effect, TechniqueMasteryProgressions.DOUBLE_VALUE,
                () -> masteryDecimalLiteral(type, effect, effect.doubleValue));
        }

        if (type.uses(AbilityEffectParameter.STAT_MULTIPLIER_CURVE)) {
            TextField minimumMultiplier = nonNegativeDecimalField(effect.minimumStatMultiplier);
            minimumMultiplier.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.minimumStatMultiplier = parseDouble(minimumMultiplier.getText());
                }
            });
            addRow(fields, "Multiplier at scaled stat 10", minimumMultiplier);

            TextField maximumMultiplier = nonNegativeDecimalField(effect.maximumStatMultiplier);
            maximumMultiplier.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.maximumStatMultiplier = parseDouble(maximumMultiplier.getText());
                }
            });
            addRow(fields, "Multiplier at scaled stat maximum", maximumMultiplier);
        }

        if (type.uses(AbilityEffectParameter.MOVE_ID)) {
            SelectBox<String> moveBox = new DynamicSelectBox<>(skin, uiProfile);
            moveBox.setItems(moveReferenceLabels(effect.moveId));
            moveBox.setSelected(moveReferenceLabel(effect.moveId));
            moveBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.moveId = moveIdFromLabel(moveBox.getSelected());
                }
            });
            addRow(fields, "Move", moveBox);
        }

        if (type.uses(AbilityEffectParameter.ABILITY_ID)) {
            SelectBox<String> abilityBox = new DynamicSelectBox<>(skin, uiProfile);
            abilityBox.setItems(abilityReferenceLabels(effect.abilityId));
            abilityBox.setSelected(abilityReferenceLabel(effect.abilityId));
            abilityBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.abilityId = referenceIdFromLabel(abilityBox.getSelected());
                }
            });
            addRow(fields, "Ability", abilityBox);
        }

        if (type.uses(AbilityEffectParameter.CHARACTER_ID)) {
            SelectBox<String> characterBox = new DynamicSelectBox<>(skin, uiProfile);
            boolean formReference = type == AbilityEffectType.TRANSFORM_CHARACTER;
            characterBox.setItems(formReference
                ? characterReferenceLabels(effect.characterId)
                : shikigamiReferenceLabels(effect.characterId));
            characterBox.setSelected(formReference
                ? characterReferenceLabel(effect.characterId)
                : shikigamiReferenceLabel(effect.characterId));
            characterBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.characterId = referenceIdFromLabel(characterBox.getSelected());
                }
            });
            addRow(fields, formReference ? "Character form" : "Shikigami", characterBox);
        }

        if (type.uses(AbilityEffectParameter.TRANSFORMATION_HP)) {
            SelectBox<String> hpMode = new DynamicSelectBox<>(skin, uiProfile);
            hpMode.setItems(java.util.Arrays.stream(TransformationHpMode.values())
                .map(TransformationHpMode::displayName).toArray(String[]::new));
            TransformationHpMode selected;
            try {
                selected = TransformationHpMode.fromName(effect.transformationHpMode);
            } catch (IllegalArgumentException exception) {
                selected = TransformationHpMode.FULL;
            }
            hpMode.setSelected(selected.displayName());
            effect.transformationHpMode = selected.name();
            hpMode.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.transformationHpMode = java.util.Arrays.stream(
                            TransformationHpMode.values())
                        .filter(mode -> mode.displayName().equals(hpMode.getSelected()))
                        .findFirst().orElse(TransformationHpMode.FULL).name();
                }
            });
            addRow(fields, "New form HP", hpMode);
        }

        if (type.uses(AbilityEffectParameter.RETURN_CONDITION)) {
            CheckBox automaticReturn = new CheckBox(" Return automatically", skin);
            automaticReturn.setChecked(effect.returnCondition != null);
            automaticReturn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.returnCondition = automaticReturn.isChecked()
                        ? AbilityConditionType.HP_PERCENT_AT_OR_BELOW.createDefault()
                        : null;
                    refreshFields.run();
                }
            });
            addRow(fields, "Reversion", automaticReturn);
            if (effect.returnCondition != null) {
                fields.add(new Label("Return condition", skin)).padRight(8).top();
                fields.add(new ConditionTreeEditor(
                    effect.returnCondition,
                    moves,
                    () -> { },
                    soundPlayer,
                    masteryEligible,
                    uiProfile,
                    skin)).growX().row();
            }
        }

        if (type.uses(AbilityEffectParameter.TECHNIQUE)) {
            SelectBox<String> techniqueBox = new DynamicSelectBox<>(skin, uiProfile);
            techniqueBox.setItems(techniqueLabels(effect.stringValue));
            techniqueBox.setSelected(techniqueLabel(effect.stringValue));
            techniqueBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.stringValue = techniqueNameFromLabel(techniqueBox.getSelected());
                }
            });
            addRow(fields, "Technique", techniqueBox);
        }

        if (type.uses(AbilityEffectParameter.STATUS_TYPE)) {
            SelectBox<String> statusBox = new DynamicSelectBox<>(skin, uiProfile);
            List<String> statuses = new ArrayList<>(AbilityEffectType.supportedAutoStatuses().stream()
                .map(StatusEffectType::displayName)
                .toList());
            String storedStatus = effect.stringValue;
            String selectedStatus = statusLabel(storedStatus);
            if (!statuses.contains(selectedStatus)) statuses.add(0, selectedStatus);
            statusBox.setItems(statuses.toArray(new String[0]));
            statusBox.setSelected(selectedStatus);
            statusBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.stringValue = selectedStatus.equals(statusBox.getSelected())
                        ? storedStatus : statusFromLabel(statusBox.getSelected()).name();
                    StatusEffectType status = statusFromLabel(statusBox.getSelected());
                    if (status.requiresTickDuration()) {
                        effect.durationRounds = 0;
                        if (effect.durationTicks == null || effect.durationTicks <= 0) {
                            effect.durationTicks = 1;
                        }
                        effect.magnitude = 0.0;
                    } else if (status.requiresRoundDuration()) {
                        if (effect.durationRounds == null || effect.durationRounds == 0
                            || effect.durationRounds < -1) {
                            effect.durationRounds = 1;
                        }
                        effect.durationTicks = 0;
                    }
                    refreshFields.run();
                }
            });
            addRow(fields, "Status", statusBox);
        }

        if (type.uses(AbilityEffectParameter.TARGET)) {
            SelectBox<String> targetBox = new DynamicSelectBox<>(skin, uiProfile);
            String self = moveEffectEditor ? "Move user" : AbilityEffectTarget.SELF.name();
            String enemy = moveEffectEditor ? "Move target" : AbilityEffectTarget.ENEMY.name();
            String ally = moveEffectEditor ? "Move ally" : AbilityEffectTarget.ALLY.name();
            String both = moveEffectEditor ? "User and target" : AbilityEffectTarget.BOTH.name();
            String selfAndAlly = moveEffectEditor ? "User and ally" : AbilityEffectTarget.SELF_AND_ALLY.name();
            String pairFirst = "Pair first";
            String pairSecond = "Pair second";
            String pairBoth = "Pair both";
            if (type == AbilityEffectType.TRANSACT_BOUNDED_RESOURCE) {
                targetBox.setItems(self);
            } else if (moveEffectEditor) {
                targetBox.setItems(self, enemy, ally, both, selfAndAlly,
                    pairFirst, pairSecond, pairBoth);
            } else {
                targetBox.setItems(self, enemy, ally, both, selfAndAlly);
            }
            targetBox.setSelected(switch (safeTarget(effect.target)) {
                case SELF -> self;
                case ENEMY -> enemy;
                case ALLY -> ally;
                case BOTH -> both;
                case SELF_AND_ALLY -> selfAndAlly;
                case PAIR_FIRST -> moveEffectEditor ? pairFirst : self;
                case PAIR_SECOND -> moveEffectEditor ? pairSecond : self;
                case PAIR_BOTH -> moveEffectEditor ? pairBoth : self;
            });
            effect.target = targetFromLabel(targetBox.getSelected()).name();
            targetBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.target = targetFromLabel(targetBox.getSelected()).name();
                }
            });
            addRow(fields, "Target", targetBox);
        }

        if (type.uses(AbilityEffectParameter.TIMING)) {
            SelectBox<String> timingBox = new DynamicSelectBox<>(skin, uiProfile);
            timingBox.setItems(
                AbilityEffectTiming.FIGHT_START.name(),
                AbilityEffectTiming.ROUND_START.name(),
                AbilityEffectTiming.ON_HIT.name());
            timingBox.setSelected(effect.timing);
            effect.timing = timingBox.getSelected();
            timingBox.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.timing = timingBox.getSelected();
                    if (AbilityEffectTiming.ROUND_START.name().equals(effect.timing)
                        && !isTickOnlyStatus(effect, type)) {
                        effect.durationRounds = 1;
                        effect.durationTicks = 0;
                        if (durationField != null) durationField.setText("1");
                        if (durationTicksField != null) durationTicksField.setText("0");
                    }
                }
            });
            addRow(fields, "Apply when", timingBox);
        }

        if (type.uses(AbilityEffectParameter.DURATION, effect)) {
            if (tickOnlyStatus) {
                effect.durationRounds = 0;
                if (effect.durationTicks == null || effect.durationTicks <= 0) {
                    effect.durationTicks = 1;
                    durationTicksField.setText("1");
                }
                durationTicksField.addListener(new ChangeListener() {
                    @Override public void changed(ChangeEvent event, Actor actor) {
                        effect.durationTicks = parseInteger(durationTicksField.getText());
                    }
                });
                addRow(fields, "Stagger duration (AP ticks)", durationTicksField);
                addMasteryProgression(fields, effect,
                    TechniqueMasteryProgressions.DURATION_TICKS,
                    () -> effect.durationTicks == null ? 0 : effect.durationTicks);
            } else if (roundOnlyStatus) {
                effect.durationTicks = 0;
                durationField.addListener(new ChangeListener() {
                    @Override public void changed(ChangeEvent event, Actor actor) {
                        effect.durationRounds = parseInteger(durationField.getText());
                    }
                });
                addRow(fields, "Duration rounds (-1 = permanent)", durationField);
                addMasteryProgression(fields, effect,
                    TechniqueMasteryProgressions.DURATION_ROUNDS,
                    () -> effect.durationRounds == null ? 0 : effect.durationRounds);
            } else {
                durationField.addListener(new ChangeListener() {
                    @Override public void changed(ChangeEvent event, Actor actor) {
                        effect.durationRounds = parseInteger(durationField.getText());
                    }
                });
                durationTicksField.addListener(new ChangeListener() {
                    @Override public void changed(ChangeEvent event, Actor actor) {
                        effect.durationTicks = parseInteger(durationTicksField.getText());
                    }
                });
                addRow(fields, "Duration rounds (-1 = permanent)", durationField);
                addMasteryProgression(fields, effect,
                    TechniqueMasteryProgressions.DURATION_ROUNDS,
                    () -> effect.durationRounds == null ? 0 : effect.durationRounds);
                addRow(fields, "Duration ticks", durationTicksField);
                addMasteryProgression(fields, effect,
                    TechniqueMasteryProgressions.DURATION_TICKS,
                    () -> effect.durationTicks == null ? 0 : effect.durationTicks);
            }
        }

        if (type.uses(AbilityEffectParameter.MAGNITUDE) && !tickOnlyStatus) {
            TextField magnitude = nonNegativeDecimalField(effect.magnitude);
            magnitude.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.magnitude = parseDouble(magnitude.getText());
                }
            });
            addRow(fields, roundOnlyStatus ? "Damage per round" : "Amount (flat points)", magnitude);
            addMasteryProgression(fields, effect, TechniqueMasteryProgressions.MAGNITUDE,
                () -> effect.magnitude == null ? 0 : (int) Math.round(effect.magnitude));
        }

        if (perTickRemovalChanceField != null) {
            perTickRemovalChanceField.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    Double value = parseDouble(perTickRemovalChanceField.getText());
                    effect.perTickRemovalChance = value == null ? null : value / 100.0;
                }
            });
            addRow(fields, "Remove per tick (%)", perTickRemovalChanceField);
            addMasteryProgression(fields, effect,
                TechniqueMasteryProgressions.PER_TICK_REMOVAL_CHANCE,
                () -> percent(effect.perTickRemovalChance));
        }

        if (type.uses(AbilityEffectParameter.CE_UPKEEP_PER_TICK)) {
            TextField ceUpkeep = nonNegativeDecimalField(effect.ceUpkeepPerTick);
            ceUpkeep.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.ceUpkeepPerTick = parseDouble(ceUpkeep.getText());
                }
            });
            addRow(fields, "CE upkeep per tick (blank = none)", ceUpkeep);
        }

        if (type.uses(AbilityEffectParameter.USES)) {
            TextField uses = integerField(effect.uses);
            uses.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.uses = parseInteger(uses.getText());
                }
            });
            addRow(fields, "Uses (-1 = unlimited)", uses);
            addMasteryProgression(fields, effect, TechniqueMasteryProgressions.USES,
                () -> effect.uses == null ? 0 : effect.uses);
        }

        if (type.uses(AbilityEffectParameter.REFRESH_GROUP)) {
            TextField group = textField(effect.refreshGroup);
            group.addListener(onChange(() -> {
                String value = group.getText().trim();
                effect.refreshGroup = value.isEmpty() ? null : value;
            }));
            addRow(fields, "Refresh group (optional)", group);
        }

        if (type.uses(AbilityEffectParameter.RESOURCE_KEY)) {
            TextField key = textField(effect.resourceKey);
            key.addListener(onChange(() -> effect.resourceKey = key.getText().trim()));
            addRow(fields, "Resource key", key);
        }
        if (type.uses(AbilityEffectParameter.RESOURCE_LABEL)) {
            TextField label = textField(effect.resourceLabel);
            label.addListener(onChange(() -> effect.resourceLabel = label.getText().trim()));
            addRow(fields, "Display name", label);
        }
        if (type.uses(AbilityEffectParameter.RESOURCE_CAPACITY)) {
            TextField capacity = nonNegativeIntegerField(effect.resourceCapacity);
            capacity.addListener(onChange(() ->
                effect.resourceCapacity = parseInteger(capacity.getText())));
            addRow(fields, "Capacity", capacity);
            addMasteryProgression(fields, effect,
                TechniqueMasteryProgressions.RESOURCE_CAPACITY,
                () -> effect.resourceCapacity == null ? 0 : effect.resourceCapacity);
        }
        if (type.uses(AbilityEffectParameter.RESOURCE_START_VALUE)) {
            TextField start = nonNegativeIntegerField(effect.resourceStartValue);
            start.addListener(onChange(() ->
                effect.resourceStartValue = parseInteger(start.getText())));
            addRow(fields, "Starting value", start);
            addMasteryProgression(fields, effect,
                TechniqueMasteryProgressions.RESOURCE_START_VALUE,
                () -> effect.resourceStartValue == null ? 0 : effect.resourceStartValue);
        }
        if (type.uses(AbilityEffectParameter.SOURCE_RESOURCE)) {
            TextField key = textField(effect.sourceResourceKey);
            key.addListener(onChange(() -> effect.sourceResourceKey = key.getText().trim()));
            addRow(fields, "Resource to spend (optional)", key);
        }
        if (type.uses(AbilityEffectParameter.SOURCE_RESOURCE_AMOUNT)) {
            TextField amount = nonNegativeIntegerField(effect.sourceResourceAmount);
            amount.addListener(onChange(() ->
                effect.sourceResourceAmount = parseInteger(amount.getText())));
            addRow(fields, "Spend amount", amount);
            addMasteryProgression(fields, effect,
                TechniqueMasteryProgressions.SOURCE_RESOURCE_AMOUNT,
                () -> effect.sourceResourceAmount == null ? 0 : effect.sourceResourceAmount);
        }
        if (type.uses(AbilityEffectParameter.TARGET_RESOURCE)) {
            TextField key = textField(effect.targetResourceKey);
            key.addListener(onChange(() -> effect.targetResourceKey = key.getText().trim()));
            addRow(fields, "Resource to gain (optional)", key);
        }
        if (type.uses(AbilityEffectParameter.TARGET_RESOURCE_AMOUNT)) {
            TextField amount = nonNegativeIntegerField(effect.targetResourceAmount);
            amount.addListener(onChange(() ->
                effect.targetResourceAmount = parseInteger(amount.getText())));
            addRow(fields, "Gain amount", amount);
            addMasteryProgression(fields, effect,
                TechniqueMasteryProgressions.TARGET_RESOURCE_AMOUNT,
                () -> effect.targetResourceAmount == null ? 0 : effect.targetResourceAmount);
        }

        return fields;
    }

    private void addMasteryProgression(
        Table fields,
        AbilityEffectData effect,
        String field,
        java.util.function.IntSupplier literal
    ) {
        if (!masteryEligible) return;
        AbilityEffectType type = safeType(effect.type);
        if (!type.masteryProgressionFields(effect).contains(field)) return;
        if (passiveAbility
            && StatKey.CURSED_TECHNIQUE_MASTERY.fieldName.equalsIgnoreCase(effect.stat)) {
            return;
        }
        MasteryProgressionEditor editor = new MasteryProgressionEditor(
            field,
            literal,
            () -> effect.masteryProgression,
            value -> effect.masteryProgression = value,
            onDirty,
            uiProfile,
            skin);
        fields.add(editor).colspan(2).growX().row();
    }

    private void addCeEfficiencyProgression(
        Table fields,
        AbilityEffectData effect,
        String field,
        java.util.function.IntSupplier literal
    ) {
        AbilityEffectType type = safeType(effect.type);
        if (!type.ceEfficiencyProgressionFields(effect).contains(field)) return;
        MasteryProgressionEditor editor = new MasteryProgressionEditor(
            field,
            literal,
            () -> effect.ceEfficiencyProgression,
            value -> effect.ceEfficiencyProgression = value,
            onDirty,
            uiProfile,
            skin,
            "Cursed Energy Efficiency",
            TechniqueMasteryProgressions.CE_EFFICIENCY_VARIABLE);
        fields.add(editor).colspan(2).growX().row();
    }

    private void addCodedMoveActionFields(
        Table fields,
        AbilityEffectData effect,
        Runnable refreshFields
    ) {
        if (RatioAbility.KEY.equalsIgnoreCase(effect.codedAbilityKey)) {
            SelectBox<String> target = new DynamicSelectBox<>(skin, uiProfile);
            String apply = "Apply to this hit";
            String create = "Create Ratio stacks";
            target.setItems(apply, create);
            target.setSelected(RatioAbility.CREATE_STACKS.equalsIgnoreCase(effect.codedTarget)
                ? create : apply);
            target.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.codedTarget = create.equals(target.getSelected())
                        ? RatioAbility.CREATE_STACKS : RatioAbility.APPLY_TO_MOVE;
                    effect.codedStackCount = RatioAbility.CREATE_STACKS.equals(effect.codedTarget)
                        ? effect.codedStackCount == null ? 1 : effect.codedStackCount
                        : null;
                    effect.codedParameters = null;
                    effect.masteryProgression = null;
                    CodedAbilityRegistry.prepareMoveEffect(effect);
                    refreshFields.run();
                }
            });
            addRow(fields, "Mode", target);
            if (RatioAbility.CREATE_STACKS.equalsIgnoreCase(effect.codedTarget)) {
                TextField stacks = integerField(
                    effect.codedStackCount == null ? 1 : effect.codedStackCount);
                stacks.addListener(new ChangeListener() {
                    @Override public void changed(ChangeEvent event, Actor actor) {
                        effect.codedStackCount = parseInteger(stacks.getText());
                    }
                });
                addRow(fields, "Stacks to create", stacks);
                addMasteryProgression(fields, effect,
                    TechniqueMasteryProgressions.CODED_STACK_COUNT,
                    () -> effect.codedStackCount == null ? 1 : effect.codedStackCount);
            }
        } else if (CursedSpeechAbility.KEY.equalsIgnoreCase(effect.codedAbilityKey)) {
            SelectBox<String> mode = new DynamicSelectBox<>(skin, uiProfile);
            mode.setItems(CursedSpeechAbility.commandModes().toArray(new String[0]));
            mode.setSelected(CursedSpeechAbility.supportsTarget(effect.codedTarget, null)
                ? effect.codedTarget : CursedSpeechAbility.DONT_MOVE);
            effect.codedTarget = mode.getSelected();
            mode.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    effect.codedTarget = mode.getSelected();
                    effect.codedParameters = null;
                    CodedAbilityRegistry.prepareMoveEffect(effect);
                    refreshFields.run();
                }
            });
            addRow(fields, "Command mode", mode);
        }

        effect.codedParameters = CodedAbilityRegistry.prepareEffectParameters(
            effect.codedParameters, effect.codedAbilityKey,
            effect.codedAction, effect.codedTarget);
        for (CodedAbilityRegistry.CodedParameter parameter
            : CodedAbilityRegistry.effectParameters(
                effect.codedAbilityKey, effect.codedAction, effect.codedTarget)) {
            TextField value = integerField(effect.codedParameters.get(parameter.key()));
            value.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    Integer parsed = parseInteger(value.getText());
                    if (parsed != null) effect.codedParameters.put(parameter.key(), parsed);
                }
            });
            addRow(fields, parameter.label(), value);
            addMasteryProgression(fields, effect, parameter.key(),
                () -> effect.codedParameters.getOrDefault(
                    parameter.key(), parameter.defaultValue()));
        }
    }

    private static int masteryDecimalLiteral(
        AbilityEffectType type,
        AbilityEffectData effect,
        Double value
    ) {
        if (value == null) return 0;
        return type.storesDecimalAsPoints(effect)
            ? (int) Math.floor(value)
            : (int) Math.floor(value * 100.0);
    }

    private void dirtyAndRebuild() {
        if (onDirty != null) onDirty.run();
        rebuildList();
        if (requestRebuild != null) requestRebuild.run();
    }

    private static void addRow(Table table, String label, Actor actor) {
        table.add(new Label(label, table.getSkin())).padRight(8);
        table.add(actor).growX().row();
    }

    private static ChangeListener onChange(Runnable action) {
        return new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        };
    }

    private TextField integerField(Integer value) {
        TextField field = new HoverTextField(value == null ? "" : String.valueOf(value), skin);
        field.setTextFieldFilter((textField, character) -> Character.isDigit(character) || character == '-');
        return field;
    }

    private TextField textField(String value) {
        return new HoverTextField(value == null ? "" : value, skin);
    }

    private TextField nonNegativeIntegerField(Integer value) {
        TextField field = new HoverTextField(value == null ? "" : String.valueOf(value), skin);
        field.setTextFieldFilter((textField, character) -> Character.isDigit(character));
        return field;
    }

    private TextField decimalField(Double value) {
        TextField field = new HoverTextField(value == null ? "" : formatNumber(value), skin);
        field.setTextFieldFilter((textField, character) ->
            Character.isDigit(character) || character == '-' || character == '.');
        return field;
    }

    private TextField nonNegativeDecimalField(Double value) {
        TextField field = new HoverTextField(value == null ? "" : formatNumber(value), skin);
        field.setTextFieldFilter((textField, character) ->
            Character.isDigit(character) || character == '.');
        return field;
    }

    private String describe(AbilityEffectData effect) {
        AbilityEffectType type = safeType(effect == null ? null : effect.type);
        if (effect == null) return type.displayName();
        StringBuilder summary = new StringBuilder(type.displayName());
        if (type.uses(AbilityEffectParameter.CODED_FEATURE)) {
            summary.append(" | ").append(codedFeature(effect).label());
        }
        if (type.uses(AbilityEffectParameter.CODED_ACTION)) {
            summary.append(" | ").append(codedAction(effect).label());
            if (effect.codedTarget != null) summary.append(" -> ").append(effect.codedTarget);
        }
        if (type.uses(AbilityEffectParameter.STAT_TYPE)) {
            summary.append(" | ").append(AbilityEffectType.selectedStatType(effect).label)
                .append(" | ").append(AbilityEffectType.selectedStatOperation(effect).label);
        }
        if (type.uses(AbilityEffectParameter.CE_DRAIN_MODE)) {
            summary.append(" | ").append(AbilityEffectType.selectedCeDrainMode(effect).label);
            if (effect.ceEfficiencyProgression != null
                && !effect.ceEfficiencyProgression.isEmpty()) {
                summary.append(" | CEE scaled");
            }
        }
        if (type.uses(AbilityEffectParameter.VALUE_MODE, effect)) {
            summary.append(" | ").append(AbilityEffectType.selectedValueMode(effect).label);
        }
        if (type.uses(AbilityEffectParameter.ACCURACY_DURATION)) {
            summary.append(" | ").append(
                AbilityEffectType.selectedAccuracyDuration(effect).label);
        }
        if (type.uses(AbilityEffectParameter.STAT, effect) && effect.stat != null) {
            summary.append(" | ").append(statLabel(effect.stat));
        }
        if (type.uses(AbilityEffectParameter.MOVE_SCOPE)) {
            summary.append(" | ").append(moveScopeLabel(effect.moveTag));
        }
        if (type.uses(AbilityEffectParameter.INTEGER, effect) && effect.intValue != null) {
            summary.append(" | ");
            if (type != AbilityEffectType.MAX_ACTIVE_SUMMONS
                && !type.isAccuracyPriority() && effect.intValue >= 0) {
                summary.append('+');
            }
            summary.append(effect.intValue);
        }
        if (type.uses(AbilityEffectParameter.DECIMAL, effect) && effect.doubleValue != null) {
            if (isPercentage(type, effect)) {
                double pct = effect.doubleValue * 100.0;
                boolean signed = type == AbilityEffectType.TIMED_STAT_MODIFIER;
                summary.append(" | ");
                if (signed && pct > 0) summary.append('+');
                summary.append(formatNumber(pct)).append('%');
            } else if (type == AbilityEffectType.SUMMON_CE_UPKEEP_PER_ACTIVE_TICK) {
                summary.append(" | ").append(formatNumber(effect.doubleValue)).append(" CE/tick");
            } else {
                summary.append(" | x").append(formatNumber(effect.doubleValue));
            }
        }
        if (type.uses(AbilityEffectParameter.STAT_MULTIPLIER_CURVE)
            && effect.minimumStatMultiplier != null && effect.maximumStatMultiplier != null) {
            summary.append(" | x").append(formatNumber(effect.minimumStatMultiplier))
                .append(" @ 10 -> x1 @ 80 -> x")
                .append(formatNumber(effect.maximumStatMultiplier)).append(" @ max");
        }
        if (type.uses(AbilityEffectParameter.MOVE_ID) && effect.moveId != null) {
            summary.append(" | ").append(moveReferenceLabel(effect.moveId));
        }
        if (type.uses(AbilityEffectParameter.ABILITY_ID) && effect.abilityId != null) {
            summary.append(" | ").append(abilityReferenceLabel(effect.abilityId));
        }
        if (type.uses(AbilityEffectParameter.CHARACTER_ID) && effect.characterId != null) {
            summary.append(" | ").append(type == AbilityEffectType.TRANSFORM_CHARACTER
                ? characterReferenceLabel(effect.characterId)
                : shikigamiReferenceLabel(effect.characterId));
        }
        if (type.uses(AbilityEffectParameter.TRANSFORMATION_HP)) {
            try {
                summary.append(" | ").append(TransformationHpMode
                    .fromName(effect.transformationHpMode).displayName());
            } catch (IllegalArgumentException ignored) { }
        }
        if (type.uses(AbilityEffectParameter.RETURN_CONDITION)) {
            summary.append(effect.returnCondition == null
                ? " | no automatic return" : " | conditional return");
        }
        if (type.uses(AbilityEffectParameter.TECHNIQUE) && effect.stringValue != null) {
            summary.append(" | ").append(effect.stringValue);
        }
        if (type.uses(AbilityEffectParameter.STATUS_TYPE) && effect.stringValue != null) {
            summary.append(" | ").append(statusLabel(effect.stringValue));
            if (effect.target != null) summary.append(" -> ").append(effect.target);
            if (effect.timing != null) summary.append(" @ ").append(effect.timing);
            if (effect.magnitude != null && statusUsesMagnitude(effect)) {
                summary.append(" | ").append(formatNumber(
                    StatusEffectType.normalizeStoredMagnitude(
                        effect.stringValue, effect.magnitude))).append(" points");
            }
            if (effect.perTickRemovalChance != null && effect.perTickRemovalChance > 0.0) {
                summary.append(" | wake ").append(formatNumber(
                    effect.perTickRemovalChance * 100.0)).append("%/tick");
            }
        }
        if (type.uses(AbilityEffectParameter.BATTLE_STAT, effect) && effect.stringValue != null) {
            summary.append(" | ").append(battleStatLabel(effect.stringValue));
        }
        if (type.uses(AbilityEffectParameter.TARGET) && !type.uses(AbilityEffectParameter.STATUS_TYPE)) {
            summary.append(" -> ").append(effect.target);
        }
        if (type.uses(AbilityEffectParameter.DURATION, effect)) {
            summary.append(" | ").append(durationLabel(effect));
        }
        if (type.uses(AbilityEffectParameter.USES)) {
            summary.append(" | ").append(effect.uses).append(" uses");
        }
        if (type.uses(AbilityEffectParameter.REFRESH_GROUP)
            && effect.refreshGroup != null && !effect.refreshGroup.isBlank()) {
            summary.append(" | refresh ").append(effect.refreshGroup);
        }
        if (type.uses(AbilityEffectParameter.RESOURCE_KEY)) {
            summary.append(" | ").append(effect.resourceLabel)
                .append(" [").append(effect.resourceKey).append("] ")
                .append(effect.resourceStartValue).append('/')
                .append(effect.resourceCapacity);
        }
        if (type == AbilityEffectType.TRANSACT_BOUNDED_RESOURCE) {
            if (effect.sourceResourceAmount != null && effect.sourceResourceAmount > 0) {
                summary.append(" | -").append(effect.sourceResourceAmount)
                    .append(' ').append(effect.sourceResourceKey);
            }
            if (effect.targetResourceAmount != null && effect.targetResourceAmount > 0) {
                summary.append(" | +").append(effect.targetResourceAmount)
                    .append(' ').append(effect.targetResourceKey);
            }
        }
        return summary.toString();
    }

    private static String durationLabel(AbilityEffectData effect) {
        int rounds = effect.durationRounds != null ? effect.durationRounds : 0;
        int ticks = effect.durationTicks != null ? effect.durationTicks : 0;
        if (isTickOnlyStatus(effect, safeType(effect.type))) return ticks + " AP ticks";
        if (rounds == -1) return "permanent";
        if (rounds == 0) return ticks + " ticks";
        if (ticks == 0) return rounds + " rounds";
        return rounds + " rounds + " + ticks + " ticks";
    }

    private static AbilityEffectType safeType(String typeName) {
        try {
            return AbilityEffectType.fromName(typeName);
        } catch (Exception ex) {
            return AbilityEffectType.STAT_ADD;
        }
    }

    private static boolean isTickOnlyStatus(AbilityEffectData effect, AbilityEffectType type) {
        if (!type.uses(AbilityEffectParameter.STATUS_TYPE)) return false;
        try {
            return StatusEffectType.fromName(effect.stringValue).requiresTickDuration();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isRoundOnlyStatus(AbilityEffectData effect, AbilityEffectType type) {
        if (!type.uses(AbilityEffectParameter.STATUS_TYPE)) return false;
        try {
            return StatusEffectType.fromName(effect.stringValue).requiresRoundDuration();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean statusUsesMagnitude(AbilityEffectData effect) {
        try {
            return StatusEffectType.fromName(effect.stringValue).usesMagnitude();
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }

    private static int percent(Double value) {
        return value == null ? 0 : (int) Math.round(value * 100.0);
    }

    private String[] effectTypeLabels() {
        String[] labels = new String[availableTypes.size()];
        for (int i = 0; i < availableTypes.size(); i++) {
            labels[i] = availableTypes.get(i).displayName();
        }
        return labels;
    }

    private AbilityEffectType typeFromLabel(String label) {
        for (AbilityEffectType type : availableTypes) {
            if (type.displayName().equals(label)) return type;
        }
        return availableTypes.get(0);
    }

    private static CodedAbilityRegistry.AbilityFeature codedFeature(
        AbilityEffectData effect
    ) {
        List<CodedAbilityRegistry.AbilityFeature> features =
            CodedAbilityRegistry.abilityFeatures();
        return features.stream()
            .filter(feature -> feature.key().equalsIgnoreCase(effect.codedAbilityKey)
                && feature.feature().equalsIgnoreCase(effect.codedFeature))
            .findFirst()
            .orElse(features.get(0));
    }

    private static CodedAbilityRegistry.EffectAction codedAction(
        AbilityEffectData effect
    ) {
        return CodedAbilityRegistry.effectActions().stream()
            .filter(action -> action.key().equalsIgnoreCase(effect.codedAbilityKey)
                && action.action().equalsIgnoreCase(effect.codedAction))
            .findFirst().orElse(CodedAbilityRegistry.effectActions().get(0));
    }

    private static AbilityEffectTarget safeTarget(String value) {
        try { return AbilityEffectTarget.valueOf(value); }
        catch (Exception exception) { return AbilityEffectTarget.SELF; }
    }

    private static AbilityEffectTarget targetFromLabel(String label) {
        if ("Move target".equals(label) || AbilityEffectTarget.ENEMY.name().equals(label)) {
            return AbilityEffectTarget.ENEMY;
        }
        if ("Move ally".equals(label) || AbilityEffectTarget.ALLY.name().equals(label)) {
            return AbilityEffectTarget.ALLY;
        }
        if ("User and target".equals(label) || AbilityEffectTarget.BOTH.name().equals(label)) {
            return AbilityEffectTarget.BOTH;
        }
        if ("User and ally".equals(label) || AbilityEffectTarget.SELF_AND_ALLY.name().equals(label)) {
            return AbilityEffectTarget.SELF_AND_ALLY;
        }
        if ("Pair first".equals(label) || AbilityEffectTarget.PAIR_FIRST.name().equals(label)) {
            return AbilityEffectTarget.PAIR_FIRST;
        }
        if ("Pair second".equals(label) || AbilityEffectTarget.PAIR_SECOND.name().equals(label)) {
            return AbilityEffectTarget.PAIR_SECOND;
        }
        if ("Pair both".equals(label) || AbilityEffectTarget.PAIR_BOTH.name().equals(label)) {
            return AbilityEffectTarget.PAIR_BOTH;
        }
        return AbilityEffectTarget.SELF;
    }

    private static String statusLabel(String statusName) {
        return StatusEffectType.referenceDisplayName(statusName);
    }

    private static StatusEffectType statusFromLabel(String label) {
        for (StatusEffectType status : StatusEffectType.values()) {
            if (status.displayName().equals(label)) return status;
        }
        return StatusEffectType.STRENGTH_INCREASE;
    }

    private static String[] statLabels() {
        StatKey[] stats = StatKey.values();
        String[] labels = new String[stats.length];
        for (int i = 0; i < stats.length; i++) labels[i] = stats[i].label;
        return labels;
    }

    private static String statLabel(String statName) {
        try {
            return StatKey.fromString(statName).label;
        } catch (Exception ex) {
            return StatKey.VITALITY.label;
        }
    }

    private static StatKey statFromLabel(String label) {
        for (StatKey stat : StatKey.values()) {
            if (stat.label.equals(label)) return stat;
        }
        return StatKey.VITALITY;
    }

    private static String[] moveScopeLabels(boolean includeAll) {
        MoveTag[] tags = MoveTag.values();
        String[] labels = new String[tags.length + (includeAll ? 1 : 0)];
        int index = 0;
        if (includeAll) labels[index++] = ALL_MOVES;
        for (MoveTag tag : tags) labels[index++] = pretty(tag.name());
        return labels;
    }

    private static String moveScopeLabel(String tagName) {
        if (tagName == null || tagName.isBlank()) return ALL_MOVES;
        try {
            return pretty(MoveTag.valueOf(tagName).name());
        } catch (Exception ex) {
            return pretty(tagName);
        }
    }

    private static MoveTag tagFromLabel(String label) {
        return MoveTag.valueOf(enumName(label));
    }

    private String[] moveReferenceLabels(String currentId) {
        List<String> labels = new ArrayList<>();
        labels.add(SELECT_MOVE);
        for (MoveData move : moves) labels.add(moveLabel(move));
        if (currentId != null && !currentId.isBlank()
            && moves.stream().noneMatch(move -> currentId.equals(move.id))) {
            labels.add(currentId + " - (missing)");
        }
        if (moves.isEmpty()) labels.add(NO_MOVES);
        return labels.toArray(new String[0]);
    }

    private String moveReferenceLabel(String moveId) {
        if (moveId == null || moveId.isBlank()) return SELECT_MOVE;
        return moves.stream()
            .filter(move -> moveId.equals(move.id))
            .findFirst()
            .map(EffectListEditor::moveLabel)
            .orElse(moveId + " - (missing)");
    }

    private static String moveLabel(MoveData move) {
        return move.id + " - " + move.name;
    }

    private static String moveIdFromLabel(String label) {
        return referenceIdFromLabel(label);
    }

    private String[] abilityReferenceLabels(String currentId) {
        List<String> labels = new ArrayList<>();
        labels.add(SELECT_ABILITY);
        for (AbilityData ability : abilities) labels.add(abilityLabel(ability));
        if (currentId != null && !currentId.isBlank()
            && abilities.stream().noneMatch(ability -> currentId.equals(ability.id))) {
            labels.add(currentId + " - (missing)");
        }
        if (abilities.isEmpty()) labels.add(NO_ABILITIES);
        return labels.toArray(new String[0]);
    }

    private String abilityReferenceLabel(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) return SELECT_ABILITY;
        return abilities.stream()
            .filter(ability -> abilityId.equals(ability.id))
            .findFirst()
            .map(EffectListEditor::abilityLabel)
            .orElse(abilityId + " - (missing)");
    }

    private static String abilityLabel(AbilityData ability) {
        return ability.id + " - " + ability.name;
    }

    /** Only SHIKIGAMI definitions are valid summon targets. */
    private String[] shikigamiReferenceLabels(String currentId) {
        List<CharacterData> shikigami = characters.stream()
            .filter(java.util.Objects::nonNull)
            .filter(EffectListEditor::isShikigami)
            .toList();
        List<String> labels = new ArrayList<>();
        labels.add(SELECT_CHARACTER);
        for (CharacterData c : shikigami) labels.add(c.id + " - " + c.name);
        if (currentId != null && !currentId.isBlank()
            && shikigami.stream().noneMatch(c -> currentId.equals(c.id))) {
            labels.add(currentId + " - (missing or not a shikigami)");
        }
        if (shikigami.isEmpty()) labels.add(NO_CHARACTERS);
        return labels.toArray(new String[0]);
    }

    private String shikigamiReferenceLabel(String characterId) {
        if (characterId == null || characterId.isBlank()) return SELECT_CHARACTER;
        return characters.stream()
            .filter(java.util.Objects::nonNull)
            .filter(c -> characterId.equals(c.id))
            .filter(EffectListEditor::isShikigami)
            .findFirst()
            .map(c -> c.id + " - " + c.name)
            .orElse(characterId + " - (missing or not a shikigami)");
    }

    static boolean isShikigamiReference(
        List<CharacterData> characters,
        String characterId
    ) {
        if (characters == null || characterId == null || characterId.isBlank()) return false;
        return characters.stream()
            .filter(java.util.Objects::nonNull)
            .filter(character -> characterId.equals(character.id))
            .anyMatch(EffectListEditor::isShikigami);
    }

    static boolean isCharacterReference(
        List<CharacterData> characters,
        String characterId
    ) {
        if (characters == null || characterId == null || characterId.isBlank()) return false;
        return characters.stream()
            .filter(java.util.Objects::nonNull)
            .anyMatch(character -> characterId.equals(character.id));
    }

    private String[] characterReferenceLabels(String currentId) {
        List<CharacterData> available = characters.stream()
            .filter(java.util.Objects::nonNull)
            .toList();
        List<String> labels = new ArrayList<>();
        labels.add(SELECT_FORM);
        for (CharacterData character : available) {
            labels.add(character.id + " - " + character.name);
        }
        if (currentId != null && !currentId.isBlank()
            && available.stream().noneMatch(character -> currentId.equals(character.id))) {
            labels.add(currentId + " - (missing)");
        }
        if (available.isEmpty()) labels.add(NO_FORMS);
        return labels.toArray(new String[0]);
    }

    private String characterReferenceLabel(String characterId) {
        if (characterId == null || characterId.isBlank()) return SELECT_FORM;
        return characters.stream()
            .filter(java.util.Objects::nonNull)
            .filter(character -> characterId.equals(character.id))
            .findFirst()
            .map(character -> character.id + " - " + character.name)
            .orElse(characterId + " - (missing)");
    }

    private static boolean isShikigami(CharacterData character) {
        try {
            return character.effectiveType() == CharacterType.SHIKIGAMI;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String referenceIdFromLabel(String label) {
        if (label == null || label.startsWith("[")) return null;
        int separator = label.indexOf(" - ");
        return separator < 0 ? label.trim() : label.substring(0, separator).trim();
    }

    private String[] techniqueLabels(String current) {
        List<String> labels = new ArrayList<>();
        labels.add(SELECT_TECHNIQUE);
        labels.addAll(techniques.stream().map(technique -> technique.name).toList());
        if (current != null && !current.isBlank()
            && techniques.stream().noneMatch(technique -> current.equalsIgnoreCase(technique.name))) {
            labels.add(current + " (missing)");
        }
        if (techniques.isEmpty()) labels.add(NO_TECHNIQUES);
        return labels.toArray(new String[0]);
    }

    private String techniqueLabel(String current) {
        if (current == null || current.isBlank()) {
            return SELECT_TECHNIQUE;
        }
        return techniques.stream()
            .filter(technique -> current.equalsIgnoreCase(technique.name))
            .findFirst()
            .map(technique -> technique.name)
            .orElse(current + " (missing)");
    }

    private static String techniqueNameFromLabel(String label) {
        if (label == null || label.startsWith("[")) return null;
        return label.endsWith(" (missing)")
            ? label.substring(0, label.length() - " (missing)".length())
            : label;
    }

    private static String integerLabel(AbilityEffectType type, AbilityEffectData effect) {
        return switch (type) {
            case NEVER_MISS, NEVER_HIT, APPLY_NEVER_HIT -> "Tier (1-5)";
            case APPLY_NEVER_MISS -> "Tier (0 keeps normal dodges; 1-5 priority)";
            case STAT_ADD -> "Amount (+/-)";
            case STAT_SET_VALUE -> "Exact value";
            case STAT_ALLOCATION_MINIMUM -> "Minimum allocation";
            case STAT_BONUS_POINTS -> "Point-budget change";
            case MOVE_ACCURACY_ADD, OPPONENT_ACCURACY_ADD -> "Accuracy points (+/-)";
            case CE_COST_ALTER -> "CE change (+/-)";
            case MODIFY_AP_BAR -> "AP change (+/-)";
            case COST_CE_PER_ROUND -> "CE cost per round";
            case MAX_ACTIVE_SUMMONS -> "Maximum active summons";
            case HEAL_HP, RESTORE_CE, DEAL_DIRECT_DAMAGE, DAMAGE_SHIELD -> "Amount";
            case DRAIN_CE -> AbilityEffectType.selectedCeDrainMode(effect)
                == AbilityEffectType.CeDrainMode.OVER_TIME ? "CE per tick" : "Amount";
            case TIMED_STAT_MODIFIER -> "Amount (+/-)";
            case TEMP_STAT_SET_VALUE -> "Exact value";
            default -> "Value";
        };
    }

    private static String decimalLabel(AbilityEffectType type, AbilityEffectData effect) {
        if (isPercentage(type, effect)) return "Percentage (+/-)";
        if (type == AbilityEffectType.TIMED_STAT_MODIFIER
            && AbilityEffectType.selectedStatOperation(effect)
                == AbilityEffectType.StatOperation.CHANGE) {
            return "Amount (+/-)";
        }
        if (type == AbilityEffectType.TIMED_STAT_MODIFIER
            && AbilityEffectType.selectedStatOperation(effect)
                == AbilityEffectType.StatOperation.SET) {
            return "Exact value";
        }
        return switch (type) {
            case STAT_DIVIDE -> "Divisor";
            case BF_CHANCE_ADD -> "Chance change % (+/-)";
            case CE_COST_ALTER -> "CE cost multiplier";
            case SUMMON_CE_UPKEEP_PER_ACTIVE_TICK -> "CE per active tick";
            default -> "Multiplier";
        };
    }

    private static boolean isPercentage(
        AbilityEffectType type,
        AbilityEffectData effect
    ) {
        return type == AbilityEffectType.BF_CHANCE_ADD
            || type.isAmountModeEffect()
                && AbilityEffectType.selectedValueMode(effect)
                    == AbilityEffectType.ValueMode.PERCENT
            || type == AbilityEffectType.TIMED_STAT_MODIFIER
                && AbilityEffectType.selectedStatOperation(effect)
                    == AbilityEffectType.StatOperation.CHANGE
                && AbilityEffectType.selectedValueMode(effect)
                    == AbilityEffectType.ValueMode.PERCENT;
    }

    private static String battleStatLabel(String value) {
        try { return BattleStatKey.fromString(value).label; }
        catch (Exception ex) { return BattleStatKey.MAX_AP.label; }
    }

    private static BattleStatKey battleStatFromLabel(String label) {
        for (BattleStatKey stat : BattleStatKey.values()) {
            if (stat.label.equals(label)) return stat;
        }
        return BattleStatKey.MAX_AP;
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank() || "-".equals(value) || ".".equals(value)) return null;
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value)) return String.valueOf((long) value);
        return String.valueOf(value);
    }

    private static String enumName(String label) {
        return label == null ? "" : label.trim().toUpperCase().replace(' ', '_');
    }

    private static String pretty(String enumName) {
        if (enumName == null || enumName.isBlank()) return "";
        String[] words = enumName.toLowerCase().split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (label.length() > 0) label.append(' ');
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }
}
