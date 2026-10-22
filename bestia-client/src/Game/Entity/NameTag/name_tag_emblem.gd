class_name NameTagEmblem
extends Sprite3D

## A guild's emblem beside a [Label3D] name tag, as Ragnarok Online draws it. A child of the label, so it shows
## and hides with it. Drawn one texel to one screen pixel, the only scale at which 32 pixels stay crisp, and
## placed in the label's own pixels, so it keeps its place at any camera distance.

const _GAP_PIXELS := 6.0

var _label: Label3D
var _hash := ""


func _init() -> void:
	billboard = BaseMaterial3D.BILLBOARD_ENABLED
	fixed_size = true
	no_depth_test = true
	texture_filter = BaseMaterial3D.TEXTURE_FILTER_NEAREST
	visible = false


func _ready() -> void:
	EmblemCache.emblem_ready.connect(func(ready_hash: String) -> void:
		if ready_hash == _hash:
			_refresh()
	)
	# The window may have been resized since the last time, which changes what one screen pixel is.
	visibility_changed.connect(func() -> void:
		if is_visible_in_tree():
			_place()
	)


## Call again whenever the label's text changes; an empty [param hash] hides the emblem.
func show_beside(label: Label3D, hash: String) -> void:
	_label = label
	_hash = hash
	_refresh()


func _refresh() -> void:
	texture = EmblemCache.texture_of(_hash)
	visible = texture != null and _label != null
	if visible:
		_place()


## Left of the name block, centred on it as the label centres its own text.
func _place() -> void:
	if texture == null or _label == null:
		return

	pixel_size = _one_texel_per_screen_pixel()
	render_priority = _label.render_priority + 1
	var label_to_screen := _label.pixel_size / pixel_size
	var font: Font = _label.font if _label.font != null else ThemeDB.fallback_font
	var widest := 0.0
	for line in _label.text.split("\n"):
		widest = maxf(widest, font.get_string_size(line, HORIZONTAL_ALIGNMENT_LEFT, -1, _label.font_size).x)
	offset = Vector2(-(widest * label_to_screen + texture.get_width()) / 2.0 - _GAP_PIXELS, 0)


## What [member pixel_size] makes a texel one pixel high on screen. A fixed-size sprite is drawn as if one unit
## from a perspective camera, which spans 2·tan(fov/2) units over the viewport's height.
func _one_texel_per_screen_pixel() -> float:
	var camera := get_viewport().get_camera_3d()
	var height := get_viewport().get_visible_rect().size.y
	if camera == null or camera.projection != Camera3D.PROJECTION_PERSPECTIVE or height <= 0.0:
		return _label.pixel_size
	return 2.0 * tan(deg_to_rad(camera.fov) / 2.0) / height
