extends Resource
class_name ItemResource

enum ItemType {USABLE, EQUIP, ETC}

@export var item_id: int
@export var icon: Texture2D
@export var name: String
@export var description: String
@export var weight: int
@export var item_script: GDScript
@export var type: ItemType

## Cache for instantiated ItemUse objects. Key: GDScript path, Value: ItemUse instance
static var _script_instance_cache: Dictionary = {}


func use_item() -> void:
	print("ItemResource: Using item: %s" % [name])
	if item_script:
		var item_use_instance: ItemUse = _get_or_create_item_use_instance()
		if item_use_instance:
			item_use_instance.on_item_used(self)
		else:
			printerr("ItemResource: Failed to load or instantiate item script for item: %s" % [name])
	elif ConnectionManager.is_ready_to_send():
		ConnectionManager.use_item(item_id)
	else:
		printerr("ItemResource: No global connection manager found")


func _get_or_create_item_use_instance() -> ItemUse:
	if not item_script:
		return null

	var script_path: String = item_script.resource_path

	# Check if we already have a cached instance
	if _script_instance_cache.has(script_path):
		return _script_instance_cache[script_path]

	# Create new instance from the script
	# Note: Godot's load() function automatically caches the GDScript resource
	var instance = item_script.new()

	# Verify the instance is of correct type
	if not instance is ItemUse:
		printerr("ItemResource: Script at '%s' does not extend ItemUse" % [script_path])
		instance.free()
		return null

	# Cache the instance for future use
	_script_instance_cache[script_path] = instance

	return instance
