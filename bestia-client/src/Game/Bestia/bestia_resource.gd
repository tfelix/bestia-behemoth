extends Resource
class_name BestiaResource

## Static, per-species bestia data the client keeps locally instead of receiving it from the server.
##
## Generated/kept in sync from zone-server's mob YMLs by './gradlew syncBestiaDb'; only [member
## bestia_id] and [member equip_slots] come from the server config, anything added later for pure
## presentation is hand-authored.

@export var bestia_id: int

## Bitmask of the [enum EquipmentSlot.Slot]s this species physically has. Test it with
## [method EquipmentSlot.has_slot]. The server enforces the same mask independently - this copy only
## exists so the UI can grey out slots that will never be usable.
@export var equip_slots: int = 0
