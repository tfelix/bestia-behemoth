extends PanelContainer
## An announcement to everyone online, across the top of the screen under the place name. It fades after a few
## seconds; the chat keeps the line. A newer announcement replaces the one showing.

const _SHOWN_SECONDS := 8.0
const _FADE_SECONDS := 0.6

@onready var _text: Label = $Margin/Text

var _fade: Tween


func _ready() -> void:
	visible = false
	_text.add_theme_color_override("font_color", UiStyle.ANNOUNCEMENT)
	ConnectionManager.announcement_received.connect(_on_announcement_received)


func _on_announcement_received(message: AnnouncementSMSG) -> void:
	_text.text = AnnouncementText.of(message)
	visible = true
	modulate.a = 1.0

	if _fade != null:
		_fade.kill()
	_fade = create_tween()
	_fade.tween_interval(_SHOWN_SECONDS)
	_fade.tween_property(self, "modulate:a", 0.0, _FADE_SECONDS)
	_fade.tween_callback(func() -> void: visible = false)
