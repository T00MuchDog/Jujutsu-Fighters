package com.jjktbf.graphics.ui.battle;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.jjktbf.graphics.audio.SoundCue;
import com.jjktbf.graphics.multiplayer.TargetListSupport;
import com.jjktbf.graphics.ui.profile.BattleUiLayout;
import com.jjktbf.model.character.coded.CodedAbilityState;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.BattleTeam;
import com.jjktbf.model.combat.BattleTeamId;
import com.jjktbf.model.combat.CombatantId;
import com.jjktbf.model.combat.TeamBattlePlan;
import com.jjktbf.model.move.Move;
import com.jjktbf.multiplayer.protocol.ActionSegmentState;
import com.jjktbf.multiplayer.protocol.PlanPlacement;
import com.jjktbf.multiplayer.protocol.PlanState;
import com.jjktbf.multiplayer.protocol.StatusEffectState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Persistent per-combatant planning pages submitted as one atomic team plan. */
public final class TeamPlanningPanel {

    public record PageSpec(
        String actorId,
        String name,
        List<Move> moves,
        Map<String, Integer> ceCosts,
        int apBudget,
        int ceBudget,
        int maxCe,
        CodedAbilityState miraclesState,
        List<PlanningPanel.TargetOption> targets,
        PlanState restoredPlan,
        List<PlanningPanel.TargetOption> allies,
        List<CodedAbilityState> abilityStates,
        List<StatusEffectState> statusEffects
    ) {
        public PageSpec(
            String actorId,
            String name,
            List<Move> moves,
            Map<String, Integer> ceCosts,
            int apBudget,
            int ceBudget,
            int maxCe,
            CodedAbilityState miraclesState,
            List<PlanningPanel.TargetOption> targets,
            PlanState restoredPlan
        ) {
            this(actorId, name, moves, ceCosts, apBudget, ceBudget, maxCe, miraclesState,
                targets, restoredPlan, List.of(), List.of(), List.of());
        }
    }

    /** One fighter card in the six-member field/reserve party tray. */
    public record PartyMember(
        String actorId,
        String name,
        int currentHp,
        int maxHp,
        boolean active,
        boolean reserve,
        boolean defeated,
        int rosterOrder
    ) { }

    private static final class Page {
        private final String name;
        private final PlanningPanel panel;
        private final boolean switchEligible;
        private String switchTargetId;
        private String switchTargetName;

        private Page(String name, PlanningPanel panel, boolean switchEligible) {
            this.name = name;
            this.panel = panel;
            this.switchEligible = switchEligible;
        }

        private String name() { return name; }
        private PlanningPanel panel() { return panel; }
    }

    private final BattleTeamId teamId;
    private final int gridLength;
    private final BattleUiAssets ui;
    private final List<Page> pages = new ArrayList<>();
    private final List<PartyMember> party = new ArrayList<>();
    private final Rectangle previousBounds = new Rectangle();
    private final Rectangle nextBounds = new Rectangle();
    private final Rectangle pageLabelBounds = new Rectangle();
    private final Rectangle switchBounds = new Rectangle();
    private final Rectangle partyPanelBounds = new Rectangle();
    private final Rectangle cancelSwitchBounds = new Rectangle();
    private final List<Rectangle> partyCardBounds = new ArrayList<>();
    private int activePage;
    private float screenWidth;
    private float screenHeight;
    private float textGeometryScale = BattleUiLayout.defaults().planner.textGeometryScale;
    private boolean submitted;
    private boolean readOnly;
    private boolean switchPanelOpen;
    private Runnable onConfirm = () -> { };
    private Consumer<SoundCue> soundPlayer = cue -> { };
    private float viewportScale = 1f;
    private float viewportOffsetX;
    private float viewportOffsetY;
    private float physicalViewportHeight;

