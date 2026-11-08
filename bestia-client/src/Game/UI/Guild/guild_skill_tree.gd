class_name GuildSkillTree
extends Control

## The guild skill tree as tiles on a grid, with a line from every prerequisite to the skill that needs it.
## The server sends the tree and its rules; where each tile sits is decided here. The tiles are the same
## [SkillTile]s the master's Skills window draws, so both trees read alike.

signal skill_selected(skill_id: int)

const GAP := Vector2(14, 6)

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
var _tiles: Dictionary[int, SkillTile] = {}
var _cells: Dictionary[int, Vector2i] = {}
var _may_cast := false


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
		var tile: SkillTile = _tiles.get(skill.SkillId)
		if tile == null:
			tile = _new_tile(skill.SkillId)
		tile.position = _tile_origin(_cells[skill.SkillId])
		var locked: bool = skill.Level == 0 and not prerequisites_met(skill)
		tile.show_skill(skill.Level, skill.MaxLevel, 0, may_raise.call(skill), locked)
		tile.button_pressed = skill.SkillId == selected_skill_id

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


func _new_tile(skill_id: int) -> SkillTile:
	var tile := SkillTile.new(skill_id)
	tile.pressed.connect(select.bind(skill_id))
	tile.set_drag_forwarding(_drag_data.bind(skill_id), Callable(), Callable())
	add_child(tile)
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


func _tile_origin(cell: Vector2i) -> Vector2:
	return Vector2(cell) * (SkillTile.SIZE + GAP)


func _canvas_size() -> Vector2:
	var far := Vector2i.ZERO
	for cell in _cells.values():
		far = Vector2i(maxi(far.x, cell.x), maxi(far.y, cell.y))
	return _tile_origin(far) + SkillTile.SIZE


## Each line leaves its prerequisite's right edge, turns in the gap before the skill's column and enters the
## skill from the left, so lines into one skill share their last stretch.
func _draw() -> void:
	for skill in _skills:
		var to := _tile_origin(_cells[skill.SkillId])
		var entry := to + Vector2(0, SkillTile.SIZE.y / 2)
		var turn_x := to.x - GAP.x / 2

		for i in skill.PrerequisiteSkillIds.size():
			var from_id: int = skill.PrerequisiteSkillIds[i]
			if not _cells.has(from_id):
				continue
			var exit := _tile_origin(_cells[from_id]) + Vector2(SkillTile.SIZE.x, SkillTile.SIZE.y / 2)
			var met: bool = level_of(from_id) >= skill.PrerequisiteLevels[i]
			var points := PackedVector2Array([exit, Vector2(turn_x, exit.y), Vector2(turn_x, entry.y), entry])
			draw_polyline(points, LINE_MET if met else LINE_UNMET, 2.0)
