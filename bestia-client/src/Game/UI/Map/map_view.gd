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

## The arrow head, in pixels from the player's own position at [member marker_scale] 1. The notch is what
## stops it reading as a plain triangle, which at this size is the difference between "facing" and "here".
const _ARROW_TIP := 6.0
const _ARROW_TAIL := 3.5
const _ARROW_HALF := 4.0
const _ARROW_NOTCH := 1.0

## How much of the dark hull the lighter one inside it covers. A ratio rather than a second set of lengths, so
## that resizing the marker keeps the outline an outline instead of swallowing it.
const _ARROW_FILL_RATIO := 0.62

## The dot the marker falls back to before the character has turned, in pixels at [member marker_scale] 1.
const _DOT_OUTLINE_RADIUS := 4.0
const _DOT_FILL_RADIUS := 2.5

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

## Label ink, and the halo behind it. The halo is the fog colour so a name reads over any tile.
const _LABEL_COLOUR := Color(0.97, 0.93, 0.85)
const _LABEL_SELECTED := Color(1.0, 0.86, 0.35)
const _LABEL_HALO := Color(0.11, 0.095, 0.08, 0.8)

## The player's own marks. A cool ink against the warm one the world's places use, because the difference
## that matters at a glance is 'mine' against 'the world's' rather than which kind of place it is.
const _MARK_COLOUR := Color(0.62, 0.85, 1.0)
const _MARK_PIN_RADIUS := 2.6

## Point size per settlement tier, and for everything that is not a settlement. A city's name carries further
## than a hamlet's for the same reason its symbol does.
const _LABEL_SIZES := {
	"CITY": 14,
	"TOWN": 12,
	"VILLAGE": 10,
	"HAMLET": 9,
}
const _LABEL_SIZE_DEFAULT := 10

## Pixels above the symbol the name sits, per tier, so a name clears the mark it belongs to.
##
## Mirrors the radii `PlaceInk` draws server-side - a city is a walled block under three towers and the widest
## mark on the map, and a flat offset that cleared it would leave a hamlet's name floating. Kept in step by
## hand rather than sent with the place: the numbers are a drawing decision on each side, and shipping them
## per tile would put the atlas's pen sizes on the wire for every town.
const _LABEL_OFFSETS := {
	"CITY": 15.0,
	"TOWN": 12.4,
	"VILLAGE": 10.7,
	"HAMLET": 8.3,
}

## For everything that is not a settlement: the site marks are all about the same size.
const _LABEL_OFFSET := 11.3

## How near a click has to land to count as hitting a place. Generous next to the symbols, which are a few
## pixels across: this is a click on a name, and the name is what the player is aiming at.
const _PLACE_HIT_RADIUS := 14.0

## The player picked a place on the map. Carries the whole entry - see [method MapSource.places_of] - so a
## listener can put it on the compass without asking anything back.
signal place_selected(place: Dictionary)

## The player right-clicked empty ground at [param at] world metres, asking for a mark there.
##
## Reported rather than acted on: this control draws a map and does not own the marks, so what a request
## for one becomes - a popup, a name, a stored entry - belongs to whoever wired the two together.
signal mark_requested(at: Vector2)

## The player right-clicked one of their own marks. [param index] indexes [member marks].
signal mark_clicked(index: int)

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

## Whether a click here sets the player walking. True for both views.
##
## Separate from [member interactive], which is about panning and zooming, because the minimap wants one
## without the other: it is deliberately fixed to the player and deliberately still a place you can point
## at and say 'there'. Keeping them as one flag was what made a minimap click do nothing at all.
@export var can_travel: bool = false

## Size of the player marker, as a multiple of the size the minimap is tuned for.
##
## Exported for the same reason the zoom is: the minimap is a 168 pixel window, where a 6 pixel arrow is a
## fair share of the view, while on the full overlay that same arrow is a speck. Scales the dot the marker
## falls back to as well, so an unturned character does not change size relative to a turned one.
@export_range(0.25, 4.0, 0.05, "or_greater") var marker_scale: float = 1.0:
	set(value):
		marker_scale = value
		queue_redraw()

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

