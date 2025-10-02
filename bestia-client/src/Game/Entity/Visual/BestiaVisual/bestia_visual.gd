class_name BestiaVisual extends Visual

var _bestia_id: int = 0
var _bestia_entity_id: int = 0

@onready var _name_tag = $NameTag
@onready var _anim_player = $AnimationPlayer as AnimationPlayer
@onready var _health_bar = $HealthBar as HealthBar


func _ready() -> void:
	_anim_player.play("appear")
	_health_bar.value = 100


func setup_visual(msg: BestiaVisualComponent) -> void:
	print("TODO: Set the bestia visuals here")
	_bestia_entity_id = msg.EntityId
	_bestia_id = msg.BestiaId
	# Load bestia data on-demand
	# TODO its not yet clear what path we go, either we load recources, we could also think
	# about a sperate scene for every bestia and just enter the values there and move around the different
	# items for a easier and more visual approach in handling data. Then this can be removed again.
	#_bestia_data = BestiaResourceManager.get_bestia_data(_bestia_id)


func vanish(msg: VanishEntitySMSG) -> void:
	if msg.IsDead():
		_anim_player.play("death")
		await _anim_player.animation_finished
		get_parent().queue_free()
	else:
		_anim_player.play("appear", -1, 1.0, true)
		await _anim_player.animation_finished
		get_parent().queue_free()


func _on_area_3d_input_event(_camera: Node, event: InputEvent, _event_position: Vector3, _normal: Vector3, _shape_idx: int) -> void:
	if event.is_action_pressed("normal_action"):
		print("bestia %s was clicked" % _bestia_entity_id)
		ConnectionManager.send_attack_entity(_bestia_entity_id, 0, 1)


func _on_area_3d_mouse_entered() -> void:
	_name_tag.visible = true


func _on_area_3d_mouse_exited() -> void:
	_name_tag.visible = false
