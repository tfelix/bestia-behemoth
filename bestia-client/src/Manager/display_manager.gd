extends Node

## Applies the display settings of [ClientSettings] (window mode, vsync) to the window.
##
## An autoload so the saved choices take hold at launch, not only once somebody opens the settings.

func _ready() -> void:
	apply()


func apply() -> void:
	var settings := SettingsManager.get_instance()
	DisplayServer.window_set_mode(
		DisplayServer.WINDOW_MODE_FULLSCREEN if settings.fullscreen else DisplayServer.WINDOW_MODE_WINDOWED
	)
	DisplayServer.window_set_vsync_mode(
		DisplayServer.VSYNC_ENABLED if settings.vsync else DisplayServer.VSYNC_DISABLED
	)
