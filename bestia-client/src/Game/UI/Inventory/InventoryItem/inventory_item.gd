extends Panel

@export var item: ItemResource
@export var amount: int

## Id of the backing item instance, or 0 for a plain stackable pile. Needed to name *which*
## physical item to equip when several copies are held.
@export var unique_id: int = 0

## Wear on the backing instance. A [member max_durability] of 0 means this item does not wear at all,
## which is the case for every material, every consumable and any gear nobody gave a number to - so the
## bar stays hidden rather than showing a full one, since "cannot break" and "unbroken" are not the same
## thing to a player deciding what to repair.
@export var durability: int = 0
@export var max_durability: int = 0

## Rune slots cut into the backing instance by Item Customization. Shown in the tooltip only; nothing
## can fill one yet.
@export var slots: int = 0

## Colours for the wear bar, worst state last. Fill is picked by [method _wear_color].
const _WEAR_FINE := Color(0.36, 0.71, 0.35)
const _WEAR_WORN := Color(0.85, 0.72, 0.22)
const _WEAR_BROKEN := Color(0.79, 0.27, 0.24)

## Assigned by Inventory when this node is instantiated - used for the double-click-to-equip
## shortcut, which has to know whether the equipment window is currently open.
## Untyped on purpose: statically typing this as Inventory forces inventory_item.gd to resolve
## the Inventory class while it's parsed, and Inventory in turn preloads InventoryItem.tscn -
## a load cycle that Godot rejects with a "Busy" parse error.
var inventory = null

@onready var _count: Label = %Count
@onready var _icon: TextureRect = %Icon
@onready var _wear: ProgressBar = %Wear


func _ready() -> void:
	_count.text = str(amount)
	_icon.texture = item.icon
	_update_wear()
	tooltip_text = _build_tooltip()


func _update_wear() -> void:
	_wear.visible = max_durability > 0
	if not _wear.visible:
		return

	_wear.max_value = max_durability
	_wear.value = durability

	var fill := StyleBoxFlat.new()
	fill.bg_color = _wear_color()
	fill.set_corner_radius_all(2)
	_wear.add_theme_stylebox_override("fill", fill)


func _wear_color() -> Color:
	var fraction := float(durability) / float(max_durability)
	if durability <= 0:
		return _WEAR_BROKEN
	if fraction < 0.35:
		return _WEAR_WORN
	return _WEAR_FINE


## Plain text rather than the BBCode skill descriptions use: a Control tooltip is a plain Label unless
## the whole tooltip scene is replaced, and one line of wear does not justify that.
func _build_tooltip() -> String:
	var lines: Array[String] = [tr(item.name_key)]

	if max_durability > 0:
		var broken := " (broken)" if durability <= 0 else ""
		lines.append("Durability %d/%d%s" % [durability, max_durability, broken])

	if slots > 0:
		lines.append("%d rune slot(s)" % slots)

	return "
".join(lines)


func _get_drag_data(_at_position: Vector2) -> Variant:
	var preview: TextureRect = TextureRect.new()
	preview.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	preview.size = Vector2(50, 50)
	preview.pivot_offset = preview.size / 2.0
	preview.rotation_degrees = 10
	set_drag_preview(preview)
	preview.texture = _icon.texture
	return {"type": "item", "id": item.item_id, "unique_id": unique_id, "source": "inventory_item"}


func _can_drop_data(_at_position: Vector2, _data: Variant) -> bool:
	return false


func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_LEFT and event.double_click:
			# With the equipment window open, double clicking a piece of gear puts it on; anything
			# else (or a closed window) falls through to the normal "use this item".
			if inventory != null and inventory.try_quick_equip(item, unique_id):
				return
			item.use_item()
		elif event.button_index == MOUSE_BUTTON_RIGHT and event.pressed:
			print("Panel was right-clicked!")
