extends MouseState
class_name MouseStateItemTargeting

## Active while a scripted item is waiting for the player to click something.
## Every discrete click (ground or object) is forwarded to the item's own
## ItemUse script instead of running the default click behavior.
##
## An item that is [i]placed[/i] rather than aimed can hand over a [member ghost] - the art of whatever it
## puts down. It is snapped to tile centres and turned with the mouse wheel, and the facing it ends on
## travels in click_info, because where a thing goes and which way it faces are one decision.
##
## An item use with a [code]snap_ghost(mgr, yaw)[/code] method places the ghost itself, for a building piece that
## snaps onto other pieces rather than onto the ground. It answers [code]{"tile": Vector3, "valid": bool}[/code] or
## null, and that tile is what a click sends.

## How far the wheel turns the ghost. Quarter turns, because everything placed is drawn on a square grid
## and nothing so far reads as being at an angle to it.
const _YAW_STEP := PI / 2.0

var item: ItemResource
var item_use: ItemUse
var cursor_texture: Texture2D

## Preview node for a placed item, or null for one that just needs a click. Owned by this state.
var ghost: Node3D = null

var _yaw: float = 0.0
var _last_ground_hit = null
var _snapped = null

const _VALID_TINT := Color(0.4, 0.9, 0.4, 0.5)
const _REFUSED_TINT := Color(0.95, 0.3, 0.3, 0.5)

## Where the refusal text floats above the tile, in metres: above a storey-high ghost.
const _REFUSAL_LABEL_HEIGHT := 3.5

## Construction sites farther than this cannot be too close: a little past BuildingPlaces.WildSpacingMetres.
const _SITE_REACH := 12.0

## The verdict is worked out again this often on the same tile, since others build and tear down nearby.
const _RECHECK_MS := 500

## Whether the ghost is a kit's, which the building rules apply to.
var _checks_placement := false

## What the ghost shows now: an OpError name, or "" where it may go.
var _shown_refusal: String = ""

var _checked_tile = null
var _checked_at_ms := 0

var _refusal_label: Label3D = null
var _refused_overlay: StandardMaterial3D = null

## The scaffold colour of construction.gdshader where the kit may not go.
const _REFUSED_GHOST := Color(0.95, 0.22, 0.18, 0.5)


## Whether this is a placement rather than a plain aim. The camera asks, so that the wheel turns the ghost
## instead of zooming - see MouseManager.is_placing.
func has_ghost() -> bool:
	return ghost != null


func enter(mgr: MouseManager) -> void:
	# An item that wants its own art gets it; the rest aim with the plain pointer.
	if cursor_texture:
		mgr.set_os_cursor(cursor_texture)
	else:
		mgr.set_cursor_for_action(DefaultAction.Kind.NONE)

	if ghost:
		mgr.add_child(ghost)
		ghost.visible = false
		_start_placement_check()


func exit(_mgr: MouseManager) -> void:
	# The cursor is not cleared here: every state sets its own in enter(), and change_state runs that
	# straight after this - so clearing would only be a flicker of the OS arrow in between.
	if ghost:
		ghost.queue_free()
		ghost = null


func process_state(mgr: MouseManager, _delta: float) -> void:
	if ghost == null:
		return

	if item_use != null and item_use.has_method("snap_ghost"):
		_place_snapped_ghost(mgr)
		return

	# Physics-picking signals only fire on discrete events, so tracking the cursor needs its own per-frame
	# raycast - the same reason MouseStateSkillTargeting has one.
	_last_ground_hit = mgr.get_floor_hit_at_mouse()

	if _last_ground_hit == null:
		ghost.visible = false
		return

	ghost.visible = true
	# The centre of the tile it will actually be placed on, not the raw hit, so what the player sees is
	# where it lands. See TileSpace.
	var tile := TileSpace.world_to_tile(_last_ground_hit)
	var centre := TileSpace.tile_centre(tile)
	ghost.global_position = Vector3(centre.x, _last_ground_hit.y, centre.z)
	ghost.rotation.y = _yaw
	_show_placement_verdict(tile)
	_tint_ghost(ghost, _shown_refusal != "")


func _place_snapped_ghost(mgr: MouseManager) -> void:
	_snapped = item_use.snap_ghost(mgr, _yaw)
	if _snapped == null:
		ghost.visible = false
		return

	ghost.visible = true
	# The ghost is drawn around the bottom centre of the piece's first cell, which is what turns with it.
	ghost.global_position = _snapped["tile"] + Vector3(0.5, 0.0, 0.5)
	ghost.rotation.y = _yaw
	_show_placement_verdict(_snapped["tile"])
	var may_build: bool = _snapped["valid"] and _shown_refusal == ""
	if ghost.has_meta("piece_material"):
		ghost.get_meta("piece_material").albedo_color = _VALID_TINT if may_build else _REFUSED_TINT


