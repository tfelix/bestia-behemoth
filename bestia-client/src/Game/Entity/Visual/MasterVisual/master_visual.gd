class_name MasterVisual extends Visual

# TODO implement a failsafe where the client sends out a sync request
#  if it thinks after a certain time not all entities are there e.g. the master.

@onready var _anim_player: AnimationPlayer = $Mage/AnimationPlayer
@onready var _cast_bar: CastBar = $CastBar


## Only BodyType.BODY_M_1 (0) exists today, so this always resolves to the static $Mage
## child - the match is structured so a future body can be added as its own branch without
## touching the dispatch here. Skin/hair color application (msg.SkinColor/msg.HairColor) is
## deferred until there's more than one body to apply them to.
func setup_visual(msg: MasterVisualComponentSMSG) -> void:
	match msg.Body:
		0: # BodyType.BODY_M_1
			_apply_mage_body(msg)
		_:
			printerr("MasterVisual: unhandled BodyType %s, falling back to Mage" % [msg.Body])
			_apply_mage_body(msg)


func _apply_mage_body(_msg: MasterVisualComponentSMSG) -> void:
	pass


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
