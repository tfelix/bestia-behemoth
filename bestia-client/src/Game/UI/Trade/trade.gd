extends PanelContainer
class_name Trade

## Face-to-face trading with another player.
##
## Authoritative-from-server throughout: nothing is applied locally, and every TradeStateSMSG re-renders both
## columns wholesale. That is also how a refused action corrects itself - the server answers with the truth
## and an optimistic drag simply snaps back, so there is no per-refusal handling here.
##
## Like the crafting window, this has no toggle of its own: there is nothing to show until two people agree
## to trade, so the message is what opens it and the message is what closes it.

signal trade_opened()
signal trade_closed()

var InventoryItem = preload("res://Game/UI/Inventory/InventoryItem/InventoryItem.tscn")
var DropAmountDialogScn = preload("res://Game/UI/Inventory/DropAmountDialog/DropAmountDialog.tscn")
var TradeRequestDialogScn = preload("res://Game/UI/Trade/TradeRequestDialog/TradeRequestDialog.tscn")

## Assigned at runtime by Game/UI/ui.gd - both live inside WidgetWindows that only instantiate their content
## in _ready(), so neither can be reached through an editor-wired NodePath.
var inventory: Inventory = null

@onready var _own_column: VBoxContainer = %OwnColumn
@onready var _own_bestias: VBoxContainer = %OwnBestias
@onready var _partner_bestias: VBoxContainer = %PartnerBestias
@onready var _own_grid: GridContainer = %OwnGrid
@onready var _own_status: Label = %OwnStatus
@onready var _partner_header: Label = %PartnerHeader
@onready var _partner_grid: GridContainer = %PartnerGrid
@onready var _partner_status: Label = %PartnerStatus
@onready var _lock_button: Button = %LockButton
@onready var _confirm_button: Button = %ConfirmButton
@onready var _cancel_button: Button = %CancelButton

var _trade_id: int = 0
var _own_locked: bool = false
var _request_dialog: TradeRequestDialog = null
## The bestia last dropped in, until the server takes it or asks whether its items go too.
var _pending_bestia_entity_id: int = 0


func _ready() -> void:
	ConnectionManager.trade_request_received.connect(_on_trade_request)
	ConnectionManager.trade_state_received.connect(_on_trade_state)
	ConnectionManager.operation_error.connect(_on_operation_error)


## True while a trade is running. The window survives closing, so the id is what says whether the buttons
## and drops mean anything.
func is_trading() -> bool:
	return _trade_id != 0


# --------------------------------------------------------------------- prompt

func _on_trade_request(msg: TradeRequestSMSG) -> void:
	if _request_dialog == null:
		_request_dialog = TradeRequestDialogScn.instantiate() as TradeRequestDialog
		add_child(_request_dialog)
		_request_dialog.answered.connect(_on_request_answered)

	_request_dialog.open_for(msg.TradeId, msg.FromMasterName)


func _on_request_answered(trade_id: int, accept: bool) -> void:
	ConnectionManager.answer_trade_request(trade_id, accept)


# ---------------------------------------------------------------------- state

func _on_trade_state(msg: TradeStateSMSG) -> void:
	if msg.StatusName == "completed" or msg.StatusName == "cancelled":
		_trade_id = 0
		# Also covers a prompt that was never answered: the asker cancelling, or the request timing out,
		# both arrive here, and a dialog left standing would answer a trade that no longer exists.
		if _request_dialog != null and _request_dialog.visible:
			_request_dialog.hide()
		trade_closed.emit()
		return

	var was_open := _trade_id != 0
	_trade_id = msg.TradeId
	# A state answers the last drop: the bestia went in, or it was refused for good. Only a carried-items
	# refusal comes without one.
	_pending_bestia_entity_id = 0
	_own_locked = msg.OwnLocked

	_partner_header.text = "%s's offer" % msg.PartnerMasterName
	_render_bestias(_own_bestias, msg.OwnBestias, true)
	_render_bestias(_partner_bestias, msg.PartnerBestias, false)
	_render_offer(_own_grid, msg.OwnOffer, true)
	_render_offer(_partner_grid, msg.PartnerOffer, false)
	_update_buttons(msg)

	if not was_open:
		trade_opened.emit()