## Where the player was the last time this view redrew on their account, and which way they faced. Null
## until the first frame that sees them. Only [method _process] reads or writes them - see there for why a
## view that does not follow the player still has to watch them.
var _marker_at: Variant = null
var _marker_forward := Vector2.ZERO

var _dragging := false

## Whether the pointer moved between press and release. What separates a click from the end of a pan.
var _dragged := false

## The places drawn on the last frame, in the order they were collected. What [method _place_at] hit-tests
## against, so a click can only reach something the player can actually see.
var _visible_places: Array = []

## Feature id of the selected place, or 0. An id rather than the entry itself: the entries are rebuilt every
## frame from the source's cache, so holding one would keep highlighting a copy after the real one moved.
var _selected_id: int = 0

## The player's own marks, or null for a view that does not show them. Assigned rather than fetched so
## that both views share one set without this knowing where it is stored.
var marks: MapMarks = null


func _ready() -> void:
	clip_contents = true


func setup(map_source: MapSource, entities: Node) -> void:
	source = map_source
	entity_manager = entities

	source.tile_ready.connect(func(_key: String, _texture: Texture2D) -> void: queue_redraw())
	source.tile_absent.connect(func(_key: String) -> void: queue_redraw())
	source.places_ready.connect(func(_key: String, _places: Array) -> void: queue_redraw())
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
	var at: Variant = player_metres()
	if at != null:
		centre = at
		queue_redraw()


func _process(_delta: float) -> void:
	# In the tree, not merely this control's own flag: the minimap's panel hides itself when the player holds
	# no chart, and its view underneath stays `visible` - so following would redraw a widget nobody can see.
	if not is_visible_in_tree():
		return

	if follow_player:
		centre_on_player()
		return

	# A view that has stopped following still has the player's marker in it, and the player under it keeps
	# walking. Nothing else here redraws on their account - tiles arrive once and then sit in the cache - so
	# without this the arrow stays where it was when the map opened while the ground under it is live.
	#
	# Compared rather than redrawn every frame: a map left open on a standing character, which is most of the
	# time one is open, then costs nothing.
	var at: Variant = player_metres()
	var forward := _player_forward()
	if at == _marker_at and forward == _marker_forward:
		return

	_marker_at = at
	_marker_forward = forward
	queue_redraw()


func _draw() -> void:
	# Before the early return, not after it: a view with nothing to draw yet still has to cover the world behind
	# it, or the map spends its first frames as a window onto the terrain.
	draw_rect(Rect2(Vector2.ZERO, size), _FOG)

	if source == null or source.meta.is_empty():
		return

	var mpp := _metres_per_pixel()
	var span := _tile_pixels * mpp
	var half := size * 0.5

	var places: Array = []

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

			# Asked for only once the picture is here. A tile the player has charted none of answers 404
			# for both, so asking on the fog path would double the requests that exist to be refused.
			source.want_places(level, tx, ty)
			places.append_array(source.places_of(key))

	_visible_places = places
	_draw_places()
	_draw_marks()
	_draw_player()
	_draw_north()


## A left click that was not a pan: pick the place under it, or set off for the ground.
##
## Selecting wins over travelling, because it is the specific intent - the player aimed at a name, and
## everywhere else on the map is everywhere else. Clicking the same name twice is how they then travel to it,
## which is the one case where "select" and "go" want the same gesture and can have it.
func _on_click(at: Vector2) -> void:
	var place := _place_at(at)

	if not place.is_empty():
		var id := int(place.get("id", 0))
		if id != _selected_id:
			_selected_id = id
			place_selected.emit(place)
			queue_redraw()
			return

		# Already selected, so this is the second click: go there.
		_travel_to(Vector2(float(place.get("x", 0.0)), float(place.get("y", 0.0))))
		return

	# Ground the player has no tile for is not a destination. The server refuses it anyway - it has the
	# authoritative fog - but refusing it here means a click on the fog is simply inert rather than a message
	# that comes back as an error line.
	var world := _to_world(at)
	if source != null and source.is_absent(_key_at(world)):
		return

	_travel_to(world)


