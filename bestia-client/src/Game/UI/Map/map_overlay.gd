class_name MapOverlay
extends Control

## The full map, over the whole screen, opened with M.
##
## An overlay rather than a [WidgetWindow] because a map is worth looking at large - the windows are for
## lists and slots, and this is the one panel whose value is its area. It dims the game behind it rather than
## pausing: the world carries on, and the player marker keeps moving while the map is open.
##
## Opens centred on the player and then stops following, so panning away stays where it was put. Re-opening
## re-centres, which is the behaviour that needs no button - but re-opening keeps the zoom, because a player
## who went out to look at the next valley wants it there again, and the centre is the part that goes stale.
##
## Only openable while a chart is carried, for the reason [Minimap] is only visible then: charts are the only
## source of map knowledge, so a player holding none would get a full screen of fog. A whole screen of it is
## the worse version of the empty panel that argument was about.

## Forwarded from the view, so a listener wires to the widget it can see rather than reaching through
## it for a child. See [MapView] for what each one means.
signal mark_requested(at: Vector2)
signal mark_clicked(index: int)
signal place_selected(place: Dictionary)
signal service_selected(service: Dictionary)

## The player switched a service kind on or off in the legend. Carries every kind now off, so a listener can
## store the answer rather than replay the clicks.
signal hidden_services_changed(kinds: Array[String])


@onready var _view: MapView = $Panel/Margin/Rows/Body/View
@onready var _travel: TravelPanel = $Panel/Margin/Rows/Body/Travel
@onready var _title: Label = $Panel/Margin/Rows/Header/Title
@onready var _scale_label: Label = $Panel/Margin/Rows/Footer/Scale
@onready var _legend: HBoxContainer = $Panel/Margin/Rows/Legend

## How often an open overlay re-asks after the services in view. It is being read, so a shop knocked down a
## minute ago should be gone from it.
const _SERVICES_REFRESH_SECONDS := 30.0

## Edge of the glyph drawn beside each legend entry, in pixels.
const _SWATCH_SIZE := 14.0

## The legend's toggles, by kind.
var _toggles: Dictionary = {}

## Level the map opens at [i]the first time[/i]; after that the view keeps whatever the wheel left it on.
## Level 3 is 8 m to the pixel, so the panel shows about 8 km of ground across - room for the widest single
## survey with some of its surroundings.
##
## Deliberately not the whole world, which was the first answer and the wrong one. Charts are the only source
## of map knowledge, so the whole world at a glance is almost entirely fog: the widest single survey is 5 km,
## which at world scale is a couple of dozen pixels and reads as a broken window rather than as an unexplored
## one. This opens at a zoom where the ground a player actually has is legible, and leaves the wheel to go out
## from there.
##
## Level 3 rather than the 16 km of level 4 because it is the coarsest level that draws services (see
## [method MapView.draws_services]): opening one step further out would open on a map with its shops, inns and
## ward stones missing, and the legend for them hidden.
const _OPEN_LEVEL := 3

var _has_chart: bool = false

## Names the species whose habitat is shown, and clears it when pressed. Hidden while none is.
var _habitat_chip: Button


func setup(source: MapSource, entities: Node) -> void:
	_view.mark_requested.connect(func(at: Vector2) -> void: mark_requested.emit(at))
	_view.mark_clicked.connect(func(index: int) -> void: mark_clicked.emit(index))
	_view.place_selected.connect(func(place: Dictionary) -> void: place_selected.emit(place))
	_view.service_selected.connect(func(service: Dictionary) -> void: service_selected.emit(service))
	_view.interactive = true
	_view.can_travel = true
	_view.services_refresh_seconds = _SERVICES_REFRESH_SECONDS
	_view.setup(source, entities)
	_view.go_to_level(_OPEN_LEVEL)
	_wire_travel(entities)

	_habitat_chip.pressed.connect(func() -> void: source.set_habitat_species(0))
	source.habitat_species_changed.connect(_show_habitat_chip)


