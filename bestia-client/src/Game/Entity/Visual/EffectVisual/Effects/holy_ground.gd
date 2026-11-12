extends Visual
class_name HolyGround

## The Priest's ground rites: Pneuma's still air, Sanctuary and Magnus Exorcismus. One script, a scene each.
## The Wizard's Safety Wall and area spells borrow it too: only the colour and the pace of the glow differ.
##
## Presentation only, like [EmberPatch]: the server owns the area and destroys the entity when it ends. Each
## scene bakes in its skill's footprint (3x3, 5x5, 7x7), for the reason given there. A burst whose size grows
## by level has a scene per level that scales the base scene's Glow.

## How fast and how deep the glow breathes. Still air barely moves; an exorcism throbs.
@export var pulse_speed := 2.0
@export var pulse_depth := 0.2

@onready var _glow: MeshInstance3D = $Glow

var _elapsed := 0.0
var _base_energy := 0.0


func _ready() -> void:
	_base_energy = _glow.material_override.emission_energy_multiplier


func setup_visual(_msg: VisualComponentSMSG) -> void:
	# Nothing to configure - the id already picked this scene.
	pass


func _process(delta: float) -> void:
	_elapsed += delta
	var pulse := 1.0 + sin(_elapsed * pulse_speed) * pulse_depth
	_glow.material_override.emission_energy_multiplier = _base_energy * pulse
