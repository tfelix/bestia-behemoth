extends PanelContainer
class_name Skills

const SkillRowScene = preload("res://Game/UI/Skills/SkillRow/SkillRow.tscn")

## Tab order, and the only place a tree is spelled for a reader. A tree the server sends that is not
## listed here still gets a tab, appended after these with its identifier title-cased - so a new tree in
## master_skill_tree.yml shows up rather than vanishing, it just sorts last until it is named here.
const TREE_ORDER: Array[String] = ["NOVICE", "CRAFTSMAN", "SURVIVAL", "SCHOLAR", "WARRIOR"]
const TREE_NAMES := {
	"NOVICE": "Novice",
	"CRAFTSMAN": "Craftsman",
	"SURVIVAL": "Survival",
	"SCHOLAR": "Scholar",
	"WARRIOR": "Warrior",
}
const SUB_TREE_NAMES := {
	"BLACKSMITH": "Blacksmith",
	"ARTIFICER": "Artificer",
	"ALCHEMIST": "Alchemist",
	"FORESTER": "Forester",
	"PROSPECTOR": "Prospector",
	"MINER": "Miner",
	"PRIEST": "Priest",
	"WIZARD": "Wizard",
}

## Skills outside the master tree - a bestia's own, or item-taught - carry no tree and collect here.
const NO_TREE := ""
const NO_TREE_TAB_NAME := "Bestia"

const _SUB_TREE_HEADER_COLOR := Color(0.75, 0.75, 0.8)
const _SUB_TREE_HEADER_FONT_SIZE := 12

@onready var _search_line_edit = %SearchLineEdit
@onready var _tree_tabs: TabBar = %TreeTabs
@onready var _skill_rows = %SkillRows
@onready var _skill_points_label: Label = %SkillPointsLabel
@onready var _confirm_button: Button = %ConfirmButton
@onready var _cancel_button: Button = %CancelButton

# The account's own master entity id, used to tell whether the entity a SkillListSMSG describes
# is the master (show Skill Points) or one of its bestias (hide it) - bestias never learn skills
# via spendable points, only via level-up or item-taught custom skills.
var _master_entity_id: int = 0
var _current_entity_id: int = 0
var _available_skill_points: int = 0
var _selected_row: Control = null

## Tree identifier per tab index, in the order the tabs are shown.
var _tabbed_trees: Array[String] = []


func _ready() -> void:
	_skill_points_label.visible = false
	_confirm_button.visible = false
	_cancel_button.visible = false
	ConnectionManager.connect("self_received", _on_self_received)
	ConnectionManager.connect("entity_received", _on_entity_received)


## Called by Game/UI/ui.gd whenever the Skills window is opened, since the window content is
## a Control nested inside a Window - a Window's own visibility does not propagate as a
## visibility_changed signal to its content the way regular Control nesting would.
func request_refresh() -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.get_skills()


func _on_self_received(msg: SelfSMSG) -> void:
	_master_entity_id = msg.MasterEntityId


func _on_entity_received(msg: EntitySMSG) -> void:
	if msg is SkillListSMSG:
		_current_entity_id = msg.EntityId
		_skill_points_label.visible = msg.EntityId == _master_entity_id
		if msg.EntityId == _master_entity_id:
			_seed_skill_points_from_cache()
		_populate_rows(msg)
	elif msg is SkillPointsComponentSMSG:
		# Gated on the master entity, not on whatever tree happens to be open
		# (_current_entity_id): skill points only ever belong to the master, and
		# this must stay accurate even while browsing a bestia's tree or with the
		# window closed entirely, so it's correct as soon as it becomes visible again.
		if msg.EntityId == _master_entity_id:
			_available_skill_points = msg.Points
			_update_skill_row_buttons()


## Seeds the displayed point count from the master Entity's cache (kept up to date by
## entity_manager.gd/entity.gd regardless of whether this window has ever been open),
## so the label is correct the moment the window opens instead of only after the next
## live SkillPointsComponentSMSG happens to arrive.
func _seed_skill_points_from_cache() -> void:
	var entity_manager := EntityManager.get_instance()
	var entity: Entity = entity_manager.get_entity(_master_entity_id) if entity_manager else null
	if entity:
		_available_skill_points = entity.get_skill_points()


