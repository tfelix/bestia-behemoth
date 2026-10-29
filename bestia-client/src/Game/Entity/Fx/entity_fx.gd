class_name EntityFx extends Node
## Plays [EntityFxDef]s on an entity's body by driving the fx knobs of its entity uber material.
##
## Effects layer: a teleport can play over a sustained status tint, and each knob shows the strongest of the
## effects driving it. A knob no effect drives is left alone, so a visual's own animation of it - the
## placeholder bestia's death fade - is not overwritten.
##
## The knobs are instance uniforms, written per mesh, so no material is duplicated. Idle costs nothing:
## [method _process] runs only while an effect is moving.

signal finished(def: EntityFxDef)

const UBER_SHADER_PATH := "res://Game/Entity/Fx/Shader/entity_uber.gdshader"

## Each scalar knob with the def curve that drives it and its resting value.
const _SCALAR_KNOBS := {
	&"fx_silhouette": [&"silhouette", 0.0],
	&"fx_glow": [&"glow", 0.0],
	&"fx_dissolve": [&"dissolve", 0.0],
	&"fx_rim": [&"rim", 0.0],
	&"fx_flash": [&"flash", 0.0],
}


class Layer:
	var def: EntityFxDef
	var duration: float
	var time := 0.0
	## Set for an effect that lasts until released, such as a status effect's look.
	var sustain_key: Variant = null

	func _init(fx_def: EntityFxDef, seconds: float) -> void:
		def = fx_def
		duration = maxf(seconds, 0.001)

	func is_moving() -> bool:
		return time < duration

	func is_over() -> bool:
		return not is_moving() and sustain_key == null and not def.hold_end

	func sample(curve: Curve) -> float:
		var progress := clampf(time / duration, 0.0, 1.0)
		return curve.sample_baked(1.0 - progress if def.reverse else progress)


var _layers: Array[Layer] = []
var _visual: Visual = null
var _meshes: Array[GeometryInstance3D] = []
## The body's bounds in the entity's own space, for where the dissolve sweep starts and how far it runs.
var _body_bounds := AABB(Vector3(-0.5, 0.0, -0.5), Vector3(1.0, 2.0, 1.0))
## What the last mix wrote, so a knob that stops being driven is put back to rest exactly once.
var _driven: Dictionary = {}
var _frozen := false

## Visual scenes already warned about, so a crowd of the same model warns once.
static var _warned_visuals: Dictionary = {}


func _ready() -> void:
	set_process(false)


## Points the effects at a freshly built body, and carries any running effect over onto it.
func bind(visual: Visual) -> void:
	_visual = visual
	_meshes.clear()
	_driven.clear()
	_frozen = false

	var bounds := AABB()
	var entity_space := (get_parent() as Node3D).global_transform.affine_inverse()
	for mesh: MeshInstance3D in visual.find_children("*", "MeshInstance3D", true, false):
		if not _draws_with_uber(mesh):
			_warn_not_uber(visual, mesh)
			continue
		_meshes.append(mesh)
		var mesh_bounds: AABB = entity_space * mesh.global_transform * mesh.get_aabb()
		bounds = mesh_bounds if bounds.size == Vector3.ZERO else bounds.merge(mesh_bounds)

	if bounds.size != Vector3.ZERO:
		_body_bounds = bounds
	_apply()


## Plays [param def] once. [param seconds] overrides its duration, for an effect the server times.
## Ends every effect held at its end state, since that was waiting for exactly this.
func play(def: EntityFxDef, seconds := -1.0) -> void:
	if def == null:
		return
	_layers = _layers.filter(func(layer: Layer) -> bool: return layer.sustain_key != null or layer.is_moving())
	var layer := Layer.new(def, seconds if seconds > 0.0 else def.duration)
	_layers.append(layer)
	if def.particles_at == EntityFxDef.ParticlesAt.START:
		_spawn_particles(def, layer.duration)
	_resume()


## Plays [param def] and keeps its end state until [method release] with the same [param key].
func sustain(key: Variant, def: EntityFxDef) -> void:
	release(key)
	var layer := Layer.new(def, def.duration)
	layer.sustain_key = key
	_layers.append(layer)
	_resume()


func release(key: Variant) -> void:
	_layers = _layers.filter(func(layer: Layer) -> bool: return layer.sustain_key != key)
	_resume()


## Ends every effect at once, leaving the body as it is drawn without any.
func clear() -> void:
	_layers.clear()
	_resume()


func is_playing() -> bool:
	return not _layers.is_empty()


func _resume() -> void:
	_apply()
	set_process(_layers.any(func(layer: Layer) -> bool: return layer.is_moving()))


