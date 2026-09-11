extends Visual
class_name StructureVisual

## Something a player is building, drawn as an ordinary entity.
##
## A finished station reaches the client on the per-chunk static batch and is drawn by StaticEntityRenderer.
## This is the other case: a construction site, whose progress and health change while somebody is watching,
## so it travels the entity channel instead. The art is the same catalogue either way - see PropVisualBuilder.

const PropVisualBuilderScript = preload("res://Game/World/PropVisualBuilder.cs")

const _ART_NODE_NAME = "Art"

## StaticEntityKind ordinal. -1 until setup_visual runs.
var _kind: int = -1

var _entity_id: int = 0

var _total_seconds: float = 0.0
var _remaining_seconds: float = 0.0

## Whether anybody is working on it. The client only runs the progress on between server corrections while
## this is true - a site nobody is building holds exactly where it is. See ConstructionComponentSMSG.
var _active: bool = false

@onready var _pick_shape: CollisionShape3D = $Area3D/CollisionShape3D


func setup_visual(msg: VisualComponentSMSG) -> void:
	_kind = msg.VisualId
	_entity_id = msg.EntityId


func get_kind() -> int:
	return _kind


func get_structure_entity_id() -> int:
	return _entity_id


func _ready() -> void:
	if _kind < 0:
		printerr("StructureVisual was readied without a kind")
		return

	var builder = PropVisualBuilderScript.new()

	var art: Node3D = builder.BuildConstruction(_kind)
	art.name = _ART_NODE_NAME
	add_child(art)

	var size: Vector3 = builder.PickSize(_kind)
	var box := BoxShape3D.new()
	box.size = size
	_pick_shape.shape = box
	_pick_shape.position = Vector3(0.0, size.y * 0.5, 0.0)

	# Whatever arrived before the art existed.
	_apply_progress()


func update_construction(msg: ConstructionComponentSMSG) -> void:
	_total_seconds = msg.TotalSeconds
	_remaining_seconds = msg.RemainingSeconds
	_active = msg.Active
	_apply_progress()


func _process(delta: float) -> void:
	if not _active or _remaining_seconds <= 0.0:
		return

	# Between corrections, which arrive every couple of seconds. The server is the authority; this only
	# keeps the fill from advancing in visible steps.
	_remaining_seconds = maxf(0.0, _remaining_seconds - delta)
	_apply_progress()


func _apply_progress() -> void:
	var art := get_node_or_null(_ART_NODE_NAME) as MeshInstance3D
	if art == null or art.material_override == null:
		return

	var progress := 1.0
	if _total_seconds > 0.0:
		progress = clampf((_total_seconds - _remaining_seconds) / _total_seconds, 0.0, 1.0)

	art.material_override.set_shader_parameter("progress", progress)


func _on_area_3d_input_event(_camera: Node, event: InputEvent, event_position: Vector3, _normal: Vector3, _shape_idx: int) -> void:
	MouseManager.get_instance().object_clicked(self, event, event_position)
