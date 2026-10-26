extends Node3D
class_name LightningBolt

## One visible bolt: a jagged, glowing ribbon from the cloud base to the ground, and a short light where it lands.
##
## Built fresh for each strike and lit by [method set_energy], so the second stroke of the same bolt is the same
## channel lighting up again, which is what a real return stroke is.

## Metres above the strike point where the bolt leaves the cloud.
@export var cloud_base_metres: float = 450.0

## Ribbon width, in metres, when seen from up close. Far bolts are drawn wider so they stay a few pixels thick.
@export var width_metres: float = 1.4

@export var colour: Color = Color(0.86, 0.90, 1.0)

@export var ground_light_energy: float = 12.0
@export var ground_light_range: float = 90.0

const _SEGMENTS := 28
const _BRANCHES := 2

var _mesh_instance := MeshInstance3D.new()
var _material := StandardMaterial3D.new()
var _ground_light := OmniLight3D.new()


func _init() -> void:
	_material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	_material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	_material.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
	_material.cull_mode = BaseMaterial3D.CULL_DISABLED
	_material.vertex_color_use_as_albedo = true
	# A bolt is seen through rain from kilometres away; the fog that hides the ground at that range must not
	# hide the channel.
	_material.disable_fog = true

	_mesh_instance.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	_mesh_instance.material_override = _material
	add_child(_mesh_instance)

	_ground_light.shadow_enabled = false
	_ground_light.light_color = colour
	_ground_light.omni_range = ground_light_range
	add_child(_ground_light)

	set_energy(0.0)


## Builds a new channel that lands at [param ground], drawn to face [param viewer].
func build(ground: Vector3, viewer: Vector3) -> void:
	var top := ground + Vector3.UP * cloud_base_metres
	var width := maxf(width_metres, ground.distance_to(viewer) * 0.003)

	var surface := SurfaceTool.new()
	surface.begin(Mesh.PRIMITIVE_TRIANGLES)

	var trunk := _jagged(top, ground, cloud_base_metres * 0.08)
	_add_ribbon(surface, trunk, viewer, width, 1.0)

	for i in _BRANCHES:
		var from: Vector3 = trunk[randi_range(_SEGMENTS / 5, _SEGMENTS * 3 / 5)]
		var drop := from.y - ground.y
		var reach := Vector3(randf_range(-0.5, 0.5), -1.0, randf_range(-0.5, 0.5)) * drop * randf_range(0.25, 0.45)
		_add_ribbon(surface, _jagged(from, from + reach, drop * 0.05), viewer, width * 0.5, 0.6)

	_mesh_instance.mesh = surface.commit()
	_ground_light.position = ground + Vector3.UP * 8.0


## Forgets the channel, for a strike too far away to draw one.
func clear() -> void:
	_mesh_instance.mesh = null
	_ground_light.light_energy = 0.0


## Brightness from 0 (gone) to 1 (the moment of the stroke).
func set_energy(energy: float) -> void:
	visible = energy > 0.002
	_material.albedo_color = Color(colour, energy)
	_ground_light.light_energy = ground_light_energy * energy if _mesh_instance.mesh != null else 0.0


## A polyline from [param from] to [param to] that kinks sideways at every segment, more in the middle.
func _jagged(from: Vector3, to: Vector3, wander: float) -> PackedVector3Array:
	var points := PackedVector3Array()
	var drift := Vector3.ZERO

	for i in _SEGMENTS + 1:
		var t := float(i) / _SEGMENTS
		# Drift is a random walk so the kinks build into a path; the sine pins both ends to where they belong.
		drift += Vector3(randf_range(-1.0, 1.0), 0.0, randf_range(-1.0, 1.0)) * wander * 0.35
		points.append(from.lerp(to, t) + drift * sin(t * PI))

	return points


func _add_ribbon(surface: SurfaceTool, points: PackedVector3Array, viewer: Vector3, width: float,
		brightness: float) -> void:
	for i in points.size() - 1:
		var a := points[i]
		var b := points[i + 1]
		var side := (b - a).cross(viewer - a).normalized() * width * 0.5
		# The channel is brightest where it meets the ground and thins out toward the cloud.
		var glow_a := Color(1, 1, 1, brightness * lerpf(0.5, 1.0, float(i) / points.size()))
		var glow_b := Color(1, 1, 1, brightness * lerpf(0.5, 1.0, float(i + 1) / points.size()))

		surface.set_color(glow_a)
		surface.add_vertex(a - side)
		surface.add_vertex(a + side)
		surface.set_color(glow_b)
		surface.add_vertex(b + side)

		surface.set_color(glow_a)
		surface.add_vertex(a - side)
		surface.set_color(glow_b)
		surface.add_vertex(b + side)
		surface.add_vertex(b - side)
