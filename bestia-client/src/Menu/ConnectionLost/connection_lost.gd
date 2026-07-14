extends Control
## Informs the player about a lost connection to the server
##
## The player can only confirm and is taken back to the main menu to
## attempt to reconnect.
##

@onready var _error_label = %ErrorLabel


func _on_confirm_button_pressed() -> void:
	SceneManager.goto_scene("res://Menu/Main/Main.tscn")


func _ready() -> void:
	var last_error := ConnectionManager.last_connection_error
	if last_error == ConnectionManager.ConnectionError.LOGIN_OFFLINE:
		_error_label.text = "Login Server offline..."
	elif last_error == ConnectionManager.ConnectionError.LOGIN_ERROR:
		_error_label.text = "Error during login..."
	elif last_error == ConnectionManager.ConnectionError.ZONE_CONNECTION_LOST:
		_error_label.text = "Connection to server was lost..."
