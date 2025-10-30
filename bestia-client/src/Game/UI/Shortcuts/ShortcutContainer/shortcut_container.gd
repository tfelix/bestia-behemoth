extends PanelContainer

@export
var shortcut_row: int = 0

@export
var shortcut_number: int = 0

@onready var shortcut: Label = $MOuter/ShortcutBg/MInner/Shortcut

var _prompt_action: String = ""


func _ready() -> void:
	# assigning your input action from Project Settings Input Map
	_prompt_action = "shortcut_%s_%s" % [shortcut_row, shortcut_number]
	if InputMap.has_action(_prompt_action):
		var key_action = InputMap.action_get_events(_prompt_action)[0]
		var key_string = OS.get_keycode_string(key_action.physical_keycode)
		shortcut.text = str(key_string)


func _shortcut_input(event: InputEvent) -> void:
	if event.is_action_pressed(_prompt_action):
		trigger_shortctu()
		get_viewport().set_input_as_handled()


func _drop_data(_at_position: Vector2, data: Variant) -> void:
	set_shortcut(data)


# Accepts items as well as skills/attacks.
func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
	return typeof(data) == TYPE_DICTIONARY and data["type"] == "item" or data["type"] == "attack"


func trigger_shortctu() -> void:
	# trigger the set shortcut.
	print(_prompt_action, " pressed")


# Sets the shortcut depending on the dropped class.
func set_shortcut(data: Dictionary) -> void:
	print("Dropped %s on shortcut %s" % [data, _prompt_action])
	pass
