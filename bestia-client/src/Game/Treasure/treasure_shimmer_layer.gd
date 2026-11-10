extends Node3D
class_name TreasureShimmerLayer

## The faint shimmer Treasure Sense raises over each cache it found, for the caster only.
##
## The GDScript half of net.bestia.zone.treasure.TreasureSense. A cast replaces the shimmer of the last one,
## because it senses the same ground again.

const _SPARKS_PER_SPOT := 14
const _SPARK_LIFETIME := 2.4
const _SPARK_SIZE := 0.07
const _SPREAD := Vector3(0.9, 0.1, 0.9)
const _GOLD := Color(1.0, 0.84, 0.4, 0.85)

var _shown: Array[Node3D] = []
var _material: StandardMaterial3D = null


func _ready() -> void:
	ConnectionManager.treasure_shimmer_received.connect(_on_treasure_shimmer_received)


func _on_treasure_shimmer_received(message: TreasureShimmerSMSG) -> void:
	_clear()
	for spot in message.Spots:
		_shown.append(_shimmer_at(TileSpace.tile_centre(spot)))

	var shown := _shown
	await get_tree().create_timer(message.Seconds).timeout
	if shown == _shown:
		_clear()


func _clear() -> void:
	for node in _shown:
		if is_instance_valid(node):
			node.queue_free()
	_shown = []


func _shimmer_at(at: Vector3) -> Node3D:
	var sparks := CPUParticles3D.new()
	sparks.amount = _SPARKS_PER_SPOT
	sparks.lifetime = _SPARK_LIFETIME
	sparks.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
	sparks.emission_box_extents = _SPREAD
	sparks.direction = Vector3.UP
	sparks.spread = 15.0
	sparks.gravity = Vector3(0.0, 0.15, 0.0)
	sparks.initial_velocity_min = 0.05
	sparks.initial_velocity_max = 0.2
	sparks.mesh = _spark_mesh()
	add_child(sparks)
	sparks.global_position = at + Vector3(0.0, 0.15, 0.0)
	return sparks


func _spark_mesh() -> QuadMesh:
	if _material == null:
		_material = StandardMaterial3D.new()
		_material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		_material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		_material.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
		_material.albedo_color = _GOLD

	var quad := QuadMesh.new()
	quad.size = Vector2(_SPARK_SIZE, _SPARK_SIZE)
	quad.material = _material
	return quad
