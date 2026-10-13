extends PanelContainer
class_name StatusPoints

## The combat stats shown beside the attributes, in the order of Ragnarok Online's status window. [code]id[/code]
## is the row (and, for all but ATK/MATK/DEF, the key in Entity.get_combat_stats()), [code]tip[/code] says what the
## number does, since the abbreviations are not self-explanatory.
##
## All of it is resolved by the server (CombatStats): the client holds no formulas, so what is shown is what a
## swing actually spends.
const _COMBAT_ROWS: Array[Dictionary] = [
	{"id": "atk", "label": "ATK", "tip": "Physical attack: what your attributes give + what your weapon adds."},
	{"id": "matk", "label": "MATK", "tip": "Magic attack: what your attributes give + what your weapon adds."},
	{
		"id": "def", "label": "DEF",
		"tip": "Defense: flat damage taken off every physical hit, plus the share of damage your armor blocks. "
			+ "Critical hits ignore both.",
	},
	{"id": "hit", "label": "HIT", "tip": "Accuracy. The higher it is above the target's FLEE, the more of your attacks land."},
	{"id": "flee", "label": "FLEE", "tip": "Evasion. The higher it is above the attacker's HIT, the more attacks miss you."},
	{
		"id": "crit", "label": "CRIT",
		"tip": "Chance for an attack to be a critical hit: it deals more damage and ignores defense. "
			+ "The target's own CRIT lowers it.",
	},
	{"id": "aspd", "label": "ASPD", "tip": "Attack speed. Higher swings faster."},
]

## Shown until the server has said anything, so an empty window reads as "not yet" and not as zero.
const _NO_VALUE := "-"

@onready var _rows: Array = _collect_rows()
@onready var _stats_grid: GridContainer = %StatsGrid
@onready var _status_points_label: Label = %StatusPointsLabel
@onready var _confirm_button: Button = %ConfirmButton
@onready var _cancel_button: Button = %CancelButton

# The account's own master entity id - status points and base status values only ever belong to
# the master, mirroring Skills._master_entity_id.
var _master_entity_id: int = 0
var _available_status_points: int = 0

# Combat stat row id -> the Label holding its value, filled by _build_combat_rows.
var _combat_value_labels: Dictionary = {}


func _ready() -> void:
	_confirm_button.visible = false
	_cancel_button.visible = false
	ConnectionManager.connect("self_received", _on_self_received)
	ConnectionManager.connect("entity_received", _on_entity_received)

	for row in _rows:
		row.investment_changed.connect(_on_row_investment_changed)

	_build_combat_rows()
	_update_row_buttons()


func _collect_rows() -> Array:
	var found: Array = []
	for node in find_children("*", "HBoxContainer", true, false):
		if node is StatusRow:
			found.append(node)
	return found


## Called by Game/UI/ui.gd whenever the StatusPoints window is opened, since the window content
## is a Control nested inside a Window - a Window's own visibility does not propagate as a
## visibility_changed signal to its content the way regular Control nesting would (mirrors
## Skills.request_refresh). Unlike skills, status values/points are regular Dirtyable ECS
## components already pushed to the owner on every change, so there is nothing to request from
## the server here - only the locally cached values need to be (re)applied.
func request_refresh() -> void:
	_seed_from_cache()


func _on_self_received(msg: SelfSMSG) -> void:
	_master_entity_id = msg.MasterEntityId
	_seed_from_cache()


func _on_entity_received(msg: EntitySMSG) -> void:
	if msg.EntityId != _master_entity_id:
		return
	# Base, not effective, values: the server prices a point off the unbuffed value, so pricing off an
	# equipment-inflated one would disagree with what it charges.
	if msg is BaseStatusValuesComponentSMSG:
		_apply_base_status_values(msg)
	elif msg is CombatStatsComponentSMSG:
		_show_combat_stats(Entity.combat_stats_of(msg))
	elif msg is StatusPointsComponentSMSG:
		_available_status_points = msg.Points
		_update_row_buttons()


## Seeds every row and the point count from the master Entity's cache (kept up to date by
## entity_manager.gd/entity.gd regardless of whether this window has ever been open), so it's
## correct the moment the window opens instead of only after the next live update happens to
## arrive - mirrors Skills._seed_skill_points_from_cache / Equipment._worn_by_slot.
func _seed_from_cache() -> void:
	var entity_manager := EntityManager.get_instance()
	var entity: Entity = entity_manager.get_entity(_master_entity_id) if entity_manager else null
	if entity == null:
		return

	_available_status_points = entity.get_status_points()
	var values: Dictionary = entity.get_base_status_values()
	for row in _rows:
		var key: String = StatusAttribute.field_key(row.attribute)
		if values.has(key):
			row.set_base_value(values[key])

	_show_combat_stats(entity.get_combat_stats())
	_update_row_buttons()


