#!/usr/bin/env python3
"""Splice Mahito (Vs. Mahito Arc) content into the canonical data JSON files.

Renders new entries in the same Jackson pretty-print style as the existing
files (2-space indent, ' : ' separator, '[ ]' / '{ }' for empty containers)
and inserts them before the closing bracket, leaving existing content bytes
untouched.
"""
import json
import sys

DATA = "/Users/zgranell/Desktop/Personnal/Coding/JJKTBF/data"


def render(entry):
    text = json.dumps(entry, indent=2, separators=(",", " : "), ensure_ascii=True)
    return text.replace("[]", "[ ]").replace("{}", "{ }")


def splice(path, entries, key):
    with open(path, "r", encoding="utf-8") as handle:
        raw = handle.read()
    existing = json.loads(raw)
    ids = {e["id"] for e in existing}
    fresh = [e for e in entries if e["id"] not in ids]
    if not fresh:
        print(f"{path}: nothing to add")
        return
    stripped = raw.rstrip()
    if not stripped.endswith("]"):
        raise SystemExit(f"{path}: unexpected file ending")
    body = stripped[:-1].rstrip()
    if not body.endswith("}"):
        raise SystemExit(f"{path}: unexpected array body ending")
    addition = ",\n".join(render(e) for e in fresh)
    if body.endswith("["):
        # empty array file
        new = body + addition + "\n]"
    else:
        new = body + ",\n" + addition + "\n]"
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(new)
    # validate round-trip
    reloaded = json.load(open(path, "r", encoding="utf-8"))
    assert len(reloaded) == len(existing) + len(fresh)
    print(f"{path}: added {len(fresh)} entries ({', '.join(e['id'] for e in fresh)})")


# ─── Abilities ────────────────────────────────────────────────────────────────

abilities = [
    {
        "id": "000060",
        "name": "Maintaining the Soul",
        "flavourText": "The soul's shape is the truth of the body; Mahito forces his flesh to conform to it without pause.",
        "mechanicText": "Drain 2 CE per TICK. Each non-SOUL damage instance is restored at a CE cost scaled to the damage dealt: 10 CE up to 10 damage, 30 up to 30, 50 up to 50, 100 up to 100, and 300 beyond. SOUL damage and lethal blows are never restored.",
        "category": "PASSIVE",
        "sourceType": "TECHNIQUE",
        "sourceValue": "Idle Transfiguration",
        "effects": [
            {
                "effectId": "effect-000000",
                "type": "CODED",
                "codedAbilityKey": "IDLE_TRANSFIGURATION",
                "codedFeature": "MAINTAINING_THE_SOUL",
                "codedParameters": {
                    "ceDrainPerTick": 2
                }
            }
        ],
        "masteryThreshold": 0
    },
    {
        "id": "000061",
        "name": "Soul Manipulation",
        "flavourText": "To touch a soul is to hold it - and a touch is all Idle Transfiguration needs.",
        "mechanicText": "Successful MELEE attacks have a 5% chance to attempt SOUL MANIPULATION. Success transfigures the target instantly. Failure teaches the target's shape: each failed attempt adds a permanent stack, and every later attempt is harder to resist.",
        "category": "PASSIVE",
        "sourceType": "TECHNIQUE",
        "sourceValue": "Idle Transfiguration",
        "effects": [
            {
                "effectId": "effect-000000",
                "type": "CODED",
                "codedAbilityKey": "IDLE_TRANSFIGURATION",
                "codedFeature": "SOUL_MANIPULATION",
                "codedParameters": {
                    "procChancePercent": 5,
                    "baseSuccessPercent": 5,
                    "ctmSuccessPerTenPoints": 1,
                    "successPerStackPercent": 12,
                    "resistPerTenCe": 1,
                    "minSuccessPercent": 1,
                    "maxSuccessPercent": 95
                }
            }
        ],
        "masteryThreshold": 0
    },
    {
        "id": "000062",
        "name": "Malleable Body",
        "flavourText": "A body with no fixed shape keeps no wound it does not want to keep.",
        "mechanicText": "Bodily injury statuses are shed whenever *ability:000060* restores the associated damage.",
        "category": "PASSIVE",
        "sourceType": "TECHNIQUE",
        "sourceValue": "Idle Transfiguration",
        "effects": [
            {
                "effectId": "effect-000000",
                "type": "CODED",
                "codedAbilityKey": "IDLE_TRANSFIGURATION",
                "codedFeature": "MALLEABLE_BODY"
            }
        ],
        "masteryThreshold": 0
    },
    {
        "id": "000063",
        "name": "Transfigured Human Stockpile",
        "flavourText": "Idle Transfiguration leaves broken people behind, and broken people are material.",
        "mechanicText": "Begin battle with 8 TRANSFIGURED HUMANS. Certain moves consume them.",
        "category": "PASSIVE",
        "sourceType": "TECHNIQUE",
        "sourceValue": "Idle Transfiguration",
        "effects": [
            {
                "effectId": "effect-000000",
                "type": "DEFINE_BOUNDED_RESOURCE",
                "resourceKey": "TRANSFIGURED_HUMANS",
                "resourceLabel": "Transfigured Humans",
                "resourceCapacity": 8,
                "resourceStartValue": 8
            }
        ],
        "masteryThreshold": 0
    },
]

