extends MouseState
class_name MouseStateItemTargeting

## Active while a scripted item is waiting for the player to click something.
## Every discrete click (ground or object) is forwarded to the item's own
## ItemUse script instead of running the default click behavior.

var item: ItemResource
var item_use: ItemUse
var cursor_texture: Texture2D


func enter(mgr) -> void:
	mgr.set_os_cursor(cursor_texture)


func exit(mgr) -> void:
	mgr.reset_os_cursor()


func handle_object_clicked(mgr, object: Node3D, event: InputEvent, click_position: Vector3) -> void:
	_try_confirm(mgr, event, click_position, object)


func handle_ground_clicked(mgr, click_position: Vector3, event: InputEvent) -> void:
	_try_confirm(mgr, event, click_position, null)


func handle_cancel(mgr) -> void:
	if item_use:
		item_use.on_targeting_cancelled(item)
	mgr.enter_default()


func _try_confirm(mgr, event: InputEvent, click_position: Vector3, target: Node3D) -> void:
	if not event.is_action_pressed("normal_action"):
		return
	if item_use == null:
		mgr.enter_default()
		return

	var click_info: Dictionary = {"position": click_position, "target": target}
	var consumed: bool = item_use.on_targeting_click(item, click_info)
	if consumed:
		mgr.enter_default()
