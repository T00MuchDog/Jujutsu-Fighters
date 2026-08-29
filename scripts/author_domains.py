"""Author the initial canonical Domain catalog and synthetic test content.

Adds:
- data/domains/all_domains.json: Unlimited Void, Malevolent Shrine,
  Chimera Shadow Garden, and the Simple Domain anti-Domain.
- Technique trees: new "Limitless"/"Shrine" techniques plus a DOMAIN node on
  the existing "Ten Shadows" technique.
- Domain-opening moves 000151-000153 and the ESTABLISH_DOMAIN row on Simple
  Domain move 000026.
- Synthetic test fighters 000022-000024 that own each Domain.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from jackson_json import dumps

ROOT = Path(__file__).resolve().parents[1]


def load(relative):
    return json.loads((ROOT / relative).read_text())


def save(relative, value):
    (ROOT / relative).write_text(dumps(value))


def row(effect_id, effect_type, **fields):
    result = {"effectId": effect_id, "type": effect_type}
    result.update(fields)
    return result


def stat_prerequisites(**minimums):
    prerequisites = {}
    for stat, minimum in minimums.items():
        if stat == "cursedTechniqueMastery":
            continue  # mirrored as the node's MASTERY rule, not a STAT rule
        prerequisites[stat] = minimum
    return prerequisites


def tree_node(node_id, domain_id, mastery, stats, x, y):
    prerequisites = [{"type": "MASTERY", "minimum": mastery}]
    for stat, minimum in stats.items():
        prerequisites.append({"type": "STAT", "stat": stat, "minimum": minimum})
    return {
        "id": node_id,
        "contentType": "DOMAIN",
        "contentId": domain_id,
        "x": x,
        "y": y,
        "prerequisites": prerequisites,
    }


# ---------------------------------------------------------------------------
# Domains
# ---------------------------------------------------------------------------

unlimited_void = {
    "id": "000000",
    "name": "Unlimited Void",
    "description": "A barrier-sealed Domain that floods captured enemies with infinite raw information, staggering their minds and dulling their bodies for as long as the barrier stands.",
    "requiredTechniqueName": "Limitless",
    "topology": "CLOSED",
    "capturePolicy": "ALL_ENEMIES",
    "recognitionPolicy": "ALL_MEMBERS",
    "entrantPolicy": "FOLLOW_SUMMONER",
    "protectionPolicy": "OWNER",
    "durationRounds": 1,
    "durationTicks": 30,
    "burnoutRounds": 2,
    "burnoutTicks": 0,
    "ceUpkeepPerTick": 2.0,
    "internalBarrierIntegrity": 120,
    "externalBarrierIntegrity": 120,
    "clashPressurePerTick": 25,
    "externalPressurePerTick": 0,
    "prerequisites": {
        "cursedTechniqueMastery": 90,
        "cursedEnergyOutput": 90,
        "cursedEnergyEfficiency": 80,
        "cursedEnergyReserves": 90,
        "jujutsuSkill": 85,
    },
    "sureHitEffects": [
        row("void-overload-cancel", "CANCEL_NEXT_MOVE",
            target="ENEMY", uses=1, durationRounds=1, durationTicks=0,
            domainTrigger="ON_ESTABLISH", domainAudience="ENEMY_MEMBERS",
            domainDeliveryClass="RULE", domainIntervalTicks=1,
            domainActivationChanceEnabled=False, domainActivationChance=1.0),
        row("void-overload-body", "APPLY_STATUS",
            target="ENEMY", stringValue="COMBAT_ABILITY_DECREASE",
            magnitude=25.0, durationRounds=-1, durationTicks=0,
            domainTrigger="ON_ESTABLISH", domainAudience="ENEMY_MEMBERS",
            domainDeliveryClass="RULE", domainIntervalTicks=1,
            domainActivationChanceEnabled=False, domainActivationChance=1.0),
        row("void-overload-senses", "APPLY_STATUS",
            target="ENEMY", stringValue="ACCURACY_DECREASE",
            magnitude=30.0, durationRounds=-1, durationTicks=0,
            domainTrigger="ON_ESTABLISH", domainAudience="ENEMY_MEMBERS",
            domainDeliveryClass="RULE", domainIntervalTicks=1,
            domainActivationChanceEnabled=False, domainActivationChance=1.0),
    ],
}

malevolent_shrine = {
    "id": "000001",
    "name": "Malevolent Shrine",
    "description": "An open-air Domain with no outer wall: boundless dismantling slashes sweep everything inside, and the edges grind against any closed barrier that dares to overlap them.",
    "requiredTechniqueName": "Shrine",
    "topology": "OPEN",
    "capturePolicy": "ALL_ENEMIES",
    "recognitionPolicy": "ALL_MEMBERS",
    "entrantPolicy": "FOLLOW_SUMMONER",
    "protectionPolicy": "OWNER",
    "durationRounds": 3,
    "durationTicks": 0,
    "burnoutRounds": 1,
    "burnoutTicks": 0,
    "ceUpkeepPerTick": 3.0,
    "internalBarrierIntegrity": 150,
    "externalBarrierIntegrity": 0,
    "clashPressurePerTick": 40,
    "externalPressurePerTick": 20,
    "prerequisites": {
        "cursedTechniqueMastery": 95,
        "cursedEnergyOutput": 95,
        "cursedEnergyEfficiency": 85,
        "cursedEnergyReserves": 95,
        "jujutsuSkill": 90,
    },
    "sureHitEffects": [
        row("shrine-opening-slashes", "DEAL_DIRECT_DAMAGE",
            target="ENEMY", valueMode="FLAT", intValue=30,
            domainTrigger="ON_ESTABLISH", domainAudience="ENEMY_MEMBERS",
            domainDeliveryClass="ATTACK", domainIntervalTicks=1,
            domainActivationChanceEnabled=False, domainActivationChance=1.0),
        row("shrine-continuous-slashes", "DEAL_DIRECT_DAMAGE",
            target="ENEMY", valueMode="FLAT", intValue=12,
            domainTrigger="EACH_TICK", domainAudience="ENEMY_MEMBERS",
            domainDeliveryClass="ATTACK", domainIntervalTicks=3,
            domainActivationChanceEnabled=False, domainActivationChance=1.0),
        row("shrine-exterior-graze", "DEAL_DIRECT_DAMAGE",
            target="ENEMY", valueMode="FLAT", intValue=6,
            domainTrigger="EACH_TICK", domainAudience="EXTERIOR_ENEMIES",
            domainDeliveryClass="ATTACK", domainIntervalTicks=3,
            domainActivationChanceEnabled=False, domainActivationChance=1.0),
    ],
}

chimera_shadow_garden = {
    "id": "000002",
    "name": "Chimera Shadow Garden",
    "description": "An incomplete Domain without a closing wall: the field floods with shadow, dragging at every enemy who steps in while hiding the caster's side. No guaranteed hit — the terrain itself is the threat.",
    "requiredTechniqueName": "Ten Shadows",
    "topology": "INCOMPLETE",
    "capturePolicy": "ALL_ACTIVE",
    "recognitionPolicy": "ALL_MEMBERS",
    "entrantPolicy": "FOLLOW_SUMMONER",
    "protectionPolicy": "OWNER_AND_ALLIES",
    "durationRounds": 2,
    "durationTicks": 0,
    "burnoutRounds": 1,
    "burnoutTicks": 0,
    "ceUpkeepPerTick": 1.5,
    "internalBarrierIntegrity": 60,
    "externalBarrierIntegrity": 0,
    "clashPressurePerTick": 10,
    "externalPressurePerTick": 5,
    "prerequisites": {
        "cursedTechniqueMastery": 60,
        "cursedEnergyOutput": 70,
        "cursedEnergyEfficiency": 65,
        "cursedEnergyReserves": 75,
        "jujutsuSkill": 60,
    },
    "fieldEffects": [
        row("garden-dragging-shadow", "APPLY_STATUS",
            target="ENEMY", stringValue="SPEED_DECREASE",
            magnitude=20.0, durationRounds=-1, durationTicks=0,
            domainTrigger="ON_MEMBER_ENTER", domainAudience="ENTERING_MEMBER",
            domainDeliveryClass="EFFECT", domainIntervalTicks=1,
            domainActivationChanceEnabled=False, domainActivationChance=1.0),
        row("garden-concealing-shadow", "APPLY_STATUS",
            target="SELF", stringValue="EVASION_INCREASE",
            magnitude=20.0, durationRounds=-1, durationTicks=0,
            domainTrigger="ON_ESTABLISH", domainAudience="ALLY_MEMBERS",
            domainDeliveryClass="EFFECT", domainIntervalTicks=1,
            domainActivationChanceEnabled=False, domainActivationChance=1.0),
    ],
}

simple_domain = {
    "id": "000003",
    "name": "Simple Domain",
    "description": "A hand-span of cursed-technique nullification. It intercepts technique contact arriving as an attack, effect, or information — but cannot answer a Domain whose sure hit is delivered as a rule. It falls the moment its owner swings, true to the New Shadow Style binding vow.",
    "antiDomain": True,
    "topology": "INCOMPLETE",
    "capturePolicy": "OWNER_AND_SELECTED",
    "recognitionPolicy": "ALL_MEMBERS",
    "entrantPolicy": "SNAPSHOT",
    "protectionPolicy": "OWNER",
    "durationRounds": -1,
    "durationTicks": 0,
    "burnoutRounds": 0,
    "burnoutTicks": 0,
    "ceUpkeepPerTick": 0.0,
    "internalBarrierIntegrity": 10,
    "externalBarrierIntegrity": 0,
    "clashPressurePerTick": 0,
    "externalPressurePerTick": 0,
    "counterType": "TECHNIQUE_CONTACT_NULLIFICATION",
    "counterPotency": 50,
    "counterUses": 1,
    "counterBreakOnOwnerMove": True,
}

existing = load("data/domains/all_domains.json")
if not existing:
    save("data/domains/all_domains.json",
         [unlimited_void, malevolent_shrine, chimera_shadow_garden, simple_domain])

# ---------------------------------------------------------------------------
# Techniques
# ---------------------------------------------------------------------------

techniques = load("data/techniques/all_techniques.json")

if not any(t.get("name") == "Limitless" for t in techniques):
    techniques.append({
    "id": "000007",
    "name": "Limitless",
    "description": "Cursed-technique test fixture: manipulation of space itself at infinite divides, culminating in the sealed Domain Unlimited Void.",
    "skillTree": [tree_node(
        "node-000000", "000000", 90,
        {"cursedEnergyOutput": 90, "cursedEnergyEfficiency": 80,
         "cursedEnergyReserves": 90, "jujutsuSkill": 85},
        x=40.0, y=40.0)],
})
if not any(t.get("name") == "Shrine" for t in techniques):
    techniques.append({
    "id": "000008",
    "name": "Shrine",
    "description": "Cursed-technique test fixture: the inherited dismantling and cleaving flame, projecting an open-air Domain that needs no barrier.",
    "skillTree": [tree_node(
        "node-000000", "000001", 95,
        {"cursedEnergyOutput": 95, "cursedEnergyEfficiency": 85,
         "cursedEnergyReserves": 95, "jujutsuSkill": 90},
        x=40.0, y=40.0)],
})
for technique in techniques:
    if technique.get("name") == "Ten Shadows" and not any(
            node.get("contentType") == "DOMAIN"
            and node.get("contentId") == "000002"
            for node in technique.get("skillTree", [])):
        technique["skillTree"].append(tree_node(
            "node-000010", "000002", 60,
            {"cursedEnergyOutput": 70, "cursedEnergyEfficiency": 65,
             "cursedEnergyReserves": 75, "jujutsuSkill": 60},
            x=40.0, y=40.0))

save("data/techniques/all_techniques.json", techniques)

# ---------------------------------------------------------------------------
# Moves
# ---------------------------------------------------------------------------

moves = load("data/moves/all_moves.json")


def opening_move(move_id, name, description, technique, domain_id, ce_cost,
                 mastery, output, efficiency, reserves, skill):
    return {
        "id": move_id,
        "name": name,
        "description": description,
        "requiredTechniqueId": technique,
        "tags": ["INNATE_TECHNIQUE", "CURSED_ENERGY", "UTILITY"],
        "basePower": 0,
        "hitComponents": [],
        "baseAccuracy": 1.0,
        "neverMiss": False,
        "guardBreak": False,
        "heavy": False,
        "potency": 0,
        "apCost": 10,
        "unleashPoint": 5,
        "baseCeCost": ce_cost,
        "hasCeCost": True,
        "minCeCost": 10,
        "maxCeCost": 1000,
        "isFreeMove": False,
        "moveCap": 1,
        "aoeTargetCount": 2,
        "defenseTargeting": "SELF",
        "defenseTargetCount": 2,
        "targeting": "DEFAULT",
        "mustBeGranted": False,
        "prerequisites": {
            "cursedTechniqueMastery": mastery,
            "cursedEnergyOutput": output,
            "cursedEnergyEfficiency": efficiency,
            "cursedEnergyReserves": reserves,
            "jujutsuSkill": skill,
        },
        "effects": [row(move_id + "-establish", "ESTABLISH_DOMAIN",
                        target="SELF", domainId=domain_id,
                        trigger="ON_FIRE",
                        condition={"type": "ALWAYS"})],
    }


if not any(m.get("id") == "000151" for m in moves):
    moves.append(opening_move(
    "000151", "Domain Expansion: Unlimited Void",
    "Seals the space around the enemy pair and drowns them in infinite information.",
    "Limitless", "000000", 320, 90, 90, 80, 90, 85))
if not any(m.get("id") == "000152" for m in moves):
    moves.append(opening_move(
    "000152", "Domain Expansion: Malevolent Shrine",
    "Projects an open-air shrine whose dismantling slashes sweep everything in reach.",
    "Shrine", "000001", 360, 95, 95, 85, 95, 90))
if not any(m.get("id") == "000153" for m in moves):
    moves.append(opening_move(
    "000153", "Domain Expansion: Chimera Shadow Garden",
    "Floods the field with shadow that drags enemies down and hides allies.",
    "Ten Shadows", "000002", 240, 60, 70, 65, 75, 60))

simple_domain_move = next(m for m in moves if m["id"] == "000026")
if not any(effect.get("type") == "ESTABLISH_DOMAIN"
           for effect in simple_domain_move["effects"]):
    simple_domain_move["effects"].append(row(
        "effect-000001", "ESTABLISH_DOMAIN",
        target="SELF", domainId="000003", trigger="ON_FIRE",
        condition={"type": "ALWAYS"}))

save("data/moves/all_moves.json", moves)

# ---------------------------------------------------------------------------
# Synthetic test fighters
# ---------------------------------------------------------------------------

characters = load("data/characters/all_characters.json")


def test_fighter(character_id, name, description, sprite, technique,
                 mastery, domain_id, opening_move_id):
    move_ids = ["000000", "000001", "000002", opening_move_id]
    return {
        "id": character_id,
        "name": name,
        "description": description,
        "spriteAsset": sprite,
        "type": "SORCERER",
        "directlySelectable": True,
        "innateTechniqueName": technique,
        "cursedTechniqueMastery": mastery,
        "vitality": 90,
        "strength": 80,
        "durability": 80,
        "speed": 90,
        "cursedEnergyReserves": 95,
        "cursedEnergyEfficiency": 90,
        "cursedEnergyOutput": 95,
        "jujutsuSkill": 90,
        "combatAbility": 85,
        "moveIds": move_ids,
        "availableMoveIds": move_ids,
        "abilityIds": [],
        "availableAbilityIds": [],
        "availableDomainIds": [domain_id],
    }


if not any(c.get("id") == "000022" for c in characters):
    characters.append(test_fighter(
    "000022", "Domain Test: Limitless Sorcerer",
    "Synthetic test fighter for the Unlimited Void Domain subsystem. Not a playable canon character.",
    "assets/sprites/characters/nanami_frontsprite.png", "Limitless",
    95, "000000", "000151"))
if not any(c.get("id") == "000023" for c in characters):
    characters.append(test_fighter(
    "000023", "Domain Test: Shrine Incarnate",
    "Synthetic test fighter for the Malevolent Shrine Domain subsystem. Not a playable canon character.",
    "assets/sprites/characters/hanami_frontsprite.png", "Shrine",
    99, "000001", "000152"))
if not any(c.get("id") == "000024" for c in characters):
    characters.append(test_fighter(
    "000024", "Domain Test: Ten Shadows Adept",
    "Synthetic test fighter for the Chimera Shadow Garden Domain subsystem. Not a playable canon character.",
    "assets/sprites/characters/megumi_frontsprite.png", "Ten Shadows",
    80, "000002", "000153"))

save("data/characters/all_characters.json", characters)

print("authored domains, techniques, moves, and test fighters")
