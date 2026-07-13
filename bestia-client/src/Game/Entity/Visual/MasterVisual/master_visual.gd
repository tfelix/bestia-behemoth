class_name MasterVisual extends Visual

# TODO implement a failsafe where the client sends out a sync request
#  if it thinks after a certain time not all entities are there e.g. the master.

@onready var _anim_player: AnimationPlayer = $Mage/AnimationPlayer


func setup_visual(msg: MasterVisualComponentSMSG) -> void:
	print("TODO: Set the master visuals here")
	pass


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
