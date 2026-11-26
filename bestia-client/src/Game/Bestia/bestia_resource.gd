extends Resource
class_name BestiaResource

## Static, per-species bestia data the client keeps locally instead of receiving it from the server.
##
## Generated/kept in sync from zone-server's mob YMLs by './gradlew syncBestiaDb', which owns [member
## bestia_id], the two text keys, [member kind], [member equip_slots], [member armor_types], [member
## non_combatant], [member habitat], [member in_compendium], [member temperament], [member activity_cycle], [member level_min], [member
## level_max], [member size], [member weight_kg], [member loot_item_ids], [member loot_chances] and the four
## [code]essence_*[/code] fields. The English text behind the keys is synced
## into Localization/bestias.csv, where it is translated. Anything else here is pure presentation with no
## server equivalent and is hand-authored.

## The body to spawn for a species whose art is not authored yet: a primitive drawn in the species'
## placeholder look. Read it through [method get_bestia_visual] rather than [member bestia_visual] directly, so the
## fallback lives in one place; without it an unknown species gets no visual child at all, and since the
## clickable Area3D lives on the visual it would be invisible *and* unclickable.
const MISSING_VISUAL: PackedScene = preload("res://Game/Entity/Visual/BestiaVisual/BestiaVisual.tscn")

## The Bestia Compendium picture of a species with no art yet. Drawn on a transparent ground, so a black
## modulate turns it into the silhouette an unseen species shows.
const MISSING_PORTRAIT: Texture2D = preload("res://Game/Bestia/missing_portrait.png")

enum PlaceholderShape { SPHERE, CAPSULE, BOX, CYLINDER, PRISM, TORUS }

@export var bestia_id: int

## The scene instantiated as this species' body.
@export var bestia_visual: PackedScene

## Translation key for the name shown on hover. A species name: the only individuals the server names are
## townsfolk and masters, and both carry it on their own visual instead.
@export var name_key: String

## Translation key for the species' flavour text in the Bestia Compendium.
@export var description_key: String

## What the species is, such as "BEAST". Its display name is the translation key "BESTIA_KIND_<kind>".
@export var kind: String

## Bitmask of the [enum EquipmentSlot.Slot]s this species physically has. Test it with
## [method EquipmentSlot.has_slot]. The server enforces the same mask independently - this copy only
## exists so the UI can grey out slots that will never be usable.
@export var equip_slots: int = 0

## Bitmask of the armor types this species can wear: bit n is the server's ArmorType ordinal n (CLOTH, LIGHT,
## MEDIUM, HEAVY). The server enforces it; this copy is for an equip window to grey out the rest.
@export var armor_types: int = 0

## True for a species nothing may damage, such as a townsperson. The client uses it to decide that a
## click means talking rather than swinging; the server enforces the same thing with its own component,
## so a hand-crafted attack gets nowhere either.
@export var non_combatant: bool = false

## How the species meets a player, read off its server AI profile's goals: [code]"HOSTILE"[/code] attacks on
## sight (KillEnemy), [code]"DEFENSIVE"[/code] fights back once hit (KillAttacker only), [code]"PASSIVE"[/code]
## never fights. A hostile species' hover name shows red, and the compendium names all three.
@export var temperament: String = "PASSIVE"

## When the species is awake, its AI profile's activity cycle: [code]"DIURNAL"[/code], [code]"NOCTURNAL"[/code]
## or [code]"CATHEMERAL"[/code] (no fixed rhythm).
@export var activity_cycle: String = "CATHEMERAL"

## The biomes this species spawns in, comma-separated as the mob YAML names them. Empty means anywhere on
## land. Read it through [method get_habitat].
@export var habitat: String = ""

## False for species the Bestia Compendium leaves out, such as townsfolk.
@export var in_compendium: bool = true

## The levels a wild one of this species spawns at.
@export var level_min: int = 1
@export var level_max: int = 1

## Body size, "SMALL", "MEDIUM" or "BIG". Its display name is the translation key "BESTIA_SIZE_<size>".
@export var size: String = "MEDIUM"

## Body weight in kilograms, with the size default already filled in for a species whose YML names none.
@export var weight_kg: int = 0

## What a wild one may drop when it dies: the item at index i of [member loot_item_ids] drops with chance
## [member loot_chances][i] out of 10000. Read both through [DropFinder].
@export var loot_item_ids: PackedInt32Array = PackedInt32Array()
@export var loot_chances: PackedInt32Array = PackedInt32Array()

## What Runic Etching binds out of this species, as the server's names: the slot group, the effect, what the effect
## aims at, and the drawback. All empty for a species without an essence. Read them through [EssenceText].
@export var essence_group: String = ""
@export var essence_effect: String = ""
@export var essence_target: String = ""
@export var essence_drawback: String = ""

## The hand-drawn picture in the Bestia Compendium. Read it through [method get_portrait]. Kept as
## DB/<bestia_id>_<identifier>.png, the name the companion app finds it by.
@export var portrait: Texture2D

@export_group("Placeholder")
## How [constant MISSING_VISUAL] draws this species, so species without art still tell apart. The defaults
## are the orange sphere every species had before.
@export var placeholder_shape: PlaceholderShape = PlaceholderShape.SPHERE
@export var placeholder_colour: Color = Color(0.8156863, 0.54509807, 0.101960786)
@export var placeholder_scale: float = 1.0
## Emission strength in [member placeholder_colour], to make a boss stand out. 0 does not glow.
@export var placeholder_glow: float = 0.0
@export_group("")


## The scene to spawn for this species' body. Always prefer this over [member bestia_visual], for the
## reason [constant MISSING_VISUAL] gives.
func get_bestia_visual() -> PackedScene:
	return bestia_visual if bestia_visual != null else MISSING_VISUAL


func get_portrait() -> Texture2D:
	return portrait if portrait != null else MISSING_PORTRAIT


func get_habitat() -> PackedStringArray:
	return habitat.split(",", false)


## True for a species that attacks on sight rather than only when it is hit.
func is_hostile() -> bool:
	return temperament == "HOSTILE"
