extends Node3D
class_name Visual


var ChatText = preload("res://Game/Entity/ChatText/ChatText.tscn")

const _CHAT_NODE_NAME = "ChatText"

func show_damage(_msg: DamageEntitySMSG) -> void:
	pass


func update_health(_msg: HealthComponentSMSG) -> void:
	pass


func update_animation(_msg: AnimationComponentSMSG) -> void:
	pass


## Drops this entity into its death pose, or picks it back up. Only player-owned entities are ever
## told this - a wild mob is destroyed on death and gets [method vanish] instead.
func set_dead(_dead: bool) -> void:
	pass


## Shows/updates the cast bar while this entity channels a skill.
func update_casting(_msg: CastingComponentSMSG) -> void:
	pass


## Hides the cast bar - the cast either completed or was interrupted.
func clear_casting() -> void:
	pass


func set_selected(_selected: bool) -> void:
	pass


func show_chat(msg: ChatSMSG) -> void:
	var chat_anchor = get_node("ChatAnchor")

	if chat_anchor == null:
		printerr("Visual %s received chat but has not ChatAnchor set." % [name])
		return
	
	for x in chat_anchor.get_children():
		x.queue_free()
	var chat_text = ChatText.instantiate()
	chat_text.chat_msg = msg
	chat_text.name = _CHAT_NODE_NAME
	chat_anchor.add_child(chat_text)
