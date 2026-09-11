extends Visual
class_name StructureVisual

## Something a player built, drawn as an ordinary entity.
##
## A finished station normally reaches the client on the per-chunk static batch and is drawn by
## StaticEntityRenderer. This is the other case: a construction site, whose progress and health change while
## somebody is watching, so it travels the entity channel instead. The art is the same catalogue either way -
## see PropVisualBuilder.

const PropVisualBuilderScript = preload("res://Game/World/PropVisualBuilder.cs")

const _ART_NODE_NAME = "Art"

## StaticEntityKind ordinal. -1 until setup_visual runs.
var _kind: int = -1

@onready var _pick_shape: CollisionShape3D = $Area3D/CollisionShape3D


func setup_visual(msg: VisualComponentSMSG) -> void:
	_kind = msg.VisualId


func get_kind() -> int:
	return _kind


func _ready() -> void:
	if _kind < 0:
		printerr("StructureVisual was readied without a kind")
		return

	var builder = PropVisualBuilderScript.new()

	var art: Node3D = builder.Build(_kind)
	art.name = _ART_NODE_NAME
	add_child(art)

	var size: Vector3 = builder.PickSize(_kind)
	var box := BoxShape3D.new()
	box.size = size
	_pick_shape.shape = box
	_pick_shape.position = Vector3(0.0, size.y * 0.5, 0.0)


## The mesh the construction shader is applied to. Null for a kind drawn from a whole scene rather than
## a single mesh, which no buildable kind is today.
func get_art_mesh() -> MeshInstance3D:
	return get_node_or_null(_ART_NODE_NAME) as MeshInstance3D


func _on_area_3d_input_event(_camera: Node, event: InputEvent, event_position: Vector3, _normal: Vector3, _shape_idx: int) -> void:
	MouseManager.get_instance().object_clicked(self, event, event_position)
