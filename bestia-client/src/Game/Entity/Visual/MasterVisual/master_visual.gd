class_name MasterVisual extends Visual

# TODO implement a failsafe where the client sends out a sync request
#  if it thinks after a certain time not all entities are there e.g. the master.

var _master_entity_id: int = 0
var _master_name: String = ""
var _guild_line: String = ""
var _hovered: bool = false
var _selected: bool = false

@onready var _anim_player: AnimationPlayer = $Mage/AnimationPlayer
@onready var _cast_bar: CastBar = $CastBar
@onready var _name_tag = $NameTag
@onready var _skeleton: Skeleton3D = $Mage/Rig/Skeleton3D

var _look: MasterVisualComponentSMSG = null
var _worn_gear: WornGearComponentSMSG = null
var _parts: MasterParts = null


## Every body type is a set of parts on the Mage's rig, so the Mage's skeleton and animations serve all.
func setup_visual(msg: MasterVisualComponentSMSG) -> void:
	_master_entity_id = msg.EntityId
	_master_name = msg.Name
	_look = msg
	_apply_name_tag()
	_apply_look()


## [Entity] calls setup_visual on a freshly instantiated visual, before it is added to the tree - so
## the @onready children are still null there. The name and look are kept and applied again from here,
## the first moment their nodes exist.
func _ready() -> void:
	_apply_name_tag()
	_apply_look()


## The Mage's own meshes are hidden in the scene; these parts replace them.
func _apply_look() -> void:
	if _look == null or _skeleton == null:
		return
	if _parts == null:
		_parts = MasterParts.new()
		_skeleton.add_child(_parts)
	_parts.show_look(_look.Body, _look.Face, _look.Hair, _look.HairColor, _look.SkinColor)
	_apply_worn_gear()


func update_worn_gear(msg: WornGearComponentSMSG) -> void:
	_worn_gear = msg
	_apply_worn_gear()


## Gear whose item names no part is not drawn yet, but it can still hide the hair.
func _apply_worn_gear() -> void:
	if _worn_gear == null or _parts == null:
		return
	var parts: Array[String] = []
	var hide_hair := false
	for item_id in _worn_gear.ItemIds:
		var item := ItemDB.get_instance().get_item(item_id)
		if item == null:
			continue
		if item.worn_part != "":
			parts.append(item.worn_part)
		hide_hair = hide_hair or item.hides_hair
	_parts.wear(parts, hide_hair)


func set_guild_tag(tag: GuildTagComponentSMSG) -> void:
	_guild_line = GuildTagLine.of(tag)
	_apply_name_tag()


func _apply_name_tag() -> void:
	if _name_tag == null:
		return
	_name_tag.text = GuildTagLine.under(_master_name, _guild_line)


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
	if not MouseManager.is_click_event(event):
		return

	MouseManager.get_instance().object_clicked(self, event, event_position)


func _on_area_3d_mouse_entered() -> void:
	_hovered = true
	_name_tag.visible = true
	MouseManager.get_instance().on_object_hover(self, true)


func _on_area_3d_mouse_exited() -> void:
	_hovered = false
	_name_tag.visible = _selected
	MouseManager.get_instance().on_object_hover(self, false)


func update_casting(msg: CastingComponentSMSG) -> void:
	_cast_bar.update_casting(msg)


func clear_casting() -> void:
	_cast_bar.clear_casting()


## Death is entity state, not one of the server's three AnimationKinds, so it bypasses
## [method _map_animation_name] entirely. Death_A plays through once and Death_A_Pose holds the body
## on the ground; [Entity] stops feeding walk/idle in the meantime.
func set_dead(dead: bool) -> void:
	if not dead:
		if _anim_player.has_animation("Idle"):
			_anim_player.play("Idle")
		return

	if _anim_player.has_animation("Death_A"):
		_anim_player.play("Death_A")
		if _anim_player.has_animation("Death_A_Pose"):
			_anim_player.queue("Death_A_Pose")


func update_animation(msg: AnimationComponentSMSG) -> void:
	if _anim_player.current_animation == msg.Kind:
		return
	
	var mapped_animation = _map_animation_name(msg.Kind)
	
	if _anim_player.has_animation(mapped_animation):
		_anim_player.play(mapped_animation)


## Called by [Entity] every frame it is walking, so it has to be cheap and idempotent - the guard below
## is what keeps it from restarting the walk clip on each of those frames.
func update_animation_from_client(animation_name: String) -> void:
	var mapped_animation = _map_animation_name(animation_name)
	if _anim_player.current_animation == mapped_animation:
		return

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
