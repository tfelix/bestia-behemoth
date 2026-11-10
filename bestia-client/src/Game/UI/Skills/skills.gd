extends PanelContainer
class_name Skills

## The Skills window: one tab per skill tree, the tree drawn as a [MasterSkillTree], and beside it what the
## picked skill does, what it needs and what it leads to. The master spends skill points here; a bestia's own
## skills show the same way, without anything to spend.
##
## Points are queued rather than sent one by one: Add a point raises the tile in green, and Apply sends every
## queued point in one InvestSkillPointCMSG, in the order they were queued, since the server applies them in
## order and an earlier point can open a later one. Reset drops them all.
##
## Built in code, like the guild window's Skills tab it mirrors, because the tree is all runtime tiles anyway.

## Tab order, and the only place a tree is ordered for a reader. A tree the server sends that is not listed
## here still gets a tab, appended after these, so a new tree in master_skill_tree.yml shows up rather than
## vanishing; its name is read from SKILL_TREE_<ID> in general.csv.
const TREE_ORDER: Array[String] = ["NOVICE", "CRAFTSMAN", "SURVIVAL", "SCHOLAR", "WARRIOR"]

## Skills outside the master tree - a bestia's own, or item-taught - carry no tree and collect here.
const NO_TREE := ""
const NOVICE_TREE := "NOVICE"

## Mirrors MasterSkillTreeService.SUB_TREE_UNLOCK_THRESHOLD - how many points must be spent anywhere in a
## tree before any of its sub-trees can be.
const SUB_TREE_UNLOCK_THRESHOLD := 5

## Basic Skill, and how far it must be taken before any tree but Novice opens - mirrors
## MasterSkillTreeService.TREE_UNLOCK_BASIC_SKILL_LEVEL. skills.yml pins Basic Skill at id 1 for exactly
## this: it is the one skill the client has to name, and naming it by id is the only way, since the wire
## carries ids and the Attack DB is keyed by them.
const BASIC_SKILL_ID := 1
const TREE_UNLOCK_BASIC_SKILL_LEVEL := 5

const _DETAILS_WIDTH := 236

# The account's own master entity id, used to tell whether the entity a SkillListSMSG describes is the
# master (spends points) or one of its bestias (never does: a bestia learns by level and by item).
var _master_entity_id: int = 0
var _current_entity_id: int = 0
var _is_master_view := false
var _available_skill_points: int = 0

## The skill ids of the last SkillListSMSG in the order the server sent them (master_skill_tree.yml's),
## and the level the server confirmed for each.
var _skill_ids: Array[int] = []
var _levels: Dictionary[int, int] = {}
## The Potency band each learned skill casts at now, from the server: 0 for none, 1 (Faint) to 5 (Overwhelming).
var _potency_bands: Dictionary[int, int] = {}
## One entry per queued point, in the order they were queued.
var _queued: Array[int] = []
## The level a skill is used at, from the picker in the details; a skill missing here uses its own level.
var _use_levels: Dictionary[int, int] = {}
## Tree identifier per tab index, in the order the tabs are shown.
var _tabbed_trees: Array[String] = []
var _selected_id := 0

var _tabs: TabBar
var _tree: MasterSkillTree
var _points: Label
var _icon: TextureRect
var _name: Label
var _level: Label
var _kind: Label
var _description: RichTextLabel
var _stats: Label
var _requirements: RichTextLabel
var _leads_to: RichTextLabel
var _use_level_row: HBoxContainer
var _use_level: Label
var _hint: Label
var _use: Button
var _unqueue: Button
var _add_point: Button
var _footer: HBoxContainer
var _queued_label: Label
var _reset: Button
var _apply: Button


func _ready() -> void:
	_build()
	ConnectionManager.connect("self_received", _on_self_received)
	ConnectionManager.connect("entity_received", _on_entity_received)
	_refresh()


## Called by Game/UI/ui.gd whenever the Skills window is opened, since the window content is a Control nested
## inside a Window - a Window's own visibility does not propagate as a visibility_changed signal to its content
## the way regular Control nesting would.
func request_refresh() -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.get_skills()


func _on_self_received(msg: SelfSMSG) -> void:
	_master_entity_id = msg.MasterEntityId


