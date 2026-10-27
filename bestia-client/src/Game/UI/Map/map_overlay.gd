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
signal place_selected(place: Dictionary)
signal service_selected(service: Dictionary)

## The player switched a service kind on or off in the legend. Carries every kind now off, so a listener can
## store the answer rather than replay the clicks.
signal hidden_services_changed(kinds: Array[String])

## The player switched the grid on or off.
signal grid_overlay_changed(on: bool)

## The player asked to delete, or to rename, one of their own marks; [param index] indexes [MapMarks.all].
signal mark_delete_requested(index: int)
signal mark_rename_requested(index: int)

const _ROUTES := "routes"


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

## Takes a grid reference or a place name; see [method _on_search].
var _search: LineEdit
var _grid_toggle: Button

## The places a name search found, offered to pick from when there is more than one.
var _results: PopupMenu
var _found: Array = []

## What a click on the map opens; see [method _on_context].
var _card: MapContextCard

## Puts the map in route mode, where a click adds a stop - see [member MapView.route_mode].
var _route_toggle: Button

## The place the card shows and where it was opened, so its detail can fill the card in when it arrives.
var _card_place_id := 0
var _card_at := Vector2.ZERO


func setup(source: MapSource, entities: Node) -> void:
	_view.mark_requested.connect(func(at: Vector2) -> void: mark_requested.emit(at))
	_view.context_requested.connect(_on_context)
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
	source.places_found.connect(_on_places_found)
	source.place_detail_ready.connect(_on_place_detail)


## The panel picks who the map's orders go to; the view drafts the stops and draws every journey.
func _wire_travel(entities: EntityManager) -> void:
	_travel.entity_chosen.connect(func(entity_id: int) -> void:
		_view.order_entity_id = entity_id
		_travel.show_journey(entities.get_journey(entity_id))
		_view.queue_redraw()
	)
	_travel.go_pressed.connect(_send_draft)
	_travel.clear_pressed.connect(_view.clear_draft)
	_travel.stop_pressed.connect(func(entity_id: int) -> void:
		var none: Array[Vector3] = []
		ConnectionManager.send_on_journey(entity_id, none)
	)
	_view.draft_changed.connect(func() -> void: _travel.show_draft(_view.draft))
	_travel.save_draft_pressed.connect(_save_draft_as_route)
	_travel.route_sent.connect(_send_along_route)
	_travel.route_renamed.connect(_rename_route)
	_travel.route_deleted.connect(func(route: Dictionary) -> void:
		Notebook.delete(_ROUTES, route["key"], route["revision"])
	)
	Notebook.collection_changed.connect(func(collection: String) -> void:
		if collection == _ROUTES:
			_travel.show_routes(Notebook.entries(_ROUTES))
	)
	Notebook.watch(_ROUTES)
	entities.journey_changed.connect(func(entity_id: int) -> void:
		if entity_id == _travel.chosen_entity_id():
			_travel.show_journey(entities.get_journey(entity_id))
	)
	entities.owned_bestias_changed.connect(func(_bestias: Array[BestiaInfo]) -> void: _show_travellers(entities))
	entities.controlled_entity_changed.connect(func(_previous: int, _now: int) -> void: _show_travellers(entities))


## A route starts where the entity it was planned for stands, so it can be walked back the other way too. Kept in
## map metres, as the companion app keeps them; an empty name lets the server make one.
func _save_draft_as_route() -> void:
	var from: Variant = _view.order_entity_metres()
	if from == null or _view.draft.is_empty():
		return

	var points: Array[Vector2] = [from]
	points.append_array(_view.draft)
	Notebook.put(_ROUTES, "", {"name": "", "waypoints": _points_json(points)}, 0)
	_view.clear_draft()


## An empty name gives the route its made name back.
func _rename_route(route: Dictionary, new_name: String) -> void:
	var value: Dictionary = route["value"]
	Notebook.put(_ROUTES, route["key"], {"name": new_name, "waypoints": value.get("waypoints", [])}, route["revision"])


func _send_along_route(route: Dictionary, backwards: bool) -> void:
	var entity := _view.order_entity()
	if entity == null:
		return

	var points: Array[Vector2] = []
	for point in route["value"].get("waypoints", []):
		points.append(Vector2(float(point.get("east", 0.0)), float(point.get("north", 0.0))))
	if backwards:
		points.reverse()
	var route_name := str(route["value"].get("name", ""))
	ConnectionManager.send_on_journey(entity.entity_id, _view.waypoints_of(points), false, route_name, route["key"])