func _populate_rows(msg: SkillListSMSG) -> void:
	# Detached before being freed, not just queue_free()d: freeing is deferred to the end of the frame,
	# and the tab rebuild and filter pass below would otherwise still walk the outgoing rows.
	for child in _skill_rows.get_children():
		_skill_rows.remove_child(child)
		child.queue_free()
	_selected_row = null

	var is_master_view = msg.EntityId == _master_entity_id
	var last_group := [null, null]

	for entry in msg.Skills:
		var row = SkillRowScene.instantiate()
		var tree: String = _tree_of(entry)
		var sub_tree: String = _sub_tree_of(entry)

		# The server sends the tree contiguously (see MasterSkillTreeRegistry), so a change of sub-tree
		# is where its heading belongs. A tree's own roots have no sub-tree and so get no heading.
		if not sub_tree.is_empty() and [tree, sub_tree] != last_group:
			_skill_rows.add_child(_sub_tree_header(tree, sub_tree))
		last_group = [tree, sub_tree]

		_skill_rows.add_child(row)
		row.set_meta("skill_tree", tree)
		row.set_meta("sub_tree", sub_tree)

		row.initialize(entry)
		row.set_is_master_row(is_master_view)
		row.row_selected.connect(_on_row_selected)
		row.investment_changed.connect(_on_row_investment_changed)

	_rebuild_tabs(msg)
	_update_skill_row_buttons()
	_apply_filters()


## The tree a skill belongs to, from the client's own Attack DB - the wire carries only an id and a
## level. An unknown skill has no catalogue entry to ask, and lands in the no-tree tab beside the
## bestia skills.
func _tree_of(entry: SkillListEntry) -> String:
	var attack: AttackResource = AttackDB.get_instance().get_attack(entry.SkillId)
	return attack.tree if attack else NO_TREE


func _sub_tree_of(entry: SkillListEntry) -> String:
	var attack: AttackResource = AttackDB.get_instance().get_attack(entry.SkillId)
	return attack.sub_tree if attack else ""


func _sub_tree_header(tree: String, sub_tree: String) -> Label:
	var header := Label.new()
	header.text = SUB_TREE_NAMES.get(sub_tree, sub_tree.capitalize())
	header.add_theme_font_size_override("font_size", _SUB_TREE_HEADER_FONT_SIZE)
	header.add_theme_color_override("font_color", _SUB_TREE_HEADER_COLOR)
	header.set_meta("skill_tree", tree)
	header.set_meta("sub_tree", sub_tree)
	header.set_meta("is_sub_tree_header", true)
	return header


## Rebuilds the tab strip from the trees this list actually contains, keeping the current tab selected
## when it survives the rebuild - a skill point spent triggers a fresh SkillListSMSG, and being thrown
## back to the first tab on every investment would make spending several points miserable.
func _rebuild_tabs(msg: SkillListSMSG) -> void:
	var present := {}
	for entry in msg.Skills:
		present[_tree_of(entry)] = true

	var previous: String = _current_tree()

	_tabbed_trees.clear()
	for tree in TREE_ORDER:
		if present.has(tree):
			_tabbed_trees.append(tree)
	for tree in present:
		if tree != NO_TREE and not _tabbed_trees.has(tree):
			_tabbed_trees.append(tree)
	if present.has(NO_TREE):
		_tabbed_trees.append(NO_TREE)

	_tree_tabs.clear_tabs()
	for tree in _tabbed_trees:
		_tree_tabs.add_tab(NO_TREE_TAB_NAME if tree == NO_TREE else TREE_NAMES.get(tree, tree.capitalize()))

	_tree_tabs.visible = _tabbed_trees.size() > 1

	var restored: int = _tabbed_trees.find(previous)
	if _tree_tabs.tab_count > 0:
		_tree_tabs.current_tab = restored if restored >= 0 else 0


func _current_tree() -> String:
	var index: int = _tree_tabs.current_tab
	return _tabbed_trees[index] if index >= 0 and index < _tabbed_trees.size() else NO_TREE


func _on_tree_tabs_tab_changed(_tab: int) -> void:
	# A selection in the tab being left would leave the Use button pointing at a row nobody can see.
	if _selected_row and is_instance_valid(_selected_row):
		_selected_row.set_selected(false)
	_selected_row = null
	_apply_filters()


