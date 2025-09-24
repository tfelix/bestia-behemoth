extends Control


func _gui_input(event: InputEvent) -> void:
	# print("UI 1: ", event.as_text())
	pass

func _unhandled_key_input(event: InputEvent) -> void:
	print("UI 2: ", event.as_text())
