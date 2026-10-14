extends PanelContainer
## The entity the player drives right now: their master, or the bestia they took control of.

const WEIGHT_UNITS_PER_KG := 100.0

var _master_info: MasterInfo
var _shown_entity_id: int = 0

# Last tile written to the position label, and whether anything has been written yet.
var _shown_tile: Vector3i = Vector3i.ZERO
var _has_shown_tile: bool = false

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
	add_to_group("world_blocking_ui")
	ConnectionManager.connect("entity_received", _on_entity_received)
	_master_info = ConnectionManager.selected_master_info

	# Also fires for the master on the first SelfSMSG.
	var entity_manager := EntityManager.get_instance()
	if entity_manager != null:
		entity_manager.controlled_entity_changed.connect(_on_controlled_entity_changed)

	if _master_info == null:
		printerr("No master info available")
		return

	_master_name.text = _master_info.Name
	_update_level(_master_info.Level)
	_update_position(_master_info.Position)


func _on_controlled_entity_changed(_previous_id: int, entity_id: int) -> void:
	_shown_entity_id = entity_id
	_has_shown_tile = false
	_clear_entity_values()

	var bestia := _owned_bestia(entity_id)
	if bestia == null:
		_show_master_identity()
	else:
		_show_bestia_identity(bestia)

	_seed_from_cache()


## The bars of the previous entity must not pass for this one's. The server resends this one's own values
## on every switch, so they are back within a tick.
func _clear_entity_values() -> void:
	for bar in [[_health_bar, _hp_value], [_mana_bar, _mana_value], [_stamina_bar, _stamina_value]]:
		_clear_bar(bar[0], bar[1])
	_clear_bar(_exp_bar, null)


## A maximum of 0 would draw the bar full.
func _clear_bar(bar: ProgressBar, value_label: Label) -> void:
	bar.max_value = 1
	bar.value = 0
	if value_label != null:
		value_label.text = "0 / 0"


func _show_master_identity() -> void:
	_profile_portrait.show_master()
	if _master_info == null:
		return
	_master_name.text = _master_info.Name
	_update_level(_master_info.Level)


func _show_bestia_identity(bestia: BestiaInfo) -> void:
	_profile_portrait.show_bestia(bestia.MobId)
	_master_name.text = OwnedSlots.name_of(bestia)
	_update_level(bestia.Level)


func _owned_bestia(entity_id: int) -> BestiaInfo:
	var entity_manager := EntityManager.get_instance()
	if entity_manager == null:
		return null
	for bestia in entity_manager.get_owned_bestias():
		if bestia.EntityId == entity_id:
			return bestia
	return null


## Seeds the condition bars from the shown Entity's cache (kept up to date by
## entity_manager.gd/entity.gd), because _on_entity_received below drops everything that
## arrives for another entity, and pools that are full are not pushed again until they change.
## Mirrors StatusPoints._seed_from_cache.
func _seed_from_cache() -> void:
	var entity_manager := EntityManager.get_instance()
	var entity: Entity = entity_manager.get_entity(_shown_entity_id) if entity_manager else null
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
	# The master's portrait is kept current while a bestia is shown, so switching back needs no resend.
	if msg is MasterVisualComponentSMSG and msg.EntityId == _owned_master_entity_id():
		_profile_portrait.apply_visual(msg)
		return

	if msg.EntityId != _shown_entity_id:
		return
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
		_update_weight(msg.Current, msg.Max)


func _owned_master_entity_id() -> int:
	var entity_manager := EntityManager.get_instance()
	return entity_manager.get_owned_master_entity_id() if entity_manager else 0


func _update_level(level: int) -> void:
	_level.text = "Lv. %s" % level


func _update_position(pos: Vector3) -> void:
	# Labelled with the server's axis names, not Godot's. A player reads these numbers straight back
	# into /carve and /mm, and both take x/y horizontal with z as the height - printing Godot's y-up
	# vector verbatim called the height "Y", and a triple copied out of here aimed a carve kilometres
	# into the sky. Whole numbers for the same reason: a position is a voxel index, and /carve's
	# parser accepts integers only.
	var tile := Vector3i(TileSpace.to_server_axes(pos))
	if _has_shown_tile and tile == _shown_tile:
		return

	_shown_tile = tile
	_has_shown_tile = true
	_position.text = "X: %s, Y: %s, Z: %s" % [tile.x, tile.y, tile.z]


## Reads the position off the shown entity rather than waiting for a PositionComponent: the server
## sends one on a spawn or teleport and then only every MoveSystem.POSITION_RESYNC_STEPS tiles, so
## a walk shorter than that delivers none and the readout would sit on where it started.
func _poll_position() -> void:
	var entity_manager := EntityManager.get_instance()
	var entity: Entity = entity_manager.get_entity(_shown_entity_id) if entity_manager else null
	if entity == null:
		return

	_update_position(entity.get_logical_position())


func _update_bar(bar: ProgressBar, value_label: Label, current: int, max_value: int) -> void:
	bar.max_value = max_value
	bar.value = current
	value_label.text = "%s / %s" % [current, max_value]


## Carry weight arrives in the server's own units - fixed point at 100 per kilogram, see `Item.weight`
## - because that is the scale the weight limit is computed on and nothing on the wire converts it.
## Displayed in kilograms, since the raw units would read "1 / 2275" and mean nothing to a player. One
## decimal is deliberate: a chart weighs 10g and should show as 0.0, while a lump of ore moves the
## number by 3.0.
func _update_weight(current: int, max_weight: int) -> void:
	_weight_label.text = "Weight: %.1f / %.1f kg" % [
		current / WEIGHT_UNITS_PER_KG, max_weight / WEIGHT_UNITS_PER_KG
	]


func _update_exp(exp: int, required_exp_next_level: int) -> void:
	_exp_bar.max_value = required_exp_next_level
	_exp_bar.value = exp


func _process(_delta: float) -> void:
	_poll_position()

	# The global Input state rather than _shortcut_input: the Skills window is a separate Window
	# (its own viewport), so once it has OS focus its events never reach this node, while
	# is_action_just_pressed reflects key state application-wide.
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
