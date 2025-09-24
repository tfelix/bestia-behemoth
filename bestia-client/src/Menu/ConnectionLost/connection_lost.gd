extends Control
## Informs the player about a lost connection to the server
##
## The player can only confirm and is taken back to the main menu to
## attempt to reconnect.
##

func _on_confirm_button_pressed() -> void:
	SceneManager.goto_scene("res://Menu/Main/Main.tscn")
