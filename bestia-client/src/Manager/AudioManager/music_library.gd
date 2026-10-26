extends Resource
class_name MusicLibrary

## The tracks the [MusicPlayer] plays. An empty entry plays silence.

## The menu picks at random from these.
@export var menu_tracks: Array[AudioStream] = []

## The world plays whichever of these matches the time of day.
@export var day_track: AudioStream
@export var night_track: AudioStream


func tracks_for(context: StringName) -> Array[AudioStream]:
	match context:
		MusicPlayer.MENU:
			return menu_tracks
	return []
