extends Node3D
class_name Visual


var ChatText = preload("res://Game/Entity/ChatText/ChatText.tscn")


@onready var _chat_anchor = $ChatAnchor
const _CHAT_NODE_NAME = "ChatText"

func show_damage(_msg: DamageEntitySMSG) -> void:
	pass


func update_health(_msg: HealthComponentSMSG) -> void:
	pass


func show_chat(msg: ChatSMSG) -> void:
	for x in _chat_anchor.get_children():
		x.queue_free()
	var chat_text = ChatText.instantiate()
	chat_text.chat_msg = msg
	chat_text.name = _CHAT_NODE_NAME
	_chat_anchor.add_child(chat_text)
