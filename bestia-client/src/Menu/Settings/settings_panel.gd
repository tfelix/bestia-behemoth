extends PanelContainer
class_name SettingsPanel

## The settings window, built in code so the main menu and the in-game ESC menu show the very same one.
##
## Every change applies the moment it is made (so audio can be judged by ear while playing) and is saved
## when the player lets go of a slider or flips a switch. [signal closed] fires for the Back button.

signal closed

const _BUS_LABELS := {
	&"Master": "Master",
	&"Music": "Music",
	&"Ambience": "Ambience",
	&"Effects": "Effects",
	&"Interface": "Interface",
}

var _back_button: Button


func _ready() -> void:
	custom_minimum_size = Vector2(520, 0)
	add_theme_stylebox_override("panel", _panel_style())

	var margin := MarginContainer.new()
	for side in ["left", "right"]:
		margin.add_theme_constant_override("margin_" + side, 32)
	for side in ["top", "bottom"]:
		margin.add_theme_constant_override("margin_" + side, 28)
	add_child(margin)

	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", 18)
	margin.add_child(column)

	var title := Label.new()
	title.text = "Settings"
	title.add_theme_font_size_override("font_size", 28)
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	column.add_child(title)
	column.add_child(HSeparator.new())

	var tabs := TabContainer.new()
	tabs.custom_minimum_size = Vector2(0, 280)
	tabs.add_child(_build_audio_tab())
	tabs.add_child(_build_video_tab())
	column.add_child(tabs)

	_back_button = Button.new()
	_back_button.text = "Back"
	_back_button.custom_minimum_size = Vector2(0, 40)
	_back_button.pressed.connect(_on_back_pressed)
	column.add_child(_back_button)


func focus_back() -> void:
	_back_button.grab_focus()


func _build_audio_tab() -> Control:
	var tab := _tab("Audio")
	var grid := _grid()
	tab.add_child(grid)

	for bus in AudioManager.BUSES:
		grid.add_child(_name_label(_BUS_LABELS.get(bus, String(bus))))
		grid.add_child(_volume_slider(bus, grid))
	return tab


func _volume_slider(bus: StringName, grid: GridContainer) -> Control:
	var row := HBoxContainer.new()
	row.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_theme_constant_override("separation", 12)

	var slider := HSlider.new()
	slider.min_value = 0.0
	slider.max_value = 1.0
	slider.step = 0.01
	slider.value = AudioManager.volume_of(bus)
	slider.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	slider.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	row.add_child(slider)

	var readout := Label.new()
	readout.custom_minimum_size = Vector2(48, 0)
	readout.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	readout.text = _percent(slider.value)
	row.add_child(readout)

	slider.value_changed.connect(func(value: float):
		AudioManager.set_volume(bus, value)
		readout.text = _percent(value)
	)
	# A click on the track moves the slider without a drag to end, so both signals can save.
	slider.drag_ended.connect(func(_changed: bool): SettingsManager.save())
	slider.gui_input.connect(func(event: InputEvent):
		if event is InputEventMouseButton and not event.pressed:
			SettingsManager.save()
	)
	return row


func _build_video_tab() -> Control:
	var tab := _tab("Video")
	var grid := _grid()
	tab.add_child(grid)

	var settings := SettingsManager.get_instance()

	grid.add_child(_name_label("Fullscreen"))
	var fullscreen := _toggle(settings.fullscreen)
	fullscreen.toggled.connect(func(on: bool):
		settings.fullscreen = on
		DisplayManager.apply()
		SettingsManager.save()
	)
	grid.add_child(fullscreen)

	grid.add_child(_name_label("Vertical sync"))
	var vsync := _toggle(settings.vsync)
	vsync.toggled.connect(func(on: bool):
		settings.vsync = on
		DisplayManager.apply()
		SettingsManager.save()
	)
	grid.add_child(vsync)
	return tab


func _tab(tab_name: String) -> MarginContainer:
	var tab := MarginContainer.new()
	tab.name = tab_name
	tab.add_theme_constant_override("margin_top", 20)
	tab.add_theme_constant_override("margin_left", 8)
	tab.add_theme_constant_override("margin_right", 8)
	return tab


func _grid() -> GridContainer:
	var grid := GridContainer.new()
	grid.columns = 2
	grid.add_theme_constant_override("h_separation", 24)
	grid.add_theme_constant_override("v_separation", 18)
	return grid


func _name_label(text: String) -> Label:
	var label := Label.new()
	label.text = text
	label.custom_minimum_size = Vector2(130, 0)
	return label


func _toggle(initial: bool) -> CheckButton:
	var toggle := CheckButton.new()
	toggle.button_pressed = initial
	toggle.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	return toggle


func _percent(linear: float) -> String:
	return "%d%%" % roundi(linear * 100.0)


func _panel_style() -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.09, 0.09, 0.1, 0.94)
	style.border_color = Color(0.85, 0.7, 0.35, 0.55)
	style.set_border_width_all(1)
	style.set_corner_radius_all(8)
	style.shadow_color = Color(0, 0, 0, 0.5)
	style.shadow_size = 18
	return style


func _on_back_pressed() -> void:
	SettingsManager.save()
	closed.emit()
