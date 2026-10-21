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


@onready var _view: MapView = $Panel/Margin/Rows/View
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
## Level 4 is 16 m to the pixel, so the panel shows about 16 km of ground across.
##
## Deliberately not the whole world, which was the first answer and the wrong one. Charts are the only source
## of map knowledge, so the whole world at a glance is almost entirely fog: the widest single survey is 5 km,
## which at world scale is a couple of dozen pixels and reads as a broken window rather than as an unexplored
## one. This opens at a zoom where the ground a player actually has is legible, and leaves the wheel to go out
## from there.
const _OPEN_LEVEL := 4

var _has_chart: bool = false


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


func _ready() -> void:
	add_to_group("world_blocking_ui")
	visible = false
	_build_legend()


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


func toggle() -> void:
	if visible:
		close()
	else:
		open()


func open() -> void:
	if not _has_chart:
		return

	visible = true
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
	_update_title()


func close() -> void:
	visible = false


func _process(_delta: float) -> void:
	if visible:
		_update_scale()


func _update_scale() -> void:
	var metres_per_pixel := pow(2.0, _view.level)
	_scale_label.text = "%d m / px  -  L%d" % [int(metres_per_pixel), _view.level]


func _input(event: InputEvent) -> void:
	if not visible:
		return

	# Escape closes, and is consumed so it does not also reach whatever else listens for it.
	if event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
		close()
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
