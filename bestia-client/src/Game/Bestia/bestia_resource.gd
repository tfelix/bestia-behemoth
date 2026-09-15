extends Resource
class_name BestiaResource

## Static, per-species bestia data the client keeps locally instead of receiving it from the server.
##
## Generated/kept in sync from zone-server's mob YMLs by './gradlew syncBestiaDb', which owns [member
## bestia_id], [member equip_slots] and [member non_combatant]. Anything else here is pure presentation
## with no server equivalent and is hand-authored.

@export var bestia_id: int

## Bitmask of the [enum EquipmentSlot.Slot]s this species physically has. Test it with
## [method EquipmentSlot.has_slot]. The server enforces the same mask independently - this copy only
## exists so the UI can grey out slots that will never be usable.
@export var equip_slots: int = 0

## True for a species nothing may damage, such as a townsperson. The client uses it to decide that a
## click means talking rather than swinging; the server enforces the same thing with its own component,
## so a hand-crafted attack gets nowhere either.
@export var non_combatant: bool = false
