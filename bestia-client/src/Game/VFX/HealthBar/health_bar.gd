extends Sprite3D
class_name HealthBar

## The bar under an entity: its health, and for the player's own entities their mana in a second row, shown
## only for a while after something happened to it.
##
## Health is a synced component, so the server pushes the full pool once as soon as the entity
## enters view. That first push is only a seed - it carries no news - and must leave the bar
## hidden, otherwise every bestia already standing around at login would greet the player with
## its health bar up until the fade timer ran out. Only a later push that actually moves the
## pool (damage, healing, a max change) pops the bar open and restarts the fade. A fight the
## player's own entity is in does too, through [method enter_combat], though a miss moves nothing.
##
## Drawn texel by texel into a small image and shown at a whole number of screen pixels per texel with
## no filtering, so the one-pixel border stays one pixel and never smears, like Ragnarok Online's bars.

const _WIDTH := 60
const _BORDER := 1
const _HEALTH_HEIGHT := 4
const _MANA_HEIGHT := 3
## At this window height a texel is one screen pixel; at twice it, two.
const _PIXELS_PER_TEXEL_AT := 1100.0
## Below this share of its health the bar turns red.
const _LOW_HEALTH := 0.25

const _BORDER_COLOUR := Color(0.02, 0.02, 0.02)
const _EMPTY_COLOUR := Color(0.22, 0.22, 0.22)
const _HEALTH_COLOUR := Color(0.16, 0.78, 0.29)
const _LOW_HEALTH_COLOUR := Color(0.89, 0.2, 0.16)
const _MANA_COLOUR := Color(0.22, 0.45, 0.95)

var _max_value: int = 100
var _value: int = 100
var _seeded: bool = false
## Null until mana is shown, which only the player's own entities are told.
var _mana: ConditionPool = null
var _fade_tween: Tween
var _image: Image

@onready var _fade_trigger_timer: Timer = $FadeTriggerTimer


func _ready() -> void:
	visible = false
	# After the entity and the camera have moved this frame, so the pixel it is snapped to is the one it is drawn at.
	process_priority = 1000
	set_process(false)
	visibility_changed.connect(_on_visibility_changed)
	_redraw()


func update_health(msg: HealthComponentSMSG) -> void:
	var changed := _seeded and (msg.Current != _value or msg.Max != _max_value)
	_value = msg.Current
	_max_value = msg.Max
	_seeded = true
	_redraw()

	if changed:
		_show_until_fade()


func update_mana(msg: ManaComponentSMSG) -> void:
	_mana = ConditionPool.new(msg.Current, msg.Max)
	_redraw()


## The entity struck or was struck at: the bar stays up for as long as the fight goes on.
func enter_combat() -> void:
	if _seeded:
		_show_until_fade()


## Only while shown: the window or the zoom can change under a hidden bar, and it is sized again on showing.
func _on_visibility_changed() -> void:
	set_process(visible)
	if visible:
		_fit_to_screen()


func _process(_delta: float) -> void:
	_fit_to_screen()


## A whole number of pixels per texel is not enough on its own: the bar's corner must also sit on a pixel corner,
## or every edge row straddles two pixels and the 3D pass's multisampling blends the border into what is behind.
## So the bar is nudged, by less than a pixel, until it does.
func _fit_to_screen() -> void:
	var camera := get_viewport().get_camera_3d()
	var size := _texel_size()
	if size != pixel_size:
		pixel_size = size
	if camera == null or texture == null or camera.is_position_behind(global_position):
		return

	var pixels_per_texel := _pixels_per_texel()
	var extent := Vector2(texture.get_width(), texture.get_height()) * pixels_per_texel
	var corner := camera.unproject_position(global_position) - extent / 2.0
	var nudge := (corner.round() - corner) / pixels_per_texel
	# Screen y runs down, a sprite's offset up.
	offset = Vector2(nudge.x, -nudge.y)


## What [member pixel_size] makes one texel a whole number of screen pixels. A fixed-size sprite is drawn as if
## one unit from a perspective camera, which spans 2·tan(fov/2) units over the viewport's height (as for
## [NameTagEmblem]).
func _texel_size() -> float:
	var camera := get_viewport().get_camera_3d()
	var height := get_viewport().get_visible_rect().size.y
	if camera == null or camera.projection != Camera3D.PROJECTION_PERSPECTIVE or height <= 0.0:
		return pixel_size
	return _pixels_per_texel() * 2.0 * tan(deg_to_rad(camera.fov) / 2.0) / height


func _pixels_per_texel() -> int:
	return maxi(1, roundi(get_viewport().get_visible_rect().size.y / _PIXELS_PER_TEXEL_AT))


func _redraw() -> void:
	var height := _BORDER + _HEALTH_HEIGHT + _BORDER
	if _mana != null:
		height += _MANA_HEIGHT + _BORDER

	if _image == null or _image.get_height() != height:
		_image = Image.create_empty(_WIDTH, height, false, Image.FORMAT_RGBA8)
	_image.fill(_BORDER_COLOUR)

	var health_share := float(_value) / _max_value if _max_value > 0 else 0.0
	var health_colour := _LOW_HEALTH_COLOUR if health_share < _LOW_HEALTH else _HEALTH_COLOUR
	_fill_row(_BORDER, _HEALTH_HEIGHT, _value, _max_value, health_colour)
	if _mana != null:
		_fill_row(_BORDER + _HEALTH_HEIGHT + _BORDER, _MANA_HEIGHT, _mana.current, _mana.maximum, _MANA_COLOUR)

	if texture is ImageTexture and texture.get_height() == height:
		(texture as ImageTexture).update(_image)
	else:
		texture = ImageTexture.create_from_image(_image)


## Anything left above nothing keeps one texel, so a body still standing never reads as empty.
func _fill_row(top: int, height: int, current: int, maximum: int, colour: Color) -> void:
	var inner := _WIDTH - 2 * _BORDER
	_image.fill_rect(Rect2i(_BORDER, top, inner, height), _EMPTY_COLOUR)
	if current <= 0 or maximum <= 0:
		return
	var filled := clampi(roundi(float(inner) * current / maximum), 1, inner)
	_image.fill_rect(Rect2i(_BORDER, top, filled, height), colour)


func _show_until_fade() -> void:
	# A fade that has already run left modulate.a at 0, and one still running would drag it back
	# down, so both have to be undone before showing the bar again - otherwise it would be
	# "visible" while fully transparent.
	if _fade_tween != null:
		_fade_tween.kill()
		_fade_tween = null
	modulate.a = 1.0
	visible = true
	_fade_trigger_timer.start()


## Trigger the fade of the health bar.
func _on_fade_trigger_timer_timeout() -> void:
	var tween := create_tween()
	_fade_tween = tween
	tween.tween_property(self, "modulate:a", 0.0, 1.0)
	await tween.finished
	# Health that changed again mid-fade replaced this tween; only the fade that is still the
	# current one may hide the bar.
	if _fade_tween == tween:
		_fade_tween = null
		visible = false
