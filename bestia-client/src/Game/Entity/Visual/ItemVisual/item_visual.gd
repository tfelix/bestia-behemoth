extends Visual
class_name ItemVisual


var _entity_id: int = 0
var _item_id: int = 0


@onready var player = $AnimationPlayer
@onready var sparkles = $Sparkles


func setup_visual(msg: VisualComponentSMSG) -> void:
	_entity_id = msg.EntityId
	_item_id = msg.VisualId


func _ready() -> void:
	player.play("appear")
	await player.animation_finished
	sparkles.visible = true


func get_item_entity_id() -> int:
	return _entity_id


func _on_area_3d_input_event(_camera: Node, event: InputEvent, event_position: Vector3, _normal: Vector3, _shape_idx: int) -> void:
	if not MouseManager.is_click_event(event):
		return

	MouseManager.get_instance().object_clicked(self, event, event_position)


func _on_area_3d_mouse_entered() -> void:
	MouseManager.get_instance().on_object_hover(self, true)


func _on_area_3d_mouse_exited() -> void:
	MouseManager.get_instance().on_object_hover(self, false)
