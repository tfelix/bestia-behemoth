extends Node3D


func _on_button_pressed() -> void:
	$EntityManager.resync_entities()
