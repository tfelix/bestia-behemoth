class_name BestiaVisual extends Visual

var DamageTagScn = preload("res://Game/Entity/Visual/DamageTag/DamageTag.tscn")

const _IDLE_ANIM = "Idle"
const _APPEAR_ANIM = "appear"

var _bestia_id: int = 0
var _bestia_entity_id: int = 0
var _hovered: bool = false
var _selected: bool = false

@onready var _name_tag = $NameTag
@onready var _anim_player = $AnimationPlayer as AnimationPlayer
@onready var _health_bar: HealthBar = $HealthBar
@onready var _cast_bar: CastBar = $CastBar
@onready var _damage_tag_anchor: Node3D = $DamageTagAnchor


func _ready() -> void:
	_anim_player.play(_APPEAR_ANIM)


func setup_visual(msg: BestiaVisualComponent) -> void:
	_bestia_entity_id = msg.EntityId
	_bestia_id = msg.BestiaId
	# Load bestia data on-demand
	# TODO its not yet clear what path we go, either we load recources, we could also think
	# about a sperate scene for every bestia and just enter the values there and move around the different
	# items for a easier and more visual approach in handling data. Then this can be removed again.
	#_bestia_data = BestiaResourceManager.get_bestia_data(_bestia_id)


func show_damage(msg: DamageEntitySMSG) -> void:
	var damage_tag: DamageTag = DamageTagScn.instantiate()
	damage_tag.damage_msg = msg
	_damage_tag_anchor.add_child(damage_tag)


func update_health(msg: HealthComponentSMSG) -> void:
	_health_bar.update_health(msg)


func update_casting(msg: CastingComponentSMSG) -> void:
	_cast_bar.update_casting(msg)


func clear_casting() -> void:
	_cast_bar.clear_casting()


func update_animation(msg: AnimationComponentSMSG) -> void:
	# The server's vocabulary is wider than any one visual's clip set - this placeholder has no Walk - so an
	# unknown kind falls back to Idle rather than leaving whatever was playing to run on. Without that a
	# creature that fell asleep and then got up and walked away would keep playing its sleep loop the whole
	# way, because nothing else would ever interrupt it.
	var clip := msg.Kind if _anim_player.has_animation(msg.Kind) else _IDLE_ANIM
	if _anim_player.current_animation == clip:
		return

	# Don't cut the fade-in short. An entity's first component sync lands within a frame or two of it being
	# spawned, which is well inside the appear animation, so playing over it would mean nothing ever faded in.
	if _anim_player.current_animation == _APPEAR_ANIM and _anim_player.is_playing():
		_anim_player.clear_queue()
		_anim_player.queue(clip)
		return

	_anim_player.play(clip)


func vanish(msg: VanishEntitySMSG) -> void:
	_health_bar.visible = false
	_cast_bar.clear_casting()
	_name_tag.visible = false
	if msg.IsDead():
		_anim_player.play("death")
		await _anim_player.animation_finished
		get_parent().queue_free()
	else:
		_anim_player.play(_APPEAR_ANIM, -1, 1.0, true)
		await _anim_player.animation_finished
		get_parent().queue_free()


func get_bestia_entity_id() -> int:
	return _bestia_entity_id


func set_selected(selected: bool) -> void:
	_selected = selected
	_name_tag.visible = _selected or _hovered


func _on_area_3d_input_event(_camera: Node, event: InputEvent, event_position: Vector3, _normal: Vector3, _shape_idx: int) -> void:
	MouseManager.object_clicked(self, event, event_position)


func _on_area_3d_mouse_entered() -> void:
	_hovered = true
	_name_tag.visible = true
	MouseManager.on_object_hover(self, true)


func _on_area_3d_mouse_exited() -> void:
	_hovered = false
	_name_tag.visible = _selected
	MouseManager.on_object_hover(self, false)