func _render_offer(grid: GridContainer, offer: Array, own: bool) -> void:
	for child in grid.get_children():
		child.queue_free()

	var item_db = ItemDB.get_instance()

	for line in offer:
		var item_resource = item_db.get_item(line.ItemId)
		if item_resource == null:
			printerr("Trade: item with ID %s not found in ItemDB" % [line.ItemId])
			continue

		var row = InventoryItem.instantiate()
		row.item = item_resource
		row.amount = line.Amount
		row.unique_id = line.UniqueId
		row.durability = line.Durability
		row.max_durability = line.MaxDurability
		row.slots = line.Slots
		row.upgrade_level = line.UpgradeLevel
		row.item_level = line.ItemLevel
		row.rolled_stats = Array(line.RolledStats)
		row.essences = Array(line.Essences)
		row.held_essence = line.HeldEssence
		row.crafted_by = line.CraftedBy
		row.crafted_in = line.CraftedIn
		row.drag_source = "trade_offer"
		row.offer_slot_id = line.OfferSlotId

		# Only our own side can be taken back, and only while we have not locked it.
		if own and not _own_locked:
			row.dragged_out.connect(_on_offer_dragged_out.bind(line.OfferSlotId))

		grid.add_child(row)


## One row per offered bestia: who it is, where it came from, its IV grades on hover, and a warning when items
## go with it. Both sides see the warning, so nobody is surprised by what arrives.
func _render_bestias(column: VBoxContainer, bestias: Array, own: bool) -> void:
	for child in column.get_children():
		child.queue_free()

	for bestia: TradeOfferBestia in bestias:
		var row := HBoxContainer.new()
		var label := Label.new()
		label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		label.mouse_filter = Control.MOUSE_FILTER_PASS
		label.text = _bestia_text(bestia)
		label.tooltip_text = BreedingText.iv_lines(bestia.Ivs)
		row.add_child(label)

		# Only our own side can be taken back, and only while we have not locked it.
		if own and not _own_locked:
			var take_back := Button.new()
			take_back.text = tr("TRADE_BESTIA_TAKE_BACK")
			take_back.pressed.connect(_on_bestia_taken_back.bind(bestia.PlayerBestiaId))
			row.add_child(take_back)

		column.add_child(row)


func _bestia_text(bestia: TradeOfferBestia) -> String:
	var bestia_name := bestia.Name if not bestia.Name.is_empty() else BreedingText.species_name(bestia.MobId)
	var lines: PackedStringArray = [
		("%s %s  %s" % [bestia_name, BreedingText.sex_mark(bestia.Sex), tr("NAME_TAG_LEVEL") % bestia.Level]).strip_edges(),
	]
	var origin := BreedingText.origin_line(bestia.Origin, bestia.OriginMasterName)
	if not origin.is_empty():
		lines.append(origin)
	if bestia.CarriedItemCount > 0 or bestia.WearsGear:
		lines.append(tr("TRADE_BESTIA_CARRIES") % bestia.CarriedItemCount)
	return "\n".join(lines)


func _update_buttons(msg: TradeStateSMSG) -> void:
	var both_locked := msg.StatusName == "locked"

	# Once both sides are locked the contents are frozen and unlocking is no longer on offer, which is what
	# keeps a confirmation from racing somebody's change of mind.
	_lock_button.text = "Unlock" if msg.OwnLocked else "Lock"
	_lock_button.disabled = both_locked

	_confirm_button.disabled = not both_locked or msg.OwnConfirmed
	_cancel_button.disabled = false

	if msg.OwnConfirmed:
		_own_status.text = "Waiting for them to confirm"
	elif both_locked:
		_own_status.text = "Confirm to complete the trade"
	elif msg.OwnLocked:
		_own_status.text = "Locked - waiting for them"
	else:
		_own_status.text = "Drag items here"

	if msg.PartnerConfirmed:
		_partner_status.text = "Confirmed"
	elif msg.PartnerLocked:
		_partner_status.text = "Locked"
	else:
		_partner_status.text = "Still choosing"


