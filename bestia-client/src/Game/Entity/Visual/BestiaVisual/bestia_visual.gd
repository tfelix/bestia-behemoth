class_name BestiaVisual extends Visual

var DamageTagScn = preload("res://Game/Entity/Visual/DamageTag/DamageTag.tscn")

const _IDLE_ANIM = "Idle"
const _APPEAR_ANIM = "appear"

var _bestia_id: int = 0
var _bestia_entity_id: int = 0
var _species_name: String = ""
## An individual's name, which wins over the species when there is one. Only townsfolk have one; a
## wolf is a Wolf.
var _display_name: String = ""
var _hovered: bool = false
var _selected: bool = false

@onready var _name_tag = $NameTag
@onready var _anim_player = $AnimationPlayer as AnimationPlayer
@onready var _health_bar: HealthBar = $HealthBar
@onready var _cast_bar: CastBar = $CastBar
@onready var _damage_tag_anchor: Node3D = $DamageTagAnchor


func _ready() -> void:
	_apply_name_tag()
	_anim_player.play(_APPEAR_ANIM)


func setup_visual(msg: VisualComponentSMSG) -> void:
	_bestia_entity_id = msg.EntityId
	_bestia_id = msg.VisualId

	var bestia_resource := BestiaDB.get_instance().get_bestia(_bestia_id)
	if bestia_resource != null and not bestia_resource.name_key.is_empty():
		_species_name = tr(bestia_resource.name_key)
	_apply_name_tag()


func set_display_name(display_name: String) -> void:
	_display_name = display_name
	_apply_name_tag()


## [Entity] calls setup_visual before the visual is in the tree, so the @onready children are still null
## there. The name is kept and written again from here, which is the first moment $NameTag exists.
func _apply_name_tag() -> void:
	if _name_tag == null:
		return
	_name_tag.text = _display_name if not _display_name.is_empty() else _species_name


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
	_play_clip(msg.Kind)


## [Entity] drives walk and idle off its own movement state instead of waiting for the server, and says so
## in the server's uppercase kinds. This placeholder has no walk clip, so WALK lands on Idle by the same
## fallback as any other kind it cannot play.
func update_animation_from_client(animation_name: String) -> void:
	if animation_name.to_upper() == "WALK":
		_play_clip("Walk")
	else:
		_play_clip(_IDLE_ANIM)


func _play_clip(kind: String) -> void:
	# The server's vocabulary is wider than any one visual's clip set - this placeholder has no Walk - so an
	# unknown kind falls back to Idle rather than leaving whatever was playing to run on. Without that a
	# creature that fell asleep and then got up and walked away would keep playing its sleep loop the whole
	# way, because nothing else would ever interrupt it.
	var clip := kind if _anim_player.has_animation(kind) else _IDLE_ANIM
	if _anim_player.current_animation == clip:
		return

	# Don't cut the fade-in short. An entity's first component sync lands within a frame or two of it being
	# spawned, which is well inside the appear animation, so playing over it would mean nothing ever faded in.
	if _anim_player.current_animation == _APPEAR_ANIM and _anim_player.is_playing():
		_anim_player.clear_queue()
		_anim_player.queue(clip)
		return

	_anim_player.play(clip)


## An owned bestia that dies keeps its body, so unlike [method vanish] this plays the death clip and
## simply lets it end - AnimationPlayer holds the final pose, which is the creature on the ground.
## Wild mobs never get here: they are destroyed on death and take the vanish path instead.
func set_dead(dead: bool) -> void:
	_anim_player.clear_queue()

	if dead:
		_anim_player.play("death")
	else:
		_anim_player.play(_IDLE_ANIM)


func vanish(msg: VanishEntitySMSG) -> void:
	_health_bar.visible = false
	_cast_bar.clear_casting()
	_name_tag.visible = false
	# Freed at once, with no send-off to await: an entity that merely left the view can be back inside the
	# second - it only takes pacing a chunk boundary - and EntityManager has already dropped the id, so a
	# node still fading out while the entity returns leaves two of it on screen.
	if msg.IsOutOfSight():
		get_parent().queue_free()
		return

	if msg.IsDead():
		_anim_player.play("death")
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
	MouseManager.get_instance().object_clicked(self, event, event_position)


func _on_area_3d_mouse_entered() -> void:
	_hovered = true
	_name_tag.visible = true
	MouseManager.get_instance().on_object_hover(self, true)


func _on_area_3d_mouse_exited() -> void:
	_hovered = false
	_name_tag.visible = _selected
	MouseManager.get_instance().on_object_hover(self, false)