# ─── Characters ───────────────────────────────────────────────────────────────

characters = [
    {
        "id": "000021",
        "name": "Mahito (Vs. Mahito Arc)",
        "description": "A special-grade cursed spirit born from humanity's hatred of one another. Mahito toys with bodies and souls alike: his flesh has no fixed shape, his touch rewrites souls, and his awakened Domain closes around everyone.",
        "type": "CURSED_SPIRIT",
        "directlySelectable": True,
        "innateTechniqueName": "Idle Transfiguration",
        "equippedWeaponTypes": [],
        "equippedCursedToolIds": [],
        "vitality": 105,
        "strength": 75,
        "durability": 85,
        "speed": 105,
        "cursedEnergyReserves": 145,
        "cursedEnergyEfficiency": 110,
        "cursedEnergyOutput": 110,
        "jujutsuSkill": 145,
        "combatAbility": 65,
        "cursedTechniqueMastery": 165,
        "moveIds": [
            "000100", "000101", "000102", "000104", "000105", "000106",
            "000112", "000113", "000114", "000116", "000120", "000121",
            "000140", "000141", "000142", "000143", "000144", "000145",
            "000146", "000147", "000148", "000149", "000150"
        ],
        "moveSetIds": [
            "000100", "000101", "000102", "000104", "000105", "000106",
            "000112", "000113", "000114", "000116", "000120", "000121",
            "000140", "000141", "000143"
        ],
        "availableMoveIds": [
            "000140", "000141", "000142", "000143", "000144", "000145",
            "000146", "000147", "000148", "000149", "000150"
        ],
        "abilityIds": [
            "000049", "000012", "000060", "000061", "000062", "000063"
        ],
        "availableAbilityIds": [
            "000060", "000061", "000062", "000063"
        ],
        "availableDomainIds": []
    },
    {
        "id": "000022",
        "name": "Transfigured Human",
        "description": "A human reshaped by Idle Transfiguration into a shambling, hostile thing.",
        "type": "SHIKIGAMI",
        "directlySelectable": False,
        "baseCeDrainPerTick": 0.1,
        "equippedWeaponTypes": [],
        "equippedCursedToolIds": [],
        "vitality": 30,
        "strength": 35,
        "durability": 30,
        "speed": 45,
        "cursedEnergyReserves": 30,
        "cursedEnergyEfficiency": 30,
        "cursedEnergyOutput": 25,
        "jujutsuSkill": 15,
        "combatAbility": 20,
        "cursedTechniqueMastery": 0,
        "moveIds": ["000000", "000001"],
        "availableMoveIds": ["000000", "000001"],
        "abilityIds": [],
        "availableAbilityIds": [],
        "availableDomainIds": []
    },
]

# ─── Moves ────────────────────────────────────────────────────────────────────

ALWAYS = {"type": "ALWAYS"}
TECHNIQUE = "Idle Transfiguration"


def attack_component(power, tags, accuracy, soul=False):
    component = {
        "basePower": power,
        "tags": tags,
        "delayTicks": 0,
        "requiresPreviousConnection": False,
        "avoidable": True,
        "baseAccuracy": accuracy,
        "onHitEffects": [],
        "reinforcementEligible": False,
        "reinforcementBonusPower": 0,
        "soulDamage": soul
    }
    return component


