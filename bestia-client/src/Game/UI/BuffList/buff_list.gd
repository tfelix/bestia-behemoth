extends VBoxContainer
## Shows the buff/debuff icons of the player's own master entity (top-right HUD) -
## not whatever's been clicked with the mouse; MouseManager selection is for
## targeting, unrelated to this. Rebuilds its icon children from scratch on every
## relevant update, mirroring Skills._populate_rows.

const BuffIconScene = preload("res://Game/UI/BuffList/BuffIcon/BuffIcon.tscn")

var _master_entity_id: int = 0


func _ready() -> void:
	_clear_icons()
	visible = false
	ConnectionManager.connect("self_received", _on_self_received)
	ConnectionManager.connect("entity_received", _on_entity_received)


## Seeds from whatever the owned Entity node already has cached (see entity.gd/
## entity_manager.gd) so this is correct immediately, without waiting for the next
## live BuffListSMSG - mirrors Skills._seed_skill_points_from_cache.
func _on_self_received(msg: SelfSMSG) -> void:
	_master_entity_id = msg.MasterEntityId
	visible = _master_entity_id != 0

	var entity_manager := EntityManager.get_instance()
	var entity: Entity = entity_manager.get_entity(_master_entity_id) if entity_manager else null
	_populate(entity.get_buffs() if entity else [])


func _on_entity_received(msg: EntitySMSG) -> void:
	if msg is BuffListSMSG and msg.EntityId == _master_entity_id and _master_entity_id != 0:
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