static func _points_json(points: Array[Vector2]) -> Array:
	var json := []
	for point in points:
		json.append({"east": point.x, "north": point.y})
	return json


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
	_build_search()
	_card = MapContextCard.new()
	_view.add_child(_card)
	($Panel/Margin/Rows/Footer/Help as Label).text = tr("MAP_HELP")


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


## The search box and the grid toggle, in the header before the close hint.
func _build_search() -> void:
	var header := _title.get_parent()
	var close_hint := header.get_node("Close")

	_search = LineEdit.new()
	_search.placeholder_text = tr("MAP_SEARCH_HINT")
	_search.custom_minimum_size = Vector2(260, 0)
	_search.add_theme_font_size_override("font_size", 13)
	_search.text_submitted.connect(_on_search)
	header.add_child(_search)
	header.move_child(_search, close_hint.get_index())

	_grid_toggle = Button.new()
	_grid_toggle.text = tr("MAP_GRID")
	_grid_toggle.tooltip_text = tr("MAP_GRID_TIP")
	_grid_toggle.toggle_mode = true
	_grid_toggle.focus_mode = Control.FOCUS_NONE
	_grid_toggle.add_theme_font_size_override("font_size", 12)
	_grid_toggle.toggled.connect(func(on: bool) -> void:
		_view.show_grid = on
		grid_overlay_changed.emit(on)
	)
	header.add_child(_grid_toggle)
	header.move_child(_grid_toggle, close_hint.get_index())

	_route_toggle = Button.new()
	_route_toggle.text = tr("MAP_ROUTE")
	_route_toggle.tooltip_text = tr("MAP_ROUTE_TIP")
	_route_toggle.toggle_mode = true
	_route_toggle.focus_mode = Control.FOCUS_NONE
	_route_toggle.add_theme_font_size_override("font_size", 12)
	_route_toggle.toggled.connect(_set_route_mode)
	header.add_child(_route_toggle)
	header.move_child(_route_toggle, close_hint.get_index())

	_results = PopupMenu.new()
	_results.id_pressed.connect(func(index: int) -> void: _go_to_place(_found[index]))
	add_child(_results)


## A grid reference is answered here at once; anything else is asked of the server as a place name.
func _on_search(text: String) -> void:
	var wanted := text.strip_edges()
	if wanted.is_empty():
		_view.pin = null
		return

	var world := _view.world_size()
	var point := GridRef.parse(wanted, world.x, world.y)
	if not point.is_empty():
		_show_pin(Vector2(point["east"], point["north"]))
		return

	_view.source.find(wanted)


func _on_places_found(text: String, places: Array) -> void:
	if text != _search.text.strip_edges():
		return

	_found = places
	if places.is_empty():
		_search.text = ""
		_search.placeholder_text = tr("MAP_SEARCH_NONE") % text
		return

	if places.size() == 1:
		_go_to_place(places[0])
		return

	_results.clear()
	for i in places.size():
		_results.add_item("%s  ·  %s" % [_view.label_of(places[i]), _view.grid_ref_of(_position_of(places[i]))], i)
	_results.popup(Rect2i(Vector2i(_search.global_position + Vector2(0, _search.size.y)), Vector2i(_search.size.x, 0)))


func _go_to_place(place: Dictionary) -> void:
	_search.placeholder_text = tr("MAP_SEARCH_HINT")
	_show_pin(_position_of(place))


static func _position_of(place: Dictionary) -> Vector2:
	return Vector2(float(place.get("x", 0.0)), float(place.get("y", 0.0)))


func _show_pin(at: Vector2) -> void:
	_view.pin = at
	_view.centre_on(at)
	_search.release_focus()


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


## Shows the grid as the notebook has it, without reporting back, so loading the setting does not save it again.
func set_grid_overlay(on: bool) -> void:
	_grid_toggle.set_pressed_no_signal(on)
	_view.show_grid = on


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


## Opens the map centred on [param at] world metres rather than on the player - a click on the minimap.
func open_at(at: Vector2) -> void:
	open()
	if visible:
		_view.centre_on(at)


func open() -> void:
	if not _has_chart:
		return

	visible = true
	var entities := EntityManager.get_instance()
	if entities != null:
		_show_travellers(entities)
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
	_card.close()
	_route_toggle.button_pressed = false
	visible = false


## Route mode keeps the draft only while it is on: leaving it is how a half-planned route is dropped.
func _set_route_mode(on: bool) -> void:
	_card.close()
	_view.route_mode = on
	if not on:
		_view.clear_draft()