func _on_entity_received(msg: EntitySMSG) -> void:
	if msg is SkillListSMSG:
		_show_list(msg)
	elif msg is SkillPointsComponentSMSG:
		# Gated on the master entity, not on whatever list happens to be open: skill points only ever belong
		# to the master, and this must stay accurate while a bestia's skills are open or the window is closed.
		if msg.EntityId == _master_entity_id:
			_available_skill_points = msg.Points
			_replay_queue()
			_refresh()


## A fresh list is also the server's answer to Apply, so whatever was queued is now either learned or refused,
## and the queue starts over.
func _show_list(msg: SkillListSMSG) -> void:
	_current_entity_id = msg.EntityId
	_is_master_view = msg.EntityId == _master_entity_id
	if _is_master_view:
		_seed_skill_points_from_cache()

	_skill_ids.clear()
	_levels.clear()
	_potency_bands.clear()
	for entry in msg.Skills:
		_skill_ids.append(entry.SkillId)
		_levels[entry.SkillId] = entry.Level
		_potency_bands[entry.SkillId] = entry.PotencyBand
	_queued.clear()
	for id in _use_levels.keys():
		if not _levels.has(id):
			_use_levels.erase(id)
	if not _levels.has(_selected_id):
		_selected_id = 0

	_rebuild_tabs()
	_show_title()
	_refresh()


## Seeds the point count from the master Entity's cache (kept up to date by entity_manager.gd/entity.gd
## whether or not this window was ever open), so it is right the moment the window opens instead of only
## after the next SkillPointsComponentSMSG happens to arrive.
func _seed_skill_points_from_cache() -> void:
	var entity := _entity(_master_entity_id)
	if entity:
		_available_skill_points = entity.get_skill_points()


func _entity(entity_id: int) -> Entity:
	var entity_manager := EntityManager.get_instance()
	return entity_manager.get_entity(entity_id) if entity_manager else null


## Whose skills these are, in the title bar: the window shows a bestia's as readily as the master's.
func _show_title() -> void:
	var window := get_parent() as WidgetWindow
	if window == null:
		return
	var entity := _entity(_current_entity_id)
	var owner_name := entity.get_display_name() if entity else ""
	window.window_title = tr("SKILLS_TITLE") if owner_name.is_empty() else "%s · %s" % [tr("SKILLS_TITLE"), owner_name]


# --- Building -----------------------------------------------------------------------------------------------


func _build() -> void:
	var rows := UiStyle.vbox(6)
	add_child(UiStyle.margin(rows, 8, 6, 8, 8))

	_tabs = TabBar.new()
	_tabs.clip_tabs = false
	_tabs.tab_changed.connect(_on_tab_changed)
	rows.add_child(_tabs)

	var body := UiStyle.hbox(8)
	body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	rows.add_child(body)

	var scroll := ScrollContainer.new()
	scroll.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_tree = MasterSkillTree.new()
	_tree.skill_selected.connect(_on_skill_selected)
	_tree.skill_activated.connect(_use_skill)
	scroll.add_child(UiStyle.margin(_tree, 2, 2, 2, 2))
	body.add_child(scroll)
	body.add_child(_build_details())

	_footer = UiStyle.hbox(6)
	_queued_label = UiStyle.label("", 12, UiStyle.MUTED)
	_queued_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_footer.add_child(_queued_label)
	_reset = Button.new()
	_reset.text = tr("SKILLS_RESET")
	_reset.pressed.connect(_on_reset_pressed)
	_footer.add_child(_reset)
	_apply = Button.new()
	_apply.text = tr("SKILLS_APPLY")
	_apply.theme_type_variation = &"PrimaryButton"
	_apply.pressed.connect(_on_apply_pressed)
	_footer.add_child(_apply)
	rows.add_child(_footer)