## Sets off for a point on the map, if this view is one that may and the world has said how big a voxel is.
##
## The [code]/meta[/code] check is not belt and braces: [member _metres_per_voxel] defaults to one, and a
## click landing before the answer would convert the destination against a guess. The map is nothing but fog
## until meta arrives, so there is nothing worth clicking on either.
##
## The walk itself is [MovementPilot]'s, and is a client-side affair from end to end - the server is only ever
## sent the ordinary short paths it already understands.
func _travel_to(at: Vector2) -> void:
	if not can_travel or source == null or source.meta.is_empty():
		return

	# Metres to tiles, the exact inverse of [method player_metres], which reads the entity's position in
	# tiles and multiplies. The map's northing is the entity's z, hence the y here landing in z there.
	MovementPilot.get_instance().travel_to(at / _metres_per_voxel)


## The tile key covering a world position at this view's level.
func _key_at(world: Vector2) -> String:
	var span := _tile_pixels * _metres_per_pixel()
	return MapSource.key_of(level, floori(world.x / span), floori(world.y / span))


## Names over the symbols the tiles already carry.
##
## [b]Labels only, never a symbol.[/b] The served tile is drawn with every mark already on it - see the
## server's `PlaceInk` - and is drawn without labels on purpose, because the client is the side with the font
## and the locale. Drawing a mark here as well would put two of them on every town.
func _draw_places() -> void:
	var font := get_theme_default_font()
	if font == null:
		return

	var bounds := Rect2(Vector2.ZERO, size)

	for place in _visible_places:
		var text := label_of(place)
		if text.is_empty():
			continue

		var at := _to_screen(Vector2(float(place.get("x", 0.0)), float(place.get("y", 0.0))))
		if not bounds.has_point(at):
			continue

		var tier := _text_of(place, "tier")
		var points: int = _LABEL_SIZES.get(tier, _LABEL_SIZE_DEFAULT)
		var lift: float = _LABEL_OFFSETS.get(tier, _LABEL_OFFSET)
		var width := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, points).x
		var origin := Vector2(at.x - width * 0.5, at.y - lift)

		var selected: bool = _selected_id != 0 and int(place.get("id", 0)) == _selected_id
		var ink := _LABEL_SELECTED if selected else _LABEL_COLOUR

		# A halo of the fog colour behind the text rather than a box, the same trick the atlas uses: a name
		# over hatching or a forest has to stay readable, and a plate behind every label would bury the map.
		for dx in [-1, 0, 1]:
			for dy in [-1, 0, 1]:
				if dx == 0 and dy == 0:
					continue
				draw_string(
					font, origin + Vector2(dx, dy), text,
					HORIZONTAL_ALIGNMENT_LEFT, -1, points, _LABEL_HALO
				)

		draw_string(font, origin, text, HORIZONTAL_ALIGNMENT_LEFT, -1, points, ink)


## The player's own marks: a pin and a name, in their own ink.
##
## These do get a symbol of their own, unlike the world's places - nothing on the tile is drawn for them,
## because the server has never heard of them.
func _draw_marks() -> void:
	if marks == null:
		return

	var font := get_theme_default_font()
	var bounds := Rect2(Vector2.ZERO, size)

	for mark in marks.all():
		var at := _to_screen(Vector2(float(mark["x"]), float(mark["y"])))
		if not bounds.has_point(at):
			continue

		draw_circle(at, _MARK_PIN_RADIUS + 1.0, _MARKER_OUTLINE)
		draw_circle(at, _MARK_PIN_RADIUS, _MARK_COLOUR)

		if font == null:
			continue

		var text := str(mark["name"])
		var width := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, _LABEL_SIZE_DEFAULT).x
		var origin := Vector2(at.x - width * 0.5, at.y - _LABEL_OFFSET)

		for dx in [-1, 0, 1]:
			for dy in [-1, 0, 1]:
				if dx == 0 and dy == 0:
					continue
				draw_string(
					font, origin + Vector2(dx, dy), text,
					HORIZONTAL_ALIGNMENT_LEFT, -1, _LABEL_SIZE_DEFAULT, _LABEL_HALO
				)

		draw_string(font, origin, text, HORIZONTAL_ALIGNMENT_LEFT, -1, _LABEL_SIZE_DEFAULT, _MARK_COLOUR)


