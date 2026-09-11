extends ItemUse
class_name StructureKitUse

## A kit that is put down somewhere rather than simply used: the player places and turns a ghost of what it
## becomes, and the click sends both to the server.
##
## The GDScript half of net.bestia.zone.item.script.StructureKitScript. [member kind_name] must match the
## StaticEntityKind that script names: it picks the art here, and the server builds what its own script says.

const PropVisualBuilderScript = preload("res://Game/World/PropVisualBuilder.cs")

## Which StaticEntityKind this kit becomes, by name. Resolved to an ordinal on the C# side, which is where
## that enum is mirrored - see PropVisualBuilder.KindByName. A subclass for a furnace kit overrides this.
var kind_name: String = "WORKBENCH"


func on_item_used(item: ItemResource) -> void:
	var mouse := MouseManager.get_instance()
	if mouse == null:
		printerr("StructureKitUse: no MouseManager to place %s with" % [item.name_key])
		return

	var builder = PropVisualBuilderScript.new()
	var kind: int = builder.KindByName(kind_name)
	if kind < 0:
		return

	mouse.enter_item_targeting(item, self, null, builder.BuildConstruction(kind))


func on_targeting_click(item: ItemResource, click_info: Dictionary) -> bool:
	if not ConnectionManager.is_ready_to_send():
		return true

	# The tile rather than the raw hit, because Vec3Convert rounds what it is handed - see item_use.gd.
	var args = ConnectionManager.ScriptArgsCls.new()
	args.SetPosition(ScriptArgKeys.POSITION, click_info["tile"])
	args.SetFloat(ScriptArgKeys.YAW, click_info["yaw"])

	# Nothing is applied locally: the site appears when the server spawns it and the kit leaves the bag when
	# the inventory resyncs. A kit the server refuses is simply still there.
	ConnectionManager.use_item(item.item_id, args)

	return true
