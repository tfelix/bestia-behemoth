class_name MasterParts extends Node3D
## A master's swappable parts, which tools/client/build_master_parts.py exports. They are skinned to a rig
## with the Mage's bone names, so they work on the skeleton this node is added to.

const _PARTS := preload("res://Game/Master/Parts/master_parts.glb")
const _HAIR_MATERIAL := "master_hair"
const _SKIN_MATERIAL := "master_skin"

## Shared by every master with the same colour, so a crowd of masters costs a few materials, not one each.
static var _tinted_materials: Dictionary = {}


func _init() -> void:
	var source := _PARTS.instantiate()
	for part: MeshInstance3D in source.find_children("*", "MeshInstance3D"):
		part.owner = null
		part.get_parent().remove_child(part)
		add_child(part)
		part.transform = Transform3D.IDENTITY
		part.skeleton = ^"../.."
	source.free()


## [param face] and [param hair] are proto Face and Hairstyle values. A hairstyle without a part is bald.
func show_look(face: int, hair: int, hair_color: Color, skin_color: Color) -> void:
	var face_part := "Face_%d" % (face + 1)
	if not has_node(face_part):
		printerr("MasterParts: unknown face %s, showing the first" % face)
		face_part = "Face_1"
	var shown := ["Head", face_part, "Hair_%d" % (hair + 1)]

	for part: MeshInstance3D in get_children():
		part.visible = part.name in shown
		if part.visible:
			_tint(part, hair_color, skin_color)


func _tint(part: MeshInstance3D, hair_color: Color, skin_color: Color) -> void:
	for surface in part.mesh.get_surface_count():
		var material := part.mesh.surface_get_material(surface) as StandardMaterial3D
		match material.resource_name:
			_HAIR_MATERIAL:
				part.set_surface_override_material(surface, _tinted(material, hair_color))
			_SKIN_MATERIAL:
				part.set_surface_override_material(surface, _tinted(material, skin_color))


static func _tinted(material: StandardMaterial3D, color: Color) -> StandardMaterial3D:
	var key := "%s#%s" % [material.resource_name, color.to_html(false)]
	if not _tinted_materials.has(key):
		var tinted := material.duplicate() as StandardMaterial3D
		tinted.albedo_color = color
		_tinted_materials[key] = tinted
	return _tinted_materials[key]
