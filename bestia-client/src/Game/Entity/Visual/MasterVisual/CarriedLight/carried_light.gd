extends Node3D
class_name CarriedLight

## The torch a master carries in the left hand: a stick, a flame, and the light it casts around them.
##
## Presentation only. Whether it burns comes from the server ([member WornGearComponentSMSG.LightLit]), which
## is also what burns the torch down, so a light is seen exactly while it costs something. Top level, so the
## Mage's 0.7 scale does not shrink the light's range. It takes the hand's position but the wearer's facing:
## the hand bone points down, and a torch is held flame up.
##
## The light itself hangs well above the flame. At hand height it lit a small hot spot; from up there it lights
## about half the screen evenly, like a dim day, which is what lets a player find their way at night.

const _FADE_SECONDS := 0.6
const _FLICKER_SPEED := 9.0
const _FLICKER_DEPTH := 0.12

@onready var _light: OmniLight3D = $Light
@onready var _flame: MeshInstance3D = $Flame

var _hand: Node3D = null
var _item: ItemResource = null
var _lit := false
## How far the light has faded in, from 0 to 1.
var _strength := 0.0
var _elapsed := 0.0


func _ready() -> void:
	top_level = true
	_apply_item()


func follow(hand: Node3D) -> void:
	_hand = hand


## Pass null when no light is worn.
func show_light(item: ItemResource, lit: bool) -> void:
	_item = item
	_lit = lit and item != null and item.gives_light()
	if is_node_ready():
		_apply_item()


func _apply_item() -> void:
	visible = _item != null
	if _item == null:
		return
	_light.light_color = _item.light_color
	_light.omni_range = _item.light_range


func _process(delta: float) -> void:
	if _item == null:
		return
	if _hand != null and is_instance_valid(_hand):
		global_transform = Transform3D(get_parent().global_basis.orthonormalized(), _hand.global_position)

	_strength = move_toward(_strength, 1.0 if _lit else 0.0, delta / _FADE_SECONDS)
	_elapsed += delta
	# Two waves out of step, so the flicker does not read as a regular pulse.
	var flicker := 1.0 + (sin(_elapsed * _FLICKER_SPEED) + 0.5 * sin(_elapsed * _FLICKER_SPEED * 2.3)) * _FLICKER_DEPTH

	_light.visible = _strength > 0.0
	_light.light_energy = _item.light_energy * _strength * flicker
	_flame.visible = _strength > 0.0
	_flame.scale = Vector3.ONE * _strength * flicker
