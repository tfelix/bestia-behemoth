extends PanelContainer
class_name StatusPoints

@onready var _rows: Array = _collect_rows()
@onready var _status_points_label: Label = %StatusPointsLabel
@onready var _confirm_button: Button = %ConfirmButton
@onready var _cancel_button: Button = %CancelButton

# The account's own master entity id - status points and base status values only ever belong to
# the master, mirroring Skills._master_entity_id.
var _master_entity_id: int = 0
var _available_status_points: int = 0


func _ready() -> void:
	_confirm_button.visible = false
	_cancel_button.visible = false
	ConnectionManager.connect("self_received", _on_self_received)
	ConnectionManager.connect("entity_received", _on_entity_received)

	for row in _rows:
		row.investment_changed.connect(_on_row_investment_changed)

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
	if msg is StatusValuesComponentSMSG:
		_apply_status_values(msg)
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
	var values: Dictionary = entity.get_status_values()
	for row in _rows:
		var key: String = StatusAttribute.field_key(row.attribute)
		if values.has(key):
			row.set_base_value(values[key])

	_update_row_buttons()


func _apply_status_values(msg: StatusValuesComponentSMSG) -> void:
	for row in _rows:
		match row.attribute:
			StatusAttribute.Attribute.STRENGTH: row.set_base_value(msg.Strength)
			StatusAttribute.Attribute.AGILITY: row.set_base_value(msg.Agility)
			StatusAttribute.Attribute.VITALITY: row.set_base_value(msg.Vitality)
			StatusAttribute.Attribute.INTELLIGENCE: row.set_base_value(msg.Intelligence)
			StatusAttribute.Attribute.DEXTERITY: row.set_base_value(msg.Dexterity)
			StatusAttribute.Attribute.WILLPOWER: row.set_base_value(msg.Willpower)


## Sum of every row's buffered, not-yet-confirmed point spend.
func _get_pending_point_total() -> int:
	var total = 0
	for row in _rows:
		if row.has_pending_investment():
			total += row.get_pending_investment()["amount"]
	return total


## Called whenever a row buffers a point spend locally (see StatusRow.investment_changed) -
## updates the remaining-points display and every row's ability to spend further, plus shows
## Confirm/Cancel once at least one row has something pending.
func _on_row_investment_changed(_row: Control) -> void:
	_update_row_buttons()


func _update_row_buttons() -> void:
	var pending_total = _get_pending_point_total()
	var remaining = _available_status_points - pending_total

	if pending_total > 0:
		_status_points_label.text = "Status Points: %s (%s pending)" % [remaining, pending_total]
	else:
		_status_points_label.text = "Status Points: %s" % [_available_status_points]

	_confirm_button.visible = pending_total > 0
	_cancel_button.visible = pending_total > 0

	for row in _rows:
		row.set_can_spend_points(remaining > 0)


## Sends every row's buffered investment to the server in a single batch. Unlike
## Skills._on_confirm_button_pressed, there's no explicit refresh to trigger afterwards: the
## server updates the (Dirtyable) StatusValues/StatusPoints ECS components, which push fresh
## StatusValuesComponentSMSG/StatusPointsComponentSMSG to the owner on their own.
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
