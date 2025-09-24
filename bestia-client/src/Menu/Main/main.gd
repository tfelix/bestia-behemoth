extends Control

@onready var versionLabel = %VersionLabel


func _ready() -> void:
	ConnectionManager.disconnect_from_server()
	versionLabel.text = SettingsManager.version


func _on_quit_button_pressed() -> void:
	get_tree().root.propagate_notification(NOTIFICATION_WM_CLOSE_REQUEST)
	get_tree().quit()


func _on_settings_button_pressed() -> void:
	SceneManager.goto_scene("res://Menu/Settings/Settings.tscn")


func _on_play_button_pressed() -> void:
	ConnectionManager.login()