    public TeamPlanningPanel(
        int gridLength,
        List<BattleCombatant> controlled,
        BattleState state,
        BattleUiAssets ui,
        float screenWidth,
        float screenHeight
    ) {
        if (controlled == null || controlled.isEmpty()) {
            throw new IllegalArgumentException("At least one controlled combatant is required");
        }
        this.teamId = controlled.get(0).getTeamId();
        this.gridLength = gridLength;
        this.ui = ui;
        BattleTeam team = state.teamOf(this.teamId);
        if (team != null) {
            for (BattleCombatant fighter : team.all()) {
                if (!fighter.isFighter()) continue;
                party.add(new PartyMember(
                    fighter.getInstanceId().value(),
                    fighter.getCharacter().getName(),
                    fighter.getCurrentHp(),
                    fighter.getMaxHp(),
                    fighter.isActive(),
                    fighter.isReserve(),
                    fighter.isDefeated(),
                    fighter.getRosterOrder()));
            }
        }
        for (BattleCombatant actor : controlled) {
            PlanningPanel panel = new PlanningPanel(
                gridLength,
                actor,
                state.activeEnemiesOf(actor),
                ui,
                screenWidth,
                screenHeight);
            panel.setBattleState(state);
            panel.setAllyTargets(state.activeAlliesOf(actor));
            addPage(actor.getCharacter().getName(), panel,
                actor.isFighter() && party.stream().anyMatch(PartyMember::reserve));
        }
        resize(screenWidth, screenHeight);
    }

    public TeamPlanningPanel(
        BattleTeamId teamId,
        int gridLength,
        List<PageSpec> specs,
        BattleUiAssets ui,
        float screenWidth,
        float screenHeight
    ) {
        this(teamId, gridLength, specs, List.of(), ui, screenWidth, screenHeight);
    }

    public TeamPlanningPanel(
        BattleTeamId teamId,
        int gridLength,
        List<PageSpec> specs,
        List<PartyMember> party,
        BattleUiAssets ui,
        float screenWidth,
        float screenHeight
    ) {
        if (specs == null || specs.isEmpty()) {
            throw new IllegalArgumentException("At least one planning page is required");
        }
        this.teamId = teamId;
        this.gridLength = gridLength;
        this.ui = ui;
        if (party != null) this.party.addAll(party);
        for (PageSpec spec : specs) {
            PlanningPanel panel = new PlanningPanel(
                gridLength,
                spec.actorId(),
                spec.targets(),
                spec.moves(),
                spec.ceCosts(),
                spec.apBudget(),
                spec.ceBudget(),
                spec.maxCe(),
                spec.miraclesState(),
                ui,
                screenWidth,
                screenHeight);
            panel.setAllyOptions(spec.allies());
            panel.setAbilityStates(spec.abilityStates());
            panel.setStatusEffects(spec.statusEffects());
            restorePlan(panel, spec);
            addPage(spec.name(), panel,
                this.party.stream().anyMatch(member -> member.actorId().equals(spec.actorId())
                    && member.active())
                    && this.party.stream().anyMatch(PartyMember::reserve));
        }
        resize(screenWidth, screenHeight);
    }

    private void addPage(String name, PlanningPanel panel, boolean switchEligible) {
        panel.setActorName(name);
        panel.setAllowManualUnlock(true);
        panel.setOnConfirm(this::pageLocked);
        panel.setSoundPlayer(soundPlayer);
        pages.add(new Page(name, panel, switchEligible));
    }

    private static void restorePlan(PlanningPanel panel, PageSpec spec) {
        PlanState restored = spec.restoredPlan();
        if (restored == null) return;
        Map<String, Move> moves = new LinkedHashMap<>();
        for (Move move : spec.moves()) moves.put(move.getId(), move);
        List<ActionSegmentState> segments = new ArrayList<>(restored.queuedSegments());
        segments.addAll(restored.resolvedSegments());
        for (ActionSegmentState segment : segments) {
            Move move = moves.get(segment.moveId());
            if (move != null) {
                panel.restorePlacement(
                    move, segment.startTick(), segment.ceCost(),
                    TargetListSupport.targetIds(segment), segment.apCost(),
                    segment.fireTick() - segment.startTick() + 1,
                    segment.reinforced(), segment.reinforcementCeCost());
            }
        }
    }

    public void setOnConfirm(Runnable onConfirm) {
        this.onConfirm = onConfirm == null ? () -> { } : onConfirm;
    }

    public void setSoundPlayer(Consumer<SoundCue> soundPlayer) {
        this.soundPlayer = soundPlayer == null ? cue -> { } : soundPlayer;
        for (Page page : pages) page.panel().setSoundPlayer(this.soundPlayer);
    }

    private void pageLocked() {
        if (submitted || pages.stream().anyMatch(page -> !pageReady(page))) return;
        submitted = true;
        for (Page page : pages) page.panel().setAllowManualUnlock(false);
        onConfirm.run();
    }

