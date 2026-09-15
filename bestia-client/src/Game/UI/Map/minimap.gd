class_name Minimap
extends PanelContainer

## The map corner of the HUD, top right under the clock.
##
## Only there when the player is carrying a chart. That is not a flourish - charts are the only source of
## map knowledge in this game, so a player with none has a minimap that would be entirely fog, and an empty
## panel reads as a broken widget rather than as something to go and earn.
##
## Shows a kilometre or so of ground at four metres to the pixel, following the player. It does not pan or
## zoom: the overlay is for looking around, and this is for knowing where you are.

## Level 2 is four metres to the pixel, so the panel below shows about a kilometre across. Close enough
## that the plan style is drawing streets and buildings rather than the atlas drawing terrain.
const _LEVEL := 2

@onready var _view: MapView = $Margin/View
@onready var _hint: Label = $Margin/Hint


func setup(source: MapSource, entities: Node) -> void:
	_view.follow_player = true
	_view.interactive = false
	_view.can_travel = true
	_view.setup(source, entities)
	_view.go_to_level(_LEVEL)


## Called whenever the inventory changes: the widget exists exactly while a chart does.
func set_has_chart(has_chart: bool) -> void:
	visible = has_chart


func _ready() -> void:
	add_to_group("world_blocking_ui")
	visible = false
	_hint.text = "M"


## The player's own marks, drawn here as well as on the overlay.
##
## Shown but not editable: this view takes no input at all, which is the same reason it does not pan.
func set_marks(marks: MapMarks) -> void:
	_view.marks = marks


## The player's position in world metres, or null. Forwarded so callers that need it - the compass, which
## reckons every bearing from where the player is - do not each redo the voxel-to-metre conversion.
func player_metres() -> Variant:
	return _view.player_metres()


func redraw() -> void:
	_view.queue_redraw()
