class_name GuildSkillTree
extends Control

## The guild skill tree as tiles on a grid, with a line from every prerequisite to the skill that needs it.
## The server sends the tree and its rules; where each tile sits is decided here.

signal skill_selected(skill_id: int)

const TILE_SIZE := Vector2(132, 64)
const GAP := Vector2(14, 6)

## Inside a tile: the icon on the left, the name beside it in up to two lines, and under the name its own row
## for the level strip and the level, kept clear of the name by [constant FOOTER_GAP].
const PAD := 6.0
const ICON_SIZE := 40.0
## Two lines of the 11 px name, with the label's own line spacing taken out.
const NAME_HEIGHT := 32.0
const FOOTER_GAP := 6.0
const LEVEL_WIDTH := 30.0
const LEVEL_HEIGHT := 14.0
const BAR_HEIGHT := 4.0

## Skill id to grid cell (column, row). Prerequisites always sit in a column further left, so every line
## runs rightwards. A skill missing here goes into a row of its own below the rest.
const CELLS := {
	9001: Vector2i(0, 0), 9002: Vector2i(1, 0), 9005: Vector2i(2, 0), 9006: Vector2i(3, 0),
	9007: Vector2i(2, 1),
	9003: Vector2i(2, 2), 9004: Vector2i(3, 2),
	9008: Vector2i(0, 3), 9017: Vector2i(1, 3), 9018: Vector2i(2, 3), 9019: Vector2i(3, 3),
	9020: Vector2i(3, 4), 9021: Vector2i(4, 4),
	9009: Vector2i(0, 5), 9010: Vector2i(1, 5), 9015: Vector2i(2, 5),
	9016: Vector2i(1, 6),
	9011: Vector2i(0, 7), 9012: Vector2i(1, 7),
	9013: Vector2i(0, 8), 9014: Vector2i(1, 8),
}

const LINE_MET := Color(1.0, 0.82, 0.35, 0.7)
const LINE_UNMET := Color(0.32, 0.32, 0.36)

var selected_skill_id := 0

var _skills: Array = []
var _levels: Dictionary[int, int] = {}
var _tiles: Dictionary[int, Button] = {}
var _cells: Dictionary[int, Vector2i] = {}
var _may_cast := false


## A recovering skill shows its seconds left where its level would be.
func _process(_delta: float) -> void:
	if not is_visible_in_tree():
		return
	for skill in _skills:
		var tile: Button = _tiles.get(skill.SkillId)
		if tile == null:
			continue
		var left := SkillCooldowns.remaining_seconds(skill.SkillId)
		var level := tile.get_node("Level") as Label
		level.text = ("%ds" % ceili(left)) if left > 0.0 else "%d/%d" % [skill.Level, skill.MaxLevel]
		level.add_theme_color_override("font_color", UiStyle.ACCENT if left > 0.0 else UiStyle.MUTED)


## [param may_raise] answers for one [GuildSkillInfo] whether the viewer could spend a point on it now;
## [param may_cast] whether the viewer's rank may cast guild skills, and so drag them to the shortcut bar.
func show_tree(skills: Array, may_raise: Callable, may_cast: bool) -> void:
	_skills = skills
	_may_cast = may_cast
	_levels.clear()
	for skill in skills:
		_levels[skill.SkillId] = skill.Level

	_place_cells()
	var seen: Dictionary[int, bool] = {}
	for skill in skills:
		seen[skill.SkillId] = true
		var tile: Button = _tiles.get(skill.SkillId)
		if tile == null:
			tile = _new_tile(skill.SkillId)
		tile.position = _tile_origin(_cells[skill.SkillId])
		_style_tile(tile, skill, may_raise.call(skill))

	for skill_id in _tiles.keys():
		if not seen.has(skill_id):
			_tiles[skill_id].queue_free()
			_tiles.erase(skill_id)

	custom_minimum_size = _canvas_size()
	queue_redraw()


func level_of(skill_id: int) -> int:
	return _levels.get(skill_id, 0)


func prerequisites_met(skill: GuildSkillInfo) -> bool:
	for i in skill.PrerequisiteSkillIds.size():
		if level_of(skill.PrerequisiteSkillIds[i]) < skill.PrerequisiteLevels[i]:
			return false
	return true


func select(skill_id: int) -> void:
	selected_skill_id = skill_id
	for id in _tiles.keys():
		_tiles[id].button_pressed = id == skill_id
	skill_selected.emit(skill_id)


func _place_cells() -> void:
	_cells.clear()
	var unplaced_column := 0
	var unplaced_row := 0
	for cell: Vector2i in CELLS.values():
		unplaced_row = maxi(unplaced_row, cell.y + 1)

	for skill in _skills:
		if CELLS.has(int(skill.SkillId)):
			_cells[skill.SkillId] = CELLS[int(skill.SkillId)]
		else:
			_cells[skill.SkillId] = Vector2i(unplaced_column, unplaced_row)
			unplaced_column += 1


