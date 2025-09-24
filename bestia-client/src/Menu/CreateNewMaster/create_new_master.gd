extends Control


func _on_create_button_pressed() -> void:
		_goto_master_selection()


func _on_cancel_button_pressed() -> void:
	_goto_master_selection()


func _goto_master_selection() -> void:
	SceneManager.goto_scene("res://Menu/MasterSelect/MasterSelect.tscn")
