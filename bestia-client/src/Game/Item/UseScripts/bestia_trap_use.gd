extends ItemUse
class_name BestiaTrapUse

## A Magic Bestia Trap: the player picks a tile and the server sets the trap there.
##
## The GDScript half of net.bestia.zone.item.script.TrapScript. The reach (ten tiles) is the server's to
## check; a refusal comes back as an OperationError and is shown in the chat like every other.

const _MARKER_SCENE := preload("res://Game/VFX/AOECastIndicator/AOECastIndicator.tscn")


func on_item_used(item: ItemResource) -> void:
	var mouse := MouseManager.get_instance()
	if mouse == null:
		printerr("BestiaTrapUse: no MouseManager to place %s with" % [item.name_key])
		return

	var marker = _MARKER_SCENE.instantiate()
	marker.set_radius(0)
	mouse.enter_item_targeting(item, self, null, marker)


func on_targeting_click(item: ItemResource, click_info: Dictionary) -> bool:
	if not ConnectionManager.is_ready_to_send():
		return true

	var args = ConnectionManager.ScriptArgsCls.new()
	args.SetPosition(ScriptArgKeys.POSITION, click_info["tile"])
	ConnectionManager.use_item(item.item_id, args)

	return true
