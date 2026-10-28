extends Resource
class_name EmoteClips

## The AnimationPlayer clips one body plays for an emote. A once emote fills only [member enter]; one that lasts
## also loops [member loop] until it is ended and then plays [member exit] on its way back to idle.

## Played first. For a once emote the whole emote.
@export var enter: StringName
## Played after [member enter] for as long as the emote lasts, looped whatever its import settings say.
@export var loop: StringName
## Played when the emote ends, before the body goes back to idle. Empty to go straight back.
@export var exit: StringName


func start_on(player: AnimationPlayer) -> void:
	player.clear_queue()
	player.play(enter)
	if loop.is_empty():
		return
	# Set here rather than in the model's import settings, so naming a clip as the loop is all an emote needs.
	var looped := player.get_animation(loop)
	if looped != null:
		looped.loop_mode = Animation.LOOP_LINEAR
	player.queue(loop)


## Back to [param idle_clip]: through [member exit] if there is one, and a once emote still playing is let finish
## rather than cut off - the server ends it when its hold runs out, which is about the clip's length, not exactly it.
## A looping [member enter] never finishes, so that one is cut: a bestia's Sleep is its whole lie-down.
func end_on(player: AnimationPlayer, idle_clip: StringName) -> void:
	player.clear_queue()
	if not exit.is_empty():
		player.play(exit)
		player.queue(idle_clip)
	elif _finishes(player) and player.is_playing() and player.current_animation == enter:
		player.queue(idle_clip)
	else:
		player.play(idle_clip)


func _finishes(player: AnimationPlayer) -> bool:
	var clip := player.get_animation(enter)
	return loop.is_empty() and clip != null and clip.loop_mode == Animation.LOOP_NONE
