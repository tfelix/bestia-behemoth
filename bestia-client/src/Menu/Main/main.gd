extends Control

@onready var _version_label = %VersionLabel
@onready var _play_button = %PlayButton

func _ready() -> void:
	ConnectionManager.disconnect_from_server()
	_version_label.text = SettingsManager.version
	_play_button.disabled = false


func _on_quit_button_pressed() -> void:
	get_tree().root.propagate_notification(NOTIFICATION_WM_CLOSE_REQUEST)
	get_tree().quit()


func _on_settings_button_pressed() -> void:
	SceneManager.goto_scene("res://Menu/Settings/Settings.tscn")


func _on_play_button_pressed() -> void:
	_play_button.disabled = true
	ConnectionManager.login()
	# SceneManager.goto_scene()
	# TODO would be nice to have a dedicated login scene which shows the login process to which we can
	# switch into.
