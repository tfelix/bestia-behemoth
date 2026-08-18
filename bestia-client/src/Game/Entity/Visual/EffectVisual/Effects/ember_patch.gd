extends Visual
class_name EmberPatch

## The burning ground Ember leaves behind.
##
## Presentation only: the server owns where the patch is, how long it burns and who it burns, and
## destroys the entity when the last tick has landed - which frees this node. Nothing here is on a
## timer of its own.
##
## The 3x3 footprint is baked into the scene rather than driven by the skill's aoeRadius: a
## VisualComponentSMSG carries a kind and a catalogue id and nothing else, so a scene sized differently
## from its skill would drift silently. Ember's radius is fixed at 1 in skills.yml (= 3x3, see
## AreaEffectSystem), and a variant with another radius wants its own scene and effect id.

const _FLICKER_SPEED := 6.0
const _FLICKER_DEPTH := 0.25

@onready var _flames: MeshInstance3D = $Flames

var _elapsed := 0.0
var _base_energy := 0.0


func _ready() -> void:
	_base_energy = _flames.material_override.emission_energy_multiplier


func setup_visual(_msg: VisualComponentSMSG) -> void:
	# Nothing to configure - the id already picked this scene.
	pass


func _process(delta: float) -> void:
	_elapsed += delta
	var flicker := 1.0 + sin(_elapsed * _FLICKER_SPEED) * _FLICKER_DEPTH
	_flames.material_override.emission_energy_multiplier = _base_energy * flicker