def base_move(mid, name, description):
    return {
        "id": mid,
        "name": name,
        "description": description,
        "tags": [],
        "basePower": 0,
        "hitComponents": [],
        "baseAccuracy": 1.0,
        "neverMiss": False,
        "guardBreak": False,
        "heavy": False,
        "potency": 1,
        "apCost": 8,
        "unleashPoint": 4,
        "baseCeCost": 0,
        "hasCeCost": True,
        "minCeCost": 0,
        "maxCeCost": 0,
        "canBeReinforced": False,
        "reinforcementBaseCeCost": 0,
        "reinforcementMinCeCost": 0,
        "reinforcementMaxCeCost": 0,
        "reinforcementDefenseType": "NONE",
        "reinforcementDefenseValue": 0,
        "defenseType": "NONE",
        "blockStyle": "PERCENTAGE",
        "blockDuration": 0,
        "blockDamageReduction": 100,
        "blockFlatReduction": 0,
        "dodgeChance": 0,
        "dodgeScope": "BOTH",
        "parryStaggerTicks": 0,
        "defenseTiming": "FIXED",
        "defenseUses": 0,
        "onHitEffects": [],
        "selfEffects": [],
        "onBlockEffects": [],
        "onParryEffects": [],
        "onDodgeEffects": [],
        "effects": [],
        "prerequisites": {},
        "isFreeMove": False,
        "moveCap": 0,
        "aoeTargetCount": 2,
        "defenseTargeting": "SELF",
        "defenseTargetCount": 2,
        "targeting": "DEFAULT",
        "mustBeGranted": False,
        "parry": False,
        "dodge": False,
        "anyBlock": False,
        "defenceAttackHybrid": False,
        "percentageBlock": False,
        "flatBlock": False,
        "requiredTechniqueId": TECHNIQUE
    }


def spend_stock(effect_id):
    return {
        "effectId": effect_id,
        "type": "TRANSACT_BOUNDED_RESOURCE",
        "target": "SELF",
        "sourceResourceKey": "TRANSFIGURED_HUMANS",
        "sourceResourceAmount": 1,
        "targetResourceAmount": 0,
        "trigger": "ON_START",
        "condition": dict(ALWAYS)
    }


moves = []

m = base_move("000140", "Release Transfigured Human",
              "Consume 1 TRANSFIGURED HUMAN and unleash it as a *character:000022* that harries the enemy.")
m["tags"] = ["UTILITY", "INNATE_TECHNIQUE", "CURSED_ENERGY"]
m["apCost"] = 8
m["unleashPoint"] = 4
m["baseCeCost"] = 30
m["minCeCost"] = 10
m["maxCeCost"] = 90
m["moveCap"] = 2
m["effects"] = [
    spend_stock("effect-000000"),
    {
        "effectId": "effect-000001",
        "type": "SUMMON_CHARACTER",
        "characterId": "000022",
        "trigger": "ON_FIRE",
        "condition": dict(ALWAYS)
    }
]
m["prerequisites"] = {
    "cursedTechniqueMastery": 0,
    "cursedEnergyReserves": 40,
    "cursedEnergyEfficiency": 40,
    "cursedEnergyOutput": 40,
    "jujutsuSkill": 45
}
moves.append(m)

m = base_move("000141", "Transfigured Human Assault",
              "Consume 1 TRANSFIGURED HUMAN, transfiguring it into a weapon that tears at the enemy and leaves them reeling.")
m["tags"] = ["ATTACK", "INNATE_TECHNIQUE", "CURSED_ENERGY"]
m["basePower"] = 45
m["hitComponents"] = [attack_component(45, ["INNATE_TECHNIQUE", "RANGED"], 0.9)]
m["apCost"] = 13
m["unleashPoint"] = 6
m["baseCeCost"] = 38
m["minCeCost"] = 12
m["maxCeCost"] = 130
m["effects"] = [
    spend_stock("effect-000000"),
    {
        "effectId": "effect-000001",
        "type": "APPLY_STATUS",
        "stringValue": "STAGGER",
        "target": "ENEMY",
        "durationRounds": 0,
        "durationTicks": 2,
        "trigger": "ON_HIT",
        "condition": dict(ALWAYS)
    }
]
m["prerequisites"] = {
    "cursedTechniqueMastery": 10,
    "cursedEnergyReserves": 50,
    "cursedEnergyEfficiency": 45,
    "cursedEnergyOutput": 45,
    "jujutsuSkill": 55
}
moves.append(m)

