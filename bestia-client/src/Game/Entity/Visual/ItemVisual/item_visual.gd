extends Visual
class_name ItemVisual


var _entity_id: int = 0
var _item_id: int = 0


@onready var player = $AnimationPlayer
@onready var sparkles = $Sparkles


func setup_visual(msg: ItemVisualComponentSMSG) -> void:
	_entity_id = msg.EntityId
	_item_id = msg.ItemId


func _ready() -> void:
	player.play("appear")
	await player.animation_finished
	sparkles.visible = true


func get_item_entity_id() -> int:
	return _entity_id


func _on_area_3d_input_event(_camera: Node, event: InputEvent, event_position: Vector3, _normal: Vector3, _shape_idx: int) -> void:
	MouseManager.object_clicked(self, event, event_position)
