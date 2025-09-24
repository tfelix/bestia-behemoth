class_name LoadingScreen extends Control

@onready var anim_player: AnimationPlayer = $AnimationPlayer
@onready var loader_ui: Control = $LoaderUi


func start_transition() -> void:
	anim_player.play("fade_to_black")
	await anim_player.animation_finished
	loader_ui.visible = true


# called by SceneManger to play the outro to the transition once the content is loaded
func finish_transition() -> void:
	loader_ui.visible = false
	anim_player.play_backwards("fade_to_black")
	# once this final animation plays, we can free this scene
	await anim_player.animation_finished
	queue_free()