## A right click: on one of the player's own marks, or on ground they might want to mark.
##
## Their own marks are tested first and win. A mark sits where the player put it, which is often exactly on
## top of something worth marking - so resolving the tie the other way would make a mark next to a town
## impossible to rename.
func _on_right_click(at: Vector2) -> void:
	if marks == null:
		return

	var world := _to_world(at)

	var hit := marks.nearest(world, _PLACE_HIT_RADIUS * _metres_per_pixel())
	if hit >= 0:
		mark_clicked.emit(hit)
		return

	mark_requested.emit(world)


## What to print for a place, or "" for one with nothing to say.
##
## A generated [code]name[/code] wins because it is the specific thing - "Elmford" beats "town". Failing that
## a kind is printed through [method Object.tr], which works precisely because [code]poi[/code] and
## [code]kind[/code] arrive as enum names rather than as the server's English labels: the landmark kinds are a
## closed set, so they can live in [code]general.csv[/code] like every other fixed string.
func label_of(place: Dictionary) -> String:
	var name_of := _text_of(place, "name")
	if not name_of.is_empty():
		return name_of

	var poi := _text_of(place, "poi")
	if not poi.is_empty():
		return tr("POI_" + poi)

	var kind := _text_of(place, "kind")
	if kind.is_empty():
		return ""

	# Untranslated keys come back as the key itself, which would print "FEATURE_LAVA_POOL" on the map. A kind
	# nobody has written a line for is better left unlabelled: its symbol is already drawn.
	var key := "FEATURE_" + kind
	var text := tr(key)
	return "" if text == key else text


## One of a place's optional string fields, or "" when it has none.
##
## A place carries every field it could have and sends JSON null for the ones it has not - an unnamed
## landmark still arrives with a [code]name[/code] key. [method Dictionary.get]'s default only covers a
## key that is absent, and [method @GlobalScope.str] of null is the literal "<null>", so reading one
## straight puts that on the map in place of the fallback the caller meant to reach.
static func _text_of(place: Dictionary, key: String) -> String:
	var value: Variant = place.get(key)
	return "" if value == null else str(value)


## The place under [param at], or an empty [Dictionary]. Nearest wins, so overlapping labels are reachable.
func _place_at(at: Vector2) -> Dictionary:
	var best := {}
	var best_distance := _PLACE_HIT_RADIUS

	for place in _visible_places:
		if label_of(place).is_empty():
			continue

		var screen := _to_screen(Vector2(float(place.get("x", 0.0)), float(place.get("y", 0.0))))
		var distance := screen.distance_to(at)
		if distance < best_distance:
			best_distance = distance
			best = place

	return best


func _draw_player() -> void:
	var at: Variant = player_metres()
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
		draw_circle(screen, _DOT_OUTLINE_RADIUS * marker_scale, _MARKER_OUTLINE)
		draw_circle(screen, _DOT_FILL_RADIUS * marker_scale, _MARKER_FILL)
		return

	# Dark under light, the same two passes the dot used: a chart tile can be any brightness, and a
	# single-colour marker disappears into half of them.
	_draw_arrow(screen, forward, marker_scale, _MARKER_OUTLINE)
	_draw_arrow(screen, forward, marker_scale * _ARROW_FILL_RATIO, _MARKER_FILL)


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
		# A view that does not pan or zoom can still be pointed at. Reached through the gate rather than
		# folded into it because everything below is about panning, and the minimap does none of it.
		if (
			can_travel
			and event is InputEventMouseButton
			and event.button_index == MOUSE_BUTTON_LEFT
			and not event.pressed
		):
			_on_click(event.position)
			accept_event()
		return

	if event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_LEFT:
			if event.pressed:
				_dragging = true
				_dragged = false
			else:
				_dragging = false
				# A press and release without motion between them is a click, and a pan ends with a release
				# too - so without this the map would act on whatever was under the cursor at the end of
				# every drag, which is something on nearly every pan.
				if not _dragged:
					_on_click(event.position)
			accept_event()

		elif event.button_index == MOUSE_BUTTON_RIGHT:
			if event.pressed:
				_on_right_click(event.position)
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
		_dragged = true
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
func player_metres() -> Variant:
	if entity_manager == null:
		return null

	var player = entity_manager.get_owned_entity()
	if player == null:
		return null

	return Vector2(player.global_position.x, player.global_position.z) * _metres_per_voxel