## The panel picks who the map's orders go to; the view drafts the stops and draws every journey.
func _wire_travel(entities: EntityManager) -> void:
	_travel.entity_chosen.connect(func(entity_id: int) -> void:
		_view.order_entity_id = entity_id
		_travel.show_journey(entities.get_journey(entity_id))
		_view.queue_redraw()
	)
	_travel.go_pressed.connect(_view.send_draft)
	_travel.clear_pressed.connect(_view.clear_draft)
	_travel.stop_pressed.connect(func(entity_id: int) -> void:
		var none: Array[Vector3] = []
		ConnectionManager.send_on_journey(entity_id, none)
	)
	_view.draft_changed.connect(func() -> void: _travel.show_draft(_view.draft))
	_travel.save_draft_pressed.connect(_save_draft_as_route)
	_travel.route_sent.connect(_send_along_route)
	_travel.route_renamed.connect(func(route: SavedRouteInfo, new_name: String) -> void:
		ConnectionManager.rename_route(route.Id, new_name)
	)
	_travel.route_deleted.connect(func(route: SavedRouteInfo) -> void: ConnectionManager.delete_route(route.Id))
	ConnectionManager.saved_routes_received.connect(func(message: SavedRoutesSMSG) -> void:
		_travel.show_routes(message.Routes)
	)
	entities.journey_changed.connect(func(entity_id: int) -> void:
		if entity_id == _travel.chosen_entity_id():
			_travel.show_journey(entities.get_journey(entity_id))
	)
	entities.owned_bestias_changed.connect(func(_bestias: Array[BestiaInfo]) -> void: _show_travellers(entities))
	entities.controlled_entity_changed.connect(func(_previous: int, _now: int) -> void: _show_travellers(entities))


## A route starts where the entity it was planned for stands, so it can be walked back the other way too.
func _save_draft_as_route() -> void:
	var entity := _view.order_entity()
	if entity == null or _view.draft.is_empty():
		return

	var waypoints: Array[Vector3] = [entity.get_logical_position()]
	waypoints.append_array(_view.draft_waypoints())
	ConnectionManager.save_route(waypoints)
	_view.clear_draft()


func _send_along_route(route: SavedRouteInfo, backwards: bool) -> void:
	var entity := _view.order_entity()
	if entity == null:
		return

	var waypoints: Array[Vector3] = []
	waypoints.assign(route.Waypoints)
	if backwards:
		waypoints.reverse()
	ConnectionManager.send_on_journey(entity.entity_id, waypoints, false, route.Name, route.Id)


## Orders go to the entity being driven until the player picks another, and again whenever it changes hands.
func _show_travellers(entities: EntityManager) -> void:
	var chosen := entities.get_controlled_entity_id()
	_view.order_entity_id = chosen
	_travel.show_entities(entities, chosen)


func _ready() -> void:
	add_to_group("world_blocking_ui")
	visible = false
	_build_legend()
	_build_habitat_chip()


## One toggle per service kind, each beside the glyph the map draws for it.
##
## Built here rather than laid out in the scene so the legend cannot list a different set of kinds from the one
## [MapView] draws: both read [constant MapView.SERVICE_KINDS].
func _build_legend() -> void:
	for kind in MapView.SERVICE_KINDS:
		var swatch := Control.new()
		swatch.custom_minimum_size = Vector2(_SWATCH_SIZE, _SWATCH_SIZE)
		swatch.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		swatch.mouse_filter = Control.MOUSE_FILTER_IGNORE
		swatch.draw.connect(func() -> void:
			MapView.draw_service_glyph(swatch, swatch.size * 0.5, kind, _SWATCH_SIZE * 0.3)
		)

		var toggle := CheckBox.new()
		toggle.text = tr("SERVICE_" + kind)
		toggle.button_pressed = true
		toggle.focus_mode = Control.FOCUS_NONE
		toggle.add_theme_font_size_override("font_size", 12)
		toggle.toggled.connect(func(_on: bool) -> void: _on_legend_toggled())

		_legend.add_child(swatch)
		_legend.add_child(toggle)
		_toggles[kind] = toggle


func _build_habitat_chip() -> void:
	_habitat_chip = Button.new()
	_habitat_chip.visible = false
	_habitat_chip.focus_mode = Control.FOCUS_NONE
	_habitat_chip.add_theme_font_size_override("font_size", 12)
	_habitat_chip.tooltip_text = tr("MAP_HABITAT_CLEAR")
	_title.get_parent().add_child(_habitat_chip)
	_title.get_parent().move_child(_habitat_chip, _title.get_index() + 1)


func _show_habitat_chip(species_id: int) -> void:
	var species := BestiaDB.get_instance().get_bestia(species_id) if species_id != 0 else null
	_habitat_chip.visible = species != null
	if species != null:
		_habitat_chip.text = tr("MAP_HABITAT_CHIP") % tr(species.name_key)


