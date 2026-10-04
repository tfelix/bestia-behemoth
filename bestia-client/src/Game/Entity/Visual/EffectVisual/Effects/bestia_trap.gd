extends Visual
class_name BestiaTrapVisual

## A set Magic Bestia Trap. A placeholder cage until there is art; the server removes the entity when it
## springs or falls apart.

const _PULSE_SPEED := 2.0
const _PULSE_DEPTH := 0.4

@onready var _plate: MeshInstance3D = $Plate

var _elapsed := 0.0
var _base_energy := 0.0


func _ready() -> void:
	_base_energy = _plate.material_override.emission_energy_multiplier


func setup_visual(_msg: VisualComponentSMSG) -> void:
	pass


func _process(delta: float) -> void:
	_elapsed += delta
	var pulse := 1.0 + sin(_elapsed * _PULSE_SPEED) * _PULSE_DEPTH
	_plate.material_override.emission_energy_multiplier = _base_energy * pulse
