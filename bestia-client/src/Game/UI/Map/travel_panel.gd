class_name TravelPanel
extends VBoxContainer

## The side of the map a journey is planned from: which of the player's entities to send, where it is going
## now, and the stops drafted for its next journey.

signal entity_chosen(entity_id: int)
signal go_pressed
signal clear_pressed
## Asks to stop [param entity_id] where it stands.
signal stop_pressed(entity_id: int)

const _SWATCH_SIZE := 12.0
const _FONT_SIZE := 13

var _entity_rows: VBoxContainer
var _status: Label
var _stop: Button
var _stops: VBoxContainer
var _go: Button
var _clear: Button
var _chosen_id := 0
var _entity_group := ButtonGroup.new()


func _ready() -> void:
	custom_minimum_size.x = 240.0
	add_theme_constant_override("separation", 6)

	_add_heading(tr("TRAVEL_TITLE"))
	_entity_rows = VBoxContainer.new()
	add_child(_entity_rows)

	_status = _label("")
	_status.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	add_child(_status)
	_stop = _button(tr("TRAVEL_STOP"), func() -> void: stop_pressed.emit(_chosen_id))
	add_child(_stop)

	_add_heading(tr("TRAVEL_DRAFT"))
	var hint := _label(tr("TRAVEL_HINT"))
	hint.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	hint.modulate = Color(1, 1, 1, 0.7)
	add_child(hint)
	_stops = VBoxContainer.new()
	add_child(_stops)

	var buttons := HBoxContainer.new()
	_go = _button(tr("TRAVEL_GO"), func() -> void: go_pressed.emit())
	_clear = _button(tr("TRAVEL_CLEAR"), func() -> void: clear_pressed.emit())
	buttons.add_child(_go)
	buttons.add_child(_clear)
	add_child(buttons)

	show_draft([])


## One row per owned entity, master first, each in its slot colour. Keeps [param chosen_id] chosen.
func show_entities(entity_manager: EntityManager, chosen_id: int) -> void:
	_chosen_id = chosen_id
	for row in _entity_rows.get_children():
		row.queue_free()

	var owned_ids := entity_manager.get_owned_entity_ids()
	for slot in owned_ids.size():
		var entity_id := owned_ids[slot]
		if entity_id == 0:
			continue
		_entity_rows.add_child(_entity_row(entity_id, slot, _name_of(entity_manager, entity_id)))

	show_journey(entity_manager.get_journey(_chosen_id))


## Where the chosen entity is going now, if anywhere.
func show_journey(journey: JourneyComponentSMSG) -> void:
	_stop.visible = journey != null
	if journey == null:
		_status.text = tr("TRAVEL_STANDING")
		return

	var left := journey.Waypoints.size() - journey.NextIndex
	var title := journey.Name if journey.Name != "" else tr("TRAVEL_UNNAMED")
	_status.text = tr("TRAVEL_UNDER_WAY") % [title, left]


func chosen_entity_id() -> int:
	return _chosen_id


## The drafted stops, numbered, each with its distance from the one before.
func show_draft(stops: Array[Vector2]) -> void:
	for row in _stops.get_children():
		row.queue_free()

	var previous: Variant = null
	for i in stops.size():
		var leg := "" if previous == null else "  +%s" % _distance_text((stops[i] - previous).length())
		_stops.add_child(_label("%d.%s" % [i + 1, leg]))
		previous = stops[i]

	_go.disabled = stops.is_empty()
	_clear.disabled = stops.is_empty()


func _entity_row(entity_id: int, slot: int, text: String) -> Button:
	var row := _button(text, func() -> void:
		_chosen_id = entity_id
		entity_chosen.emit(entity_id)
	)
	row.toggle_mode = true
	row.button_group = _entity_group
	row.button_pressed = entity_id == _chosen_id
	row.alignment = HORIZONTAL_ALIGNMENT_LEFT
	row.icon = _swatch(OwnedSlots.color_of(slot))
	return row


func _name_of(entity_manager: EntityManager, entity_id: int) -> String:
	var bestia_name := entity_manager.owned_name_of(entity_id)
	return bestia_name if bestia_name != "" else tr("TRAVEL_MASTER")


func _distance_text(metres: float) -> String:
	if metres >= 1000.0:
		return "%.1f km" % (metres / 1000.0)
	return "%d m" % int(metres)


func _swatch(colour: Color) -> Texture2D:
	var image := Image.create(int(_SWATCH_SIZE), int(_SWATCH_SIZE), false, Image.FORMAT_RGBA8)
	image.fill(colour)
	return ImageTexture.create_from_image(image)


func _add_heading(text: String) -> void:
	var heading := _label(text)
	heading.add_theme_font_size_override("font_size", _FONT_SIZE + 3)
	add_child(heading)


func _label(text: String) -> Label:
	var label := Label.new()
	label.text = text
	label.add_theme_font_size_override("font_size", _FONT_SIZE)
	return label


func _button(text: String, on_pressed: Callable) -> Button:
	var button := Button.new()
	button.text = text
	button.focus_mode = Control.FOCUS_NONE
	button.add_theme_font_size_override("font_size", _FONT_SIZE)
	button.pressed.connect(on_pressed)
	return button
