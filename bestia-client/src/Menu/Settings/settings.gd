extends Control

@onready var _volumes: GridContainer = %Volumes


func _ready() -> void:
	for bus in Audio.BUSES:
		_add_volume_row(bus)


func _add_volume_row(bus: StringName) -> void:
	var label := Label.new()
	label.text = String(bus)
	_volumes.add_child(label)

	var slider := HSlider.new()
	slider.min_value = 0.0
	slider.max_value = 1.0
	slider.step = 0.01
	slider.value = Audio.volume_of(bus)
	slider.custom_minimum_size = Vector2(240, 0)
	slider.value_changed.connect(func(value: float): Audio.set_volume(bus, value))
	slider.drag_ended.connect(func(_changed: bool): SettingsManager.save())
	_volumes.add_child(slider)


func _on_button_pressed() -> void:
	# Saved here as well, because a click on the track moves the slider without a drag to end.
	SettingsManager.save()
	SceneManager.goto_scene("res://Menu/Main/Main.tscn")