## One label pair per [constant _COMBAT_ROWS] entry. Built here rather than hand-placed in the scene because
## every row is the same two labels and the table above is already the list of what is shown.
func _build_combat_rows() -> void:
	for row in _COMBAT_ROWS:
		var name_label := Label.new()
		name_label.text = row["label"]
		var value_label := Label.new()
		value_label.text = _NO_VALUE
		value_label.custom_minimum_size = Vector2(70, 0)

		# A Label ignores the mouse by default, which would swallow its tooltip.
		for label in [name_label, value_label]:
			label.tooltip_text = row["tip"]
			label.mouse_filter = Control.MOUSE_FILTER_STOP
			_stats_grid.add_child(label)

		_combat_value_labels[row["id"]] = value_label


## Fills the combat stat labels from [param stats] (Entity.combat_stats_of's shape). Empty means the server has
## not sent any yet, which leaves the placeholders in place.
func _show_combat_stats(stats: Dictionary) -> void:
	if stats.is_empty():
		return

	for row in _COMBAT_ROWS:
		var value_label: Label = _combat_value_labels[row["id"]]
		value_label.text = _format_combat_stat(row["id"], stats)


## ATK and MATK read "from attributes + from weapon" the way RO shows them. DEF is flat soft defense, with the
## armor's percentage appended only when something is worn, so a bare fighter is not shown a "+ 0%".
func _format_combat_stat(id: String, stats: Dictionary) -> String:
	match id:
		"atk":
			return "%d + %d" % [stats["atk"], stats["weapon_atk"]]
		"matk":
			return "%d + %d" % [stats["matk"], stats["weapon_matk"]]
		"def":
			if stats["hard_defense"] != 0:
				return "%d + %d%%" % [stats["soft_defense"], stats["hard_defense"]]
			return "%d" % stats["soft_defense"]
		"crit":
			return "%d%%" % stats["crit"]
		_:
			return "%d" % stats[id]


func _apply_base_status_values(msg: BaseStatusValuesComponentSMSG) -> void:
	for row in _rows:
		match row.attribute:
			StatusAttribute.Attribute.STRENGTH: row.set_base_value(msg.Strength)
			StatusAttribute.Attribute.AGILITY: row.set_base_value(msg.Agility)
			StatusAttribute.Attribute.VITALITY: row.set_base_value(msg.Vitality)
			StatusAttribute.Attribute.INTELLIGENCE: row.set_base_value(msg.Intelligence)
			StatusAttribute.Attribute.DEXTERITY: row.set_base_value(msg.Dexterity)
			StatusAttribute.Attribute.WILLPOWER: row.set_base_value(msg.Willpower)


## Attribute points every row has buffered. Not the same number as the status points they cost - see
## _get_pending_cost_total.
func _get_pending_point_total() -> int:
	var total = 0
	for row in _rows:
		total += row.pending_points
	return total


## Status points every row's buffered investment costs under the escalating effort value curve. This,
## not the raw point count, is what comes off the available pool.
func _get_pending_cost_total() -> int:
	var total = 0
	for row in _rows:
		total += row.pending_cost()
	return total


## Called whenever a row buffers a point spend locally (see StatusRow.investment_changed) -
## updates the remaining-points display and every row's ability to spend further, plus shows
## Confirm/Cancel once at least one row has something pending.
func _on_row_investment_changed(_row: Control) -> void:
	_update_row_buttons()


func _update_row_buttons() -> void:
	var pending_total = _get_pending_point_total()
	var pending_cost = _get_pending_cost_total()
	var remaining = _available_status_points - pending_cost

	if pending_total > 0:
		_status_points_label.text = "Status Points: %s (%s spent on +%s)" % [remaining, pending_cost, pending_total]
	else:
		_status_points_label.text = "Status Points: %s" % [_available_status_points]

	_confirm_button.visible = pending_total > 0
	_cancel_button.visible = pending_total > 0

	# Gated per row rather than on "any points left": the cost escalates with the value, so a pool can
	# afford a cheap attribute's next point while an expensive one's is already out of reach.
	for row in _rows:
		row.set_can_spend_points(remaining >= StatusAttribute.step_cost(row.next_value()))


## Sends every row's buffered investment to the server in a single batch. Unlike
## Skills._on_confirm_button_pressed, there's no explicit refresh to trigger afterwards: the
## server updates the (Dirtyable) BaseStatusValues/StatusPoints ECS components, which push fresh
## BaseStatusValuesComponentSMSG/StatusPointsComponentSMSG to the owner on their own.
##
## Sends raw attribute point counts, not what they cost: the server re-prices every step off its own
## copy of the base values, so it is the authority on how much comes off the pool.
func _on_confirm_button_pressed() -> void:
	var investments: Array = []
	for row in _rows:
		var investment = row.get_pending_investment()
		if not investment.is_empty():
			investments.append(investment)

	if investments.is_empty():
		return

	ConnectionManager.invest_status_points(investments)


## Discards every row's buffered, not-yet-confirmed investment without contacting the server.
func _on_cancel_button_pressed() -> void:
	for row in _rows:
		row.reset_pending_investment()
	_update_row_buttons()


## StatusPoints is instantiated as a WidgetWindow's content (see ui.gd), so hiding this panel
## alone would leave the surrounding window visible - hide the parent WidgetWindow instead,
## mirroring Skills._on_close_button_pressed.
func _on_close_button_pressed() -> void:
	get_parent().hide()