func handle_input(_mgr: MouseManager, event: InputEvent) -> void:
	if ghost == null:
		return

	# The zoom actions rather than the raw buttons, so which buttons these are stays in the input map.
	# camera_spring_arm.gd skips its own zoom while a ghost is held - see MouseManager.is_placing.
	if event.is_action_pressed("camera_zoom_in"):
		_yaw = fposmod(_yaw + _YAW_STEP, TAU)
	elif event.is_action_pressed("camera_zoom_out"):
		_yaw = fposmod(_yaw - _YAW_STEP, TAU)


func handle_object_clicked(mgr: MouseManager, object: Node3D, event: InputEvent, click_position: Vector3) -> void:
	_try_confirm(mgr, event, click_position, object)


func handle_ground_input_event(mgr: MouseManager, click_position: Vector3, event: InputEvent) -> void:
	_try_confirm(mgr, event, click_position, null)


func handle_cancel(mgr: MouseManager) -> void:
	if item_use:
		item_use.on_targeting_cancelled(item)
	mgr.enter_default()


func _try_confirm(mgr: MouseManager, event: InputEvent, click_position: Vector3, target: Node3D) -> void:
	if not event.is_action_pressed("normal_action"):
		return
	if item_use == null:
		mgr.enter_default()
		return

	var click_info: Dictionary = {
		"position": click_position,
		"target": target,
		"tile": TileSpace.world_to_tile(click_position),
		"yaw": _yaw,
	}
	# The server would refuse it and say why in chat; the label already says so, and the ghost stays in hand.
	if _shown_refusal != "":
		return
	if item_use.has_method("snap_ghost"):
		if _snapped == null:
			return
		click_info["tile"] = _snapped["tile"]
		click_info["valid"] = _snapped["valid"]
	var consumed: bool = item_use.on_targeting_click(item, click_info)
	if consumed:
		mgr.enter_default()


func _start_placement_check() -> void:
	_shown_refusal = ""
	_checked_tile = null
	_checks_placement = item_use is StructureKitUse and item_use.kind >= 0
	if not _checks_placement:
		return

	_refused_overlay = StandardMaterial3D.new()
	_refused_overlay.albedo_color = _REFUSED_TINT
	_refused_overlay.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	_refused_overlay.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED

	_refusal_label = Label3D.new()
	_refusal_label.billboard = BaseMaterial3D.BILLBOARD_ENABLED
	_refusal_label.no_depth_test = true
	_refusal_label.fixed_size = true
	_refusal_label.pixel_size = 0.0012
	_refusal_label.font_size = 34
	_refusal_label.outline_size = 10
	_refusal_label.modulate = Color(1.0, 0.85, 0.85)
	_refusal_label.outline_modulate = Color(0.35, 0.0, 0.0)
	_refusal_label.width = 560.0
	_refusal_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_refusal_label.position = Vector3(0.0, _REFUSAL_LABEL_HEIGHT, 0.0)
	_refusal_label.visible = false
	ghost.add_child(_refusal_label)


func _show_placement_verdict(tile: Vector3) -> void:
	if not _checks_placement:
		return

	var now := Time.get_ticks_msec()
	if tile == _checked_tile and now - _checked_at_ms < _RECHECK_MS:
		return
	_checked_tile = tile
	_checked_at_ms = now
	_shown_refusal = _placement_refusal(tile)

	_refusal_label.visible = _shown_refusal != ""
	if _refusal_label.visible:
		_refusal_label.text = tr("ERROR_%s" % _shown_refusal.to_upper())


## Every mesh of the ghost, since a station's art may be several meshes deep. A construction ghost turns its
## own scaffold red; any other mesh gets a red overlay.
func _tint_ghost(node: Node, refused: bool) -> void:
	if node is GeometryInstance3D and not node is Label3D:
		var scaffold := node.material_override as ShaderMaterial
		if scaffold != null and _has_uniform(scaffold, "ghost_color"):
			# null drops the override, so the shader's own green comes back.
			scaffold.set_shader_parameter("ghost_color", _REFUSED_GHOST if refused else null)
		else:
			node.material_overlay = _refused_overlay if refused else null
	for child in node.get_children():
		_tint_ghost(child, refused)


func _has_uniform(material: ShaderMaterial, uniform_name: String) -> bool:
	return material.shader != null and material.shader.get_shader_uniform_list().any(
		func(uniform: Dictionary) -> bool: return uniform["name"] == uniform_name
	)


## The building rules applied here from what this client holds, so the ghost needs no round trip. The server
## still decides when the kit is used.
func _placement_refusal(tile: Vector3) -> String:
	var entities := EntityManager.get_instance()
	var chunk_stream = ConnectionManager.chunk_stream
	if entities == null or chunk_stream == null:
		return ""

	var master: Entity = entities.get_entity(entities.get_owned_master_entity_id())
	var place: PlaceComponentSMSG = master.get_place() if master != null else null
	var sites := entities.construction_sites_near(tile, _SITE_REACH)

	return chunk_stream.PlacementRefusal(item_use.kind, tile, entities.get_owned_master_id(), place, sites)