func _build_details() -> Control:
	var card := PanelContainer.new()
	card.add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ROW_BG, 6, 10))
	card.custom_minimum_size = Vector2(_DETAILS_WIDTH, 0)
	var rows := UiStyle.vbox(6)
	card.add_child(rows)

	var points := UiStyle.hbox(6)
	points.add_child(UiStyle.label(tr("SKILLS_POINTS"), 12, UiStyle.MUTED))
	_points = UiStyle.label("", 12, UiStyle.ACCENT)
	points.add_child(_points)
	rows.add_child(points)
	rows.add_child(HSeparator.new())

	var head := UiStyle.hbox(8)
	_icon = TextureRect.new()
	_icon.custom_minimum_size = Vector2(40, 40)
	_icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	_icon.mouse_filter = Control.MOUSE_FILTER_STOP
	_icon.set_drag_forwarding(func(_at: Vector2) -> Variant: return _drag_data(_selected_id), Callable(), Callable())
	head.add_child(_icon)
	var title := UiStyle.vbox(0)
	title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_name = UiStyle.label("", 14, UiStyle.ACCENT)
	_name.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	title.add_child(_name)
	_level = UiStyle.label("", 12, UiStyle.MUTED)
	title.add_child(_level)
	head.add_child(title)
	rows.add_child(head)

	# The text scrolls on its own, so a long description never pushes the buttons below out of the window. The
	# bar's room is kept even while it is hidden: a bar that came and went would change the width the text
	# wraps to, and with it the height that decides whether the bar is needed, and layout never settled.
	var text_scroll := ScrollContainer.new()
	text_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	text_scroll.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_RESERVE
	text_scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var text := UiStyle.vbox(6)
	text.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	text_scroll.add_child(text)
	rows.add_child(text_scroll)

	_kind = UiStyle.label("", 11, UiStyle.MUTED)
	_kind.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	text.add_child(_kind)
	_description = _rich_text()
	text.add_child(_description)
	_stats = UiStyle.label("", 12, UiStyle.MUTED)
	_stats.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	text.add_child(_stats)
	_requirements = _rich_text()
	text.add_child(_requirements)
	_leads_to = _rich_text()
	text.add_child(_leads_to)

	_use_level_row = UiStyle.hbox(4)
	var use_level_caption := UiStyle.label(tr("SKILLS_USE_AT_LEVEL"), 12, UiStyle.MUTED)
	use_level_caption.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_use_level_row.add_child(use_level_caption)
	_use_level_row.add_child(_step_button("<", -1))
	_use_level = UiStyle.label("", 12)
	_use_level.custom_minimum_size = Vector2(24, 0)
	_use_level.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_use_level_row.add_child(_use_level)
	_use_level_row.add_child(_step_button(">", 1))
	rows.add_child(_use_level_row)

	_hint = UiStyle.label("", 11, UiStyle.MUTED)
	_hint.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	rows.add_child(_hint)

	_use = Button.new()
	_use.text = tr("SKILLS_USE")
	_use.pressed.connect(func() -> void: _use_skill(_selected_id))
	rows.add_child(_use)

	var invest := UiStyle.hbox(6)
	_unqueue = Button.new()
	_unqueue.text = "−"
	_unqueue.tooltip_text = tr("SKILLS_UNQUEUE")
	_unqueue.custom_minimum_size = Vector2(32, 0)
	_unqueue.pressed.connect(_on_unqueue_pressed)
	invest.add_child(_unqueue)
	_add_point = Button.new()
	_add_point.theme_type_variation = &"PrimaryButton"
	_add_point.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_add_point.pressed.connect(_on_add_point_pressed)
	invest.add_child(_add_point)
	rows.add_child(invest)
	return card


func _rich_text() -> RichTextLabel:
	var text := RichTextLabel.new()
	text.bbcode_enabled = true
	text.fit_content = true
	text.scroll_active = false
	text.add_theme_font_size_override("normal_font_size", 12)
	text.add_theme_color_override("default_color", UiStyle.MUTED)
	return text


func _step_button(glyph: String, step: int) -> Button:
	var button := Button.new()
	button.text = glyph
	button.focus_mode = Control.FOCUS_NONE
	button.pressed.connect(_on_use_level_step.bind(step))
	return button


# --- Tabs ---------------------------------------------------------------------------------------------------