func _new_tile(skill_id: int) -> Button:
	var tile := Button.new()
	tile.toggle_mode = true
	tile.focus_mode = Control.FOCUS_NONE
	tile.size = TILE_SIZE
	tile.pressed.connect(select.bind(skill_id))
	tile.set_drag_forwarding(_drag_data.bind(skill_id), Callable(), Callable())
	# In the tree before its labels are sized: until then a label measures with the default 16 px font, and a
	# size set then keeps that taller height, which pushed the level text below the bar.
	add_child(tile)

	var icon := TextureRect.new()
	icon.name = "Icon"
	icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	icon.mouse_filter = Control.MOUSE_FILTER_IGNORE
	icon.position = Vector2(PAD, (TILE_SIZE.y - ICON_SIZE) / 2)
	icon.size = Vector2(ICON_SIZE, ICON_SIZE)
	tile.add_child(icon)

	var text_x := PAD + ICON_SIZE + PAD
	var text_width := TILE_SIZE.x - text_x - PAD

	# A label rather than the button's own text, which would grow the tile to fit a long name.
	var title := UiStyle.label("", 11)
	title.name = "Title"
	title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	title.vertical_alignment = VERTICAL_ALIGNMENT_TOP
	title.clip_text = true
	title.add_theme_constant_override("line_spacing", 0)
	title.mouse_filter = Control.MOUSE_FILTER_IGNORE
	tile.add_child(title)
	title.position = Vector2(text_x, PAD - 2)
	title.size = Vector2(text_width, NAME_HEIGHT)

	var footer_y := title.position.y + NAME_HEIGHT + FOOTER_GAP
	var bar := UiStyle.bar(UiStyle.ACCENT, int(BAR_HEIGHT))
	bar.name = "Bar"
	bar.mouse_filter = Control.MOUSE_FILTER_IGNORE
	bar.position = Vector2(text_x, footer_y + (LEVEL_HEIGHT - BAR_HEIGHT) / 2)
	bar.size = Vector2(text_width - LEVEL_WIDTH - 4, BAR_HEIGHT)
	tile.add_child(bar)

	var level := UiStyle.label("", 10, UiStyle.MUTED)
	level.name = "Level"
	level.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	level.mouse_filter = Control.MOUSE_FILTER_IGNORE
	tile.add_child(level)
	level.position = Vector2(TILE_SIZE.x - PAD - LEVEL_WIDTH, footer_y)
	level.size = Vector2(LEVEL_WIDTH, LEVEL_HEIGHT)

	_tiles[skill_id] = tile
	return tile


## A learned guild skill the viewer may cast goes onto the shortcut bar like any other skill.
func _drag_data(_at_position: Vector2, skill_id: int) -> Variant:
	var attack := AttackDB.get_instance().get_attack(skill_id)
	if not _may_cast or attack == null or attack.is_passive or level_of(skill_id) == 0:
		return null

	var preview := TextureRect.new()
	preview.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	preview.size = Vector2(40, 40)
	preview.texture = attack.get_icon()
	set_drag_preview(preview)
	return {"type": "skill", "id": skill_id, "level": level_of(skill_id)}


func _style_tile(tile: Button, skill: GuildSkillInfo, raisable: bool) -> void:
	var attack := AttackDB.get_instance().get_attack(skill.SkillId)
	var title := tile.get_node("Title") as Label
	title.text = attack.name if attack != null else str(skill.SkillId)
	(tile.get_node("Level") as Label).text = "%d/%d" % [skill.Level, skill.MaxLevel]
	UiStyle.set_bar(tile.get_node("Bar") as ProgressBar, skill.Level, skill.MaxLevel)
	var icon := tile.get_node("Icon") as TextureRect
	icon.texture = attack.get_icon() if attack != null else AttackResource.MISSING_ICON

	var learned := skill.Level > 0
	var locked := not learned and not prerequisites_met(skill)
	var border := UiStyle.ACCENT if learned else (UiStyle.SELF_BORDER if raisable else UiStyle.OFFLINE)
	var bg := UiStyle.BAR_BG if locked else UiStyle.ROW_BG
	var text := UiStyle.MUTED if locked else UiStyle.TEXT

	tile.add_theme_stylebox_override("normal", UiStyle.flat(bg, 4, 6, border, 1))
	tile.add_theme_stylebox_override("hover", UiStyle.flat(UiStyle.ROW_HOVER_BG, 4, 6, border, 1))
	tile.add_theme_stylebox_override("pressed", UiStyle.flat(UiStyle.ROW_HOVER_BG, 4, 6, UiStyle.TEXT, 2))
	tile.add_theme_stylebox_override("hover_pressed", UiStyle.flat(UiStyle.ROW_HOVER_BG, 4, 6, UiStyle.TEXT, 2))
	title.add_theme_color_override("font_color", text)
	icon.modulate.a = 0.45 if locked else 1.0
	tile.button_pressed = skill.SkillId == selected_skill_id


func _tile_origin(cell: Vector2i) -> Vector2:
	return Vector2(cell) * (TILE_SIZE + GAP)


func _canvas_size() -> Vector2:
	var far := Vector2i.ZERO
	for cell in _cells.values():
		far = Vector2i(maxi(far.x, cell.x), maxi(far.y, cell.y))
	return _tile_origin(far) + TILE_SIZE


## Each line leaves its prerequisite's right edge, turns in the gap before the skill's column and enters the
## skill from the left, so lines into one skill share their last stretch.
func _draw() -> void:
	for skill in _skills:
		var to := _tile_origin(_cells[skill.SkillId])
		var entry := to + Vector2(0, TILE_SIZE.y / 2)
		var turn_x := to.x - GAP.x / 2

		for i in skill.PrerequisiteSkillIds.size():
			var from_id: int = skill.PrerequisiteSkillIds[i]
			if not _cells.has(from_id):
				continue
			var exit := _tile_origin(_cells[from_id]) + Vector2(TILE_SIZE.x, TILE_SIZE.y / 2)
			var met: bool = level_of(from_id) >= skill.PrerequisiteLevels[i]
			var points := PackedVector2Array([exit, Vector2(turn_x, exit.y), Vector2(turn_x, entry.y), entry])
			draw_polyline(points, LINE_MET if met else LINE_UNMET, 2.0)
