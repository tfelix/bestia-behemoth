class_name CompendiumRow
extends PanelContainer

## One species in the compendium list: its number, a small portrait, its name and a caught mark. An unseen
## species keeps its number and shows a silhouette and "???".

signal picked(species_id: int)

const _PORTRAIT_SIZE := 36

var species_id: int = 0

var _number: Label
var _portrait: TextureRect
var _name: Label
var _caught: Label
var _selected := false
var _hovered := false


func _init() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND

	var line := UiStyle.hbox(8)
	add_child(line)

	_number = UiStyle.label("", 11, UiStyle.MUTED)
	_number.custom_minimum_size = Vector2(34, 0)
	line.add_child(_number)

	var frame := PanelContainer.new()
	frame.add_theme_stylebox_override("panel", UiStyle.flat(CompendiumPanel.PARCHMENT, 3, 1))
	frame.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_portrait = TextureRect.new()
	_portrait.custom_minimum_size = Vector2(_PORTRAIT_SIZE, _PORTRAIT_SIZE)
	_portrait.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_portrait.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	_portrait.mouse_filter = Control.MOUSE_FILTER_IGNORE
	frame.add_child(_portrait)
	line.add_child(frame)

	_name = UiStyle.label("", 13)
	_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_name.clip_text = true
	_name.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	line.add_child(_name)

	_caught = UiStyle.label("◆", 12, UiStyle.ACCENT)
	_caught.tooltip_text = tr("COMPENDIUM_CAUGHT")
	_caught.mouse_filter = Control.MOUSE_FILTER_PASS
	line.add_child(_caught)

	mouse_entered.connect(func() -> void: _set_hovered(true))
	mouse_exited.connect(func() -> void: _set_hovered(false))
	_restyle()



func show_species(species: BestiaResource, number_label: String, seen: bool, caught: bool) -> void:
	species_id = species.bestia_id
	_number.text = number_label
	_portrait.texture = species.get_portrait()
	_portrait.modulate = Color.WHITE if seen else CompendiumPanel.SILHOUETTE
	_name.text = tr(species.name_key) if seen else tr("COMPENDIUM_UNKNOWN")
	_name.add_theme_color_override("font_color", UiStyle.TEXT if seen else UiStyle.MUTED)
	_caught.visible = caught


func set_selected(selected: bool) -> void:
	_selected = selected
	_restyle()


func _gui_input(event: InputEvent) -> void:
	var click := event as InputEventMouseButton
	if click != null and click.pressed and click.button_index == MOUSE_BUTTON_LEFT:
		picked.emit(species_id)
		accept_event()


func _set_hovered(hovered: bool) -> void:
	_hovered = hovered
	_restyle()


func _restyle() -> void:
	var background := UiStyle.ROW_HOVER_BG if _hovered else UiStyle.ROW_BG
	var border := 1 if _selected else 0
	add_theme_stylebox_override("panel", UiStyle.flat(background, 4, 6, UiStyle.SELF_BORDER, border))
