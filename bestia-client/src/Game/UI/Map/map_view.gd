class_name MapView
extends Control

## Draws a window onto the world map, out of tiles fetched by a [MapSource].
##
## Used twice: as the minimap, following the player at a fixed close zoom, and as the full overlay, panned
## and zoomed by hand. The difference is entirely in [member interactive] and [member follow_player] - the
## drawing is one thing, because a minimap is a map view that happens to be small.
##
## [b]Zoom is discrete.[/b] A level means a fixed metres-per-pixel, so a tile is always drawn at its native
## size and the map stays crisp. Zooming changes which level is fetched rather than scaling what is
## already here, which is what keeps hand-drawn line work from turning to mush.
##
## [b]Uncharted ground is this view's own background.[/b] The server answers 404 for a tile the player has
## charted none of, and leaves the rest transparent where their charts stop - so the fog is simply whatever is
## painted underneath, and painting it is this control's job. There is no separate fog layer beyond that,
## because a mask composited on the client would mean the client had been sent the picture underneath it.

## Ground the player holds no chart of.
##
## Opaque, and that is the whole point rather than a styling choice: behind this control is the 3D world, so a
## translucent one shows the player the very ground their charts are meant to be withholding. Drawn here and
## not left to the hosting panel so that both views get it, and so the fog cannot be lost by restyling a
## window.
const _FOG := Color(0.11, 0.095, 0.08)

## The player marker: a dark hull with a lighter one inside it, so it reads over any tile.
const _MARKER_OUTLINE := Color(0.15, 0.11, 0.08)
const _MARKER_FILL := Color(1.0, 0.86, 0.35)

## The arrow head, in pixels from the player's own position. The notch is what stops it reading as a plain
## triangle, which at this size is the difference between "facing" and "here".
const _ARROW_TIP := 6.0
const _ARROW_TAIL := 3.5
const _ARROW_HALF := 4.0
const _ARROW_NOTCH := 1.0

## The north mark. Warm rather than the marker's amber, so that the fixed thing and the moving thing are not
## the same colour at a glance.
const _NORTH_COLOUR := Color(1.0, 0.94, 0.78)
const _NORTH_FONT_SIZE := 11
const _NORTH_PIP_TIP := 2.0
const _NORTH_PIP_BASE := 9.0
const _NORTH_PIP_HALF := 4.0
const _NORTH_PIP_INSET := 1.5

## The tip is a point rather than an edge, so pulling it in by the plain inset would barely move it. This is
## the ratio that keeps the light triangle's apex clear of the dark one's.
const _NORTH_PIP_TIP_INSET_RATIO := 1.6

## Metres per pixel is 2^level, so 0 is one metre to the pixel and 9 is 512.
@export var level: int = 2

## Whether the wheel zooms and dragging pans. False for the minimap, which follows the player instead.
@export var interactive: bool = false

## Whether the centre tracks the player. True for the minimap; the overlay sets it once when it opens.
@export var follow_player: bool = false

## Whether to mark which way north is. True for the minimap, false for the overlay.
##
## Not because the overlay is any less north-up - it is the same drawing - but because it is large enough to
## carry its own labelled ground, while the minimap is a 168 pixel window with nothing in it to say which way
## it is held.
@export var show_north: bool = false

## Where the player's own marker is drawn, and what [member follow_player] follows.
var entity_manager: Node = null

var source: MapSource = null

## Centre of the view in world metres.
var centre := Vector2.ZERO

var _min_level: int = 0
var _max_level: int = 9
var _metres_per_voxel: float = 1.0

## Tile edge in pixels, from [code]/meta[/code]. Read rather than assumed: it sets both the metres a tile
## spans and the rectangle one is drawn into, so a server that changed it would otherwise misplace every tile
## rather than fail.
var _tile_pixels: int = 256

## World extent in metres, or zero before [code]/meta[/code] answers. Zero means "do not clamp" - an
## unclamped map is a worse map, but a map, and a wrong extent would be an empty one.
var _world_width: float = 0.0
var _world_height: float = 0.0

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
	_tile_pixels = maxi(int(meta.get("tileSize", 256)), 1)
	_world_width = float(meta.get("worldWidthMetres", 0.0))
	_world_height = float(meta.get("worldHeightMetres", 0.0))
	level = clampi(level, _min_level, _max_level)
	_clamp_centre()
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
	# In the tree, not merely this control's own flag: the minimap's panel hides itself when the player holds
	# no chart, and its view underneath stays `visible` - so following would redraw a widget nobody can see.
	if follow_player and is_visible_in_tree():
		centre_on_player()


func _draw() -> void:
	# Before the early return, not after it: a view with nothing to draw yet still has to cover the world behind
	# it, or the map spends its first frames as a window onto the terrain.
	draw_rect(Rect2(Vector2.ZERO, size), _FOG)

	if source == null or source.meta.is_empty():
		return

	var mpp := _metres_per_pixel()
	var span := _tile_pixels * mpp
	var half := size * 0.5

	var west := centre.x - half.x * mpp
	var east := centre.x + half.x * mpp
	var south := centre.y - half.y * mpp
	var north := centre.y + half.y * mpp

	# Only the tiles the world actually has. The world wraps, and the server folds an out-of-world tile back
	# onto a real one - so without this the map draws the same ground once per world width, which at the
	# coarse levels fills the panel with copies of a chart a few pixels across.
	var last_x := _last_tile(_world_width, span)
	var last_y := _last_tile(_world_height, span)

	for ty in range(maxi(floori(south / span), 0), mini(floori(north / span), last_y) + 1):
		for tx in range(maxi(floori(west / span), 0), mini(floori(east / span), last_x) + 1):
			var key := MapSource.key_of(level, tx, ty)
			var texture := source.cached(key)
			if texture == null:
				source.want(level, tx, ty)
				continue

			# The tile's north-west corner: world y grows north and screen y grows down, so the *top* edge
			# of a tile is its ty+1 boundary.
			var corner := _to_screen(Vector2(tx * span, (ty + 1) * span))
			draw_texture_rect(texture, Rect2(corner, Vector2(_tile_pixels, _tile_pixels)), false)

	_draw_player()
	_draw_north()