# -------------------------------------------------------------------- actions

func _on_offer_dragged_out(offer_slot_id: int) -> void:
	if _trade_id == 0:
		return

	ConnectionManager.retract_trade_item(_trade_id, offer_slot_id)


func _on_bestia_taken_back(player_bestia_id: int) -> void:
	if _trade_id == 0:
		return

	ConnectionManager.retract_trade_bestia(_trade_id, player_bestia_id)


## The server refused the bestia because its items would go with it: ask, and offer it again if the player agrees.
func _on_operation_error(msg: OperationError) -> void:
	if msg.CodeName != "trade_bestia_carries_items" or _pending_bestia_entity_id == 0 or _trade_id == 0:
		return

	var entity_id := _pending_bestia_entity_id
	var trade_id := _trade_id
	_pending_bestia_entity_id = 0
	var count: String = msg.Args[0] if msg.Args.size() > 0 else "?"

	var dialog := ConfirmationDialog.new()
	dialog.title = tr("TRADE_BESTIA_CARRIES_TITLE")
	dialog.dialog_text = tr("TRADE_BESTIA_CARRIES_CONFIRM") % count
	dialog.confirmed.connect(func() -> void:
		if _trade_id == trade_id:
			ConnectionManager.offer_trade_bestia(trade_id, entity_id, true))
	dialog.visibility_changed.connect(func() -> void:
		if not dialog.visible:
			dialog.queue_free())
	add_child(dialog)
	dialog.popup_centered()


func _on_lock_pressed() -> void:
	if _trade_id == 0:
		return

	ConnectionManager.set_trade_lock(_trade_id, not _own_locked)


func _on_confirm_pressed() -> void:
	if _trade_id == 0:
		return

	ConnectionManager.confirm_trade(_trade_id)


func _on_cancel_pressed() -> void:
	if _trade_id == 0:
		return

	ConnectionManager.cancel_trade(_trade_id)


# ----------------------------------------------------------------- drag & drop

## Only drops landing over our own column count. The check is on the position rather than on a script of the
## column's own, so the whole window keeps one script; the partner's half is theirs to fill, not ours.
func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
	if _trade_id == 0 or _own_locked or inventory == null:
		return false

	if typeof(data) != TYPE_DICTIONARY or not data.get("source") in ["inventory_item", "party_bestia"]:
		return false

	return _own_column.get_global_rect().has_point(get_global_mouse_position())


func _drop_data(_at_position: Vector2, data: Variant) -> void:
	# A party bestia, dragged from its row: offered as it is, the server asks back if it carries items.
	if data.get("source") == "party_bestia":
		_pending_bestia_entity_id = data.get("entity_id", 0)
		ConnectionManager.offer_trade_bestia(_trade_id, _pending_bestia_entity_id, false)
		return

	var item_id: int = data.get("id")
	var player_item_id: int = data.get("player_item_id", 0)
	var held: int = data.get("amount", 0)
	if held <= 0:
		return

	# A unique item is one thing by definition, so there is no amount to ask about.
	if held == 1:
		ConnectionManager.offer_trade_item(_trade_id, player_item_id, 1)
		return

	var item_resource = ItemDB.get_instance().get_item(item_id)
	var dialog = DropAmountDialogScn.instantiate() as DropAmountDialog
	add_child(dialog)
	dialog.amount_confirmed.connect(
		func(_id: int, amount: int) -> void: ConnectionManager.offer_trade_item(_trade_id, player_item_id, amount)
	)
	dialog.confirmed.connect(dialog.queue_free)
	dialog.canceled.connect(dialog.queue_free)
	dialog.open_for(item_resource, held, "Offer how many %s?")
