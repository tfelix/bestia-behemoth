extends Node

## Owns the audio buses: their volumes, and the music that plays on one of them.
##
## The buses come from `res://default_bus_layout.tres`. Each has a `<bus>_volume` setting in
## [ClientSettings], stored linear so a slider maps onto it directly.

const MASTER := &"Master"
const MUSIC := &"Music"
const AMBIENCE := &"Ambience"
const EFFECTS := &"Effects"
const INTERFACE := &"Interface"

const BUSES: Array[StringName] = [MASTER, MUSIC, AMBIENCE, EFFECTS, INTERFACE]


var music: MusicPlayer


func _ready() -> void:
	for bus in BUSES:
		_apply_volume(bus, volume_of(bus))

	music = MusicPlayer.new()
	music.name = "Music"
	add_child(music)


func volume_of(bus: StringName) -> float:
	return SettingsManager.get_instance().get(_setting_of(bus))


## Sets a bus volume, linear from 0 (silent) to 1 (as mixed). Not saved: a slider calls this on every step,
## so the caller saves with [method SettingsManager.save] once the player lets go.
func set_volume(bus: StringName, linear: float) -> void:
	SettingsManager.get_instance().set(_setting_of(bus), clampf(linear, 0.0, 1.0))
	_apply_volume(bus, linear)


func _apply_volume(bus: StringName, linear: float) -> void:
	var index := AudioServer.get_bus_index(bus)
	if index < 0:
		push_error("Audio: no bus named %s in the bus layout" % bus)
		return

	AudioServer.set_bus_volume_db(index, linear_to_db(clampf(linear, 0.0, 1.0)))


func _setting_of(bus: StringName) -> String:
	return "%s_volume" % String(bus).to_lower()
