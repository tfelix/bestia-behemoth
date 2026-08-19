class_name MapView
extends Control

## Draws a window onto the world map, out of tiles fetched by a [MapSource].
##
## Used twice: as the minimap, following the player at a fixed close zoom, and as the full overlay, panned
## and zoomed by hand. The difference is entirely in [member interactive] and [member follow_player] - the
## drawing is one thing, because a minimap is a map view that happens to be small.
##
## [b]Zoom is discrete.[/b] A level means a fixed metres-per-pixel, so a tile is always drawn at its native
## 256 pixels and the map stays crisp. Zooming changes which level is fetched rather than scaling what is
## already here, which is what keeps hand-drawn line work from turning to mush.
##
## [b]Uncharted ground is this view's own background.[/b] The server answers 404 for a tile the player has
## charted none of, and leaves the rest transparent where their charts stop - so the fog is simply whatever is
## painted underneath, and painting it is this control's job. There is no separate fog layer beyond that,
## because a mask composited on the client would mean the client had been sent the picture underneath it.

const _TILE := 256

## Ground the player holds no chart of.
##
## Opaque, and that is the whole point rather than a styling choice: behind this control is the 3D world, so a
## translucent one shows the player the very ground their charts are meant to be withholding. Drawn here and
## not left to the hosting panel so that both views get it, and so the fog cannot be lost by restyling a
## window.
const _FOG := Color(0.11, 0.095, 0.08)

## Metres per pixel is 2^level, so 0 is one metre to the pixel and 9 is 512.
@export var level: int = 2

## Whether the wheel zooms and dragging pans. False for the minimap, which follows the player instead.
@export var interactive: bool = false

## Whether the centre tracks the player. True for the minimap; the overlay sets it once when it opens.
@export var follow_player: bool = false

## Where the player's own marker is drawn, and what [member follow_player] follows.
var entity_manager: Node = null

var source: MapSource = null

## Centre of the view in world metres.
var centre := Vector2.ZERO

var _min_level: int = 0
var _max_level: int = 9
var _metres_per_voxel: float = 1.0
var _dragging := false


func _ready() -> void:
	clip_contents = true


func setup(map_source: MapSource, entities: Node) -> void:
	source = map_source
	entity_manager = entities

	source.tile_ready.connect(func(_key: String, _texture: Texture2D) -> void: queue_redraw())
	source.tile_absent.connect(func(_key: String) -> void: queue_redraw())
	source.meta_ready.connect(_on_meta_ready)

	if not source.meta.is_empty():
		_on_meta_ready(source.meta)


func _on_meta_ready(meta: Dictionary) -> void:
	_min_level = int(meta.get("minLevel", 0))
	_max_level = int(meta.get("maxLevel", 9))
	_metres_per_voxel = float(meta.get("metresPerVoxel", 1.0))
	level = clampi(level, _min_level, _max_level)
	queue_redraw()


## Jumps to a zoom level, clamped to the ladder this world actually has.
##
## What a caller opening a view should use rather than assigning [member level] itself: how many levels exist
## above the finest depends on the world's size, so a fixed number is a request and not a fact.
func go_to_level(to_level: int) -> void:
	level = clampi(to_level, _min_level, _max_level)
	queue_redraw()


## Centres on the player, if there is one to centre on.
func centre_on_player() -> void:
	var at: Variant = _player_metres()
	if at != null:
		centre = at
		queue_redraw()


func _process(_delta: float) -> void:
	if follow_player and visible:
		centre_on_player()


