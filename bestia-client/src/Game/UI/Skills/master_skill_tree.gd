class_name MasterSkillTree
extends Control

## One tab of the Skills window: a tree's skills as [SkillTile]s on a grid, a heading over each sub-tree, and a
## line from every prerequisite to the skill that needs it.
##
## Unlike the guild tree, nothing here names a cell: the tree is laid out from the prerequisites the Attack DB
## carries, so a skill added to master_skill_tree.yml finds its own place. Within a section a skill sits one
## column right of its deepest prerequisite, on its parent's row if it is the first child and on the next free
## row otherwise; skills that neither need nor lead to anything share rows at the bottom of the section.
## A requirement from another section (Forge Weapon needs the trunk's Item Customization) draws no line, as it
## would cross the headings: the details pane names it instead.

signal skill_selected(skill_id: int)
## Double-click, the window's shortcut for using a skill.
signal skill_activated(skill_id: int)

const GAP := Vector2(14, 6)
## The narrowest a tab is drawn, so the window keeps its width from tab to tab.
const MIN_COLUMNS := 4
const HEADER_HEIGHT := 18.0
const HEADER_GAP := 4.0
const SECTION_GAP := 14.0

const LINE_MET := Color(1.0, 0.82, 0.35, 0.7)
const LINE_UNMET := Color(0.32, 0.32, 0.36)

var selected_skill_id := 0

var _tiles: Dictionary[int, SkillTile] = {}
var _headers: Array[Control] = []
## Start and end of every line, and whether its requirement is met.
var _lines: Array[Dictionary] = []


## [param sections] is the tab top to bottom, each a Dictionary with
## [code]title[/code] (empty for the tree's own trunk, which then has no heading unless it has a note),
## [code]note[/code] (what still keeps the section closed, empty once it is open) and [code]ids[/code], the
## section's skill ids in the order the server sent them.
##
## [param state] answers for a skill id a Dictionary with [code]level[/code], [code]max_level[/code],
## [code]pending[/code], [code]raisable[/code] and [code]locked[/code], the arguments of
## [method SkillTile.show_skill]. [param drag] answers the drag data for a skill id, or null.
func show_tab(sections: Array, state: Callable, drag: Callable) -> void:
	for header in _headers:
		header.queue_free()
	_headers.clear()
	_lines.clear()

	var seen: Dictionary[int, bool] = {}
	var y := 0.0
	var columns := MIN_COLUMNS

	for section: Dictionary in sections:
		var ids: Array = section.ids
		# A tree with no trunk (Scholar is only its Priest sub-tree) still says why it is closed.
		if ids.is_empty() and section.note.is_empty():
			continue
		if y > 0.0:
			y += SECTION_GAP
		if not section.title.is_empty() or not section.note.is_empty():
			_add_header(section.title, section.note, y)
			y += HEADER_HEIGHT + HEADER_GAP

		var cells := _layout(ids)
		var rows := 0
		for id: int in ids:
			var cell: Vector2i = cells[id]
			rows = maxi(rows, cell.y + 1)
			columns = maxi(columns, cell.x + 1)
			seen[id] = true

			var tile: SkillTile = _tiles.get(id)
			if tile == null:
				tile = _new_tile(id, drag)
			tile.position = Vector2(cell.x * (SkillTile.SIZE.x + GAP.x), y + cell.y * (SkillTile.SIZE.y + GAP.y))
			var s: Dictionary = state.call(id)
			tile.show_skill(s.level, s.max_level, s.pending, s.raisable, s.locked)
			tile.button_pressed = id == selected_skill_id

		_add_lines(ids, state)
		if rows > 0:
			y += rows * (SkillTile.SIZE.y + GAP.y) - GAP.y
		else:
			y -= HEADER_GAP

	for id in _tiles.keys():
		if not seen.has(id):
			_tiles[id].queue_free()
			_tiles.erase(id)

	custom_minimum_size = Vector2(columns * (SkillTile.SIZE.x + GAP.x) - GAP.x, y)
	queue_redraw()


func select(skill_id: int) -> void:
	selected_skill_id = skill_id
	for id in _tiles.keys():
		_tiles[id].button_pressed = id == skill_id
	skill_selected.emit(skill_id)