## Highlights whichever row was last clicked so the footer's Use button knows what to
## activate - rows have no standing selection state of their own beyond this.
func _on_row_selected(row: Control) -> void:
	if _selected_row and is_instance_valid(_selected_row):
		_selected_row.set_selected(false)
	_selected_row = row
	_selected_row.set_selected(true)


func _on_use_button_pressed() -> void:
	if _selected_row == null or not is_instance_valid(_selected_row):
		return
	ConnectionManager.activate_skill(_selected_row.skill_id, _selected_row.get_selected_level())


## Every actual skill row, skipping the sub-tree headings that share the container.
func _rows() -> Array:
	var rows: Array = []
	for child in _skill_rows.get_children():
		if not child.has_meta("is_sub_tree_header"):
			rows.append(child)
	return rows


## Sum of every row's buffered, not-yet-confirmed point spend.
func _get_pending_point_total() -> int:
	var total = 0
	for row in _rows():
		if row.has_pending_investment():
			total += row.get_pending_investment()["amount"]
	return total


## Called whenever a row buffers a level-up locally (see SkillRow.investment_changed) - updates
## the remaining-points display and every row's ability to spend further, plus shows Confirm/
## Cancel once at least one row has something pending.
func _on_row_investment_changed(_row: Control) -> void:
	_update_skill_row_buttons()


## Broadcasts the current spendable skill point count (minus whatever is already buffered
## locally) to every row so a SpendSkillPointButton can hide itself once there are no points
## left to spend - rows have no standing connection to this state on their own since they're
## plain instantiated children.
func _update_skill_row_buttons() -> void:
	var pending_total = _get_pending_point_total()
	var remaining = _available_skill_points - pending_total

	if pending_total > 0:
		_skill_points_label.text = "Skill Points: %s (%s pending)" % [remaining, pending_total]
	else:
		_skill_points_label.text = "Skill Points: %s" % [_available_skill_points]

	_confirm_button.visible = pending_total > 0
	_cancel_button.visible = pending_total > 0

	for row in _rows():
		row.set_can_spend_points(remaining > 0)


## Sends every row's buffered investment to the server in a single batch and makes it
## permanent. The server proactively pushes back a fresh SkillListSMSG once the investment is
## applied (see InvestSkillPointHandler) - that rebuild (_populate_rows) also implicitly clears
## every row's pending buffer, so there's nothing left to do here but send the request.
func _on_confirm_button_pressed() -> void:
	var investments: Array = []
	for row in _rows():
		var investment = row.get_pending_investment()
		if not investment.is_empty():
			investments.append(investment)

	if investments.is_empty():
		return

	ConnectionManager.invest_skill_points(investments)


## Discards every row's buffered, not-yet-confirmed investment without contacting the server.
func _on_cancel_button_pressed() -> void:
	for row in _rows():
		row.reset_pending_investment()
	_update_skill_row_buttons()


func _on_clear_button_pressed() -> void:
	_search_line_edit.text = ""
	_apply_filters()


## Skills is instantiated as a WidgetWindow's content (see ui.gd), so hiding
## this panel alone would leave the surrounding window visible - hide the
## parent WidgetWindow instead, mirroring its own title bar close button.
func _on_close_button_pressed() -> void:
	get_parent().hide()


func _on_search_line_edit_text_changed(_new_text: String) -> void:
	_apply_filters()


## A row shows when it is in the open tab and matches the search; a sub-tree heading shows only when
## something under it survived both, so filtering never leaves a heading over nothing.
func _apply_filters() -> void:
	var query: String = _search_line_edit.text.strip_edges().to_lower()
	var tree := _current_tree()
	var groups_with_rows := {}

	for row in _rows():
		var shown: bool = row.get_meta("skill_tree", NO_TREE) == tree \
			and (query.is_empty() or row.get_skill_name().to_lower().contains(query))
		row.visible = shown
		if shown:
			groups_with_rows[row.get_meta("sub_tree", "")] = true

	for child in _skill_rows.get_children():
		if child.has_meta("is_sub_tree_header"):
			child.visible = child.get_meta("skill_tree", NO_TREE) == tree \
				and groups_with_rows.has(child.get_meta("sub_tree", ""))
