extends VBoxContainer
class_name BestiaParty

## The master and every bestia it owns, one row each; clicking a row asks the server to hand control over.
##
## Rows follow EntityManager rather than the wire: it already folds SelfSMSG and OwnedBestiasSMSG into one
## list and knows which entity is being driven.

## How often the health bars are re-read off the entity cache. Polled for the reason MasterProfile polls its
## position: the rows exist for entities whose component pushes may have arrived before them.
const _REFRESH_SECONDS := 0.25

const _ACTIVE_COLOR := Color(1.0, 0.85, 0.4)
const _IDLE_COLOR := Color(1.0, 1.0, 1.0)

## entity id -> {"button": Button, "hp": ProgressBar}
var _rows: Dictionary = {}
var _since_refresh := 0.0


func _ready() -> void:
	var entity_manager := EntityManager.get_instance()
	if entity_manager == null:
		return

	entity_manager.owned_bestias_changed.connect(_on_owned_bestias_changed)
	entity_manager.controlled_entity_changed.connect(_on_controlled_entity_changed)
	_rebuild()


func _process(delta: float) -> void:
	_since_refresh += delta
	if _since_refresh < _REFRESH_SECONDS:
		return
	_since_refresh = 0.0

	var entity_manager := EntityManager.get_instance()
	if entity_manager == null:
		return

	for entity_id in _rows:
		var entity: Entity = entity_manager.get_entity(entity_id)
		var health: ConditionPool = entity.get_health() if entity != null else null
		var bar: ProgressBar = _rows[entity_id]["hp"]
		if health == null or health.maximum <= 0:
			bar.value = bar.max_value
			continue
		bar.max_value = health.maximum
		bar.value = health.current


func _on_owned_bestias_changed(_bestias: Array[BestiaInfo]) -> void:
	_rebuild()


func _on_controlled_entity_changed(_previous_id: int, _entity_id: int) -> void:
	_highlight()


## The master's row only ever appears alongside a bestia: with nothing to switch to, the panel is noise.
func _rebuild() -> void:
	for child in get_children():
		child.queue_free()
	_rows.clear()

	var entity_manager := EntityManager.get_instance()
	var bestias := entity_manager.get_owned_bestias()
	if bestias.is_empty():
		return

	var master_name: String = tr("BESTIA_PARTY_MASTER")
	if ConnectionManager.selected_master_info != null:
		master_name = ConnectionManager.selected_master_info.Name
	_add_row(entity_manager.get_owned_master_entity_id(), master_name)

	for bestia in bestias:
		_add_row(bestia.EntityId, "%s  Lv. %d" % [_bestia_name(bestia), bestia.Level])

	_highlight()


func _add_row(entity_id: int, label: String) -> void:
	var row := VBoxContainer.new()
	row.add_theme_constant_override("separation", 1)

	var button := Button.new()
	button.text = label
	button.alignment = HORIZONTAL_ALIGNMENT_LEFT
	button.custom_minimum_size = Vector2(180, 0)
	button.pressed.connect(_on_row_pressed.bind(entity_id))
	row.add_child(button)

	var hp := ProgressBar.new()
	hp.show_percentage = false
	hp.custom_minimum_size = Vector2(180, 5)
	hp.modulate = Color(0.85, 0.25, 0.25)
	row.add_child(hp)

	add_child(row)
	_rows[entity_id] = {"button": button, "hp": hp}


func _highlight() -> void:
	var entity_manager := EntityManager.get_instance()
	var active_id: int = entity_manager.get_controlled_entity_id() if entity_manager else 0
	for entity_id in _rows:
		var button: Button = _rows[entity_id]["button"]
		button.add_theme_color_override("font_color", _ACTIVE_COLOR if entity_id == active_id else _IDLE_COLOR)


func _on_row_pressed(entity_id: int) -> void:
	var entity_manager := EntityManager.get_instance()
	if entity_manager == null or entity_id == entity_manager.get_controlled_entity_id():
		return

	ConnectionManager.select_active_entity(entity_id)


## A bestia the player has not named goes by its species.
func _bestia_name(bestia: BestiaInfo) -> String:
	if bestia.Name != "":
		return bestia.Name

	var species := BestiaDB.get_instance().get_bestia(bestia.MobId)
	return tr(species.name_key) if species != null else "?"
