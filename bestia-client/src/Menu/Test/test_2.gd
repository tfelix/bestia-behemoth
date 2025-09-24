extends Control

@onready var timer = $Timer

func _on_button_pressed() -> void:
	SceneManager.goto_scene("res://Menu/Test/Test.tscn")


func _on_timer_timeout() -> void:
	SceneManager.unblock_transition()
