extends Resource
class_name ItemResource

enum ItemType {USABLE, EQUIP, ETC}

## Stand-in for an item whose art is not authored yet. [member icon] is left unset in the .tres until
## then (ItemDbSyncTask never writes the field), and every read goes through [method get_icon] so an
## unfinished item draws something visible instead of an empty slot.
const MISSING_ICON: Texture2D = preload("res://Game/UI/Inventory/InventoryItem/item_placeholder.png")

## The same deal for an item's ground art. Without it a dropped item is not merely undecorated: the
## entity gets no visual child at all, so there is nothing to see and - because the clickable Area3D
## lives on the visual - nothing to click either, and the item sits on the ground unlootable until
## someone authors a mesh for it.
const MISSING_VISUAL: PackedScene = preload("res://Game/Entity/Visual/ItemVisual/Items/MissingItem.tscn")

@export var item_id: int
@export var icon: Texture2D
@export var name_key: String
@export var description_key: String
@export var weight: int

## The item's tier: what it takes to have anything to do with it. It is the level a wearer needs to put gear
## on and the reach a crafter needs to make it or work on it; for a material it says which tier of work it
## belongs to. Kept in sync with items.yml by './gradlew syncItemDb'.
##
## An instance's effective tier is this plus its upgrade level, so a well-upgraded item is harder to work on
## than a plain one of the same kind. Only the server does that arithmetic - see Item.effectiveLevel.
@export var level: int = 1
@export var item_script: GDScript
@export var type: ItemType
@export var item_visual: PackedScene

## Which slot this item is worn in, as an [enum EquipmentSlot.Slot] ordinal [b]+ 1[/b] - 0 means
## "not equipment". Kept in sync with items.yml by './gradlew syncItemDb'; use
## [method EquipmentSlot.from_item_value] to turn it into a real slot.
@export var equip_slot: int = 0

## Cache for instantiated ItemUse objects. Key: GDScript path, Value: ItemUse instance
static var _script_instance_cache: Dictionary = {}


## The texture to draw for this item. Always prefer this over reading [member icon] directly, so the
## not-authored-yet fallback lives in exactly one place rather than at each of the call sites.
func get_icon() -> Texture2D:
	return icon if icon != null else MISSING_ICON


## The scene to spawn for this item lying on the ground. Always prefer this over reading
## [member item_visual] directly, for the same reason [method get_icon] exists.
func get_item_visual() -> PackedScene:
	return item_visual if item_visual != null else MISSING_VISUAL


func use_item() -> void:
	print("ItemResource: Using item: %s" % [tr(name_key)])
	if item_script:
		var item_use_instance: ItemUse = _get_or_create_item_use_instance()
		if item_use_instance:
			item_use_instance.on_item_used(self)
		else:
			printerr("ItemResource: Failed to load or instantiate item script for item: %s" % [tr(name_key)])
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
