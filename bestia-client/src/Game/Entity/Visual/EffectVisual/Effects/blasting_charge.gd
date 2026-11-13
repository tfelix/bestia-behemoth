extends Visual
class_name BlastingChargeVisual

## A set Blasting Charge with its fuse burning. A placeholder bundle until there is art. The spark flickers
## faster as the fuse burns down; the server removes the entity when the charge goes off.

## As long as the server's fuse, see net.bestia.zone.item.mining.Blasts.
const _FUSE_SECONDS := 3.0
const _SLOW_FLICKER := 6.0
const _FAST_FLICKER := 30.0

@onready var _spark: MeshInstance3D = $Spark
@onready var _glow: OmniLight3D = $Spark/Glow

var _elapsed := 0.0


func setup_visual(_msg: VisualComponentSMSG) -> void:
	pass


func _process(delta: float) -> void:
	_elapsed += delta
	var burnt: float = clamp(_elapsed / _FUSE_SECONDS, 0.0, 1.0)
	var speed: float = lerp(_SLOW_FLICKER, _FAST_FLICKER, burnt)
	var flicker := 0.6 + 0.4 * sin(_elapsed * speed)
	_spark.scale = Vector3.ONE * flicker
	_glow.light_energy = 1.5 * flicker