## Grid cell (column, row) per skill id, rows counted from the section's own top.
func _layout(ids: Array) -> Dictionary:
	var in_section: Dictionary[int, bool] = {}
	for id: int in ids:
		in_section[id] = true

	var parents: Dictionary[int, Array] = {}
	for id: int in ids:
		parents[id] = _prerequisites(id).filter(func(p: int) -> bool: return in_section.has(p))

	var depth: Dictionary[int, int] = {}
	for id: int in ids:
		_depth_of(id, parents, depth, {})

	# A skill hangs under its deepest prerequisite, the one that decided its column.
	var children: Dictionary[int, Array] = {}
	for id: int in ids:
		children[id] = []
	for id: int in ids:
		var primary := -1
		for parent: int in parents[id]:
			if primary == -1 or depth[parent] > depth[primary]:
				primary = parent
		if primary != -1:
			children[primary].append(id)

	var cells := {}
	var next_row := 0
	var loners: Array[int] = []
	for id: int in ids:
		if not parents[id].is_empty():
			continue
		if children[id].is_empty():
			loners.append(id)
		else:
			next_row = _place(id, next_row, depth, children, cells) + 1

	var column := 0
	for id in loners:
		if column == MIN_COLUMNS:
			column = 0
			next_row += 1
		cells[id] = Vector2i(column, next_row)
		column += 1
	return cells


## Places [param id] on [param row] and its children to its right, and answers the last row it used.
func _place(id: int, row: int, depth: Dictionary, children: Dictionary, cells: Dictionary) -> int:
	cells[id] = Vector2i(depth[id], row)
	var last := row
	var first := true
	for child: int in children[id]:
		last = _place(child, row if first else last + 1, depth, children, cells)
		first = false
	return maxi(row, last)


## A cycle in the data would recurse forever, so a skill met again on its own path counts as a root.
func _depth_of(id: int, parents: Dictionary, depth: Dictionary, visiting: Dictionary) -> int:
	if depth.has(id):
		return depth[id]
	if visiting.has(id):
		return 0
	visiting[id] = true
	var deepest := -1
	for parent: int in parents[id]:
		deepest = maxi(deepest, _depth_of(parent, parents, depth, visiting))
	depth[id] = deepest + 1
	return depth[id]


func _prerequisites(id: int) -> Array:
	var attack := AttackDB.get_instance().get_attack(id)
	return Array(attack.prerequisite_ids) if attack != null else []


func _add_header(title: String, note: String, y: float) -> void:
	var row := UiStyle.hbox(8)
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(row)
	if not title.is_empty():
		row.add_child(UiStyle.label(title, 12, UiStyle.TEXT))
	if not note.is_empty():
		row.add_child(UiStyle.label(note, 11, UiStyle.DANGER))
	row.position = Vector2(0, y)
	_headers.append(row)


## Each line leaves its prerequisite's right edge, turns in the gap before the skill's column and enters the
## skill from the left, so lines into one skill share their last stretch.
func _add_lines(ids: Array, state: Callable) -> void:
	for id: int in ids:
		var attack := AttackDB.get_instance().get_attack(id)
		if attack == null:
			continue
		var to := _tiles[id].position
		var entry := to + Vector2(0, SkillTile.SIZE.y / 2)
		var turn_x := to.x - GAP.x / 2
		for i in attack.prerequisite_ids.size():
			var from_id := attack.prerequisite_ids[i]
			if not ids.has(from_id):
				continue
			var from_state: Dictionary = state.call(from_id)
			var exit := _tiles[from_id].position + Vector2(SkillTile.SIZE.x, SkillTile.SIZE.y / 2)
			_lines.append({
				"points": PackedVector2Array([exit, Vector2(turn_x, exit.y), Vector2(turn_x, entry.y), entry]),
				"met": from_state.level + from_state.pending >= attack.prerequisite_levels[i],
			})


func _draw() -> void:
	for line in _lines:
		draw_polyline(line.points, LINE_MET if line.met else LINE_UNMET, 2.0)


func _new_tile(skill_id: int, drag: Callable) -> SkillTile:
	var tile := SkillTile.new(skill_id)
	tile.pressed.connect(select.bind(skill_id))
	tile.double_clicked.connect(func() -> void: skill_activated.emit(skill_id))
	tile.set_drag_forwarding(func(_at: Vector2) -> Variant: return drag.call(skill_id), Callable(), Callable())
	add_child(tile)
	_tiles[skill_id] = tile
	return tile