func _send_draft() -> void:
	_view.send_draft()
	_route_toggle.button_pressed = false


## The context card for [param hit] - see [method MapView.hit_at] - with what can be done there.
func _on_context(hit: Dictionary, at: Vector2, services: Array = []) -> void:
	var world: Vector2 = hit["world"]
	var entry: Dictionary = hit.get("entry", {})
	var title := tr("MAP_GROUND")
	var lines: Array[String] = []
	var actions: Array[Dictionary] = [{"label": tr("MAP_GO"), "run": func() -> void: _view.travel_to(world)}]
	actions.append({"label": tr("MAP_ADD_TO_ROUTE"), "run": func() -> void: _add_to_route(world)})

	_card_place_id = 0
	match hit["kind"]:
		"place":
			title = _view.label_of(entry)
			var tier := str(entry.get("tier", "")) if entry.get("tier") != null else ""
			if not tier.is_empty():
				lines.append(tr("SETTLEMENT_TIER_" + tier))
			if entry.get("region") != null:
				lines.append(str(entry["region"]))
			# What the view has drawn at once, and the whole town from the server a moment later - see
			# [method _on_place_detail] - since at coarse zooms no service is drawn at all.
			if services.is_empty():
				services = _view.services_of_settlement(title)
				_card_place_id = int(entry.get("id", 0))
				_card_at = at
				_view.source.place_detail(_card_place_id)
			for service in services:
				var at_service := _position_of(service)
				actions.append({
					"label": tr("MAP_GO_TO") % _view.service_label(service),
					"run": func() -> void: _view.travel_to(at_service),
				})
		"service":
			title = _view.service_label(entry)
			lines.append(_view.service_hours(entry))
		"mark":
			title = str(entry["name"])
			lines.append(tr("MAP_HOVER_MARK"))
			var index: int = hit["index"]
			actions.append({"label": tr("MAP_RENAME"), "run": func() -> void: mark_rename_requested.emit(index)})
			actions.append({"label": tr("MAP_DELETE"), "run": func() -> void: mark_delete_requested.emit(index)})
		"bestia":
			title = tr("MAP_HOVER_BESTIA")
		_:
			actions.append({"label": tr("MAP_MARK_HERE"), "run": func() -> void: mark_requested.emit(world)})

	lines.append(_view.grid_ref_of(world))
	var from: Variant = _view.order_entity_metres()
	if from != null:
		lines.append(tr("MAP_DISTANCE") % _distance_text(world.distance_to(from)))

	_card.present(at, title, lines, actions)


## Fills the open card in with every service of its town, once the server has said what they are.
func _on_place_detail(place_id: int, detail: Dictionary) -> void:
	if place_id != _card_place_id or not _card.visible or detail.is_empty():
		return

	var place: Dictionary = detail.get("place", {}).duplicate()
	place["region"] = detail.get("region")
	var services: Array = detail.get("services", [])
	if services.is_empty():
		return
	_on_context({"kind": "place", "world": _position_of(place), "entry": place}, _card_at, services)


func _add_to_route(world: Vector2) -> void:
	_route_toggle.button_pressed = true
	_view.add_stop(world)


static func _distance_text(metres: float) -> String:
	return "%.1f km" % (metres / 1000.0) if metres >= 1000.0 else "%d m" % int(metres)


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


## The scale, and the grid reference under the cursor - or of the middle of the map while the cursor is off it.
func _update_scale() -> void:
	var metres_per_pixel := pow(2.0, _view.level)
	var under: Variant = _view.world_under_mouse()
	var ref := _view.grid_ref_of(under if under != null else _view.centre)
	_scale_label.text = "%d m / px  -  L%d    %s" % [int(metres_per_pixel), _view.level, ref]


func _input(event: InputEvent) -> void:
	if not visible:
		return

	if not event is InputEventKey or not event.pressed:
		return

	# Typing in the search box is not a shortcut.
	var typing := _search.has_focus()
	match event.keycode:
		KEY_G when not typing:
			_grid_toggle.button_pressed = not _grid_toggle.button_pressed
		KEY_R when not typing:
			_route_toggle.button_pressed = not _route_toggle.button_pressed
		KEY_ENTER, KEY_KP_ENTER when _view.route_mode and not typing:
			_send_draft()
		# Escape undoes one thing at a time - the card, then route mode, then the map - and is consumed so it
		# does not also reach whatever else listens for it.
		KEY_ESCAPE:
			if _card.visible:
				_card.close()
			elif _view.route_mode:
				_route_toggle.button_pressed = false
			else:
				close()
		_:
			return
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
