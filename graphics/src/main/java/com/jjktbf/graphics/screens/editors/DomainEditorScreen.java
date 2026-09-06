package com.jjktbf.graphics.screens.editors;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.jjktbf.graphics.AssetLoader;
import com.jjktbf.graphics.JJKGame;
import com.jjktbf.graphics.audio.SoundCue;
import com.jjktbf.graphics.ui.DynamicSelectBox;
import com.jjktbf.graphics.ui.editor.EditorScreenBase;
import com.jjktbf.graphics.ui.editor.EffectListEditor;
import com.jjktbf.graphics.ui.editor.EnumSelectBox;
import com.jjktbf.graphics.ui.editor.HoverTextField;
import com.jjktbf.graphics.ui.editor.ValidationResult;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectParameter;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.AbilityRepository;
import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.character.CharacterRepository;
import com.jjktbf.model.domain.DomainCapturePolicy;
import com.jjktbf.model.domain.DomainCounterType;
import com.jjktbf.model.domain.DomainData;
import com.jjktbf.model.domain.DomainEntrantPolicy;
import com.jjktbf.model.domain.DomainProtectionPolicy;
import com.jjktbf.model.domain.DomainRepository;
import com.jjktbf.model.domain.DomainTopology;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveRepository;
import com.jjktbf.model.technique.InnateTechniqueData;
import com.jjktbf.model.technique.SkillTreeNodeData;
import com.jjktbf.model.technique.TechniqueRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Dedicated authoring surface for Domains and anti-Domain fields. */
public final class DomainEditorScreen extends EditorScreenBase<DomainData> {

    private static final String NO_TECHNIQUE = "[none - anti-Domain only]";
    private static final List<AbilityEffectType> DOMAIN_EFFECT_TYPES = Arrays.stream(
            AbilityEffectType.values())
        .filter(AbilityEffectType::requiresActivation)
        .filter(AbilityEffectType::isDomainProgramEffect)
        .toList();

    private final DomainRepository repo;
    private final MoveRepository moveRepo;
    private final AbilityRepository abilityRepo;
    private final TechniqueRepository techniqueRepo;
    private final CharacterRepository characterRepo;

    public DomainEditorScreen(JJKGame game, AssetLoader assets) {
        super(game, assets);
        repo = new DomainRepository("data/domains");
        moveRepo = new MoveRepository("data/moves");
        abilityRepo = new AbilityRepository("data/abilities");
        techniqueRepo = new TechniqueRepository("data/techniques");
        characterRepo = new CharacterRepository("data/characters");
    }

    @Override protected String title() { return "DOMAIN EDITOR"; }

    @Override
    protected DomainData newDraft() {
        DomainData domain = new DomainData();
        domain.name = "New Domain";
        domain.description = "";
        domain.requiredTechniqueName = techniqueRepo.getAll().stream()
            .map(technique -> technique.name)
            .filter(name -> name != null && !name.isBlank())
            .findFirst().orElse(null);
        return domain;
    }

    @Override protected DomainData draftFromRecord(DomainData stored) { return stored.copy(); }
    @Override protected String idOf(DomainData record) { return record.id; }
    @Override protected String nextId() { return repo.nextId(); }
    @Override protected void stampNewId(DomainData draft) { draft.id = repo.nextId(); }

    @Override
    protected String listLabel(DomainData record) {
        String name = record.name == null || record.name.isBlank() ? "(unnamed)" : record.name;
        return name + (record.antiDomain ? " [ANTI-DOMAIN]" : " [" + record.topology + "]");
    }

    @Override
    protected boolean isNewDraft(DomainData draft) {
        return draft.id == null || draft.id.isBlank() || repo.findById(draft.id).isEmpty();
    }

    @Override
    protected void reloadRecords() throws IOException {
        repo.load();
        moveRepo.load();
        abilityRepo.load();
        techniqueRepo.load();
        characterRepo.load();
        records.clear();
        records.addAll(repo.getAll());
    }

