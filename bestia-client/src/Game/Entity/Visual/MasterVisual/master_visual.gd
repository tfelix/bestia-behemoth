class_name MasterVisual extends Visual

# TODO implement a failsafe where the client sends out a sync request
#  if it thinks after a certain time not all entities are there e.g. the master.

var _master_entity_id: int = 0
var _master_name: String = ""
var _hovered: bool = false
var _selected: bool = false

@onready var _anim_player: AnimationPlayer = $Mage/AnimationPlayer
@onready var _cast_bar: CastBar = $CastBar
@onready var _name_tag = $NameTag


## Only BodyType.BODY_M_1 (0) exists today, so this always resolves to the static $Mage
## child - the match is structured so a future body can be added as its own branch without
## touching the dispatch here. Skin/hair color application (msg.SkinColor/msg.HairColor) is
## deferred until there's more than one body to apply them to.
func setup_visual(msg: MasterVisualComponentSMSG) -> void:
	_master_entity_id = msg.EntityId
	_master_name = msg.Name
	_apply_name_tag()

	match msg.Body:
		0: # BodyType.BODY_M_1
			_apply_mage_body(msg)
		_:
			printerr("MasterVisual: unhandled BodyType %s, falling back to Mage" % [msg.Body])
			_apply_mage_body(msg)


func _apply_mage_body(_msg: MasterVisualComponentSMSG) -> void:
	pass


## [Entity] calls setup_visual on a freshly instantiated visual, before it is added to the tree - so
## the @onready children are still null there. The name is kept and written again from here, which is
## the first moment $NameTag exists.
func _ready() -> void:
	_apply_name_tag()


func _apply_name_tag() -> void:
	if _name_tag == null:
		return
	_name_tag.text = _master_name


## Named for the method [MouseManager] duck-types across every clickable visual, not for bestias - a master
## answers it too, or it could not be selected or traded with.
func get_bestia_entity_id() -> int:
	return _master_entity_id


func get_master_name() -> String:
	return _master_name


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


func update_casting(msg: CastingComponentSMSG) -> void:
	_cast_bar.update_casting(msg)


func clear_casting() -> void:
	_cast_bar.clear_casting()


func update_animation(msg: AnimationComponentSMSG) -> void:
	if _anim_player.current_animation == msg.Kind:
		return
	
	var mapped_animation = _map_animation_name(msg.Kind)
	
	if _anim_player.has_animation(mapped_animation):
		_anim_player.play(mapped_animation)


func update_animation_direct(animation_name: String) -> void:	
	var mapped_animation = _map_animation_name(animation_name)
	
	if _anim_player.has_animation(mapped_animation):
		_anim_player.play(mapped_animation)


func _map_animation_name(server_anim_kind: String) -> String:
	var aninmation := server_anim_kind.to_upper()
	if aninmation == "WALK":
		return "Walking_A"
	elif aninmation == "IDLE":
		return "Idle"
	else:
		printerr("MasterVisual: Unknown server animation: %s" % [server_anim_kind])
		return "Idle"
