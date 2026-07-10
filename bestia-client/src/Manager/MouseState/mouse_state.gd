extends RefCounted
class_name MouseState

## Base class for a MouseManager mode. Each mode (default, item targeting,
## skill targeting) owns its own click/hover/frame behavior so mode-specific
## setup and cleanup (cursor texture, 3D indicators) can't leak into the
## others - see MouseManager.change_state().

## mgr is the MouseManager autoload; left untyped since autoload scripts
## can't declare a class_name identical to their singleton name.
@warning_ignore("unused_parameter")
func enter(mgr) -> void:
	pass


@warning_ignore("unused_parameter")
func exit(mgr) -> void:
	pass


@warning_ignore("unused_parameter")
func process_state(mgr, delta: float) -> void:
	pass


@warning_ignore("unused_parameter")
func handle_object_clicked(mgr, object: Node3D, event: InputEvent, click_position: Vector3) -> void:
	pass


@warning_ignore("unused_parameter")
func handle_object_hover(mgr, object: Node3D, entered: bool) -> void:
	pass


@warning_ignore("unused_parameter")
func handle_ground_clicked(mgr, click_position: Vector3, event: InputEvent) -> void:
	pass


@warning_ignore("unused_parameter")
func handle_right_click(mgr, screen_position: Vector2) -> void:
	pass


## Cancel gesture for this mode (Escape or a "clean" right-click while
## targeting). Default states have nothing to cancel; targeting states
## override this to free their indicator and return to default.
@warning_ignore("unused_parameter")
func handle_cancel(mgr) -> void:
	pass
