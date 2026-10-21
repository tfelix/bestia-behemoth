extends VBoxContainer
class_name BestiaParty

## The master and every bestia slot it has, one row each; clicking an owned row asks the server to hand control
## over. The bar on a row's left is the colour of that entity's dot on the map.
##
## Rows follow EntityManager rather than the wire: it already folds SelfSMSG and OwnedBestiasSMSG into one
## list and knows which entity is being driven.

## How often the health bars are re-read off the entity cache. Polled for the reason MasterProfile polls its
## position: the rows exist for entities whose component pushes may have arrived before them.
const _REFRESH_SECONDS := 0.25

const _ACTIVE_COLOR := Color(1.0, 0.85, 0.4)
const _IDLE_COLOR := Color(1.0, 1.0, 1.0)

const _ROW_WIDTH := 180.0
const _SLOT_BAR_WIDTH := 4.0
const _HP_BAR_HEIGHT := 4.0
## Small enough that the master and four bestia rows end above the chat.
const _FONT_SIZE := 13

## entity id -> {"button": Button, "hp": ProgressBar}
var _rows: Dictionary = {}
var _since_refresh := 0.0


func _ready() -> void:
	add_to_group("world_blocking_ui")

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
		var health: ConditionPool = entity_manager.get_health_of(entity_id)
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


## Empty slots show from the start, so a new player learns there is room for bestia before they catch one.
func _rebuild() -> void:
	# Removed before freed, so the old rows do not take up layout space for one more frame.
	for child in get_children():
		remove_child(child)
		child.queue_free()
	_rows.clear()

	var entity_manager := EntityManager.get_instance()
	if entity_manager.get_owned_master_entity_id() == 0:
		# No SelfSMSG yet, so there is no master to list.
		return

	var bestias := entity_manager.get_owned_bestias()

	var master_name: String = tr("BESTIA_PARTY_MASTER")
	if ConnectionManager.selected_master_info != null:
		master_name = ConnectionManager.selected_master_info.Name
	_add_owned_row(entity_manager.get_owned_master_entity_id(), master_name, OwnedSlots.color_of(0))

	for i in bestias.size():
		var bestia := bestias[i]
		var label := "%s  Lv. %d" % [OwnedSlots.name_of(bestia), bestia.Level]
		_add_owned_row(bestia.EntityId, label, OwnedSlots.color_of(i + 1))

	for i in maxi(entity_manager.get_bestia_slot_count() - bestias.size(), 0):
		_add_empty_row()

	_highlight()


func _add_owned_row(entity_id: int, label: String, slot_color: Color) -> void:
	var button := _make_slot_button(label)
	button.pressed.connect(_on_row_pressed.bind(entity_id))

	var hp := ProgressBar.new()
	hp.show_percentage = false
	hp.custom_minimum_size = Vector2(0, _HP_BAR_HEIGHT)
	hp.modulate = Color(0.85, 0.25, 0.25)

	_add_row(slot_color, button, hp)
	_rows[entity_id] = {"button": button, "hp": hp}


## Disabled rather than a plain label so it has the same height and frame as an owned row, only greyed out.
func _add_empty_row() -> void:
	var button := _make_slot_button(tr("BESTIA_PARTY_EMPTY_SLOT"))
	button.disabled = true
	button.tooltip_text = tr("BESTIA_PARTY_EMPTY_SLOT_HINT")

	var no_hp := Control.new()
	no_hp.custom_minimum_size = Vector2(0, _HP_BAR_HEIGHT)

	_add_row(OwnedSlots.EMPTY_COLOR, button, no_hp)


func _make_slot_button(label: String) -> Button:
	var button := Button.new()
	button.text = label
	button.alignment = HORIZONTAL_ALIGNMENT_LEFT
	button.add_theme_font_size_override("font_size", _FONT_SIZE)
	return button


func _add_row(slot_color: Color, button: Button, below_button: Control) -> void:
	var slot_bar := ColorRect.new()
	slot_bar.color = slot_color
	slot_bar.custom_minimum_size = Vector2(_SLOT_BAR_WIDTH, 0)
	slot_bar.mouse_filter = Control.MOUSE_FILTER_IGNORE

	var stack := VBoxContainer.new()
	stack.add_theme_constant_override("separation", 1)
	stack.custom_minimum_size = Vector2(_ROW_WIDTH - _SLOT_BAR_WIDTH, 0)
	stack.add_child(button)
	stack.add_child(below_button)

	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 0)
	row.add_child(slot_bar)
	row.add_child(stack)
	add_child(row)


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
