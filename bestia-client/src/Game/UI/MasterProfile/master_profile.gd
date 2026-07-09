extends PanelContainer

var _master_info: MasterInfo
var _master_entity_id: int = 0

@onready var _master_name: Label = %MasterName
@onready var _level: Label = %Level
@onready var _position: Label = %Position

signal inventory_win_toggled
signal skills_win_toggled

func _ready() -> void:
	ConnectionManager.connect("self_received", _on_self_received)
	ConnectionManager.connect("entity_received", _on_entity_received)
	_master_info = ConnectionManager.selected_master_info

	if _master_info == null:
		printerr("No master info available")
		return

	_master_name.text = _master_info.Name
	_update_level(_master_info.Level)
	_update_position(_master_info.Position)


func _on_self_received(msg: SelfSMSG) -> void:
	_master_entity_id = msg.MasterEntityId


func _on_entity_received(msg: EntitySMSG) -> void:
	# Skip when it is not adressing our own entity.
	if msg.EntityId != _master_entity_id:
		return
	if msg is PositionComponent:
		_update_position(msg.Position)
	if msg is LevelComponentSMSG:
		_update_level(msg.Level)


func _update_level(level: int) -> void:
	_level.text = "Lv. %s" % level


func _update_position(pos: Vector3) -> void:
	_position.text = "X: %s, Y: %s, Z: %s" % [pos.x, pos.y, pos.z]


## Polls the global Input state instead of overriding _shortcut_input: the Skills window is a
## separate Window (its own viewport), so once it has OS focus, input events are delivered to
## its viewport and never reach this node - Input.is_action_just_pressed reflects key state
## application-wide regardless of which window/viewport currently has focus.
func _process(_delta: float) -> void:
	if Input.is_action_just_pressed("toggle_inventory"):
		_toggle_inventory()
	if Input.is_action_just_pressed("toggle_skills"):
		_toggle_skills()


func _toggle_inventory() -> void:
	emit_signal("inventory_win_toggled")


func _toggle_skills() -> void:
	emit_signal("skills_win_toggled")


func _on_inventory_pressed() -> void:
	_toggle_inventory()


func _on_skills_pressed() -> void:
	_toggle_skills()
