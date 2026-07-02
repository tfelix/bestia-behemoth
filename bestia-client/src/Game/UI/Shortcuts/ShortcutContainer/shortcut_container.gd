extends PanelContainer
class_name ShortcutContainer

signal shortcut_changed(row: int, number: int, shortcut_data: ShortcutData)
signal item_count_requested( row: int, number: int, item_id: int)

@export
var shortcut_row: int = 0

@export
var shortcut_number: int = 0

@onready var shortcut: Label = %Shortcut
@onready var _icon: TextureRect = %Icon
@onready var _count: Label = %Count
@onready var _count_bg: PanelContainer = %ShortcutBg

const DISABLED_MODULATE: Color = Color(0.4, 0.4, 0.4, 0.6)

var _prompt_action: String = ""
var _shortcut_data: ShortcutData = ShortcutData.new()
var _is_dragging_self: bool = false
var _disabled: bool = false


func _ready() -> void:
	# assigning your input action from Project Settings Input Map
	_prompt_action = "shortcut_%s_%s" % [shortcut_row, shortcut_number]
	if InputMap.has_action(_prompt_action):
		var key_action = InputMap.action_get_events(_prompt_action)[0]
		var key_string = OS.get_keycode_string(key_action.physical_keycode)
		shortcut.text = str(key_string)

	_update_display()


func _shortcut_input(event: InputEvent) -> void:
	if event.is_action_pressed(_prompt_action):
		trigger_shortcut()
		get_viewport().set_input_as_handled()


func _drop_data(_at_position: Vector2, data: Variant) -> void:
	var source_container = data.get("source_container", null)

	# Dragged from another shortcut slot: move it here, swapping if this slot
	# is already occupied, instead of leaving a copy behind.
	if source_container is ShortcutContainer and source_container != self:
		var previous_data: ShortcutData = _shortcut_data.duplicate()
		set_shortcut(data)

		if previous_data.is_empty():
			source_container.clear_shortcut()
		else:
			source_container.set_shortcut(_to_shortcut_dict(previous_data))
	else:
		set_shortcut(data)


# Accepts items as well as skills/attacks.
func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
	if typeof(data) != TYPE_DICTIONARY:
		return false

	return data["type"] == "item" or data["type"] == "attack"


# Allows an assigned shortcut to be picked up and dragged elsewhere.
func _get_drag_data(_at_position: Vector2) -> Variant:
	if _shortcut_data.is_empty():
		return null

	var preview: TextureRect = TextureRect.new()
	preview.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	preview.size = Vector2(50, 50)
	preview.pivot_offset = preview.size / 2.0
	preview.texture = _icon.texture
	set_drag_preview(preview)

	_is_dragging_self = true

	var drag_data := _to_shortcut_dict(_shortcut_data)
	if drag_data.is_empty():
		return null

	drag_data["source_container"] = self
	return drag_data


# Converts shortcut data into the dictionary format used both for drag data
# and for set_shortcut(), so a slot's contents can be handed off to another.
func _to_shortcut_dict(data: ShortcutData) -> Dictionary:
	match data.type:
		ShortcutData.ShortcutType.ITEM:
			return {"type": "item", "id": data.reference_id}
		ShortcutData.ShortcutType.ATTACK:
			return {"type": "attack", "id": data.reference_id}

	return {}


# NOTIFICATION_DRAG_END is broadcast to every control in the viewport, so only
# react when this container was the one that started the drag. If it wasn't
# dropped onto a valid target (e.g. dropped on the game background), remove it.
func _notification(what: int) -> void:
	if what == NOTIFICATION_DRAG_END and _is_dragging_self:
		_is_dragging_self = false
		if not get_viewport().gui_is_drag_successful():
			clear_shortcut()


func trigger_shortcut() -> void:
	if _shortcut_data.is_empty():
		return

	# Precondition no longer met (e.g. item left the inventory while offline).
	# Keep the assignment so it lights back up if the player gets the item
	# again, but don't let it fire.
	if _disabled:
		return

	# Default handling based on type
	match _shortcut_data.type:
		ShortcutData.ShortcutType.ITEM:
			_use_item()
		ShortcutData.ShortcutType.ATTACK:
			_use_attack()


# Sets the shortcut depending on the dropped class.
func set_shortcut(data: Dictionary) -> void:
	_shortcut_data.clear()

	if data["type"] == "item":
		_shortcut_data.type = ShortcutData.ShortcutType.ITEM
		_shortcut_data.reference_id = data["id"]
	elif data["type"] == "attack":
		_shortcut_data.type = ShortcutData.ShortcutType.ATTACK
		_shortcut_data.reference_id = data["id"]

	_update_display()
	shortcut_changed.emit(shortcut_row, shortcut_number, _shortcut_data)


func get_shortcut_data() -> ShortcutData:
	return _shortcut_data


func set_shortcut_data(data: ShortcutData) -> void:
	_shortcut_data = data
	_update_display()


func update_item_count(count: int) -> void:
	if _shortcut_data.type == ShortcutData.ShortcutType.ITEM:
		_count.text = str(count)
		_count_bg.visible = count > 0
		set_disabled(count <= 0)


# Grays out the icon and blocks trigger_shortcut() while keeping the
# assignment, drag/drop, and clearing fully functional.
func set_disabled(disabled: bool) -> void:
	_disabled = disabled
	_icon.modulate = DISABLED_MODULATE if disabled else Color.WHITE


func clear_shortcut() -> void:
	_shortcut_data.clear()
	_update_display()
	shortcut_changed.emit(shortcut_row, shortcut_number, _shortcut_data)


func _update_display() -> void:
	if _shortcut_data.is_empty():
		_icon.texture = null
		_icon.visible = false
		_count_bg.visible = false
		set_disabled(false)
		return

	_icon.visible = true

	match _shortcut_data.type:
		ShortcutData.ShortcutType.ITEM:
			var item = ItemDB.get_instance().get_item(_shortcut_data.reference_id)
			if item:
				_icon.texture = item.icon
				# Request initial count update, which also refreshes the disabled state
				item_count_requested.emit(shortcut_row, shortcut_number, _shortcut_data.reference_id)
				_count.visible = true
			else:
				printerr("Item ID %s not found in item_db, can not display it" % [_shortcut_data.reference_id])
		ShortcutData.ShortcutType.ATTACK:
			# TODO: Implement attack database similar to ItemDB
			# For now just show a placeholder
			_count.visible = false
			set_disabled(false)


func _use_item() -> void:
	var item_res = ItemDB.get_instance().get_item(_shortcut_data.reference_id)
	if item_res == null:
		printerr("ShortcutContainer: Can not use item, no item returned for ID: %s" % [_shortcut_data.reference_id])
		return
	item_res.use_item()


func _use_attack() -> void:
	# TODO: Send attack message to server
	print("ShortcutContainer: Using attack with ID: ", _shortcut_data.reference_id)
	# Placeholder for server communication
	# var attack_msg = AttackEntityCMSG.new()
	# attack_msg.UsedAttackId = _shortcut_data.reference_id
	# BnetSocket.send_message(attack_msg)