## Rebuilds the tab strip from the trees this list contains, keeping the open tab when it survives: a point
## applied brings a fresh list, and being thrown back to the first tab on every Apply would be miserable.
func _rebuild_tabs() -> void:
	var present := {}
	for id in _skill_ids:
		present[_tree_of(id)] = true

	var previous := _current_tree()
	_tabbed_trees.clear()
	for tree in TREE_ORDER:
		if present.has(tree):
			_tabbed_trees.append(tree)
	for tree: String in present:
		if tree != NO_TREE and not _tabbed_trees.has(tree):
			_tabbed_trees.append(tree)
	if present.has(NO_TREE):
		_tabbed_trees.append(NO_TREE)

	# Silent throughout: adding the first tab already moves the current tab, and a refresh then would read
	# trees past the tabs that exist so far. The caller refreshes once the strip is whole.
	_tabs.set_block_signals(true)
	_tabs.clear_tabs()
	for tree in _tabbed_trees:
		_tabs.add_tab(_tree_name(tree))
	_tabs.visible = _tabbed_trees.size() > 1

	var restored := _tabbed_trees.find(previous)
	if _tabs.tab_count > 0:
		_tabs.current_tab = restored if restored >= 0 else 0
	_tabs.set_block_signals(false)


## Each tab says how many points sit in its tree, and a closed tree says why in its tooltip.
func _update_tab_titles(levels: Dictionary) -> void:
	for i in _tabbed_trees.size():
		var tree := _tabbed_trees[i]
		var points := _tree_points(tree, levels)
		var title := _tree_name(tree)
		if _is_master_view and tree != NO_TREE and points > 0:
			title = "%s (%d)" % [title, points]
		_tabs.set_tab_title(i, title)
		_tabs.set_tab_tooltip(i, _tree_lock_note(tree, levels))


## The details would otherwise go on describing a skill nobody can see.
func _on_tab_changed(_tab: int) -> void:
	if _tree_of(_selected_id) != _current_tree():
		_selected_id = 0
	_refresh()


func _current_tree() -> String:
	var index := _tabs.current_tab if _tabs else -1
	return _tabbed_trees[index] if index >= 0 and index < _tabbed_trees.size() else NO_TREE


func _tree_name(tree: String) -> String:
	return tr("SKILLS_TAB_OTHER") if tree == NO_TREE else _named("SKILL_TREE_" + tree, tree)


## A translation, or the identifier title-cased when there is none yet: a missing key comes back unchanged.
func _named(key: String, identifier: String) -> String:
	var text := tr(key)
	return identifier.capitalize() if text == key else text


# --- The rules the server applies, mirrored ----------------------------------------------------------------


func _tree_of(id: int) -> String:
	var attack := AttackDB.get_instance().get_attack(id)
	return attack.tree if attack else NO_TREE


func _sub_tree_of(id: int) -> String:
	var attack := AttackDB.get_instance().get_attack(id)
	return attack.sub_tree if attack else ""


func _max_level(id: int) -> int:
	var attack := AttackDB.get_instance().get_attack(id)
	return attack.max_level if attack else 1


func _skill_name(id: int) -> String:
	var attack := AttackDB.get_instance().get_attack(id)
	return attack.name if attack else str(id)


func _queued_on(id: int) -> int:
	return _queued.count(id)


## The confirmed levels with the queue on top, which is what every rule below is judged against: the server
## applies the queue in order, so a queued point counts for the ones after it.
func _queued_levels() -> Dictionary:
	var levels := _levels.duplicate()
	for id in _queued:
		levels[id] = levels.get(id, 0) + 1
	return levels


func _remaining_points() -> int:
	return _available_skill_points - _queued.size()


func _tree_points(tree: String, levels: Dictionary) -> int:
	var points := 0
	for id in _skill_ids:
		if _tree_of(id) == tree:
			points += levels.get(id, 0)
	return points


## Scholar and Warrior are nothing but a sub-tree today, and the server only gates a sub-tree behind its
## trunk when there is a trunk to spend on first (see MasterSkillTreeService.hasTrunkSkills).
func _tree_has_trunk(tree: String) -> bool:
	for id in _skill_ids:
		if _tree_of(id) == tree and _sub_tree_of(id).is_empty():
			return true
	return false


