extends VBoxContainer
## Shows the buff/debuff icons of the entity the player controls (top-right HUD) - their
## master or the bestia they switched to, not whatever's been clicked with the mouse;
## MouseManager selection is for targeting, unrelated to this. Rebuilds its icon children
## from scratch on every relevant update, mirroring Skills._populate_rows.

const BuffIconScene = preload("res://Game/UI/BuffList/BuffIcon/BuffIcon.tscn")

var _shown_entity_id: int = 0


func _ready() -> void:
	_clear_icons()
	visible = false
	ConnectionManager.connect("entity_received", _on_entity_received)

	# Also fires for the master on the first SelfSMSG.
	var entity_manager := EntityManager.get_instance()
	if entity_manager != null:
		entity_manager.controlled_entity_changed.connect(_on_controlled_entity_changed)


## Seeds from whatever the Entity node already has cached (see entity.gd/
## entity_manager.gd) so this is correct immediately, without waiting for the next
## live BuffListSMSG - mirrors Skills._seed_skill_points_from_cache. A ward keeps the
## master warded at home while a bestia it switched to roams far off, so following the
## master here showed a protection the driven entity did not have.
func _on_controlled_entity_changed(_previous_id: int, entity_id: int) -> void:
	_shown_entity_id = entity_id
	visible = _shown_entity_id != 0

	var entity_manager := EntityManager.get_instance()
	var entity: Entity = entity_manager.get_entity(_shown_entity_id) if entity_manager else null
	_populate(entity.get_effects() if entity else [])


func _on_entity_received(msg: EntitySMSG) -> void:
	if msg is BuffListSMSG and msg.EntityId == _shown_entity_id and _shown_entity_id != 0:
		_populate(msg.Effects)


func _populate(effects: Array) -> void:
	_clear_icons()
	for entry in effects:
		var icon = BuffIconScene.instantiate()
		add_child(icon)
		icon.setup(entry)


func _clear_icons() -> void:
	for child in get_children():
		child.queue_free()
