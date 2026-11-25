class_name EssenceText
## What an essence does, written out for a tooltip: the species sets the effect, the gem its strength.
##
## The numbers mirror the server's EssenceEffect, which owns them. Static, so the lookups go through
## [TranslationServer] for the reason [DialogText] gives.


const POINTS_BY_TIER := {"AMETHYST": 3, "EMERALD": 5, "RUBY": 8, "PYRELITH": 11, "DIAMOND": 15}

## What a Bestia must have reached to fill a gem, and what an item must have reached to take the essence.
const MIN_BESTIA_LEVEL_BY_TIER := {"AMETHYST": 1, "EMERALD": 21, "RUBY": 41, "PYRELITH": 61, "DIAMOND": 81}
const MIN_ITEM_LEVEL_BY_TIER := {"AMETHYST": 0, "EMERALD": 1, "RUBY": 21, "PYRELITH": 41, "DIAMOND": 61}

## The cut gem item ids Runic Etching fills, by the tier each becomes.
const TIER_BY_CUT_GEM := {136: "AMETHYST", 137: "EMERALD", 138: "RUBY", 139: "PYRELITH", 140: "DIAMOND"}

## The one tool a tool essence fits, by its server ToolKind. Every other essence fits its whole group.
const TOOL_BY_EFFECT := {
	"MINING_SPEED": "MINING", "RICH_SEAM": "MINING", "FELLING_SPEED": "FELLING", "DIGGING_SPEED": "DIGGING",
	"FASTER_BITE": "FISHING",
}

## The highest attack level a Learned Trick casts at, by gem. A Diamond has no cap.
const LEVEL_CAP_BY_TIER := {"AMETHYST": 20, "EMERALD": 40, "RUBY": 60, "PYRELITH": 80, "DIAMOND": 100}

const _BOSS_LEVEL_MARGIN := 20

## Effects whose value is plain points times this, in percent or flat as their text says.
const PER_POINT := {
	"BANE": 3, "ELEMENT_HUNTER": 3, "GIANT_SLAYER": 2, "WARD": 3, "ARROW_WARD": 1, "ELEMENT_SKIN": 3,
	"ELEMENT_VEIL": 3, "HARDY": 1, "MIST_VEIL": 1, "WHISPER": 1, "LONG_STRIDE": 5, "CLEAR_MIND": 4, "FOCUS": 1,
	"WISDOM": 5, "QUICK_HANDS": 1, "QUIET_STEP": 2, "REFLEX": 1, "MINING_SPEED": 3, "FELLING_SPEED": 3,
	"DIGGING_SPEED": 3, "FASTER_BITE": 3, "RICH_SEAM": 1, "TOUGH_GRIP": 3,
}

## The one gem a boss fills, or empty for any other species. Mirrors the server's GemTier.ofBossLevel.
static func boss_tier(bestia: BestiaResource) -> String:
	if bestia == null or bestia.essence_effect != "BOSS":
		return ""
	if bestia.level_min <= 60:
		return "RUBY"
	if bestia.level_min <= 80:
		return "PYRELITH"
	return "DIAMOND"


## The lowest item level the essence goes into: the gem's, and for a boss at most 20 below the boss.
static func min_item_level(bestia: BestiaResource, tier: String) -> int:
	var gem_level := int(MIN_ITEM_LEVEL_BY_TIER.get(tier, 0))
	if boss_tier(bestia).is_empty():
		return gem_level
	return maxi(gem_level, bestia.level_min - _BOSS_LEVEL_MARGIN)


## The socket group of [param item], or empty for an item no essence fits. Mirrors the server's SocketGroup.of.
static func group_of(item: ItemResource) -> String:
	if not item.tool_kind.is_empty():
		return "TOOL"
	match EquipmentSlot.from_item_value(item.equip_slot):
		EquipmentSlot.Slot.RIGHT_HAND: return "WEAPON"
		EquipmentSlot.Slot.LEFT_HAND: return "SHIELD"
		EquipmentSlot.Slot.ARMOR: return "ARMOR"
		EquipmentSlot.Slot.GARMENT: return "GARMENT"
		EquipmentSlot.Slot.FOOTGEAR: return "FOOTGEAR"
		EquipmentSlot.Slot.HEAD_UPPER, EquipmentSlot.Slot.HEAD_MID: return "HEADGEAR"
		EquipmentSlot.Slot.ACCESSORY_1, EquipmentSlot.Slot.ACCESSORY_2, EquipmentSlot.Slot.LIGHT_SOURCE: return "ACCESSORY"
	return ""


## Empty for a species without an essence, so the caller shows the gem's name alone.
static func describe(bestia: BestiaResource, tier: String) -> String:
	if bestia == null or bestia.essence_effect.is_empty():
		return ""

	# A boss essence has fixed numbers, so its whole text is one key.
	if bestia.essence_effect == "BOSS":
		return _translate("ESSENCE_BOSS_" + bestia.essence_target)

	var points: int = POINTS_BY_TIER.get(tier, 0)
	if not bestia.essence_drawback.is_empty():
		points = points * 3 / 2

	var values := _values(bestia.essence_effect, bestia.essence_target, points, tier)
	values["target"] = _target_name(bestia.essence_target)
	var text := _translate("ESSENCE_" + bestia.essence_effect).format(values)

	if not bestia.essence_drawback.is_empty():
		text += ", " + _translate("ESSENCE_DRAWBACK_" + bestia.essence_drawback)
	return text


static func _values(effect: String, target: String, points: int, tier: String) -> Dictionary:
	match effect:
		"RIDER":
			var hard_control := target == "STUN" or target == "FROZEN"
			return {"value": points * 7 / 10 if hard_control else points}
		"PRECISION":
			return {"value": points, "crit": points / 3}
		"LEECH":
			return {"value": String.num(points * 0.4, 1)}
		"STEADFAST", "ELEMENT_BODY":
			return {"value": points - 3}
		"COATED":
			return {"value": points - 2}
		"SWIFT":
			return {"value": mini(points, 10)}
		"DEEP_POOLS":
			return {"value": points / 2}
		"TAMER":
			return {"value": points / 2}
		"LEARNED_TRICK":
			return {"value": LEVEL_CAP_BY_TIER.get(tier, 0)}
		"ATTRIBUTE_PAIR":
			var pair := target.split("_")
			return {"value": points - points / 2, "first": pair[0], "value2": points / 2, "second": pair[1]}
	return {"value": points * PER_POINT.get(effect, 0)}


## A kind, an element or a status by its display name; an attack by its identifier, which reads well capitalized.
static func _target_name(target: String) -> String:
	if target.is_empty():
		return ""
	for prefix in ["BESTIA_KIND_", "ELEMENT_", "ESSENCE_STATUS_"]:
		var translated := _translate(prefix + target)
		if translated != prefix + target:
			return translated
	return target.capitalize()


static func _translate(key: String) -> String:
	return TranslationServer.translate(key)
