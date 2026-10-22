extends ItemUse
class_name WarBannerUse

## The War Banner: the player picks a tile and the server plants the guild's banner there.
##
## The GDScript half of net.bestia.zone.guild.skill.WarBannerScript, which checks the reach and whether the guild
## may plant one; a refusal comes back as an OperationError and is shown in the chat.

const _MARKER_SCENE := preload("res://Game/VFX/AOECastIndicator/AOECastIndicator.tscn")


func on_item_used(item: ItemResource) -> void:
	var mouse := MouseManager.get_instance()
	if mouse == null:
		printerr("WarBannerUse: no MouseManager to place %s with" % [item.name_key])
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