## Why a whole tree is still closed, or empty once it is open: every tree but Novice waits for Basic Skill.
func _tree_lock_note(tree: String, levels: Dictionary) -> String:
	if not _is_master_view or tree == NO_TREE or tree == NOVICE_TREE:
		return ""
	var basic: int = levels.get(BASIC_SKILL_ID, 0)
	if basic >= TREE_UNLOCK_BASIC_SKILL_LEVEL:
		return ""
	return tr("SKILLS_TREE_LOCKED") % [_skill_name(BASIC_SKILL_ID), TREE_UNLOCK_BASIC_SKILL_LEVEL, basic]


## Why a sub-tree is still closed, or empty once it is open: it needs points in its tree first.
func _sub_tree_lock_note(tree: String, sub_tree: String, levels: Dictionary) -> String:
	if not _is_master_view or sub_tree.is_empty() or not _tree_has_trunk(tree):
		return ""
	var points := _tree_points(tree, levels)
	if points >= SUB_TREE_UNLOCK_THRESHOLD:
		return ""
	return tr("SKILLS_SUB_TREE_LOCKED") % [SUB_TREE_UNLOCK_THRESHOLD, _tree_name(tree), points]


func _prerequisites_met(id: int, levels: Dictionary) -> bool:
	var attack := AttackDB.get_instance().get_attack(id)
	if attack == null:
		return true
	for i in attack.prerequisite_ids.size():
		if levels.get(attack.prerequisite_ids[i], 0) < attack.prerequisite_levels[i]:
			return false
	return true


## Whether a requirement keeps one more level of [param id] closed - mirrors MasterSkillTreeService.nextLevel,
## less the max level and the points, which are not requirements.
func _locked(id: int, levels: Dictionary) -> bool:
	if not _is_master_view or _tree_of(id) == NO_TREE:
		return false
	var tree := _tree_of(id)
	return not _tree_lock_note(tree, levels).is_empty() \
		or not _sub_tree_lock_note(tree, _sub_tree_of(id), levels).is_empty() \
		or not _prerequisites_met(id, levels)


## Whether a point could be queued on [param id] now. An item-taught skill has no tree node to raise.
func _raisable(id: int, levels: Dictionary, remaining: int) -> bool:
	return _is_master_view and _tree_of(id) != NO_TREE and remaining > 0 \
		and levels.get(id, 0) < _max_level(id) and not _locked(id, levels)


## Plays the queue again from the confirmed levels and keeps only the points that still hold: taking a point
## back can close something queued after it, and fewer points than were queued can arrive from the server.
func _replay_queue() -> void:
	var levels := _levels.duplicate()
	var remaining := _available_skill_points
	var kept: Array[int] = []
	for id in _queued:
		if _raisable(id, levels, remaining):
			levels[id] = levels.get(id, 0) + 1
			remaining -= 1
			kept.append(id)
	_queued = kept


# --- Showing ------------------------------------------------------------------------------------------------


func _refresh() -> void:
	var levels := _queued_levels()
	var remaining := _remaining_points()

	_update_tab_titles(levels)
	_tree.selected_skill_id = _selected_id
	_tree.show_tab(_sections(_current_tree(), levels), _tile_state.bind(levels, remaining), _drag_data)

	_points.get_parent().visible = _is_master_view
	_points.text = str(remaining)
	_footer.visible = _is_master_view
	_reset.disabled = _queued.is_empty()
	_apply.disabled = _queued.is_empty()
	_queued_label.text = tr("SKILLS_NOTHING_QUEUED") if _queued.is_empty() else tr("SKILLS_QUEUED") % _queued.size()
	_show_selected(levels, remaining)


## The open tab as [MasterSkillTree] sections: the trunk first, then each sub-tree in the order the server
## sends them, each with whatever still keeps it closed.
func _sections(tree: String, levels: Dictionary) -> Array:
	var trunk := {"title": "", "note": _tree_lock_note(tree, levels), "ids": []}
	var sections: Array = [trunk]
	var by_sub_tree := {}
	for id in _skill_ids:
		if _tree_of(id) != tree:
			continue
		var sub_tree := _sub_tree_of(id)
		if sub_tree.is_empty():
			trunk.ids.append(id)
			continue
		if not by_sub_tree.has(sub_tree):
			by_sub_tree[sub_tree] = {
				"title": _named("SKILL_SUB_TREE_" + sub_tree, sub_tree),
				"note": _sub_tree_lock_note(tree, sub_tree, levels),
				"ids": [],
			}
			sections.append(by_sub_tree[sub_tree])
		by_sub_tree[sub_tree].ids.append(id)
	return sections