    @Override
    protected ValidationResult validateAndSave(DomainData domain) {
        if (domain.name == null || domain.name.trim().isEmpty()) {
            return ValidationResult.error("Name is required.");
        }
        domain.name = domain.name.trim();
        for (DomainData existing : repo.getAll()) {
            if (domain.id != null && domain.id.equals(existing.id)) continue;
            if (existing.name != null && domain.name.equalsIgnoreCase(existing.name)) {
                return ValidationResult.error(
                    "A Domain named \"" + existing.name + "\" already exists.");
            }
        }
        if (!domain.antiDomain && domain.requiredTechniqueName != null
            && !domain.requiredTechniqueName.isBlank() && techniqueRepo.findByName(
                domain.requiredTechniqueName).isEmpty()) {
            return ValidationResult.error("Choose an existing required technique.");
        }
        String referenceError = effectReferenceError(domain);
        if (referenceError != null) return ValidationResult.error(referenceError);

        boolean adding = isNewDraft(domain);
        if (adding && (domain.id == null || domain.id.isBlank())) domain.id = repo.nextId();
        try {
            domain.validate();
            if (adding) {
                domain.id = null;
                repo.add(domain);
            } else {
                repo.update(domain);
            }
            repo.save();
            TechniqueTreeRepositorySync.synchronize();
            return ValidationResult.ok("Saved \"" + domain.name + "\".");
        } catch (Exception exception) {
            return ValidationResult.error("Save failed: " + exception.getMessage());
        }
    }

    @Override
    protected ValidationResult delete(String id) {
        DomainData domain = repo.findById(id).orElse(null);
        if (domain == null) return ValidationResult.error("Domain no longer exists.");
        MoveData openingMove = moveRepo.getAll().stream()
            .filter(move -> move.effects != null)
            .filter(move -> move.effects.stream().anyMatch(effect -> effect != null
                && AbilityEffectType.ESTABLISH_DOMAIN.name().equalsIgnoreCase(effect.type)
                && id.equals(effect.domainId)))
            .findFirst().orElse(null);
        if (openingMove != null) {
            return ValidationResult.error(
                "Cannot delete while move \"" + openingMove.name + "\" establishes it.");
        }

        Map<String, String> remappedIds = new LinkedHashMap<>();
        int nextIndex = 0;
        for (DomainData candidate : repo.getAll()) {
            if (id.equals(candidate.id)) continue;
            remappedIds.put(candidate.id,
                com.jjktbf.model.repo.BaseRepository.formatId(nextIndex++));
        }
        try {
            for (MoveData move : moveRepo.getAll()) {
                if (move.effects == null) continue;
                for (MoveEffectData effect : move.effects) {
                    if (effect != null && effect.domainId != null) {
                        effect.domainId = remappedIds.getOrDefault(effect.domainId, effect.domainId);
                    }
                }
            }
            for (CharacterData character : characterRepo.getAll()) {
                if (character.availableDomainIds == null) continue;
                character.availableDomainIds = character.availableDomainIds.stream()
                    .filter(candidate -> !id.equals(candidate))
                    .map(candidate -> remappedIds.getOrDefault(candidate, candidate))
                    .distinct()
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            }
            repo.delete(id);
            repo.save();
            moveRepo.save();
            characterRepo.save();
            TechniqueTreeRepositorySync.synchronize(
                SkillTreeNodeData.DOMAIN, remappedIds, id);
            return ValidationResult.ok("Deleted.");
        } catch (Exception exception) {
            return ValidationResult.error("Delete failed: " + exception.getMessage());
        }
    }