    public TeamBattlePlan getTeamPlan() {
        TeamBattlePlan teamPlan = new TeamBattlePlan(teamId, gridLength);
        for (Page page : pages) {
            CombatantId actor = new CombatantId(page.panel().getActorId());
            if (page.switchTargetId != null) {
                teamPlan.switchTo(actor, new CombatantId(page.switchTargetId));
            } else {
                teamPlan.put(actor, page.panel().getPlan());
            }
        }
        return teamPlan;
    }

    public List<PlanPlacement> getPlacements() {
        return pages.stream()
            .filter(page -> page.switchTargetId == null)
            .flatMap(page -> page.panel().getPlacements().stream())
            .toList();
    }

    public List<com.jjktbf.multiplayer.protocol.SwitchSelection> getSwitches() {
        return pages.stream()
            .filter(page -> page.switchTargetId != null)
            .map(page -> new com.jjktbf.multiplayer.protocol.SwitchSelection(
                page.panel().getActorId(), page.switchTargetId))
            .toList();
    }

    public void lock() {
        for (Page page : pages) {
            page.panel().setAllowManualUnlock(false);
            page.panel().lock();
        }
        submitted = true;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        if (readOnly) switchPanelOpen = false;
        for (Page page : pages) {
            page.panel().setReadOnly(readOnly || page.switchTargetId != null);
        }
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setActionButtonShifted(boolean actionButtonShifted) {
        for (Page page : pages) page.panel().setActionButtonShifted(actionButtonShifted);
    }

    public void unlock() {
        for (Page page : pages) {
            page.panel().setAllowManualUnlock(true);
            if (page.switchTargetId == null) page.panel().unlock();
            page.panel().setReadOnly(page.switchTargetId != null);
        }
        submitted = false;
    }

    public void resize(float width, float height) {
        screenWidth = width;
        screenHeight = height;
        for (Page page : pages) page.panel().resize(width, height);
        BattleCanvas canvas = BattleCanvas.fit(width, height);
        setViewportTransform(canvas.scale(), canvas.offsetX(),
            canvas.offsetY(BattleCanvas.Anchor.BOTTOM), canvas.viewportHeight());
        layoutNavigation();
        layoutSwitchControls();
    }

    public void setLayout(BattleUiLayout battleLayout) {
        if (battleLayout == null) return;
        textGeometryScale = battleLayout.planner.textGeometryScale;
        for (Page page : pages) {
            page.panel().setLayout(battleLayout);
        }
        layoutNavigation();
        layoutSwitchControls();
    }

    /** Applies the fixed-canvas transform to navigation and every planning page. */
    public void setViewportTransform(
        float scale,
        float offsetX,
        float offsetY,
        float physicalHeight
    ) {
        viewportScale = Math.max(0.0001f, scale);
        viewportOffsetX = offsetX;
        viewportOffsetY = offsetY;
        physicalViewportHeight = Math.max(1f, physicalHeight);
        for (Page page : pages) {
            page.panel().setViewportTransform(
                viewportScale, viewportOffsetX, viewportOffsetY, physicalViewportHeight);
        }
    }

    private void layoutNavigation() {
        float headerY = BattleCanvas.BOTTOM_SECTION_HEIGHT - 32f;
        previousBounds.set(72f, headerY, 72f, 32f);
        nextBounds.set(162f, headerY, 72f, 32f);
        pageLabelBounds.set(252f, headerY, 126f, 32f);
    }

    private void layoutSwitchControls() {
        Rectangle lock = active().layoutSnapshot().lock();
        float gap = 18f;
        float width = Math.max(180f, Math.min(lock.width, 260f));
        switchBounds.set(lock.x - width - gap, lock.y, width, lock.height);

        float logicalWidth = BattleCanvas.WIDTH;
        float logicalHeight = BattleCanvas.HEIGHT;
        float panelWidth = Math.min(900f, Math.max(420f, logicalWidth - 48f));
        float panelHeight = Math.min(520f, Math.max(300f, logicalHeight * 0.58f));
        partyPanelBounds.set(
            (logicalWidth - panelWidth) / 2f,
            (logicalHeight - panelHeight) / 2f,
            panelWidth,
            panelHeight);
        cancelSwitchBounds.set(
            partyPanelBounds.x + partyPanelBounds.width - 172f,
            partyPanelBounds.y + 18f,
            150f,
            42f);
        partyCardBounds.clear();
        float cardGap = 14f;
        float sidePad = 22f;
        float topPad = 74f;
        float bottomPad = 78f;
        float cardWidth = (partyPanelBounds.width - sidePad * 2f - cardGap * 2f) / 3f;
        float cardHeight = (partyPanelBounds.height - topPad - bottomPad - cardGap) / 2f;
        for (int slot = 0; slot < party.size(); slot++) {
            int column = slot % 3;
            int row = slot / 3;
            partyCardBounds.add(new Rectangle(
                partyPanelBounds.x + sidePad + column * (cardWidth + cardGap),
                partyPanelBounds.y + partyPanelBounds.height - topPad
                    - (row + 1) * cardHeight - row * cardGap,
                cardWidth,
                cardHeight));
        }
    }

    private float scaled(float value) {
        return value * textGeometryScale;
    }

    public void draw(Batch batch, BitmapFont font, BitmapFont titleFont, BitmapFont statFont) {
        active().draw(batch, font, titleFont, statFont);
        batch.begin();
        if (pages.size() > 1) {
            batch.setColor(new Color(0.38f, 0.41f, 0.46f, 1f));
            batch.draw(ui.pixel, previousBounds.x, previousBounds.y,
                previousBounds.width, previousBounds.height);
            batch.draw(ui.pixel, nextBounds.x, nextBounds.y, nextBounds.width, nextBounds.height);
            batch.setColor(Color.WHITE);
            font.setColor(Color.WHITE);
            drawCentered(batch, font, "<", previousBounds);
            drawCentered(batch, font, ">", nextBounds);
            drawCentered(batch, font, (activePage + 1) + "/" + pages.size(), pageLabelBounds);
            if (readOnly) {
                batch.setColor(0.32f, 0.32f, 0.34f, 0.62f);
                batch.draw(ui.pixel,
                    previousBounds.x,
                    previousBounds.y,
                    pageLabelBounds.x + pageLabelBounds.width - previousBounds.x,
                    previousBounds.height);
                batch.setColor(Color.WHITE);
            }
        }
        drawSwitchButton(batch, font);
        if (switchPanelOpen) drawPartyPanel(batch, font, titleFont);
        batch.end();
    }

    private void drawSwitchButton(Batch batch, BitmapFont font) {
        Page page = pages.get(activePage);
        if (!page.switchEligible) return;
        boolean enabled = !readOnly && !submitted;
        batch.setColor(enabled
            ? new Color(0.82f, 0.22f, 0.18f, 1f)
            : new Color(0.30f, 0.31f, 0.34f, 1f));
        batch.draw(ui.pixel, switchBounds.x, switchBounds.y, switchBounds.width, switchBounds.height);
        batch.setColor(Color.WHITE);
        String label = page.switchTargetName == null
            ? "SWITCH" : "SWITCH: " + ellipsize(font, page.switchTargetName,
                Math.max(1f, switchBounds.width - 18f));
        drawCentered(batch, font, label, switchBounds);
    }

    private void drawPartyPanel(Batch batch, BitmapFont font, BitmapFont titleFont) {
        float logicalWidth = BattleCanvas.WIDTH + 2f * viewportOffsetX / viewportScale;
        float logicalHeight = physicalViewportHeight / viewportScale;
        batch.setColor(0.02f, 0.03f, 0.06f, 0.78f);
        batch.draw(ui.pixel, -viewportOffsetX / viewportScale, 0f, logicalWidth, logicalHeight);
        batch.setColor(Color.WHITE);
        ui.palette.draw(batch, partyPanelBounds.x, partyPanelBounds.y,
            partyPanelBounds.width, partyPanelBounds.height);
        titleFont.setColor(BattleUiAssets.YELLOW);
        titleFont.draw(batch, "CHOOSE A RESERVE",
            partyPanelBounds.x + 22f,
            partyPanelBounds.y + partyPanelBounds.height - 24f);
        font.setColor(new Color(0.72f, 0.80f, 0.95f, 1f));
        font.draw(batch, "Switching uses this fighter's entire round.",
            partyPanelBounds.x + 22f,
            partyPanelBounds.y + partyPanelBounds.height - 51f);

        for (int index = 0; index < Math.min(party.size(), partyCardBounds.size()); index++) {
            PartyMember member = party.get(index);
            Rectangle bounds = partyCardBounds.get(index);
            boolean selectedElsewhere = reserveSelectedElsewhere(member.actorId());
            boolean selectable = member.reserve() && !member.defeated() && !selectedElsewhere;
            batch.setColor(selectable
                ? new Color(0.96f, 0.95f, 0.89f, 1f)
                : new Color(0.48f, 0.49f, 0.53f, 1f));
            ui.card.draw(batch, bounds.x, bounds.y, bounds.width, bounds.height);
            batch.setColor(Color.WHITE);

            font.setColor(BattleUiAssets.TEXT);
            font.draw(batch, ellipsize(font, member.name(), bounds.width - 24f),
                bounds.x + 12f, bounds.y + bounds.height - 18f);
            String status = member.defeated() ? "FAINTED"
                : member.active() ? "ON FIELD"
                : selectedElsewhere ? "ALREADY PICKED" : "RESERVE";
            font.setColor(selectable ? BattleUiAssets.CURSED_ENERGY : BattleUiAssets.MUTED);
            font.draw(batch, status, bounds.x + 12f, bounds.y + bounds.height - 45f);

            float hpWidth = Math.max(1f, bounds.width - 24f);
            float hpFraction = member.maxHp() <= 0 ? 0f
                : Math.max(0f, Math.min(1f, member.currentHp() / (float) member.maxHp()));
            batch.setColor(new Color(0.18f, 0.20f, 0.24f, 1f));
            batch.draw(ui.pixel, bounds.x + 12f, bounds.y + 22f, hpWidth, 12f);
            batch.setColor(hpFraction > 0.5f
                ? new Color(0.25f, 0.76f, 0.35f, 1f)
                : hpFraction > 0.2f
                    ? new Color(0.96f, 0.70f, 0.18f, 1f)
                    : new Color(0.88f, 0.20f, 0.18f, 1f));
            batch.draw(ui.pixel, bounds.x + 12f, bounds.y + 22f,
                hpWidth * hpFraction, 12f);
            batch.setColor(Color.WHITE);
            font.setColor(BattleUiAssets.TEXT);
            font.draw(batch, "HP " + member.currentHp() + "/" + member.maxHp(),
                bounds.x + 12f, bounds.y + 50f);
        }

        batch.setColor(new Color(0.35f, 0.38f, 0.44f, 1f));
        batch.draw(ui.pixel, cancelSwitchBounds.x, cancelSwitchBounds.y,
            cancelSwitchBounds.width, cancelSwitchBounds.height);
        batch.setColor(Color.WHITE);
        drawCentered(batch, font,
            pages.get(activePage).switchTargetId == null ? "CANCEL" : "KEEP BATTLING",
            cancelSwitchBounds);
    }

    private static void drawCentered(Batch batch, BitmapFont font, String value, Rectangle bounds) {
        GlyphLayout glyph = new GlyphLayout(font, value);
        font.draw(batch, value,
            bounds.x + (bounds.width - glyph.width) / 2f,
            bounds.y + (bounds.height + glyph.height) / 2f);
    }

    private static String ellipsize(BitmapFont font, String value, float maximumWidth) {
        if (new GlyphLayout(font, value).width <= maximumWidth) return value;
        String suffix = "...";
        if (new GlyphLayout(font, suffix).width > maximumWidth) return "";
        String result = value;
        while (result.length() > 1
            && new GlyphLayout(font, result + suffix).width > maximumWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + suffix;
    }

    record HeaderRegions(
        Rectangle previous,
        Rectangle next,
        Rectangle pageLabel
    ) { }

    record SwitchRegions(
        Rectangle button,
        Rectangle panel,
        List<Rectangle> cards,
        boolean open
    ) { }

    HeaderRegions headerRegions() {
        return new HeaderRegions(
            new Rectangle(previousBounds),
            new Rectangle(nextBounds),
            new Rectangle(pageLabelBounds));
    }

    SwitchRegions switchRegions() {
        return new SwitchRegions(
            new Rectangle(switchBounds),
            new Rectangle(partyPanelBounds),
            partyCardBounds.stream().map(Rectangle::new).toList(),
            switchPanelOpen);
    }

    public InputAdapter inputProcessor() {
        return new TeamPlanningInputProcessor();
    }

    public int pageCount() { return pages.size(); }
    public int activePageIndex() { return activePage; }
    public String activePageName() { return pages.get(activePage).name(); }
    public PlanningPanel activePlanningPanel() { return active(); }
    public String activeActorId() { return active().getActorId(); }

    public void previousPage() {
        if (pages.size() > 1) {
            activePage = (activePage - 1 + pages.size()) % pages.size();
            switchPanelOpen = false;
            layoutSwitchControls();
        }
    }

    public void nextPage() {
        if (pages.size() > 1) {
            activePage = (activePage + 1) % pages.size();
            switchPanelOpen = false;
            layoutSwitchControls();
        }
    }

    private PlanningPanel active() {
        return pages.get(activePage).panel();
    }

    private static boolean pageReady(Page page) {
        return page.switchTargetId != null || page.panel().isConfirmed();
    }

    private boolean reserveSelectedElsewhere(String actorId) {
        for (int index = 0; index < pages.size(); index++) {
            if (index != activePage && actorId.equals(pages.get(index).switchTargetId)) return true;
        }
        return false;
    }

    private void selectReserve(PartyMember member) {
        Page page = pages.get(activePage);
        page.switchTargetId = member.actorId();
        page.switchTargetName = member.name();
        page.panel().lock();
        page.panel().setReadOnly(true);
        switchPanelOpen = false;
        soundPlayer.accept(SoundCue.UI_PLAN_LOCK);
        pageLocked();
    }

    private void cancelSwitch() {
        Page page = pages.get(activePage);
        page.switchTargetId = null;
        page.switchTargetName = null;
        page.panel().setReadOnly(false);
        page.panel().unlock();
        switchPanelOpen = false;
    }

    private final class TeamPlanningInputProcessor extends InputAdapter {
        @Override public boolean keyDown(int keycode) {
            if (readOnly) return false;
            if (switchPanelOpen) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                    switchPanelOpen = false;
                    return true;
                }
                return true;
            }
            if (keycode == Input.Keys.S && !submitted
                && pages.get(activePage).switchEligible) {
                switchPanelOpen = true;
                soundPlayer.accept(SoundCue.UI_CONFIRM);
                return true;
            }
            if (keycode == Input.Keys.LEFT) {
                previousPage();
                return true;
            }
            if (keycode == Input.Keys.RIGHT) {
                nextPage();
                return true;
            }
            return active().inputProcessor().keyDown(keycode);
        }

        @Override public boolean touchDown(int x, int y, int pointer, int button) {
            if (readOnly) return false;
            float plannerX = (x - viewportOffsetX) / viewportScale;
            float plannerY = (physicalViewportHeight - y - viewportOffsetY) / viewportScale;
            if (button == Input.Buttons.LEFT && switchPanelOpen) {
                if (cancelSwitchBounds.contains(plannerX, plannerY)) {
                    if (pages.get(activePage).switchTargetId == null) {
                        switchPanelOpen = false;
                    } else {
                        cancelSwitch();
                    }
                    soundPlayer.accept(SoundCue.UI_BACK);
                    return true;
                }
                for (int index = 0; index < Math.min(party.size(), partyCardBounds.size()); index++) {
                    PartyMember member = party.get(index);
                    if (partyCardBounds.get(index).contains(plannerX, plannerY)
                        && member.reserve() && !member.defeated()
                        && !reserveSelectedElsewhere(member.actorId())) {
                        selectReserve(member);
                        return true;
                    }
                }
                return true;
            }
            if (button == Input.Buttons.LEFT && !submitted
                && pages.get(activePage).switchEligible
                && switchBounds.contains(plannerX, plannerY)) {
                switchPanelOpen = true;
                soundPlayer.accept(SoundCue.UI_CONFIRM);
                return true;
            }
            if (button == Input.Buttons.LEFT && pages.size() > 1) {
                if (previousBounds.contains(plannerX, plannerY)) {
                    previousPage();
                    soundPlayer.accept(SoundCue.UI_CONFIRM);
                    return true;
                }
                if (nextBounds.contains(plannerX, plannerY)) {
                    nextPage();
                    soundPlayer.accept(SoundCue.UI_CONFIRM);
                    return true;
                }
            }
            return active().inputProcessor().touchDown(x, y, pointer, button);
        }

        @Override public boolean touchDragged(int x, int y, int pointer) {
            if (readOnly || switchPanelOpen) return false;
            return active().inputProcessor().touchDragged(x, y, pointer);
        }

        @Override public boolean touchUp(int x, int y, int pointer, int button) {
            if (readOnly || switchPanelOpen) return false;
            return active().inputProcessor().touchUp(x, y, pointer, button);
        }

        @Override public boolean mouseMoved(int x, int y) {
            if (switchPanelOpen) return false;
            return active().inputProcessor().mouseMoved(x, y);
        }

        @Override public boolean scrolled(float amountX, float amountY) {
            if (readOnly || switchPanelOpen) return false;
            return active().inputProcessor().scrolled(amountX, amountY);
        }
    }
}