func _process(delta: float) -> void:
	for layer in _layers.duplicate():
		if not layer.is_moving():
			continue
		layer.time += delta
		if layer.is_moving():
			continue
		if layer.def.particles_at == EntityFxDef.ParticlesAt.END:
			_spawn_particles(layer.def, layer.duration)
		finished.emit(layer.def)

	_layers = _layers.filter(func(layer: Layer) -> bool: return not layer.is_over())
	_resume()


## Mixes every layer into knob values and writes those that changed.
func _apply() -> void:
	var mix := {}
	var strongest := {}
	for layer in _layers:
		for knob: StringName in _SCALAR_KNOBS:
			var curve: Curve = layer.def.get(_SCALAR_KNOBS[knob][0])
			if curve == null:
				continue
			var value := layer.sample(curve)
			if not mix.has(knob) or value > mix[knob]:
				mix[knob] = value
				strongest[knob] = layer.def
		if layer.def.tint_strength != null:
			var tint := layer.def.tint_colour
			tint.a = layer.sample(layer.def.tint_strength)
			if not mix.has(&"fx_tint") or tint.a > mix[&"fx_tint"].a:
				mix[&"fx_tint"] = tint

	if mix.has(&"fx_glow") or mix.has(&"fx_silhouette") or mix.has(&"fx_dissolve"):
		var glowing: EntityFxDef = strongest.get(&"fx_glow", strongest.get(&"fx_silhouette", strongest.get(&"fx_dissolve")))
		mix[&"fx_glow_colour"] = glowing.glow_colour
	if mix.has(&"fx_rim"):
		mix[&"fx_rim_colour"] = strongest[&"fx_rim"].rim_colour
	if mix.has(&"fx_dissolve"):
		_add_sweep(mix, strongest[&"fx_dissolve"].dissolve_direction)

	for knob in _driven.keys():
		if not mix.has(knob):
			_write(knob, null)
	for knob in mix:
		_write(knob, mix[knob])
	_driven = mix

	var fade: float = mix.get(&"fx_dissolve", 0.0)
	var frozen := _layers.any(func(layer: Layer) -> bool: return layer.def.freeze)
	if _visual != null and is_instance_valid(_visual):
		_visual.set_effect_fade(fade)
		if frozen != _frozen:
			_visual.set_frozen(frozen)
	_frozen = frozen


## Where the dissolve edge starts and how far it travels: across the body, along [param direction].
func _add_sweep(mix: Dictionary, direction: Vector3) -> void:
	var entity := get_parent() as Node3D
	var world_direction := direction.normalized()
	var size := _body_bounds.size
	mix[&"fx_dissolve_dir"] = world_direction
	mix[&"fx_origin"] = entity.global_transform * _body_bounds.get_center()
	mix[&"fx_extent"] = absf(world_direction.x) * size.x + absf(world_direction.y) * size.y + absf(world_direction.z) * size.z


## Null puts the knob back to the shader's own default.
func _write(knob: StringName, value: Variant) -> void:
	for mesh in _meshes:
		if is_instance_valid(mesh):
			mesh.set_instance_shader_parameter(knob, value)


func _spawn_particles(def: EntityFxDef, seconds: float) -> void:
	if def.particles == null:
		return
	var entity := get_parent() as Node3D
	var particles := def.particles.instantiate()
	entity.add_child(particles)
	# Left where the body was, so the streaks rise from the spot it vanished from even if it moves on.
	particles.top_level = true
	particles.global_position = entity.global_transform * _body_bounds.get_center()
	if particles.has_method("burst"):
		particles.burst(def.glow_colour, _body_bounds.size, seconds)


func _draws_with_uber(mesh: MeshInstance3D) -> bool:
	if mesh.mesh == null or mesh.mesh.get_surface_count() == 0:
		return false
	var material := mesh.get_active_material(0)
	return material is ShaderMaterial and material.shader != null and material.shader.resource_path == UBER_SHADER_PATH


## A visual drawn with another material cannot show effects. Said once per visual, in debug builds only, so a
## new model says why it does not dissolve instead of silently not doing it. A carried light fades on its own.
func _warn_not_uber(visual: Visual, mesh: MeshInstance3D) -> void:
	if not OS.is_debug_build() or not mesh.visible or _warned_visuals.has(visual.scene_file_path):
		return
	var ancestor := mesh.get_parent()
	while ancestor != visual:
		if ancestor is CarriedLight:
			return
		ancestor = ancestor.get_parent()
	_warned_visuals[visual.scene_file_path] = true
	push_warning("EntityFx: %s draws %s without the entity uber material, so effects skip it" % [visual.scene_file_path, mesh.name])