m = base_move("000142", "Hooved Legs",
              "Reshape both legs into haunches and hooves, roughly a third faster than they have any right to be.")
m["tags"] = ["UTILITY", "INNATE_TECHNIQUE"]
m["apCost"] = 6
m["unleashPoint"] = 3
m["baseCeCost"] = 18
m["minCeCost"] = 6
m["maxCeCost"] = 60
m["effects"] = [
    {
        "effectId": "effect-000000",
        "type": "TIMED_STAT_MODIFIER",
        "statType": "CORE",
        "statOperation": "CHANGE",
        "valueMode": "PERCENT",
        "stat": "speed",
        "doubleValue": 0.3,
        "target": "SELF",
        "durationRounds": 1,
        "durationTicks": 0,
        "refreshGroup": "HOOFED_LEGS_SPEED",
        "trigger": "ON_FIRE",
        "condition": dict(ALWAYS)
    }
]
m["prerequisites"] = {
    "cursedTechniqueMastery": 15,
    "jujutsuSkill": 50
}
moves.append(m)

m = base_move("000143", "Idle Transfiguration",
              "Lay a hand on the enemy's SOUL and simply rewrite them. The touch guarantees a SOUL MANIPULATION attempt - the target's soul decides the rest.")
m["tags"] = ["ATTACK", "INNATE_TECHNIQUE"]
m["basePower"] = 0
m["hitComponents"] = [attack_component(0, ["INNATE_TECHNIQUE", "MELEE"], 0.9, soul=True)]
m["apCost"] = 10
m["unleashPoint"] = 4
m["baseCeCost"] = 30
m["minCeCost"] = 10
m["maxCeCost"] = 100
m["effects"] = [
    {
        "effectId": "effect-000000",
        "type": "CODED_MOVE_ACTION",
        "codedAbilityKey": "IDLE_TRANSFIGURATION",
        "codedAction": "SOUL_MANIPULATION",
        "target": "ENEMY",
        "soulDamage": True,
        "trigger": "ON_HIT",
        "condition": dict(ALWAYS)
    }
]
m["prerequisites"] = {
    "cursedTechniqueMastery": 20,
    "cursedEnergyOutput": 55,
    "jujutsuSkill": 65
}
moves.append(m)

m = base_move("000144", "Miniature Form",
              "Shrink the whole body down to something barely there - fragile, but nearly impossible to land a clean hit on.")
m["tags"] = ["UTILITY", "DEFENSIVE", "INNATE_TECHNIQUE"]
m["apCost"] = 8
m["unleashPoint"] = 3
m["baseCeCost"] = 25
m["minCeCost"] = 8
m["maxCeCost"] = 80
m["effects"] = [
    {
        "effectId": "effect-000000",
        "type": "TIMED_STAT_MODIFIER",
        "statType": "BATTLE",
        "statOperation": "CHANGE",
        "valueMode": "PERCENT",
        "stringValue": "EVASION",
        "doubleValue": 0.6,
        "target": "SELF",
        "durationRounds": 0,
        "durationTicks": 5,
        "refreshGroup": "MINIATURE_FORM_EVASION",
        "trigger": "ON_FIRE",
        "condition": dict(ALWAYS)
    },
    {
        "effectId": "effect-000001",
        "type": "TIMED_STAT_MODIFIER",
        "statType": "CORE",
        "statOperation": "CHANGE",
        "valueMode": "PERCENT",
        "stat": "speed",
        "doubleValue": 0.2,
        "target": "SELF",
        "durationRounds": 0,
        "durationTicks": 5,
        "refreshGroup": "MINIATURE_FORM_SPEED",
        "trigger": "ON_FIRE",
        "condition": dict(ALWAYS)
    },
    {
        "effectId": "effect-000002",
        "type": "TIMED_STAT_MODIFIER",
        "statType": "BATTLE",
        "statOperation": "CHANGE",
        "valueMode": "PERCENT",
        "stringValue": "DAMAGE_TAKEN",
        "doubleValue": 1.0,
        "target": "SELF",
        "durationRounds": 0,
        "durationTicks": 5,
        "refreshGroup": "MINIATURE_FORM_DAMAGE_TAKEN",
        "trigger": "ON_FIRE",
        "condition": dict(ALWAYS)
    }
]
m["prerequisites"] = {
    "cursedTechniqueMastery": 25,
    "cursedEnergyEfficiency": 60
}
moves.append(m)