func _on_legend_toggled() -> void:
	var hidden: Array[String] = []
	for kind in _toggles:
		if not _toggles[kind].button_pressed:
			hidden.append(kind)

	_view.hidden_services = hidden
	hidden_services_changed.emit(hidden)


## Shows the kinds the player had switched off, as stored. Sets the toggles without them reporting back, so
## loading the setting does not also save it again.
func set_hidden_services(kinds: Array[String]) -> void:
	for kind in _toggles:
		_toggles[kind].set_pressed_no_signal(kind not in kinds)

	_view.hidden_services = kinds.duplicate()


## Called whenever the inventory changes, like [method Minimap.set_has_chart].
##
## Closes an open map rather than leaving it to the next keypress: the charts can go while it is being read -
## dropped, traded, or the last one used up - and what is on screen would otherwise stay there as a picture of
## ground the player can no longer see.
func set_has_chart(has_chart: bool) -> void:
	_has_chart = has_chart
	if not has_chart and visible:
		close()


## Warms the tiles the overlay will open onto, so the first press of M does not wait for the network.
##
## Returns the tile the player stands on at the overlay's zoom, or null when nothing was asked - no chart, an
## overlay already open and asking for itself, or no player yet. Sized from the window rather than from this
## control, which has no layout while it is hidden.
func warm() -> Variant:
	if not _has_chart or visible:
		return null

	return _view.prefetch_around_player(Vector2(get_viewport_rect().size))


func toggle() -> void:
	if visible:
		close()
	else:
		open()


func open() -> void:
	if not _has_chart:
		return

	visible = true
	var entities := EntityManager.get_instance()
	if entities != null:
		_show_travellers(entities)
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.request_saved_routes()
	# The zoom is deliberately left alone - see [constant _OPEN_LEVEL]. It is set once in [method setup] and
	# is the view's own from then on.
	_view.centre_on_player()
	# Deliberately after centring: it follows for exactly one frame, so a player who opens the map while
	# running gets it centred on where they are rather than where they were when the scene loaded.
	_view.follow_player = false
	# The services on screen may be from before the player last walked away from this ground, and the moment
	# the map opens is the moment they are about to be read.
	_view.revalidate_services()
	_update_scale()
	_update_legend()
	_update_title()


func close() -> void:
	visible = false


func _process(_delta: float) -> void:
	if visible:
		_update_scale()
		_update_legend()


## Shows the legend only at the zooms that draw services, so it never offers toggles for icons the map is
## not showing.
##
## The entries are hidden rather than the row: the row keeps its height, so zooming across the boundary does
## not resize the map under the cursor and shift the ground the player was zooming towards.
func _update_legend() -> void:
	var shown := _view.draws_services()
	for entry in _legend.get_children():
		if entry is CanvasItem and entry.visible != shown:
			entry.visible = shown


func _update_scale() -> void:
	var metres_per_pixel := pow(2.0, _view.level)
	_scale_label.text = "%d m / px  -  L%d" % [int(metres_per_pixel), _view.level]


func _input(event: InputEvent) -> void:
	if not visible:
		return

	# Escape drops a draft first and closes after, and is consumed so it does not also reach whatever else
	# listens for it.
	if event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
		if _view.draft.is_empty():
			close()
		else:
			_view.clear_draft()
		get_viewport().set_input_as_handled()


## Names the map after wherever the player is standing.
##
## Read when the map opens rather than kept in step with a signal: the map re-centres on the player every
## time it opens and does not follow them afterwards, so the name and the view are captured at the same
## moment and cannot drift apart while it is being panned.
##
## Falls back to the static title against a server that sends no place - the same reason
## [code]location_panel.gd[/code] stays hidden rather than showing "Unknown".
func _update_title() -> void:
	var entity_manager := EntityManager.get_instance()
	var player: Entity = entity_manager.get_controlled_entity() if entity_manager else null
	if player == null:
		return

	var place: PlaceComponentSMSG = player.get_place()
	if place == null or place.Name.is_empty():
		return

	_title.text = place.Name


## The player's own marks. Editable here, unlike on the minimap: this is the view with room to aim in.
func set_marks(marks: MapMarks) -> void:
	_view.marks = marks


## What the map prints for a place, so a caller putting one on the compass labels it the same way.
func label_of(place: Dictionary) -> String:
	return _view.label_of(place)


## What the map prints for a service, for the same reason as [method label_of].
func service_label(service: Dictionary) -> String:
	return _view.service_label(service)


func redraw() -> void:
	_view.queue_redraw()
