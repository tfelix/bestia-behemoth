class_name CompassStrip
extends Control

## A ribbon of bearings that slides as the camera turns, with whatever the player is facing at its centre.
##
## A strip rather than a dial because of what it has to grow into. A dial shows a bearing; a strip shows a
## bearing [i]and how far off it you are[/i], which is the reading a waypoint marker exists to give. On a dial
## the two halves of "slightly left of north" and "slightly right of north" are a few pixels apart at opposite
## ends of an arc; here they are a few pixels apart on a line.
##
## [b]North is +Z and east is +X[/b], which is not this control's choice - it is the frame the map already
## draws in, see [MapView]. A bearing here is radians clockwise from north, the same convention a compass rose
## is printed in, and [method bearing_of] is the one place a world direction becomes one.
##
## Only the wind is drawn on it today. Everything else it can carry - the player's map waypoints, most
## obviously - goes on through [method set_marks] without this file changing, which is the reason marks are a
## registry rather than a field.

## Names of the eight points, clockwise from north. Held here rather than in [code]general.csv[/code] for the
## reason [code]clock.gd[/code] holds its season names: what a bearing is called belongs to whatever prints
## it, and the translation file is for sentences.
const POINT_NAMES := ["N", "NE", "E", "SE", "S", "SW", "W", "NW"]

## How much of the horizon fits across the strip. Roughly a wide-angle glance, and deliberately less than the
## camera's own 65 degree field: a strip that showed exactly what was on screen would put north at the edge
## just as it came into view, which is the moment it is least useful.
@export var span_degrees: float = 140.0

## The bearing the camera looks along. Read it; do not set it - it is taken from the camera every frame.
var heading: float = 0.0

## Whether there is a camera to take a heading from yet.
##
## False for the whole login and master-select sequence: the camera is instantiated at runtime as a child of
## the owned entity, so it does not exist until the player is actually standing somewhere. What hangs off this
## is the widget's visibility - a compass with no heading points north and lies.
var has_heading: bool = false

## Bearings drawn besides the cardinals, grouped by whatever put them there.
##
## Keyed by source rather than kept as one array so that the wind and, later, the player's waypoints can each
## replace their own set without knowing the other exists. A mark is a plain [Dictionary] and not a class, so
## a caller builds one without importing this script:
## [codeblock]
## {"bearing": float, "colour": Color, "label": String}
## [/codeblock]
var _marks: Dictionary[StringName, Array] = {}

## Whether something has changed that has not been drawn yet. See [method _process].
var _dirty := true

## Amber for where the player is looking, matching the minimap's own player arrow; cream for north, matching
## its north pip. The two widgets are read together, so the same thing is the same colour in both.
const _INDEX_COLOUR := Color(1.0, 0.86, 0.35)
const _NORTH_COLOUR := Color(1.0, 0.94, 0.78)
const _CARDINAL_COLOUR := Color(0.80, 0.76, 0.68)
const _INTERCARDINAL_COLOUR := Color(0.58, 0.55, 0.49)
const _RULE_COLOUR := Color(0.30, 0.28, 0.24)

const _CARDINAL_FONT_SIZE := 11
const _INTERCARDINAL_FONT_SIZE := 9

## The vertical layout, in pixels down from the top of the control.
const _INDEX_HEIGHT := 6.0
const _INDEX_HALF := 5.0
const _TICK_TOP := 8.0
const _TICK_BOTTOM_MAJOR := 16.0
const _TICK_BOTTOM_MID := 14.0
const _TICK_BOTTOM_MINOR := 12.0
const _LABEL_BASELINE := 27.0

## Ungraduated degrees between the smallest ticks. Purely texture: without them the strip is four letters and
## reads as a label rather than as something moving.
const _MINOR_TICK_DEGREES := 15.0

## How far past each edge a mark is still drawn, so one leaves the strip by sliding off rather than by
## vanishing a tick early.
const _CULL_PAD := 10.0

## Heading change too small to redraw for, in radians - about a fiftieth of a degree. This control is visible
## the whole time the player is in the world, so a redraw every frame of a still camera is the one cost worth
## refusing.
const _HEADING_EPSILON := 0.0003


## A compass bearing from a flat world direction, in radians clockwise from north.
##
## North is +Z and east is +X - the frame [MapView] already draws the world in.
static func bearing_of(direction: Vector3) -> float:
	return atan2(direction.x, direction.z)


## The bearing on which [param to] lies as seen from [param from]. What a waypoint marker is built out of.
static func bearing_between(from: Vector3, to: Vector3) -> float:
	return atan2(to.x - from.x, to.z - from.z)


## The eight-point name of a bearing, for a readout with room for two letters and not for degrees.
static func name_of(bearing: float) -> String:
	return POINT_NAMES[wrapi(int(round(bearing / (PI / 4.0))), 0, POINT_NAMES.size())]


## Replaces everything [param source] had on the strip. An empty array is how a source says "nothing now".
func set_marks(source: StringName, marks: Array) -> void:
	_marks[source] = marks
	_dirty = true


func clear_marks(source: StringName) -> void:
	if _marks.erase(source):
		_dirty = true