func _tile_state(id: int, levels: Dictionary, remaining: int) -> Dictionary:
	var queued := _queued_on(id)
	return {
		"level": _levels.get(id, 0),
		"max_level": _max_level(id),
		"pending": queued,
		"raisable": _raisable(id, levels, remaining),
		"locked": levels.get(id, 0) == 0 and _locked(id, levels),
	}


func _show_selected(levels: Dictionary, remaining: int) -> void:
	var attack := AttackDB.get_instance().get_attack(_selected_id) if _levels.has(_selected_id) else null
	for part: Control in [_icon, _level, _kind, _description, _stats, _requirements, _leads_to, _use_level_row,
			_hint, _use, _unqueue, _add_point]:
		part.visible = attack != null
	if attack == null:
		_name.text = tr("SKILLS_PICK")
		return

	var id := _selected_id
	var level: int = _levels[id]
	var queued := _queued_on(id)
	var max_level := attack.max_level
	_icon.texture = attack.get_icon()
	_name.text = attack.name
	_level.text = tr("SKILLS_LEVEL") % [level + queued, max_level]
	if queued > 0:
		_level.text += "  " + tr("SKILLS_LEVEL_QUEUED") % queued

	_kind.text = _kind_text(attack)
	_description.text = tr(attack.description_key) if not attack.description_key.is_empty() else ""
	_description.visible = not _description.text.is_empty()
	_stats.text = _stats_text(attack, _potency_bands.get(id, 0))
	_stats.visible = not _stats.text.is_empty()
	_requirements.text = _requirements_text(id, levels)
	_requirements.visible = not _requirements.text.is_empty()
	_leads_to.text = _leads_to_text(id)
	_leads_to.visible = not _leads_to.text.is_empty()

	var usable := level > 0 and not attack.is_passive
	_use_level_row.visible = usable and level > 1
	_use_level.text = str(_use_level_of(id))
	_use.visible = usable
	_hint.visible = usable
	_hint.text = tr("SKILLS_DRAG_HINT")

	var investable := _is_master_view and _tree_of(id) != NO_TREE
	_unqueue.visible = investable and queued > 0
	_add_point.visible = investable
	var maxed: bool = levels.get(id, 0) >= max_level
	_add_point.text = tr("SKILLS_MAXED") if maxed else tr("SKILLS_ADD_POINT") % (levels.get(id, 0) + 1)
	_add_point.disabled = not _raisable(id, levels, remaining)
	_add_point.tooltip_text = tr("SKILLS_NO_POINTS") if remaining <= 0 and not maxed else ""


## A skill with a script of its own (crafting, self-casts) decides its target itself, so its target type,
## which defaults to the ground, says nothing about it.
func _kind_text(attack: AttackResource) -> String:
	if attack.is_passive:
		return tr("SKILLS_PASSIVE")
	if attack.attack_script != null:
		return tr("SKILLS_ACTIVE")
	return tr("SKILLS_TARGET_" + attack.target_type)


func _stats_text(attack: AttackResource, potency_band: int = 0) -> String:
	if attack.is_passive:
		return ""
	var parts: PackedStringArray = []
	if potency_band > 0:
		parts.append(tr("SKILLS_POTENCY") % tr("POTENCY_BAND_%d" % potency_band))
	if attack.mana_cost > 0:
		parts.append(tr("SKILLS_MANA") % attack.mana_cost)
	if attack.cooldown > 0.0:
		parts.append(tr("SKILLS_COOLDOWN") % String.num(attack.cooldown, 1))
	if attack.cast_time > 0.0:
		parts.append(tr("SKILLS_CAST_TIME") % String.num(attack.cast_time, 1))
	return "  ·  ".join(parts)