func _draw_player() -> void:
	var at: Variant = _player_metres()
	if at == null:
		return

	var screen := _to_screen(at)
	if not Rect2(Vector2.ZERO, size).has_point(screen):
		return

	var forward := _player_forward()

	# A dot until the character has turned at all. An arrow needs a direction to be honest about, and a
	# default one would point north on every spawn - which reads as a heading rather than as the absence of
	# one.
	if forward.is_zero_approx():
		draw_circle(screen, 4.0, _MARKER_OUTLINE)
		draw_circle(screen, 2.5, _MARKER_FILL)
		return

	# Dark under light, the same two passes the dot used: a chart tile can be any brightness, and a
	# single-colour marker disappears into half of them.
	_draw_arrow(screen, forward, 1.0, _MARKER_OUTLINE)
	_draw_arrow(screen, forward, 0.62, _MARKER_FILL)


## An arrow head pointing along [param forward], scaled about [param at] rather than about its own centroid.
##
## Scaling about the point is what lets the light pass sit inside the dark one while both stay centred on the
## player's actual position - the same trick the two concentric circles above play.
func _draw_arrow(at: Vector2, forward: Vector2, scale: float, colour: Color) -> void:
	var f := forward * scale
	var r := Vector2(-forward.y, forward.x) * scale

	draw_colored_polygon(PackedVector2Array([
		at + f * _ARROW_TIP,
		at - f * _ARROW_TAIL + r * _ARROW_HALF,
		at - f * _ARROW_NOTCH,
		at - f * _ARROW_TAIL - r * _ARROW_HALF,
	]), colour)


## Marks the top of the view as north.
##
## Static geometry, because this view is north-up and has no way not to be - see [method _to_screen], which is
## a translate and a flip with no rotation in it anywhere. If that ever stops being true, this is one of the
## two places that has to learn about it; the other is the player arrow.
func _draw_north() -> void:
	if not show_north:
		return

	var centre_x := size.x * 0.5

	# Two passes, dark under light, for the reason the player marker has two: the tile underneath can be any
	# brightness.
	_draw_north_pip(centre_x, 0.0, _MARKER_OUTLINE)
	_draw_north_pip(centre_x, _NORTH_PIP_INSET, _NORTH_COLOUR)

	# draw_string positions the *baseline*, not the top left, so the ascent has to be added on and the width
	# measured to centre it. The project has no Theme resource, hence the default font.
	var font := get_theme_default_font()
	var width := font.get_string_size("N", HORIZONTAL_ALIGNMENT_LEFT, -1, _NORTH_FONT_SIZE).x
	var baseline := _NORTH_PIP_BASE + 1.0 + font.get_ascent(_NORTH_FONT_SIZE)

	draw_string(font, Vector2(centre_x - width * 0.5, baseline), "N",
		HORIZONTAL_ALIGNMENT_LEFT, -1, _NORTH_FONT_SIZE, _NORTH_COLOUR)


## The pip, pulled [param inset] pixels in from every edge.
##
## Inset rather than scaled, unlike the player arrow above. The arrow is scaled about the player's own
## position, which is inside it; this triangle has no such point to scale about, and scaling it about its tip
## left the two passes sharing that vertex - so the light one had no dark edge exactly where it needed one.
func _draw_north_pip(centre_x: float, inset: float, colour: Color) -> void:
	draw_colored_polygon(PackedVector2Array([
		Vector2(centre_x, _NORTH_PIP_TIP + inset * _NORTH_PIP_TIP_INSET_RATIO),
		Vector2(centre_x - _NORTH_PIP_HALF + inset, _NORTH_PIP_BASE - inset),
		Vector2(centre_x + _NORTH_PIP_HALF - inset, _NORTH_PIP_BASE - inset),
	]), colour)


## Which way the player faces in this view's pixels, or [constant Vector2.ZERO] when there is nothing to ask.
##
## The view's own y flip is the whole conversion. [method _to_screen] is a translate and a flip; applied to a
## direction rather than a point the translate drops out, so north (+z) becomes screen-up and east (+x) stays
## screen-right. The scale drops out too, which is why this needs no metres-per-pixel.
func _player_forward() -> Vector2:
	if entity_manager == null:
		return Vector2.ZERO

	var player = entity_manager.get_owned_entity()
	if player == null:
		return Vector2.ZERO

	var facing: Vector3 = player.facing()

	return Vector2(facing.x, -facing.z)


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
		_clamp_centre()
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
	_clamp_centre()
	queue_redraw()


func _metres_per_pixel() -> float:
	return pow(2.0, level)


## Index of the last tile the world has along an axis, or a bound past any screen when the extent is unknown.
func _last_tile(metres: float, span: float) -> int:
	if metres <= 0.0:
		return 1 << 30

	return maxi(ceili(metres / span), 1) - 1


## Holds the view over the world.
##
## The world wraps but the map deliberately draws it once, so panning has to stop somewhere and the world's
## own edge is the only honest place. Without this the centre walks off into ground that has no tiles, and
## the panel becomes fog with nothing in it to say which way is back.
func _clamp_centre() -> void:
	if _world_width > 0.0:
		centre.x = clampf(centre.x, 0.0, _world_width)
	if _world_height > 0.0:
		centre.y = clampf(centre.y, 0.0, _world_height)


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
