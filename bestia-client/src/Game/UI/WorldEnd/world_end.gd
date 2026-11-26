extends Control
## The end of the world on every screen, played from the server's [WorldEndSMSG].
##
## Lost: fire eats the view from its edges until all is black, then the red title. Saved: the world fills with
## white light while every master and bestia in view dissolves into it, then the white title. Both end with what the
## world lived through and the hint that a new one is coming. Nothing behind the overlay takes input meanwhile.
##
## The server closes the connection shortly after; the overlay stays, and the button leads back to the title. A
## GM's preview (/doom sequence) is never followed by that, so there the button closes the overlay instead.

const _CALLED_FX_KIND := "WORLD_CALLED"

const _BURN_SECONDS := 8.0
const _BLACK_HOLD_SECONDS := 1.0
## The masters and bestia turn to light and dissolve before the light fills the screen.
const _CALLED_SECONDS := 3.5
const _LIGHT_SECONDS := 7.0
const _SETTLE_SECONDS := 3.0
const _TITLE_SECONDS := 2.0
const _LINE_SECONDS := 1.2

const _LOST_TITLE_COLOUR := Color(0.88, 0.1, 0.07)
const _LOST_TEXT_COLOUR := Color(0.82, 0.78, 0.74)
const _SAVED_TEXT_COLOUR := Color(1.0, 1.0, 1.0)
const _SAVED_OUTLINE_COLOUR := Color(0.3, 0.33, 0.45, 0.85)

@onready var _burn: ColorRect = $Burn
@onready var _light: ColorRect = $Light
@onready var _title: Label = $Center/Column/Title
@onready var _summary: VBoxContainer = $Center/Column/Summary
@onready var _new_world: Label = $Center/Column/NewWorld
@onready var _leave: Button = $Center/Column/Leave
@onready var _music: AudioStreamPlayer = $CallingMusic

var _sequence: Tween
var _called: Array[EntityFx] = []
var _connection_closed := false


func _ready() -> void:
	visible = false
	add_to_group("world_blocking_ui")
	_title.add_theme_font_size_override("font_size", 56)
	_leave.pressed.connect(_on_leave_pressed)
	ConnectionManager.world_end_received.connect(_on_world_end_received)
	ConnectionManager.world_ended.connect(_on_world_ended)


## Plays the final sequence of [param message]. In a saved world, [param bodies] dissolve into the light.
func play(message: WorldEndSMSG, bodies: Array[EntityFx]) -> void:
	_stop()
	visible = true
	_fill_summary(message)
	for line: Control in _text_lines():
		line.modulate.a = 0.0
	_leave.visible = false

	_sequence = create_tween()
	if message.EndingName == "saved":
		_play_saved(bodies)
	else:
		_play_lost()
	for line: Control in _text_lines():
		var fade_in := _TITLE_SECONDS if line == _title else _LINE_SECONDS
		_sequence.tween_property(line, "modulate:a", 1.0, fade_in)
	_sequence.tween_callback(_offer_leave)


## Only keys: the buttons need the mouse, and every other control lies under this one.
func _input(event: InputEvent) -> void:
	if visible and event is InputEventKey:
		get_viewport().set_input_as_handled()


func _on_world_end_received(message: WorldEndSMSG) -> void:
	play(message, _mana_born_bodies())


func _on_world_ended() -> void:
	_connection_closed = true
	_leave.text = tr("WORLD_END_RETURN")


func _play_lost() -> void:
	_style_text(_LOST_TEXT_COLOUR, Color(0, 0, 0, 0))
	_title.add_theme_color_override("font_color", _LOST_TITLE_COLOUR)
	_title.text = tr("WORLD_END_LOST_TITLE")
	_burn.visible = true
	_sequence.tween_method(_set_shader.bind(_burn, &"progress"), 0.0, 1.0, _BURN_SECONDS)
	_sequence.tween_interval(_BLACK_HOLD_SECONDS)


func _play_saved(bodies: Array[EntityFx]) -> void:
	_style_text(_SAVED_TEXT_COLOUR, _SAVED_OUTLINE_COLOUR)
	_title.text = tr("WORLD_END_SAVED_TITLE")
	if _music.stream != null:
		_music.play()

	var called := EntityFxDB.get_instance().get_fx(_CALLED_FX_KIND)
	_called = bodies
	for fx: EntityFx in _called:
		fx.sustain(_CALLED_FX_KIND, called)

	_light.visible = true
	_sequence.tween_interval(_CALLED_SECONDS)
	_sequence.tween_method(_set_shader.bind(_light, &"progress"), 0.0, 1.0, _LIGHT_SECONDS)
	_sequence.tween_method(_set_shader.bind(_light, &"settle"), 0.0, 1.0, _SETTLE_SECONDS)


func _fill_summary(message: WorldEndSMSG) -> void:
	for line in _summary.get_children():
		_summary.remove_child(line)
		line.queue_free()

	_add_line(tr("WORLD_END_LIVED") % [message.WorldName, message.DaysLived])
	if message.Threat == "":
		_add_line(tr("WORLD_END_NO_THREAT"))
	else:
		_add_line(tr("WORLD_END_THREAT") % tr("DOOM_" + message.Threat))
	_add_line(tr("WORLD_END_T") % message.TPercent)
	match message.TownsLost:
		0:
			_add_line(tr("WORLD_END_NO_TOWN_LOST"))
		1:
			_add_line(tr("WORLD_END_ONE_TOWN_LOST"))
		_:
			_add_line(tr("WORLD_END_TOWNS_LOST") % message.TownsLost)


func _add_line(text: String) -> void:
	var line := Label.new()
	line.text = text
	line.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	line.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_summary.add_child(line)


## The title, each summary line and the new-world hint, in the order they appear.
func _text_lines() -> Array[Control]:
	var lines: Array[Control] = [_title]
	for line: Control in _summary.get_children():
		lines.append(line)
	lines.append(_new_world)
	return lines


func _style_text(colour: Color, outline: Color) -> void:
	for line: Control in _text_lines():
		line.add_theme_color_override("font_color", colour)
		line.add_theme_color_override("font_outline_color", outline)
		line.add_theme_constant_override("outline_size", 6 if outline.a > 0.0 else 0)
		if line != _title:
			line.add_theme_font_size_override("font_size", 22)


func _offer_leave() -> void:
	_leave.text = tr("WORLD_END_RETURN") if _connection_closed else tr("WORLD_END_CLOSE")
	_leave.visible = true


func _on_leave_pressed() -> void:
	if _connection_closed:
		SceneManager.goto_scene("res://Menu/Main/Main.tscn")
		return
	_stop()
	visible = false


## Ends a running sequence and gives the bodies it called back their look.
func _stop() -> void:
	if _sequence != null:
		_sequence.kill()
	for fx: EntityFx in _called:
		if is_instance_valid(fx):
			fx.release(_CALLED_FX_KIND)
	_called.clear()
	_burn.visible = false
	_light.visible = false
	_set_shader(0.0, _burn, &"progress")
	_set_shader(0.0, _light, &"progress")
	_set_shader(0.0, _light, &"settle")
	_music.stop()


func _set_shader(value: float, rect: ColorRect, parameter: StringName) -> void:
	(rect.material as ShaderMaterial).set_shader_parameter(parameter, value)


## Every master and bestia in view: all that is born of mana, which the Calling draws away.
func _mana_born_bodies() -> Array[EntityFx]:
	var bodies: Array[EntityFx] = []
	var entities := EntityManager.get_instance()
	if entities == null:
		return bodies
	for entity: Entity in entities.get_mana_born_entities():
		bodies.append(entity.fx)
	return bodies
