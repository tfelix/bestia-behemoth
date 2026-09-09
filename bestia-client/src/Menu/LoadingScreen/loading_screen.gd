class_name LoadingScreen extends CanvasLayer
## What the player looks at while something they asked for is still arriving.
##
## Presentation only: it is told a caption and a figure and knows nothing about what is being waited
## for. SceneManager owns it and decides when it appears; see [SceneFade] for the plain curtain that
## covers a scene swap underneath it.

## Emitted once the screen is actually gone, however it went: the reveal finishing, or an
## outright dismissal. What the player was waiting for is on screen from here on.
signal dismissed()

## Matches SceneFade's fade_to_black, so the handover from black to this reads as one movement.
const FADE_IN_SECONDS := 0.15

## Longer on the way out, because this one is revealing what the player has been waiting for.
const REVEAL_SECONDS := 0.4

@onready var _fade: Control = %Fade
@onready var _bar: ProgressBar = %Progress
@onready var _caption: Label = %Caption

var _tween: Tween = null


func _ready() -> void:
	hide()
	set_process(false)


## Raises the screen with no figure yet, for a wait whose length is not knowable.
##
## Always a fade, including the handover from [SceneFade]'s black. Appearing at full opacity the
## instant that fade lands means the black is never actually drawn - the screen replaces it in the
## same frame - so the transition reads as the outgoing scene popping into artwork.
func appear(caption: String) -> void:
	_caption.text = caption
	_bar.indeterminate = true
	_bar.show_percentage = false
	_bar.value = 0.0

	_cancel_tween()
	_fade.modulate.a = 0.0
	show()
	set_process(true)

	_tween = create_tween()
	_tween.tween_property(_fade, "modulate:a", 1.0, FADE_IN_SECONDS)


## A figure between 0 and 1. The first call switches the bar from indeterminate to a percentage.
func set_progress(value: float, caption: String) -> void:
	_bar.indeterminate = false
	_bar.show_percentage = true
	_bar.value = clampf(value, 0.0, 1.0) * 100.0
	_caption.text = caption


## Fades out, revealing whatever is behind. Processing stops here: the wait is over.
func reveal() -> void:
	if not visible:
		return

	set_process(false)
	_cancel_tween()
	_tween = create_tween()
	_tween.tween_property(_fade, "modulate:a", 0.0, REVEAL_SECONDS)
	_tween.tween_callback(_finish_reveal)


## Hides at once. For the cases where there is nothing worth revealing - a dropped connection, say.
func dismiss() -> void:
	_cancel_tween()

	if not visible:
		return

	hide()
	set_process(false)
	_fade.modulate.a = 1.0

	dismissed.emit()


func _input(_event: InputEvent) -> void:
	# Everything, so a click behind the screen cannot walk the player or open a window.
	if visible:
		get_viewport().set_input_as_handled()


func _finish_reveal() -> void:
	_tween = null
	dismiss()


func _cancel_tween() -> void:
	if _tween == null:
		return

	_tween.kill()
	_tween = null
