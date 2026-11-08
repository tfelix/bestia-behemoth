class_name SkillTile
extends Button

## One skill on a skill tree: its icon, its name in up to two lines, and under the name a level bar with the
## level beside it. The master's Skills window and the guild window's Skills tab both build their trees from
## these, so the two trees read alike.
##
## A gold frame means learned, a teal one that a point could go into it now, and a dark, dimmed tile that a
## requirement still keeps it closed. A level in green holds points queued but not yet applied, one in gold
## is maxed, and while the skill recovers its seconds left stand where the level would be.

signal double_clicked

const SIZE := Vector2(132, 64)

## Inside a tile: the icon on the left, the name beside it in up to two lines, and under the name its own row
## for the level strip and the level, kept clear of the name by [constant _FOOTER_GAP].
const _PAD := 6.0
const _ICON_SIZE := 40.0
## Two lines of the 11 px name, with the label's own line spacing taken out.
const _NAME_HEIGHT := 32.0
const _FOOTER_GAP := 6.0
const _LEVEL_WIDTH := 30.0
const _LEVEL_HEIGHT := 14.0
const _BAR_HEIGHT := 4.0

var skill_id := 0

var _icon := TextureRect.new()
var _title := UiStyle.label("", 11)
var _bar := UiStyle.bar(UiStyle.ACCENT, int(_BAR_HEIGHT))
var _level := UiStyle.label("", 10, UiStyle.MUTED)
var _level_text := ""
var _level_color := UiStyle.MUTED


func _init(id: int) -> void:
	skill_id = id
	toggle_mode = true
	focus_mode = Control.FOCUS_NONE
	size = SIZE

	_icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	# A label rather than the button's own text, which would grow the tile to fit a long name.
	_title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_title.vertical_alignment = VERTICAL_ALIGNMENT_TOP
	_title.clip_text = true
	_title.add_theme_constant_override("line_spacing", 0)
	_level.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT

	for part: Control in [_icon, _title, _bar, _level]:
		part.mouse_filter = Control.MOUSE_FILTER_IGNORE
		add_child(part)


## Sized here, in the tree, rather than in [method _init]: outside it a label measures with the default 16 px
## font, and a size set then keeps that taller height, which pushed the level text below the bar.
func _ready() -> void:
	var text_x := _PAD + _ICON_SIZE + _PAD
	var text_width := SIZE.x - text_x - _PAD
	_icon.position = Vector2(_PAD, (SIZE.y - _ICON_SIZE) / 2)
	_icon.size = Vector2(_ICON_SIZE, _ICON_SIZE)
	_title.position = Vector2(text_x, _PAD - 2)
	_title.size = Vector2(text_width, _NAME_HEIGHT)

	var footer_y := _title.position.y + _NAME_HEIGHT + _FOOTER_GAP
	_bar.position = Vector2(text_x, footer_y + (_LEVEL_HEIGHT - _BAR_HEIGHT) / 2)
	_bar.size = Vector2(text_width - _LEVEL_WIDTH - 4, _BAR_HEIGHT)
	_level.position = Vector2(SIZE.x - _PAD - _LEVEL_WIDTH, footer_y)
	_level.size = Vector2(_LEVEL_WIDTH, _LEVEL_HEIGHT)


## A recovering skill shows its seconds left where its level would be.
func _process(_delta: float) -> void:
	if not is_visible_in_tree():
		return
	var left := SkillCooldowns.remaining_seconds(skill_id)
	_level.text = ("%ds" % ceili(left)) if left > 0.0 else _level_text
	_level.add_theme_color_override("font_color", UiStyle.ACCENT if left > 0.0 else _level_color)


## [param level] is what the server confirmed and [param pending] the points queued on top of it but not yet
## applied. [param raisable] marks a tile a point could go into now, [param locked] one a requirement (a
## prerequisite, a tree that is still closed) keeps shut.
func show_skill(level: int, max_level: int, pending: int, raisable: bool, locked: bool) -> void:
	var attack := AttackDB.get_instance().get_attack(skill_id)
	_title.text = attack.name if attack != null else str(skill_id)
	_icon.texture = attack.get_icon() if attack != null else AttackResource.MISSING_ICON

	var shown := level + pending
	UiStyle.set_bar(_bar, shown, max_level)
	_level_text = "%d/%d" % [shown, max_level]
	if pending > 0:
		_level_color = UiStyle.ONLINE
	elif shown >= max_level:
		_level_color = UiStyle.ACCENT
	else:
		_level_color = UiStyle.MUTED

	var border := UiStyle.ACCENT if shown > 0 else (UiStyle.SELF_BORDER if raisable else UiStyle.OFFLINE)
	var bg := UiStyle.BAR_BG if locked else UiStyle.ROW_BG
	add_theme_stylebox_override("normal", UiStyle.flat(bg, 4, 6, border, 1))
	add_theme_stylebox_override("hover", UiStyle.flat(UiStyle.ROW_HOVER_BG, 4, 6, border, 1))
	add_theme_stylebox_override("pressed", UiStyle.flat(UiStyle.ROW_HOVER_BG, 4, 6, UiStyle.TEXT, 2))
	add_theme_stylebox_override("hover_pressed", UiStyle.flat(UiStyle.ROW_HOVER_BG, 4, 6, UiStyle.TEXT, 2))
	_title.add_theme_color_override("font_color", UiStyle.MUTED if locked else UiStyle.TEXT)
	_icon.modulate.a = 0.45 if locked else 1.0
	_level.text = _level_text
	_level.add_theme_color_override("font_color", _level_color)


func _gui_input(event: InputEvent) -> void:
	var click := event as InputEventMouseButton
	if click != null and click.double_click and click.button_index == MOUSE_BUTTON_LEFT:
		double_clicked.emit()
