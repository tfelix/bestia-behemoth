extends Node
class_name MusicPlayer

## Plays the music for the scene the player is in, crossfading when that changes.
##
## Scenes that are not listed in [constant CONTEXT_OF_SCENE] keep whatever is playing, so the settings
## and credits screens do not restart the menu music.
##
## In the world the music follows the time of day instead of a playlist: the day track while the sun is up,
## the night track while it is down, crossfaded as the world clock passes dawn and dusk.

const MENU := &"menu"
const WORLD := &"world"

const CONTEXT_OF_SCENE := {
	"res://Menu/Main/Main.tscn": MENU,
	"res://Game/Game.tscn": WORLD,
}

const LIBRARY_PATH := "res://Manager/AudioManager/music_library.tres"

## Daylight is a ramp through dawn and dusk. The track changes over once it has clearly crossed the middle,
## so a clock that wobbles around the half way point does not flip the music back and forth.
const _DAY_BEGINS := 0.55
const _NIGHT_BEGINS := 0.45

## Short enough not to drag on across a dusk, long enough not to sound like a cut.
@export var crossfade_seconds: float = 2.5

var _library: MusicLibrary
var _context: StringName = &""
var _is_day := true

# Two players, so the outgoing track can fade while the incoming one rises.
var _current: AudioStreamPlayer
var _fading: AudioStreamPlayer
var _fade: Tween


func _ready() -> void:
	_library = load(LIBRARY_PATH) as MusicLibrary
	if _library == null:
		push_error("MusicPlayer: could not load %s; playing no music" % LIBRARY_PATH)
		_library = MusicLibrary.new()
	_current = _new_player()
	_fading = _new_player()

	SceneManager.scene_changed.connect(_on_scene_changed)
	# The main scene is loaded by the engine rather than through SceneManager, so nothing announces it.
	_announce_main_scene.call_deferred()


func _process(_delta: float) -> void:
	if _context != WORLD:
		return

	_update_is_day()
	if _world_track() != _current.stream:
		_play_next()


func play_context(context: StringName) -> void:
	if context == _context:
		return

	_context = context
	_update_is_day()
	_play_next()


func _announce_main_scene() -> void:
	var scene := get_tree().current_scene
	if scene != null:
		_on_scene_changed(scene.scene_file_path)


func _on_scene_changed(path: String) -> void:
	if CONTEXT_OF_SCENE.has(path):
		play_context(CONTEXT_OF_SCENE[path])


func _update_is_day() -> void:
	var clock := ConnectionManager.world_clock
	if clock == null:
		return

	var daylight: float = clock.Daylight
	if daylight >= _DAY_BEGINS:
		_is_day = true
	elif daylight <= _NIGHT_BEGINS:
		_is_day = false


func _world_track() -> AudioStream:
	return _library.day_track if _is_day else _library.night_track


func _play_next() -> void:
	var next := _world_track() if _context == WORLD else _pick(_library.tracks_for(_context))

	var outgoing := _current
	_current = _fading
	_fading = outgoing

	if _fade != null:
		_fade.kill()
	_fade = create_tween().set_parallel()
	_fade.tween_property(_fading, "volume_linear", 0.0, crossfade_seconds)

	if next == null:
		_current.stop()
		return

	_current.stream = next
	_current.volume_linear = 0.0
	_current.play()
	_fade.tween_property(_current, "volume_linear", 1.0, crossfade_seconds)


## A random track, but not the one that just played when there is a choice.
func _pick(tracks: Array[AudioStream]) -> AudioStream:
	if tracks.is_empty():
		return null

	var candidates := tracks.filter(func(track): return track != _fading.stream)
	if candidates.is_empty():
		candidates = tracks

	return candidates.pick_random()


func _new_player() -> AudioStreamPlayer:
	var player := AudioStreamPlayer.new()
	player.bus = AudioManager.MUSIC
	player.finished.connect(_on_track_finished.bind(player))
	add_child(player)
	return player


func _on_track_finished(player: AudioStreamPlayer) -> void:
	if player == _current:
		_play_next()