    @Override
    protected Actor buildDetailForm(DomainData domain) {
        Table form = formRoot();

        Table identity = formSection(form, "IDENTITY");
        identity.add(idBadge(domain.id)).left().row();
        identity.add(labelledField("Name", domain.name, value -> domain.name = value)).growX().row();
        identity.add(labelledField(
            "Description", domain.description, value -> domain.description = value)).growX().row();

        CheckBox antiDomain = checkBox("Anti-Domain field", domain.antiDomain, checked -> {
            domain.antiDomain = checked;
            if (checked) {
                domain.requiredTechniqueName = null;
                domain.topology = DomainTopology.INCOMPLETE.name();
                domain.capturePolicy = DomainCapturePolicy.SELECTED_TARGETS.name();
                domain.protectionPolicy = DomainProtectionPolicy.OWNER_AND_SELECTED.name();
                domain.internalBarrierIntegrity = DomainData.DEFAULT_ANTI_DOMAIN_INTEGRITY;
                domain.counterType = DomainCounterType.SURE_HIT_NULLIFICATION.name();
                domain.burnoutRounds = 0;
                domain.burnoutTicks = 0;
                domain.sureHitEffects = new ArrayList<>();
                domain.fieldEffects = new ArrayList<>();
                domain.casterEffects = new ArrayList<>();
            } else {
                domain.requiredTechniqueName = techniqueRepo.getAll().stream()
                    .map(technique -> technique.name).filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
                domain.topology = DomainTopology.CLOSED.name();
                domain.internalBarrierIntegrity = DomainData.DEFAULT_DOMAIN_INTEGRITY;
                domain.counterType = DomainCounterType.NONE.name();
                domain.burnoutRounds = 1;
                domain.burnoutTicks = 0;
            }
            rebuildDetail();
        });
        identity.add(labelledRow("Kind", antiDomain)).growX().row();
        identity.add(labelledRow("Required technique", techniqueSelect(domain))).growX().row();
        identity.add(formHint(domain.antiDomain
            ? "Anti-Domains need no innate technique or technique-tree node."
            : domain.requiredTechniqueName == null || domain.requiredTechniqueName.isBlank()
                ? "Non-innate fields have no technique burnout or technique-tree node."
                : "Saving creates a Domain node in the selected technique's tree."))
            .left().row();

        Table space = formSection(form, "SPACE AND MEMBERSHIP");
        addEnumRow(space, "Topology", DomainTopology.class, domain.topology,
            value -> domain.topology = value);
        addEnumRow(space, "Capture policy", DomainCapturePolicy.class, domain.capturePolicy,
            value -> domain.capturePolicy = value);
        addEnumRow(space, "New entrants", DomainEntrantPolicy.class, domain.entrantPolicy,
            value -> domain.entrantPolicy = value);
        addEnumRow(space, "Protection", DomainProtectionPolicy.class,
            domain.protectionPolicy, value -> domain.protectionPolicy = value);

        Table lifetime = formSection(form, "LIFETIME AND COST");
        lifetime.add(labelledIntField("Duration rounds", value(domain.durationRounds), -1, 999,
            value -> domain.durationRounds = value)).growX().row();
        lifetime.add(labelledIntField("Duration ticks", value(domain.durationTicks), 0, 9999,
            value -> domain.durationTicks = value)).growX().row();
        lifetime.add(decimalField("CE upkeep / tick", domain.ceUpkeepPerTick,
            value -> domain.ceUpkeepPerTick = value, false)).growX().row();
        lifetime.add(labelledIntField("Burnout rounds", value(domain.burnoutRounds), 0, 999,
            value -> domain.burnoutRounds = value)).growX().row();
        lifetime.add(labelledIntField("Burnout ticks", value(domain.burnoutTicks), 0, 9999,
            value -> domain.burnoutTicks = value)).growX().row();

        Table barrier = formSection(form, "BARRIER AND CLASH");
        barrier.add(labelledIntField("Internal integrity",
            value(domain.internalBarrierIntegrity), 0, Integer.MAX_VALUE,
            value -> domain.internalBarrierIntegrity = value)).growX().row();
        barrier.add(labelledIntField("Base clash value",
            value(domain.clashValue), 0, Integer.MAX_VALUE,
            value -> domain.clashValue = value)).growX().row();
        barrier.add(formHint("Integrity scales 3:1 with Jujutsu Skill and CE output. "
            + "Clashes damage the weaker Domain by the score difference; an anti-Domain "
            + "takes the hostile Domain's full clash score.")).left().row();

        if (domain.antiDomain) {
            Table counter = formSection(form, "COUNTER PROGRAM");
            addEnumRow(counter, "Counter type", DomainCounterType.class, domain.counterType,
                value -> domain.counterType = value);
            counter.add(labelledIntField("Counter potency", value(domain.counterPotency), 0,
                Integer.MAX_VALUE, value -> domain.counterPotency = value)).growX().row();
            counter.add(labelledIntField("Counter uses (-1 unlimited)",
                value(domain.counterUses), -1, Integer.MAX_VALUE,
                value -> domain.counterUses = value)).growX().row();
            counter.add(labelledRow("Break when owner moves", checkBox(
                "End this field after another move", Boolean.TRUE.equals(domain.counterBreakOnOwnerMove),
                checked -> domain.counterBreakOnOwnerMove = checked))).growX().row();
        }

        if (!domain.antiDomain) {
            addEffectSection(form, "SURE-HIT PROGRAM", domain.sureHitEffects,
                "Admitted rows bypass normal accuracy, then pass protection and counter checks.");
            addEffectSection(form, "FIELD PROGRAM", domain.fieldEffects,
                "Environmental effects for Domain members.");
            addEffectSection(form, "CASTER PROGRAM", domain.casterEffects,
                "Effects sourced by the Domain owner while the Domain exists.");
        }
        addEffectSection(form, "BARRIER PROGRAM", domain.barrierEffects,
            "Effects associated with barrier state and opposing barriers.");
        addEffectSection(form, "PROCEDURE PROGRAM", domain.procedureEffects,
            "Rule/procedure rows are authored now; interactive procedures remain deferred.");
        return form;
    }

