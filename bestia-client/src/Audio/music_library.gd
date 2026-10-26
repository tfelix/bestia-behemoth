extends Resource
class_name MusicLibrary

## The tracks the [MusicPlayer] picks from, per context. An empty list plays silence.

@export var menu_tracks: Array[AudioStream] = []
@export var world_tracks: Array[AudioStream] = []


func tracks_for(context: StringName) -> Array[AudioStream]:
	match context:
		MusicPlayer.MENU:
			return menu_tracks
		MusicPlayer.WORLD:
			return world_tracks
	return []