m = base_move("000145", "Blade Mill",
              "Both arms burst into banks of grinding blades. Wild, heavy, and catastrophically sharp.")
m["tags"] = ["ATTACK", "INNATE_TECHNIQUE"]
m["basePower"] = 75
m["hitComponents"] = [attack_component(75, ["INNATE_TECHNIQUE", "MELEE"], 0.8)]
m["heavy"] = True
m["apCost"] = 16
m["unleashPoint"] = 8
m["baseCeCost"] = 55
m["minCeCost"] = 20
m["maxCeCost"] = 200
m["prerequisites"] = {
    "cursedTechniqueMastery": 35,
    "cursedEnergyReserves": 70,
    "cursedEnergyOutput": 75,
    "jujutsuSkill": 85
}
moves.append(m)

m = base_move("000146", "Flesh Drill",
              "Extend and spin the whole arm into a piercing drill that crosses the field with unusual precision.")
m["tags"] = ["ATTACK", "INNATE_TECHNIQUE"]
m["basePower"] = 57
m["hitComponents"] = [attack_component(57, ["INNATE_TECHNIQUE", "RANGED"], 0.95)]
m["apCost"] = 12
m["unleashPoint"] = 5
m["baseCeCost"] = 40
m["minCeCost"] = 12
m["maxCeCost"] = 140
m["prerequisites"] = {
    "cursedTechniqueMastery": 30,
    "cursedEnergyReserves": 60,
    "cursedEnergyOutput": 65,
    "jujutsuSkill": 75
}
moves.append(m)

m = base_move("000147", "Giant Fist",
              "Swell a hand into a crushing slab that pins the enemy where they stand - the opening Idle Transfiguration needs.")
m["tags"] = ["ATTACK", "INNATE_TECHNIQUE"]
m["basePower"] = 45
m["hitComponents"] = [attack_component(45, ["INNATE_TECHNIQUE", "MELEE"], 0.9)]
m["apCost"] = 12
m["unleashPoint"] = 5
m["baseCeCost"] = 35
m["minCeCost"] = 10
m["maxCeCost"] = 120
m["effects"] = [
    {
        "effectId": "effect-000000",
        "type": "APPLY_STATUS",
        "stringValue": "STAGGER",
        "target": "ENEMY",
        "durationRounds": 0,
        "durationTicks": 2,
        "trigger": "ON_HIT",
        "condition": dict(ALWAYS)
    }
]
m["prerequisites"] = {
    "cursedTechniqueMastery": 25,
    "cursedEnergyOutput": 55,
    "jujutsuSkill": 65
}
moves.append(m)

m = base_move("000148", "Spike Form",
              "Bloom the whole body into an outward ball of spikes. Anyone who swings into it regrets it; anything thrown from afar does not.")
m["tags"] = ["DEFENSIVE", "INNATE_TECHNIQUE"]
m["defenseType"] = "BLOCK"
m["blockStyle"] = "PERCENTAGE"
m["blockDuration"] = 8
m["blockRanges"] = ["MELEE"]
m["blockDamageReduction"] = 35
m["defenseTiming"] = "REACTION"
m["defenseUses"] = 2
m["anyBlock"] = True
m["percentageBlock"] = True
m["apCost"] = 10
m["unleashPoint"] = 3
m["baseCeCost"] = 18
m["minCeCost"] = 5
m["maxCeCost"] = 60
m["effects"] = [
    {
        "effectId": "effect-000000",
        "type": "DEAL_DIRECT_DAMAGE",
        "intValue": 40,
        "valueMode": "FLAT",
        "target": "ENEMY",
        "trigger": "ON_BLOCK",
        "condition": dict(ALWAYS)
    }
]
m["prerequisites"] = {
    "cursedTechniqueMastery": 20,
    "jujutsuSkill": 55
}
moves.append(m)

