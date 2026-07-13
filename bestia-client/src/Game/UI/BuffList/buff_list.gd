extends VBoxContainer
## Shows the buff/debuff icons of whichever entity is currently selected via
## MouseManager. Hidden while nothing is selected. Rebuilds its icon children
## from scratch on every relevant update, mirroring Skills._populate_rows.

const BuffIconScene = preload("res://Game/UI/BuffList/BuffIcon/BuffIcon.tscn")

var _selected_entity_id: int = 0


func _ready() -> void:
	_clear_icons()
	visible = false
	MouseManager.entity_selected.connect(_on_entity_selected)
	ConnectionManager.connect("entity_received", _on_entity_received)


func _on_entity_selected(entity_id: int) -> void:
	_selected_entity_id = entity_id
	if entity_id == 0:
		visible = false
		_clear_icons()
		return

	visible = true
	var entity_manager = get_tree().get_first_node_in_group("entity_manager")
	var entity: Entity = entity_manager.get_entity(entity_id) if entity_manager else null
	_populate(entity.get_buffs() if entity else [])


func _on_entity_received(msg: EntitySMSG) -> void:
	if msg is BuffListSMSG and msg.EntityId == _selected_entity_id and _selected_entity_id != 0:
		_populate(msg.Buffs)


func _populate(buffs: Array) -> void:
	_clear_icons()
	for entry in buffs:
		var icon = BuffIconScene.instantiate()
		add_child(icon)
		icon.setup(entry)


func _clear_icons() -> void:
	for child in get_children():
		child.queue_free()
