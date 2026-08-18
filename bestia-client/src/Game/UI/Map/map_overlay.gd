class_name MapOverlay
extends Control

## The full map, over the whole screen, opened with M.
##
## An overlay rather than a [WidgetWindow] because a map is worth looking at large - the windows are for
## lists and slots, and this is the one panel whose value is its area. It dims the game behind it rather than
## pausing: the world carries on, and the player marker keeps moving while the map is open.
##
## Opens centred on the player and then stops following, so panning away stays where it was put. Re-opening
## re-centres, which is the behaviour that needs no button.

@onready var _view: MapView = $Panel/Margin/Rows/View
@onready var _title: Label = $Panel/Margin/Rows/Header/Title
@onready var _scale_label: Label = $Panel/Margin/Rows/Footer/Scale

## Level the map opens at. Level 7 is 128 m to the pixel, which fits a 128 km world in about a thousand
## pixels - the whole world at a glance, which is what a player opening the map wants first.
const _OPEN_LEVEL := 7


func setup(source: MapSource, entities: Node) -> void:
	_view.interactive = true
	_view.level = _OPEN_LEVEL
	_view.setup(source, entities)


func _ready() -> void:
	visible = false


func toggle() -> void:
	if visible:
		close()
	else:
		open()


func open() -> void:
	visible = true
	_view.level = _OPEN_LEVEL
	_view.centre_on_player()
	# Deliberately after centring: it follows for exactly one frame, so a player who opens the map while
	# running gets it centred on where they are rather than where they were when the scene loaded.
	_view.follow_player = false
	_update_scale()


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
