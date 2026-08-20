extends PanelContainer

var _master_info: MasterInfo
var _master_entity_id: int = 0

@onready var _master_name: Label = %MasterName
@onready var _level: Label = %Level
@onready var _position: Label = %Position
@onready var _health_bar: ProgressBar = %HealthBar
@onready var _hp_value: Label = %HPValue
@onready var _mana_bar: ProgressBar = %ManaBar
@onready var _mana_value: Label = %ManaValue
@onready var _stamina_bar: ProgressBar = %StaminaBar
@onready var _stamina_value: Label = %StaminaValue
@onready var _exp_bar: ProgressBar = %ExpBar
@onready var _weight_label: Label = %WeightLabel
@onready var _profile_portrait = %ProfileImage

signal inventory_win_toggled
signal skills_win_toggled
signal equipment_win_toggled
signal status_win_toggled

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
	_seed_from_cache()


## Seeds the condition bars from the master Entity's cache (kept up to date by
## entity_manager.gd/entity.gd), because _on_entity_received below has to drop everything that
## arrives before _master_entity_id is known - and a master spawns with full pools, so the server's
## push at spawn is the only one it will get until something damages it. Mirrors
## StatusPoints._seed_from_cache.
func _seed_from_cache() -> void:
	var entity_manager := EntityManager.get_instance()
	var entity: Entity = entity_manager.get_entity(_master_entity_id) if entity_manager else null
	if entity == null:
		return

	_seed_bar(_health_bar, _hp_value, entity.get_health())
	_seed_bar(_mana_bar, _mana_value, entity.get_mana())
	_seed_bar(_stamina_bar, _stamina_value, entity.get_stamina())


## Skips pools we have never received: an absent pool is not the same as a pool of zero, and writing
## a 0/0 bar over a value a live update already delivered would be a regression, not a seed.
func _seed_bar(bar: ProgressBar, value_label: Label, pool: ConditionPool) -> void:
	if pool == null:
		return
	_update_bar(bar, value_label, pool.current, pool.maximum)


func _on_entity_received(msg: EntitySMSG) -> void:
	# Skip when it is not adressing our own entity.
	if msg.EntityId != _master_entity_id:
		return
	if msg is PositionComponent:
		_update_position(msg.Position)
	if msg is LevelComponentSMSG:
		_update_level(msg.Level)
	if msg is HealthComponentSMSG:
		_update_bar(_health_bar, _hp_value, msg.Current, msg.Max)
	if msg is ManaComponentSMSG:
		_update_bar(_mana_bar, _mana_value, msg.Current, msg.Max)
	if msg is StaminaComponentSMSG:
		_update_bar(_stamina_bar, _stamina_value, msg.Current, msg.Max)
	if msg is ExpComponentSMSG:
		_update_exp(msg.Exp, msg.RequiredExpNextLevel)
	if msg is CarryCapacityComponentSMSG:
		_weight_label.text = "Weight: %s / %s" % [msg.Current, msg.Max]
	if msg is MasterVisualComponentSMSG:
		_profile_portrait.apply_visual(msg)


func _update_level(level: int) -> void:
	_level.text = "Lv. %s" % level


func _update_position(pos: Vector3) -> void:
	# Labelled with the server's axis names, not Godot's. A player reads these numbers straight back
	# into /carve and /mm, and both take x/y horizontal with z as the height - printing Godot's y-up
	# vector verbatim called the height "Y", and a triple copied out of here aimed a carve kilometres
	# into the sky. Whole numbers for the same reason: a position is a voxel index, and /carve's
	# parser accepts integers only.
	var shown := TileSpace.to_server_axes(pos)
	_position.text = "X: %s, Y: %s, Z: %s" % [int(shown.x), int(shown.y), int(shown.z)]


func _update_bar(bar: ProgressBar, value_label: Label, current: int, max_value: int) -> void:
	bar.max_value = max_value
	bar.value = current
	value_label.text = "%s / %s" % [current, max_value]


func _update_exp(exp: int, required_exp_next_level: int) -> void:
	_exp_bar.max_value = required_exp_next_level
	_exp_bar.value = exp


## Polls the global Input state instead of overriding _shortcut_input: the Skills window is a
## separate Window (its own viewport), so once it has OS focus, input events are delivered to
## its viewport and never reach this node - Input.is_action_just_pressed reflects key state
## application-wide regardless of which window/viewport currently has focus.
func _process(_delta: float) -> void:
	if Input.is_action_just_pressed("toggle_inventory"):
		_toggle_inventory()
	if Input.is_action_just_pressed("toggle_skills"):
		_toggle_skills()
	if Input.is_action_just_pressed("toggle_equipment"):
		_toggle_equipment()
	if Input.is_action_just_pressed("toggle_status"):
		_toggle_status()


func _toggle_inventory() -> void:
	emit_signal("inventory_win_toggled")


func _toggle_skills() -> void:
	emit_signal("skills_win_toggled")


func _toggle_equipment() -> void:
	emit_signal("equipment_win_toggled")


func _toggle_status() -> void:
	emit_signal("status_win_toggled")


func _on_inventory_pressed() -> void:
	_toggle_inventory()


func _on_skills_pressed() -> void:
	_toggle_skills()


func _on_equipment_pressed() -> void:
	_toggle_equipment()


func _on_status_pressed() -> void:
	_toggle_status()
