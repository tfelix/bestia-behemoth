extends Node3D

@onready var label = $Label3D

var chat_msg: ChatSMSG

func _ready() -> void:
	var text = ""
	if chat_msg.SenderName != null:
		text = "%s: %s" % [chat_msg.SenderName, chat_msg.Text]
	else:
		text = chat_msg.Text
	label.text = text


func _on_timer_timeout() -> void:
	queue_free()
