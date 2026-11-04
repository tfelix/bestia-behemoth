class_name DisplaySettings

## Applies the display settings of [ClientSettings] (window mode, vsync) to the window: once at launch, so the saved
## choices take hold before anybody opens the settings, and again on every change made there.

static var _launched := false


## For the main menu, which is shown again after every logout.
static func apply_at_launch() -> void:
	if _launched:
		return
	_launched = true
	apply()


static func apply() -> void:
	var settings := SettingsManager.get_instance()
	DisplayServer.window_set_mode(
		DisplayServer.WINDOW_MODE_FULLSCREEN if settings.fullscreen else DisplayServer.WINDOW_MODE_WINDOWED
	)
	# Mailbox rather than plain vsync: it still never tears, but shows the newest finished frame instead of queueing
	# them, which takes frames of lag off anything that follows the mouse, like the move marker. A driver without it
	# falls back to plain vsync.
	DisplayServer.window_set_vsync_mode(
		DisplayServer.VSYNC_MAILBOX if settings.vsync else DisplayServer.VSYNC_DISABLED
	)
