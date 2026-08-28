extends PanelContainer

## The weather and the compass, top centre of the HUD.
##
## Visible only once there is a camera to take a heading from. The camera is instantiated at runtime under the
## owned entity, so before the player is standing somewhere there is nothing to point at - and a compass that
## defaults to north while the master-select screen is up is not a compass at rest, it is a wrong one.

@onready var _compass: CompassStrip = $Margin/Rows/Compass


func _ready() -> void:
	visible = false


func _process(_delta: float) -> void:
	visible = _compass.has_heading
