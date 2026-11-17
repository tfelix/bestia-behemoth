extends Visual
class_name StructureVisual

## A structure drawn as an ordinary entity: a construction site, a ward stone, or a short-lived one like a stall.
##
## A finished station reaches the client on the per-chunk static batch and is drawn by StaticEntityRenderer.
## A construction site's progress and health change while somebody is watching, so it travels the entity
## channel instead. The art is the same catalogue either way - see PropVisualBuilder.

const PropVisualBuilderScript = preload("res://Game/World/PropVisualBuilder.cs")

const _ART_NODE_NAME = "Art"

const _CITY_SIGN_KIND := 57

## StaticEntityKind ordinal. -1 until setup_visual runs.
var _kind: int = -1

var _entity_id: int = 0

## What a click on the finished structure does, named as on a PropPicker. Set in _ready from the kind.
var action: StringName = &"none"

var _is_landmark: bool = false

var _total_seconds: float = 0.0
var _remaining_seconds: float = 0.0

## Whether anybody is working on it. The client only runs the progress on between server corrections while
## this is true - a site nobody is building holds exactly where it is. See ConstructionComponentSMSG.
var _active: bool = false

## How often a board's marker asks again whether it fits: the master's level changes without the board knowing.
const _OFFER_RECHECK_SECONDS := 2.0
## Above the placeholder board, clear of a player's name tag.
const _OFFER_MARKER_HEIGHT := 3.0

var _formatter := QuestFormatter.new()
var _offer_marker: Label3D
var _offer_low: int = 0
var _offer_high: int = 0

@onready var _pick_shape: CollisionShape3D = $Area3D/CollisionShape3D


func setup_visual(msg: VisualComponentSMSG) -> void:
	_kind = msg.VisualId
	_entity_id = msg.EntityId


func get_kind() -> int:
	return _kind


func get_structure_entity_id() -> int:
	return _entity_id


## A ward stone is never sent a ConstructionComponentSMSG.
func is_construction_site() -> bool:
	return _total_seconds > 0.0


## A standing structure that opens a window when clicked, like a city sign. Kind ordinals from StaticEntityKind.
func is_usable() -> bool:
	return _kind == _CITY_SIGN_KIND


## A landmark the world generator placed: examined with a click, attacked rather than repaired.
func is_landmark() -> bool:
	return _is_landmark


func _ready() -> void:
	if _kind < 0:
		printerr("StructureVisual was readied without a kind")
		return

	var builder = PropVisualBuilderScript.new()
	action = builder.ActionName(_kind)
	_is_landmark = builder.IsLandmark(_kind)

	var art: Node3D = builder.BuildConstruction(_kind)
	art.name = _ART_NODE_NAME
	add_child(art)

	# A designer's scene is measured, so it needs no collision of its own.
	var size: Vector3 = builder.PickSizeOf(art) if builder.HasScene(_kind) else builder.PickSize(_kind)
	var box := BoxShape3D.new()
	box.size = size
	_pick_shape.shape = box
	_pick_shape.position = Vector3(0.0, size.y * 0.5, 0.0)

	# Whatever arrived before the art existed.
	_apply_progress()


## A quest board's open levels. The "!" shows while one fits the played master, who may level up meanwhile.
func set_offer(levels_low: int, levels_high: int) -> void:
	if _offer_marker == null:
		_offer_marker = _build_offer_marker()
		add_child(_offer_marker)
		var timer := Timer.new()
		timer.wait_time = _OFFER_RECHECK_SECONDS
		timer.autostart = true
		timer.timeout.connect(_recheck_offer)
		add_child(timer)
	_offer_low = levels_low
	_offer_high = levels_high
	_recheck_offer()


func _recheck_offer() -> void:
	var manager := EntityManager.get_instance()
	var master := manager.get_master_entity() if manager != null else null
	var fits: bool = master != null and _formatter.OfferFits(_offer_low, _offer_high, master.get_level())
	_offer_marker.visible = fits


func _build_offer_marker() -> Label3D:
	var marker := Label3D.new()
	marker.text = "!"
	marker.font_size = 96
	marker.outline_size = 18
	marker.modulate = QuestStyle.BAND_COLOURS["fair"]
	marker.billboard = BaseMaterial3D.BILLBOARD_ENABLED
	marker.no_depth_test = true
	marker.position = Vector3(0.0, _OFFER_MARKER_HEIGHT, 0.0)
	marker.visible = false
	return marker


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
	if not MouseManager.is_click_event(event):
		return

	MouseManager.get_instance().object_clicked(self, event, event_position)


func _on_area_3d_mouse_entered() -> void:
	MouseManager.get_instance().on_object_hover(self, true)


func _on_area_3d_mouse_exited() -> void:
	MouseManager.get_instance().on_object_hover(self, false)