func _draw() -> void:
	# Before the early return, not after it: a view with nothing to draw yet still has to cover the world behind
	# it, or the map spends its first frames as a window onto the terrain.
	draw_rect(Rect2(Vector2.ZERO, size), _FOG)

	if source == null or source.meta.is_empty():
		return

	var mpp := _metres_per_pixel()
	var span := _TILE * mpp
	var half := size * 0.5

	var west := centre.x - half.x * mpp
	var east := centre.x + half.x * mpp
	var south := centre.y - half.y * mpp
	var north := centre.y + half.y * mpp

	for ty in range(floori(south / span), floori(north / span) + 1):
		for tx in range(floori(west / span), floori(east / span) + 1):
			var key := MapSource.key_of(level, tx, ty)
			var texture := source.cached(key)
			if texture == null:
				source.want(level, tx, ty)
				continue

			# The tile's north-west corner: world y grows north and screen y grows down, so the *top* edge
			# of a tile is its ty+1 boundary.
			var corner := _to_screen(Vector2(tx * span, (ty + 1) * span))
			draw_texture_rect(texture, Rect2(corner, Vector2(_TILE, _TILE)), false)

	_draw_player()


func _draw_player() -> void:
	var at: Variant = _player_metres()
	if at == null:
		return

	var screen := _to_screen(at)
	if not Rect2(Vector2.ZERO, size).has_point(screen):
		return

	draw_circle(screen, 4.0, Color(0.15, 0.11, 0.08))
	draw_circle(screen, 2.5, Color(1.0, 0.86, 0.35))


func _gui_input(event: InputEvent) -> void:
	if not interactive:
		return

	if event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_LEFT:
			_dragging = event.pressed
			accept_event()

		# The camera's own zoom actions rather than the wheel buttons directly, so the map and the world behind
		# it can never disagree about which way the wheel means closer.
		#
		# Accepted whether or not the level moved, and that is the part that matters: Godot lets a wheel event a
		# [method Control._gui_input] did not accept carry on to [method Node._unhandled_input], where the spring
		# arm is listening - so without this the map zooms and the camera underneath it zooms with it.
		elif event.is_action_pressed("camera_zoom_in"):
			_zoom(-1, event.position)
			accept_event()
		elif event.is_action_pressed("camera_zoom_out"):
			_zoom(1, event.position)
			accept_event()

	elif event is InputEventMouseMotion and _dragging:
		var mpp := _metres_per_pixel()
		centre.x -= event.relative.x * mpp
		centre.y += event.relative.y * mpp
		follow_player = false
		queue_redraw()
		accept_event()


## Zooms a step, keeping the world point under the cursor where it is - so zooming follows what the player
## was looking at rather than drifting towards the middle.
func _zoom(steps: int, at: Vector2) -> void:
	var next := clampi(level + steps, _min_level, _max_level)
	if next == level:
		return

	var anchor := _to_world(at)
	level = next
	follow_player = false

	var mpp := _metres_per_pixel()
	centre.x = anchor.x - (at.x - size.x * 0.5) * mpp
	centre.y = anchor.y + (at.y - size.y * 0.5) * mpp
	queue_redraw()


func _metres_per_pixel() -> float:
	return pow(2.0, level)


func _to_screen(world: Vector2) -> Vector2:
	var mpp := _metres_per_pixel()
	return Vector2(
		(world.x - centre.x) / mpp + size.x * 0.5,
		size.y * 0.5 - (world.y - centre.y) / mpp
	)


func _to_world(screen: Vector2) -> Vector2:
	var mpp := _metres_per_pixel()
	return Vector2(
		centre.x + (screen.x - size.x * 0.5) * mpp,
		centre.y + (size.y * 0.5 - screen.y) * mpp
	)


## The player's position in world metres, or null when there is no player entity yet.
##
## Godot is Y-up and the server is Z-up, so the map's northing is the entity's [code]z[/code] - the same
## swap [code]Vec3Convert[/code] performs in the other direction. Positions are in voxels, which is why
## the world's metres-per-voxel is read from [code]/meta[/code] rather than assumed to be one.
func _player_metres() -> Variant:
	if entity_manager == null:
		return null

	var player = entity_manager.get_owned_entity()
	if player == null:
		return null

	return Vector2(player.global_position.x, player.global_position.z) * _metres_per_voxel
