extends Control

func _on_connect_button_pressed() -> void:
	SceneManager.goto_scene("res://Menu/Test/Test2.tscn", true)
	$Timer.start()


func _on_timer_timeout() -> void:
	SceneManager.unblock_transition()