func _process(_delta: float) -> void:
	var next := _camera_heading()
	var camera_found := not is_nan(next)

	if camera_found != has_heading:
		has_heading = camera_found
		_dirty = true

	if camera_found and absf(wrapf(next - heading, -PI, PI)) >= _HEADING_EPSILON:
		heading = next
		_dirty = true

	# The visibility test guards the redraw and nothing above it, deliberately. The panel hosting this strip
	# hides itself until there is a heading to show, so a test at the top of this function would leave
	# has_heading false for ever and the widget would never appear at all.
	#
	# The change is held in a flag rather than acted on where it is found for the other half of that: the
	# panel reads has_heading one frame later than this sets it, so the frame that finds the camera is always
	# a frame this strip is still hidden for. Dropping the redraw there left the strip blank until the player
	# happened to turn the camera.
	if _dirty and is_visible_in_tree():
		_dirty = false
		queue_redraw()


## The bearing the camera looks along, or [constant NAN] before there is a camera to ask.
##
## Read off the camera's own basis rather than the spring arm's rotation, because the spring arm is
## instantiated under the owned entity at runtime and the HUD has no path to it - the same reason
## [code]mouse_manager.gd[/code] and [code]cloud_shadows.gd[/code] go through the viewport.
func _camera_heading() -> float:
	var camera := get_viewport().get_camera_3d()
	if camera == null:
		return NAN

	var camera_basis := camera.global_transform.basis
	var flat := Vector2(-camera_basis.z.x, -camera_basis.z.z)

	# Straight down is reachable - the spring arm's min_vertical_angle is -PI/2 and its clamp lands exactly
	# on it - and there the flat forward is exactly zero, where atan2 would snap the whole strip to north. At
	# that pitch the camera's up axis lies along the heading it came from, so it stands in. Straight up needs
	# no such branch: max_vertical_angle stops well short of it.
	if flat.is_zero_approx():
		flat = Vector2(camera_basis.y.x, camera_basis.y.z)

	return atan2(flat.x, flat.y)


func _draw() -> void:
	if not has_heading:
		return

	var font := get_theme_default_font()

	draw_line(Vector2(0.0, _TICK_TOP), Vector2(size.x, _TICK_TOP), _RULE_COLOUR, 1.0)

	_draw_minor_ticks()
	_draw_points(font)

	for marks in _marks.values():
		for mark in marks:
			_draw_mark(font, mark)

	# Last, so the one fixed thing on the strip is never drawn over by something sliding past it.
	draw_colored_polygon(PackedVector2Array([
		Vector2(size.x * 0.5, _INDEX_HEIGHT),
		Vector2(size.x * 0.5 - _INDEX_HALF, 0.0),
		Vector2(size.x * 0.5 + _INDEX_HALF, 0.0),
	]), _INDEX_COLOUR)


func _draw_minor_ticks() -> void:
	var step := deg_to_rad(_MINOR_TICK_DEGREES)
	var per_turn := int(round(TAU / step))

	for i in range(per_turn):
		# The eight named points draw their own, taller, below.
		if i % 3 == 0:
			continue

		var x := _visible_x(i * step)
		if is_nan(x):
			continue

		draw_line(Vector2(x, _TICK_TOP), Vector2(x, _TICK_BOTTOM_MINOR), _RULE_COLOUR, 1.0)


func _draw_points(font: Font) -> void:
	for i in range(POINT_NAMES.size()):
		var x := _visible_x(i * PI / 4.0)
		if is_nan(x):
			continue

		var is_cardinal := i % 2 == 0
		var colour := _CARDINAL_COLOUR if is_cardinal else _INTERCARDINAL_COLOUR
		if i == 0:
			colour = _NORTH_COLOUR

		var bottom := _TICK_BOTTOM_MAJOR if is_cardinal else _TICK_BOTTOM_MID
		draw_line(Vector2(x, _TICK_TOP), Vector2(x, bottom), colour, 1.0)

		var font_size := _CARDINAL_FONT_SIZE if is_cardinal else _INTERCARDINAL_FONT_SIZE
		_draw_centred(font, POINT_NAMES[i], x, font_size, colour)


func _draw_mark(font: Font, mark: Dictionary) -> void:
	var x := _visible_x(float(mark.get("bearing", 0.0)))
	if is_nan(x):
		return

	var colour: Color = mark.get("colour", _CARDINAL_COLOUR)

	# A diamond rather than a tick, so a mark is never mistaken for a graduation.
	var centre := Vector2(x, (_TICK_TOP + _TICK_BOTTOM_MAJOR) * 0.5)
	draw_colored_polygon(PackedVector2Array([
		centre + Vector2(0.0, -4.0),
		centre + Vector2(3.5, 0.0),
		centre + Vector2(0.0, 4.0),
		centre + Vector2(-3.5, 0.0),
	]), colour)

	var label: String = mark.get("label", "")
	if not label.is_empty():
		_draw_centred(font, label, x, _INTERCARDINAL_FONT_SIZE, colour)


## Where a bearing falls across the strip, or NAN when it is off the end.
##
## The single place the strip decides what it can show. An off-strip waypoint should one day be pinned to the
## edge with a chevron rather than dropped, and this is the only function that has to learn how.
##
## NAN rather than null for the miss, so this returns a float and every caller stays statically typed - a
## Variant return infers Variant at each `var`, which this project treats as an error.
func _visible_x(bearing: float) -> float:
	var offset := wrapf(bearing - heading, -PI, PI)
	var x := size.x * 0.5 + offset * size.x / deg_to_rad(span_degrees)

	if x < -_CULL_PAD or x > size.x + _CULL_PAD:
		return NAN

	return x


## draw_string takes a baseline and a left edge, so centring means measuring the string first.
func _draw_centred(font: Font, text: String, centre_x: float, font_size: int, colour: Color) -> void:
	var width := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size).x

	draw_string(font, Vector2(centre_x - width * 0.5, _LABEL_BASELINE), text,
		HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, colour)
