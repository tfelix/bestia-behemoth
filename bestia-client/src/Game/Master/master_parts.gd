class_name MasterParts extends Node3D
## A master's swappable parts, which tools/client/build_master_parts.py exports. They are skinned to a rig
## with the Mage's bone names, so they work on the skeleton this node is added to.

const _PARTS := preload("res://Game/Master/Parts/master_parts.glb")
const _HAIR_MATERIAL := "master_hair"
const _SKIN_MATERIAL := "master_skin"
const _BODY_PARTS := ["Body", "ArmLeft", "ArmRight", "LegLeft", "LegRight"]
## Part name suffix per proto BodyType.
const _BODY_SUFFIXES := ["", "_F"]

## Shared by every master with the same colour, so a crowd of masters costs a few materials, not one each.
static var _tinted_materials: Dictionary = {}

var _look_parts: Array[String] = []
var _body_suffix := ""
## The hair and any beard: what a helmet hides.
var _hair_parts: Array[String] = []
var _hair_color := Color.WHITE
var _skin_color := Color.WHITE
var _worn_parts: Array[String] = []
var _hair_hidden := false


func _init() -> void:
	var source := _PARTS.instantiate()
	for part: MeshInstance3D in source.find_children("*", "MeshInstance3D"):
		part.owner = null
		part.get_parent().remove_child(part)
		add_child(part)
		part.transform = Transform3D.IDENTITY
		part.skeleton = ^"../.."
	source.free()


## [param body], [param face] and [param hair] are proto BodyType, Face and Hairstyle values. A hairstyle
## without a part is bald.
func show_look(body: int, face: int, hair: int, hair_color: Color, skin_color: Color) -> void:
	if body < 0 or body >= _BODY_SUFFIXES.size():
		printerr("MasterParts: unknown body %s, showing the first" % body)
		body = 0
	var face_number := face + 1
	if not has_node("Face_%d" % face_number):
		printerr("MasterParts: unknown face %s, showing the first" % face)
		face_number = 1
	_body_suffix = _BODY_SUFFIXES[body]
	_look_parts = ["Head", "Face_%d" % face_number]
	for part in _BODY_PARTS:
		_look_parts.append(part + _body_suffix)
	_hair_parts = ["Hair_%d" % (hair + 1), "FacialHair_%d" % face_number]
	_hair_color = hair_color
	_skin_color = skin_color
	_refresh()


## [param parts] are the parts of the worn items. [param hide_hair] is true when one of them covers the hair,
## so the two cannot clip.
func wear(parts: Array[String], hide_hair: bool) -> void:
	_worn_parts = parts
	_hair_hidden = hide_hair
	_refresh()


func _refresh() -> void:
	var shown := _look_parts.duplicate()
	for part in _worn_parts:
		shown.append(_fitted(part))
	if not _hair_hidden:
		shown.append_array(_hair_parts)

	for part: MeshInstance3D in get_children():
		part.visible = part.name in shown
		if part.visible:
			_tint(part, _hair_color, _skin_color)


## Body armor comes in one part per body; the female body's has the body's suffix.
func _fitted(part: String) -> String:
	var for_this_body := part + _body_suffix
	return for_this_body if has_node(for_this_body) else part


func _tint(part: MeshInstance3D, hair_color: Color, skin_color: Color) -> void:
	for surface in part.mesh.get_surface_count():
		var material := part.mesh.surface_get_material(surface) as ShaderMaterial
		match material.resource_name:
			_HAIR_MATERIAL:
				part.set_surface_override_material(surface, _tinted(material, hair_color))
			_SKIN_MATERIAL:
				part.set_surface_override_material(surface, _tinted(material, skin_color))


static func _tinted(material: ShaderMaterial, color: Color) -> ShaderMaterial:
	var key := "%s#%s" % [material.resource_name, color.to_html(false)]
	if not _tinted_materials.has(key):
		var tinted := material.duplicate() as ShaderMaterial
		tinted.set_shader_parameter(&"albedo_colour", color)
		_tinted_materials[key] = tinted
	return _tinted_materials[key]
