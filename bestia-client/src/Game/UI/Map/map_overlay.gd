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


@onready var _view: MapView = $Panel/Margin/Rows/View
@onready var _title: Label = $Panel/Margin/Rows/Header/Title
@onready var _scale_label: Label = $Panel/Margin/Rows/Footer/Scale

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
	_view.interactive = true
	_view.can_travel = true
	_view.setup(source, entities)
	_view.go_to_level(_OPEN_LEVEL)


func _ready() -> void:
	add_to_group("world_blocking_ui")
	visible = false


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
	var player: Entity = entity_manager.get_owned_entity() if entity_manager else null
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


func redraw() -> void:
	_view.queue_redraw()
