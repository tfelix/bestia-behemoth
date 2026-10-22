class_name GuildSkillTree
extends Control

## The guild skill tree as tiles on a grid, with a line from every prerequisite to the skill that needs it.
## The server sends the tree and its rules; where each tile sits is decided here.

signal skill_selected(skill_id: int)

const TILE_SIZE := Vector2(94, 46)
const GAP := Vector2(16, 6)

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


## [param may_raise] answers for one [GuildSkillInfo] whether the viewer could spend a point on it now.
func show_tree(skills: Array, may_raise: Callable) -> void:
	_skills = skills
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

	# A label rather than the button's own text, which would grow the tile to fit a long name.
	var title := UiStyle.label("", 11)
	title.name = "Title"
	title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	title.vertical_alignment = VERTICAL_ALIGNMENT_TOP
	title.clip_text = true
	title.mouse_filter = Control.MOUSE_FILTER_IGNORE
	title.position = Vector2(6, 2)
	title.size = Vector2(TILE_SIZE.x - 12, 34)
	tile.add_child(title)

	var level := UiStyle.label("", 10, UiStyle.MUTED)
	level.name = "Level"
	level.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	level.mouse_filter = Control.MOUSE_FILTER_IGNORE
	level.position = Vector2(TILE_SIZE.x - 46, TILE_SIZE.y - 14)
	level.size = Vector2(40, 13)
	tile.add_child(level)

	add_child(tile)
	_tiles[skill_id] = tile
	return tile


func _style_tile(tile: Button, skill: GuildSkillInfo, raisable: bool) -> void:
	var attack := AttackDB.get_instance().get_attack(skill.SkillId)
	var title := tile.get_node("Title") as Label
	title.text = attack.name if attack != null else str(skill.SkillId)
	(tile.get_node("Level") as Label).text = "%d/%d" % [skill.Level, skill.MaxLevel]

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
