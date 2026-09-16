@abstract
class_name MouseState
extends RefCounted


## Base class for a MouseManager mode. Each mode (default, item targeting,
## skill targeting) owns its own click/hover/frame behavior so mode-specific
## setup and cleanup (cursor texture, 3D indicators) can't leak into the
## others - see MouseManager.change_state().

## Every state sets the cursor here, so none has to clear it on the way out - change_state runs exit()
## then enter(), and a state that cleared would only flash the OS arrow in between.
@warning_ignore("unused_parameter")
func enter(mgr: MouseManager) -> void:
	pass


@warning_ignore("unused_parameter")
func exit(mgr: MouseManager) -> void:
	pass


@warning_ignore("unused_parameter")
func process_state(mgr: MouseManager, delta: float) -> void:
	pass


@warning_ignore("unused_parameter")
func handle_object_clicked(mgr: MouseManager, object: Node3D, event: InputEvent, click_position: Vector3) -> void:
	pass


@warning_ignore("unused_parameter")
func handle_object_hover(mgr: MouseManager, object: Node3D, entered: bool) -> void:
	pass


@warning_ignore("unused_parameter")
func handle_ground_input_event(mgr: MouseManager, click_position: Vector3, event: InputEvent) -> void:
	pass


@warning_ignore("unused_parameter")
func handle_right_click(mgr: MouseManager, screen_position: Vector2) -> void:
	pass


## Cancel gesture for this mode (Escape or a "clean" right-click while
## targeting). Default states have nothing to cancel; targeting states
## override this to free their indicator and return to default.
@warning_ignore("unused_parameter")
func handle_cancel(mgr: MouseManager) -> void:
	pass


## Any other unhandled input, for a mode that reads more than clicks - turning a placement ghost, say.
## Clicks do not arrive here: those come through the physics-picking path above.
@warning_ignore("unused_parameter")
func handle_input(mgr: MouseManager, event: InputEvent) -> void:
	pass