## Each prerequisite in green once met and red until then, and below them whatever closes the tree.
func _requirements_text(id: int, levels: Dictionary) -> String:
	var lines: PackedStringArray = []
	var attack := AttackDB.get_instance().get_attack(id)
	var parts: PackedStringArray = []
	for i in attack.prerequisite_ids.size():
		var required_id := attack.prerequisite_ids[i]
		var required_level := attack.prerequisite_levels[i]
		var color := UiStyle.ONLINE if levels.get(required_id, 0) >= required_level else UiStyle.DANGER
		parts.append("[color=#%s]%s Lv. %d[/color]" % [color.to_html(false), _skill_name(required_id), required_level])
	if not parts.is_empty():
		lines.append(tr("SKILLS_REQUIRES") % ", ".join(parts))

	var danger := UiStyle.DANGER.to_html(false)
	for note in [_tree_lock_note(_tree_of(id), levels), _sub_tree_lock_note(_tree_of(id), _sub_tree_of(id), levels)]:
		if not note.is_empty():
			lines.append("[color=#%s]%s[/color]" % [danger, note])
	return "\n".join(lines)


## The skills this one is a prerequisite of, and at what level it opens each.
func _leads_to_text(id: int) -> String:
	var parts: PackedStringArray = []
	for other in _skill_ids:
		var attack := AttackDB.get_instance().get_attack(other)
		if attack == null:
			continue
		var at := attack.prerequisite_ids.find(id)
		if at >= 0:
			parts.append("%s (Lv. %d)" % [attack.name, attack.prerequisite_levels[at]])
	return "" if parts.is_empty() else tr("SKILLS_LEADS_TO") % ", ".join(parts)


# --- Acting -------------------------------------------------------------------------------------------------


func _on_skill_selected(skill_id: int) -> void:
	_selected_id = skill_id
	_show_selected(_queued_levels(), _remaining_points())


func _use_level_of(id: int) -> int:
	return clampi(_use_levels.get(id, _levels.get(id, 1)), 1, maxi(_levels.get(id, 1), 1))


func _on_use_level_step(step: int) -> void:
	_use_levels[_selected_id] = clampi(_use_level_of(_selected_id) + step, 1, maxi(_levels.get(_selected_id, 1), 1))
	_use_level.text = str(_use_level_of(_selected_id))


## Double-click on a tile, or the Use button: the same path as the shortcut bar, so a targeted skill asks for
## its target rather than going out without one.
func _use_skill(id: int) -> void:
	var attack := AttackDB.get_instance().get_attack(id)
	if attack == null or attack.is_passive or _levels.get(id, 0) == 0:
		return
	attack.use_skill(_use_level_of(id))


## A learned skill that can be cast goes onto the shortcut bar at the level picked in the details (see
## ShortcutContainer._can_drop_data, which accepts "skill"). A passive has nothing to trigger, so it never
## leaves the window - the drop side rejects it as well, since a shortcut can also be dragged between slots.
func _drag_data(id: int) -> Variant:
	var attack := AttackDB.get_instance().get_attack(id)
	if attack == null or attack.is_passive or _levels.get(id, 0) == 0:
		return null

	var preview := TextureRect.new()
	preview.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	preview.size = Vector2(40, 40)
	preview.texture = attack.get_icon()
	set_drag_preview(preview)
	return {"type": "skill", "id": id, "level": _use_level_of(id)}


func _on_add_point_pressed() -> void:
	if _raisable(_selected_id, _queued_levels(), _remaining_points()):
		_queued.append(_selected_id)
		_refresh()


## Takes back the selected skill's last queued point, and with it anything queued later that needed it.
func _on_unqueue_pressed() -> void:
	var at := _queued.rfind(_selected_id)
	if at < 0:
		return
	_queued.remove_at(at)
	_replay_queue()
	_refresh()


func _on_reset_pressed() -> void:
	_queued.clear()
	_refresh()


## Sends the queue in the order it was built, consecutive points on one skill folded together. The server
## answers with a fresh SkillListSMSG once it is applied (see InvestSkillPointHandler), which clears the queue.
func _on_apply_pressed() -> void:
	if _queued.is_empty():
		return
	var investments: Array = []
	for id in _queued:
		if not investments.is_empty() and investments[-1]["attack_id"] == id:
			investments[-1]["amount"] += 1
		else:
			investments.append({"attack_id": id, "amount": 1})
	ConnectionManager.invest_skill_points(investments)
