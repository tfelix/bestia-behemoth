extends Visual
class_name ItemVisual


var _item_id: int = 0


@onready var player = $AnimationPlayer
@onready var sparkles = $Sparkles

func _ready() -> void:
	player.play("appear")
	await player.animation_finished
	sparkles.visible = true


func _on_area_3d_input_event(_camera: Node, event: InputEvent, _event_position: Vector3, _normal: Vector3, _shape_idx: int) -> void:
	if event.is_action_pressed("normal_action"):
		print("item %s was clicked" % _item_id)
