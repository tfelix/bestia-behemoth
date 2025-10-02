extends PanelContainer


var _master_info: MasterInfo
var _master_entity_id: int = 0

@onready var _master_name: Label = %MasterName
@onready var _level: Label = %Level
@onready var _position: Label = %Position


func _ready() -> void:
	_master_info = ConnectionManager.selected_master_info
	assert(_master_info != null)
	ConnectionManager.connect("self_received", _on_self_received)
	ConnectionManager.connect("entity_received", _on_entity_received)
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