    private void addEffectSection(
        Table form,
        String title,
        List<AbilityEffectData> effects,
        String hint
    ) {
        Table section = formSection(form, title);
        section.add(formHint(hint)).growX().row();
        section.add(new EffectListEditor(
            effects,
            moveRepo.getAll(),
            abilityRepo.getAll(),
            techniqueRepo.getAll(),
            characterRepo.getAll(),
            repo.getAll(),
            this::markDirty,
            this::rebuildDetail,
            game.audio()::play,
            true,
            false,
            DOMAIN_EFFECT_TYPES,
            false,
            true,
            uiProfile,
            skin)).growX().row();
    }

    private SelectBox<String> techniqueSelect(DomainData domain) {
        SelectBox<String> select = new DynamicSelectBox<>(skin, uiProfile);
        List<String> labels = new ArrayList<>();
        labels.add(NO_TECHNIQUE);
        techniqueRepo.getAll().stream().map(technique -> technique.name)
            .filter(java.util.Objects::nonNull).forEach(labels::add);
        if (labels.isEmpty()) labels.add(NO_TECHNIQUE);
        select.setItems(labels.toArray(new String[0]));
        String selected = domain.requiredTechniqueName == null ? NO_TECHNIQUE
            : labels.stream().filter(domain.requiredTechniqueName::equalsIgnoreCase)
                .findFirst().orElse(NO_TECHNIQUE);
        select.setSelected(selected);
        select.setDisabled(domain.antiDomain);
        select.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                domain.requiredTechniqueName = NO_TECHNIQUE.equals(select.getSelected())
                    ? null : select.getSelected();
                if (domain.requiredTechniqueName == null) {
                    domain.burnoutRounds = 0;
                    domain.burnoutTicks = 0;
                }
                markDirty();
            }
        });
        return select;
    }

    private CheckBox checkBox(String label, boolean selected, Consumer<Boolean> onChange) {
        CheckBox checkBox = new CheckBox(" " + label, skin);
        checkBox.setChecked(selected);
        checkBox.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.audio().play(SoundCue.UI_TOGGLE);
                onChange.accept(checkBox.isChecked());
                markDirty();
            }
        });
        return checkBox;
    }

    private <E extends Enum<E>> void addEnumRow(
        Table section,
        String label,
        Class<E> enumType,
        String current,
        Consumer<String> onChange
    ) {
        EnumSelectBox<E> select = new EnumSelectBox<>(
            enumType, current, false, value -> {
                onChange.accept(value);
                markDirty();
            }, skin, uiProfile);
        section.add(labelledRow(label, select)).growX().row();
    }

    private Table decimalField(
        String label,
        Double initial,
        Consumer<Double> onChange,
        boolean allowNegative
    ) {
        Table row = new Table(skin);
        addFormLabel(row, label);
        TextField field = new HoverTextField(
            initial == null ? "" : String.valueOf(initial), skin);
        field.setTextFieldFilter((textField, character) ->
            java.lang.Character.isDigit(character) || character == '.'
                || allowNegative && character == '-');
        field.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                try {
                    onChange.accept(Double.parseDouble(field.getText()));
                    markDirty();
                } catch (NumberFormatException ignored) { }
            }
        });
        row.add(field).left().width(uiProfile == com.jjktbf.graphics.ui.profile.UiProfile.WINDOWS
            ? 180f : 120f);
        row.add().growX();
        return row;
    }

    private String effectReferenceError(DomainData domain) {
        for (List<AbilityEffectData> channel : List.of(
            domain.sureHitEffects, domain.fieldEffects, domain.casterEffects,
            domain.barrierEffects, domain.procedureEffects)) {
            if (channel == null) continue;
            for (AbilityEffectData effect : channel) {
                if (effect == null || effect.type == null) continue;
                AbilityEffectType type;
                try { type = AbilityEffectType.fromName(effect.type); }
                catch (IllegalArgumentException exception) { continue; }
                if (type.uses(AbilityEffectParameter.MOVE_ID)
                    && moveRepo.findById(effect.moveId).isEmpty()) {
                    return "A Domain effect references a move that no longer exists.";
                }
                if (type.uses(AbilityEffectParameter.ABILITY_ID)
                    && abilityRepo.findById(effect.abilityId).isEmpty()) {
                    return "A Domain effect references an ability that no longer exists.";
                }
                if (type.uses(AbilityEffectParameter.CHARACTER_ID)
                    && characterRepo.findById(effect.characterId).isEmpty()) {
                    return "A Domain effect references a character that no longer exists.";
                }
            }
        }
        return null;
    }

    private static int value(Integer value) { return value == null ? 0 : value; }
}
