extends Control
## The main menu's settings page: the shared [SettingsPanel] centred on the menu background.

const MAIN_SCENE := "res://Menu/Main/Main.tscn"


func _ready() -> void:
	var background: Node = load("res://Menu/Main/Background/Background.tscn").instantiate()
	add_child(background)

	var center := CenterContainer.new()
	center.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(center)

	var panel := SettingsPanel.new()
	panel.closed.connect(func(): SceneManager.goto_scene(MAIN_SCENE))
	center.add_child(panel)
	panel.focus_back.call_deferred()


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel"):
		SettingsManager.save()
		SceneManager.goto_scene(MAIN_SCENE)
		get_viewport().set_input_as_handled()
