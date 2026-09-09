class_name SceneFade extends Control
## The black curtain SceneManager draws over a scene swap.
##
## Only a fade. Anything the player should read while waiting - a caption, a percentage - belongs to
## [LoadingScreen], which SceneManager raises on top of this one.

@onready var anim_player: AnimationPlayer = $AnimationPlayer


func start_transition() -> void:
	anim_player.play("fade_to_black")


## Called by SceneManager to play the outro once the content is loaded.
func finish_transition() -> void:
	anim_player.play_backwards("fade_to_black")
	await anim_player.animation_finished
	queue_free()
