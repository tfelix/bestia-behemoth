extends Resource
class_name BestiaResource

## Static, per-species bestia data the client keeps locally instead of receiving it from the server.
##
## Generated/kept in sync from zone-server's mob YMLs by './gradlew syncBestiaDb', which owns [member
## bestia_id], [member equip_slots] and [member non_combatant]. Anything else here is pure presentation
## with no server equivalent and is hand-authored.

## The body to spawn for a species whose art is not authored yet - the orange placeholder every mob used
## to get. Read it through [method get_bestia_visual] rather than [member bestia_visual] directly, so the
## fallback lives in one place; without it an unknown species gets no visual child at all, and since the
## clickable Area3D lives on the visual it would be invisible *and* unclickable.
const MISSING_VISUAL: PackedScene = preload("res://Game/Entity/Visual/BestiaVisual/BestiaVisual.tscn")

@export var bestia_id: int

## The scene instantiated as this species' body.
@export var bestia_visual: PackedScene

## Translation key for the name shown on hover. A species name, not an individual's - the server sends no
## per-entity name yet.
@export var name_key: String

## Bitmask of the [enum EquipmentSlot.Slot]s this species physically has. Test it with
## [method EquipmentSlot.has_slot]. The server enforces the same mask independently - this copy only
## exists so the UI can grey out slots that will never be usable.
@export var equip_slots: int = 0

## True for a species nothing may damage, such as a townsperson. The client uses it to decide that a
## click means talking rather than swinging; the server enforces the same thing with its own component,
## so a hand-crafted attack gets nowhere either.
@export var non_combatant: bool = false


## The scene to spawn for this species' body. Always prefer this over [member bestia_visual], for the
## reason [constant MISSING_VISUAL] gives.
func get_bestia_visual() -> PackedScene:
	return bestia_visual if bestia_visual != null else MISSING_VISUAL