m = base_move("000149", "Winged Evasion",
              "Sprout wings from the back and simply be elsewhere. Nothing is answered, nothing is touched.")
m["tags"] = ["DEFENSIVE", "INNATE_TECHNIQUE"]
m["defenseType"] = "DODGE"
m["blockDuration"] = 5
m["dodgeChance"] = 80
m["dodgeScope"] = "BOTH"
m["defenseTiming"] = "REACTION"
m["defenseUses"] = 1
m["dodge"] = True
m["apCost"] = 8
m["unleashPoint"] = 2
m["baseCeCost"] = 20
m["minCeCost"] = 6
m["maxCeCost"] = 70
m["prerequisites"] = {
    "cursedTechniqueMastery": 15,
    "jujutsuSkill": 50
}
moves.append(m)

m = base_move("000150", "Domain Expansion: Self-Embodiment of Perfection",
              "Close the barrier and complete the technique: within it, Mahito's hands never leave anyone's soul. Establish *domain:000001*.")
m["tags"] = ["UTILITY", "INNATE_TECHNIQUE", "CURSED_ENERGY"]
m["apCost"] = 10
m["unleashPoint"] = 6
m["baseCeCost"] = 300
m["minCeCost"] = 100
m["maxCeCost"] = 600
m["effects"] = [
    {
        "effectId": "effect-000000",
        "type": "ESTABLISH_DOMAIN",
        "domainId": "000001",
        "target": "SELF",
        "trigger": "ON_FIRE",
        "condition": dict(ALWAYS)
    }
]
m["prerequisites"] = {
    "cursedTechniqueMastery": 140,
    "jujutsuSkill": 100,
    "cursedEnergyOutput": 60,
    "cursedEnergyReserves": 60,
    "cursedEnergyEfficiency": 50
}
moves.append(m)

# ─── Domain ───────────────────────────────────────────────────────────────────

domains = [
    {
        "id": "000001",
        "name": "Self-Embodiment of Perfection",
        "description": "A newly awakened, closed barrier that captures everyone but its owner. Inside it Mahito is always touching every exposed soul: each attempt is the same SOUL MANIPULATION his hands deliver, on entry and every tick thereafter.",
        "requiredTechniqueName": TECHNIQUE,
        "antiDomain": False,
        "topology": "CLOSED",
        "capturePolicy": "EVERYONE",
        "entrantPolicy": "FOLLOW_SUMMONER",
        "protectionPolicy": "OWNER",
        "durationRounds": 2,
        "durationTicks": 0,
        "burnoutRounds": 2,
        "burnoutTicks": 0,
        "ceUpkeepPerTick": 35.0,
        "internalBarrierIntegrity": 90,
        "clashValue": 90,
        "counterType": "NONE",
        "counterPotency": 0,
        "counterUses": -1,
        "counterBreakOnOwnerMove": False,
        "prerequisites": {
            "cursedTechniqueMastery": 140,
            "jujutsuSkill": 100,
            "cursedEnergyOutput": 60,
            "cursedEnergyReserves": 60,
            "cursedEnergyEfficiency": 50
        },
        "sureHitEffects": [
            {
                "effectId": "effect-000000",
                "type": "CODED_MOVE_ACTION",
                "codedAbilityKey": "IDLE_TRANSFIGURATION",
                "codedAction": "SOUL_MANIPULATION",
                "target": "ENEMY",
                "soulDamage": True,
                "domainTrigger": "ON_MEMBER_ENTER",
                "domainAudience": "ENTERING_MEMBER",
                "domainDeliveryClass": "EFFECT",
                "domainIntervalTicks": 1
            },
            {
                "effectId": "effect-000000",
                "type": "CODED_MOVE_ACTION",
                "codedAbilityKey": "IDLE_TRANSFIGURATION",
                "codedAction": "SOUL_MANIPULATION",
                "target": "ENEMY",
                "soulDamage": True,
                "domainTrigger": "EACH_TICK",
                "domainAudience": "ENEMY_MEMBERS",
                "domainDeliveryClass": "EFFECT",
                "domainIntervalTicks": 1
            }
        ],
        "fieldEffects": [],
        "casterEffects": [],
        "barrierEffects": [],
        "procedureEffects": []
    },
]

