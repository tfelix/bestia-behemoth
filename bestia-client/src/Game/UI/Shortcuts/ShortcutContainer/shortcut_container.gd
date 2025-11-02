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

var _prompt_action: String = ""
var _shortcut_data: ShortcutData = ShortcutData.new()


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
	set_shortcut(data)


# Accepts items as well as skills/attacks.
func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
	if typeof(data) != TYPE_DICTIONARY:
		return false

	return data["type"] == "item" or data["type"] == "attack"


func trigger_shortcut() -> void:
	if _shortcut_data.is_empty():
		return

	# Execute custom script if available
	if _shortcut_data.custom_script:
		_execute_custom_script()
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
		_count.text = str(count) if count > 1 else ""
		_count.visible = count > 0


func clear_shortcut() -> void:
	_shortcut_data.clear()
	_update_display()
	shortcut_changed.emit(shortcut_row, shortcut_number, _shortcut_data)


func _update_display() -> void:
	if _shortcut_data.is_empty():
		_icon.texture = null
		_icon.visible = false
		_count.visible = false
		return

	_icon.visible = true

	match _shortcut_data.type:
		ShortcutData.ShortcutType.ITEM:
			var item = ItemDB.get_instance().get_item(_shortcut_data.reference_id)
			if item:
				_icon.texture = item.icon
				# Request initial count update
				item_count_requested.emit(shortcut_row, shortcut_number, _shortcut_data.reference_id)
				_count.visible = true
			else:
				printerr("Item ID %s not found in item_db, can not display it." % [_shortcut_data.reference_id])
		ShortcutData.ShortcutType.ATTACK:
			# TODO: Implement attack database similar to ItemDB
			# For now just show a placeholder
			_count.visible = false


func _use_item() -> void:
	# TODO: Send message to server to use item
	print("Using item with ID: ", _shortcut_data.reference_id)
	# Placeholder for server communication
	# var use_item_msg = UseItemCMSG.new()
	# use_item_msg.item_id = _shortcut_data.reference_id
	# BnetSocket.send_message(use_item_msg)


func _use_attack() -> void:
	# TODO: Send attack message to server
	print("Using attack with ID: ", _shortcut_data.reference_id)
	# Placeholder for server communication
	# var attack_msg = AttackEntityCMSG.new()
	# attack_msg.UsedAttackId = _shortcut_data.reference_id
	# BnetSocket.send_message(attack_msg)


func _execute_custom_script() -> void:
	# Execute custom script behavior
	if _shortcut_data.custom_script and _shortcut_data.custom_script.has_method("execute"):
		var script_instance = _shortcut_data.custom_script.new()
		script_instance.execute(_shortcut_data)
	else:
		printerr("Custom script doesn't have 'execute' method")