# ─── Technique ────────────────────────────────────────────────────────────────


def node(nid, content_type, content_id, x, y, prerequisites):
    return {
        "id": nid,
        "contentType": content_type,
        "contentId": content_id,
        "x": float(x),
        "y": float(y),
        "prerequisites": prerequisites
    }


def node_prereq(nid, attached=True):
    return {"type": "NODE", "nodeId": nid, "attached": attached}


def mastery(minimum):
    return {"type": "MASTERY", "minimum": minimum}


def stat(stat_name, minimum):
    return {"type": "STAT", "stat": stat_name, "minimum": minimum}


technique = {
    "id": "000006",
    "name": TECHNIQUE,
    "description": "A technique that touches, sees, and reshapes souls. Bodies follow the soul's shape, so Idle Transfiguration is only ever one touch away from rewriting an enemy entirely.",
    "skillTree": [
        node("node-000000", "ABILITY", "000060", 40, 325, []),
        node("node-000001", "ABILITY", "000061", 40, 75, []),
        node("node-000002", "ABILITY", "000062", 40, 575, []),
        node("node-000003", "MOVE", "000143", 360, 75, [
            node_prereq("node-000001"), mastery(20),
            stat("cursedEnergyOutput", 55), stat("jujutsuSkill", 65)]),
        node("node-000004", "ABILITY", "000063", 360, 325, [
            node_prereq("node-000000"), mastery(0)]),
        node("node-000005", "MOVE", "000140", 700, 325, [
            node_prereq("node-000004"), mastery(0),
            stat("cursedEnergyReserves", 40), stat("cursedEnergyEfficiency", 40),
            stat("cursedEnergyOutput", 40), stat("jujutsuSkill", 45)]),
        node("node-000006", "MOVE", "000141", 700, 575, [
            node_prereq("node-000004"), mastery(10),
            stat("cursedEnergyReserves", 50), stat("cursedEnergyEfficiency", 45),
            stat("cursedEnergyOutput", 45), stat("jujutsuSkill", 55)]),
        node("node-000007", "MOVE", "000142", 700, 75, [
            node_prereq("node-000003"), mastery(15), stat("jujutsuSkill", 50)]),
        node("node-000008", "MOVE", "000147", 1050, 75, [
            node_prereq("node-000003"), mastery(25),
            stat("cursedEnergyOutput", 55), stat("jujutsuSkill", 65)]),
        node("node-000009", "MOVE", "000144", 360, 575, [
            node_prereq("node-000000"), mastery(25), stat("cursedEnergyEfficiency", 60)]),
        node("node-000010", "MOVE", "000148", 360, 725, [
            node_prereq("node-000002"), mastery(20), stat("jujutsuSkill", 55)]),
        node("node-000011", "MOVE", "000149", 700, 725, [
            node_prereq("node-000010"), mastery(15), stat("jujutsuSkill", 50)]),
        node("node-000012", "MOVE", "000146", 1050, 325, [
            node_prereq("node-000005"), mastery(30),
            stat("cursedEnergyReserves", 60), stat("cursedEnergyOutput", 65),
            stat("jujutsuSkill", 75)]),
        node("node-000013", "MOVE", "000145", 1050, 575, [
            node_prereq("node-000006"), mastery(35),
            stat("cursedEnergyReserves", 70), stat("cursedEnergyOutput", 75),
            stat("jujutsuSkill", 85)]),
        node("node-000014", "DOMAIN", "000001", 1400, 400, [
            node_prereq("node-000013"), mastery(140),
            stat("jujutsuSkill", 100), stat("cursedEnergyOutput", 60),
            stat("cursedEnergyReserves", 60), stat("cursedEnergyEfficiency", 50)]),
    ]
}

splice(f"{DATA}/abilities/all_abilities.json", abilities, "ability")
splice(f"{DATA}/characters/all_characters.json", characters, "character")
splice(f"{DATA}/moves/all_moves.json", moves, "move")
splice(f"{DATA}/domains/all_domains.json", domains, "domain")
splice(f"{DATA}/techniques/all_techniques.json", [technique], "technique")
print("done")
