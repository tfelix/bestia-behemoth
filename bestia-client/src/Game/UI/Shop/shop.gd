extends PanelContainer
class_name Shop

## Buying from and selling to one merchant, as one trade.
##
## The player fills a basket from both sides - the merchant's wares here, their own goods from the inventory
## docked beside this window - and the footer shows what it comes to. Every price is the server's: each unit
## is priced against a town with one more or one fewer of it, so the basket is quoted rather than added up.
## The trade then carries the quoted balance, and the server refuses it if it would come out any worse.
##
## Like the crafting and trade windows this has no toggle of its own: talking to a merchant at work is what
## opens it, so the message is what shows it, and walking away or the end of their shift is what closes it.

signal shop_opened()
signal shop_closed()

## Assigned at runtime by Game/UI/ui.gd - the window only instantiates its content in _ready(), so this
## cannot be reached through an editor-wired NodePath.
var inventory: Inventory = null

const _WALK_AWAY_METRES := 10.0

## The conversation's root topic. Talking to a merchant at work opens this window, so this is how to chat.
const _CONVERSATION_ROOT := 0

## The server's cap on one line, ShopTradeLine.MAX_UNITS.
const _MAX_UNITS := 500

## Long enough that holding + or typing an amount asks once, short enough that the total keeps up.
const _QUOTE_DELAY_SECONDS := 0.2

const _ROW_HEIGHT := 28
const _PRICE_WIDTH := 64

var DropAmountDialogScn = preload("res://Game/UI/Inventory/DropAmountDialog/DropAmountDialog.tscn")

@onready var _title: Label = %Title
@onready var _hours: Label = %Hours
@onready var _wares: VBoxContainer = %Wares
@onready var _empty: Label = %Empty
@onready var _selling: VBoxContainer = %Selling
@onready var _sell_hint: Label = %SellHint
@onready var _buy_total: Label = %BuyTotal
@onready var _sell_total: Label = %SellTotal
@onready var _balance_label: Label = %BalanceLabel
@onready var _balance: HBoxContainer = %Balance
@onready var _gold: Label = %Gold
@onready var _load: Label = %Load
@onready var _confirm: Button = %Confirm
@onready var _reason: Label = %Reason

## Whose counter is open. Zero while there is none, which is what says whether the buttons mean anything.
var _merchant_entity_id: int = 0
var _opened_at := Vector3.ZERO

## The merchant's wares by item id, as the last ShopOfferSMSG listed them.
var _entries: Dictionary[int, ShopEntry] = {}
var _basket := ShopBasket.new()

## The quote for the basket as it stands, or null while it is being asked for.
var _quote: ShopQuoteSMSG = null
## Quotes asked for and not yet answered. Only the answer to the last one describes the basket.
var _quotes_in_flight := 0
var _quote_timer := Timer.new()

## Set from Confirm until the server answers: a fresh offer means the trade went through.
var _awaiting_trade := false


func _ready() -> void:
	ConnectionManager.shop_offer_received.connect(_on_shop_offer)
	ConnectionManager.shop_quote_received.connect(_on_shop_quote)
	ConnectionManager.operation_error.connect(_on_operation_error)
	%Chat.pressed.connect(_on_chat_pressed)
	%Clear.pressed.connect(_on_clear_pressed)
	_confirm.pressed.connect(_on_confirm_pressed)
	# The window's own close button only hides it, so this is how walking away from a counter is noticed.
	visibility_changed.connect(_on_visibility_changed)

	_quote_timer.one_shot = true
	_quote_timer.wait_time = _QUOTE_DELAY_SECONDS
	_quote_timer.timeout.connect(_request_quote)
	add_child(_quote_timer)

	set_process(false)


## Connected by ui.gd once [member inventory] is assigned.
func connect_inventory() -> void:
	inventory.sell_requested.connect(_on_sell_requested)
	inventory.inventory_updated.connect(_on_inventory_updated)


func is_shopping() -> bool:
	return _merchant_entity_id != 0


## Shuts the counter without telling the server. There is nothing to end: opening a shop reads and writes
## nothing, so a window nobody closed costs the server nothing either.
func close_shop() -> void:
	if not is_shopping():
		return

	_merchant_entity_id = 0
	_basket.clear()
	_awaiting_trade = false
	set_process(false)
	if inventory != null:
		inventory.stop_trading()
	shop_closed.emit()


func _process(_delta: float) -> void:
	var player := _player()
	if player == null or player.global_position.distance_to(_opened_at) > _WALK_AWAY_METRES:
		close_shop()


func _on_visibility_changed() -> void:
	if not is_visible_in_tree():
		close_shop()


# ------------------------------------------------------------------ server messages

func _on_shop_offer(msg: ShopOfferSMSG) -> void:
	var was_open := is_shopping()
	if was_open and msg.MerchantEntityId != _merchant_entity_id:
		_basket.clear()
	_merchant_entity_id = msg.MerchantEntityId

	_entries.clear()
	for entry in msg.Entries:
		_entries[int(entry.ItemId)] = entry

	if _awaiting_trade:
		_awaiting_trade = false
		_basket.clear()

	_render_header(msg)
	if inventory != null:
		inventory.start_trading(_sell_prices())
	_basket_changed()

	if not was_open:
		var player := _player()
		_opened_at = player.global_position if player != null else Vector3.ZERO
		set_process(player != null)
		shop_opened.emit()


func _on_shop_quote(msg: ShopQuoteSMSG) -> void:
	_quotes_in_flight = maxi(0, _quotes_in_flight - 1)
	if msg.MerchantEntityId != _merchant_entity_id or _quotes_in_flight > 0:
		return

	_quote = msg
	_render_totals()


func _on_operation_error(message) -> void:
	if not is_shopping() or not message.CodeName.begins_with("shop_"):
		return

	if message.CodeName == "shop_closed":
		close_shop()
		return

	# The basket stays as it was; the prices behind it may not have.
	_awaiting_trade = false
	_request_quote()


# ------------------------------------------------------------------ the basket

func _on_sell_requested(item_id: int, amount: int) -> void:
	if not is_shopping():
		return

	if amount == 0:
		_ask(item_id, true)
	else:
		_add(item_id, true, amount)


func _ask(item_id: int, selling: bool) -> void:
	var room := _limit_of(item_id, selling) - _basket.amount_of(item_id, selling)
	if room <= 0:
		return

	var dialog := DropAmountDialogScn.instantiate() as DropAmountDialog
	add_child(dialog)
	dialog.amount_confirmed.connect(func(_id: int, amount: int) -> void: _add(item_id, selling, amount))
	dialog.confirmed.connect(dialog.queue_free)
	dialog.canceled.connect(dialog.queue_free)
	var prompt := tr("SHOP_SELL_HOW_MANY") if selling else tr("SHOP_BUY_HOW_MANY")
	dialog.open_for(ItemDB.get_instance().get_item(item_id), room, prompt)


func _add(item_id: int, selling: bool, amount: int) -> void:
	_set_amount(item_id, selling, _basket.amount_of(item_id, selling) + amount)


func _set_amount(item_id: int, selling: bool, amount: int) -> void:
	if _basket.is_on_other_side(item_id, selling):
		return

	_basket.set_amount(item_id, selling, clampi(amount, 0, _limit_of(item_id, selling)))
	_basket_changed()


## As many as the merchant has on offer, or as the player holds - never more than one line may carry.
func _limit_of(item_id: int, selling: bool) -> int:
	if selling:
		var held := inventory.get_item_count(item_id) if inventory != null else 0
		return mini(held, _MAX_UNITS) if _entries.has(item_id) else 0

	var entry: ShopEntry = _entries.get(item_id)
	return mini(entry.Offered, _MAX_UNITS) if entry != null else 0


## A refreshed offer or bag can leave a line asking for more than there is.
func _clamp_basket() -> void:
	for selling in [false, true]:
		for item_id in _basket.items_on(selling):
			_basket.set_amount(item_id, selling, mini(_basket.amount_of(item_id, selling), _limit_of(item_id, selling)))


func _on_inventory_updated() -> void:
	if is_shopping():
		_basket_changed()


func _on_clear_pressed() -> void:
	_basket.clear()
	_basket_changed()


func _basket_changed() -> void:
	_clamp_basket()
	_quote = null
	if inventory != null:
		inventory.hold_back(_basket.sold())

	_render_wares()
	_render_selling()
	_render_totals()

	if not _basket.is_empty():
		_quote_timer.start()


func _request_quote() -> void:
	if not is_shopping() or _basket.is_empty():
		return

	_quote = null
	_quotes_in_flight += 1
	ConnectionManager.shop_quote(_merchant_entity_id, _basket.lines())
	_render_totals()


func _on_confirm_pressed() -> void:
	if _blocked_reason() != "":
		return

	_awaiting_trade = true
	ConnectionManager.shop_trade(_merchant_entity_id, _basket.lines(), _quote.Balance)
	_confirm.disabled = true


func _on_chat_pressed() -> void:
	if is_shopping():
		ConnectionManager.answer_conversation(_merchant_entity_id, _CONVERSATION_ROOT)


# ------------------------------------------------------------------ drawing

func _render_header(msg: ShopOfferSMSG) -> void:
	_title.text = msg.MerchantName
	_hours.visible = msg.KeepsHours
	if msg.KeepsHours:
		_hours.text = tr("SHOP_HOURS") % [_clock(msg.OpensAtMinute), _clock(msg.ClosesAtMinute)]


func _render_wares() -> void:
	for child in _wares.get_children():
		child.queue_free()

	var item_db := ItemDB.get_instance()
	for item_id in _entries:
		var item: ItemResource = item_db.get_item(item_id)
		if item == null:
			printerr("Shop: item with ID %s not found in ItemDB" % [item_id])
			continue

		var entry := _entries[item_id]
		var stock := UiStyle.label("×%d" % entry.Offered, 12, UiStyle.MUTED)
		_wares.add_child(_row(item, false, entry.BuyPrice, stock))

	# A merchant with an empty counter is an ordinary thing here rather than an error: a town sells what is
	# left after its own people have eaten, and in a bad month that is nothing.
	_empty.visible = _entries.is_empty()


func _render_selling() -> void:
	for child in _selling.get_children():
		if child != _sell_hint:
			child.queue_free()

	var item_db := ItemDB.get_instance()
	var sold := _basket.items_on(true)
	for item_id in sold:
		var held := UiStyle.label("/%d" % _limit_of(item_id, true), 12, UiStyle.MUTED)
		var row := _row(item_db.get_item(item_id), true, _entries[item_id].SellPrice, held)
		var remove := UiStyle.glyph_button("×", tr("SHOP_REMOVE"))
		remove.pressed.connect(_set_amount.bind(item_id, true, 0))
		row.get_child(0).add_child(remove)
		_selling.add_child(row)

	_sell_hint.visible = sold.is_empty()


## One line of either list: icon, name, a note on how many there are, price for one, and the amount picked.
func _row(item: ItemResource, selling: bool, unit_price: int, how_many: Control) -> Control:
	var amount := _basket.amount_of(item.item_id, selling)

	var row := PanelContainer.new()
	row.custom_minimum_size = Vector2(0, _ROW_HEIGHT)
	var tint := UiStyle.ROW_HOVER_BG if amount > 0 else UiStyle.ROW_BG
	row.add_theme_stylebox_override("panel", UiStyle.flat(tint, 4, 3, UiStyle.ACCENT if amount > 0 else Color.TRANSPARENT, 1 if amount > 0 else 0))
	# Passed on, so goods dragged from the bag onto a row still land in the basket.
	row.mouse_filter = Control.MOUSE_FILTER_PASS
	row.tooltip_text = tr("SHOP_ROW_TOOLTIP")
	row.gui_input.connect(_on_row_input.bind(item.item_id, selling))

	var line := UiStyle.hbox(6)
	line.mouse_filter = Control.MOUSE_FILTER_IGNORE
	row.add_child(line)

	var icon := TextureRect.new()
	icon.texture = item.get_icon()
	icon.custom_minimum_size = Vector2(22, 22)
	icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	line.add_child(icon)

	var name_label := UiStyle.label(tr(item.name_key))
	name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	name_label.clip_text = true
	line.add_child(name_label)

	line.add_child(how_many)
	line.add_child(AuctionFormat.price(unit_price, _PRICE_WIDTH))
	line.add_child(_stepper(item.item_id, selling, amount))

	_ignore_mouse_except_buttons(line)
	return row


func _stepper(item_id: int, selling: bool, amount: int) -> HBoxContainer:
	var stepper := UiStyle.hbox(2)
	var other_side := _basket.is_on_other_side(item_id, selling)

	var less := UiStyle.glyph_button("−", tr("SHOP_LESS"))
	less.disabled = amount <= 0
	less.pressed.connect(func() -> void: _set_amount(item_id, selling, amount - _step()))
	stepper.add_child(less)

	var count := UiStyle.label(str(amount), 13, UiStyle.TEXT if amount > 0 else UiStyle.MUTED)
	count.custom_minimum_size = Vector2(30, 0)
	count.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	stepper.add_child(count)

	var more := UiStyle.glyph_button("+", tr("SHOP_MORE"))
	more.disabled = other_side or amount >= _limit_of(item_id, selling)
	more.pressed.connect(func() -> void: _set_amount(item_id, selling, amount + _step()))
	stepper.add_child(more)

	return stepper


## Shift steps by ten, as stacks of loot usually come.
func _step() -> int:
	return 10 if Input.is_key_pressed(KEY_SHIFT) else 1


## Rows take clicks and drops, so their labels and icons must not swallow them.
func _ignore_mouse_except_buttons(node: Node) -> void:
	for child in node.get_children():
		if child is Control and not child is BaseButton:
			child.mouse_filter = Control.MOUSE_FILTER_IGNORE
		_ignore_mouse_except_buttons(child)


## Double-click adds one, shift-click asks how many - the same gestures as in the bag.
func _on_row_input(event: InputEvent, item_id: int, selling: bool) -> void:
	if not (event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed):
		return

	if event.shift_pressed:
		_ask(item_id, selling)
		accept_event()
	elif event.double_click:
		_add(item_id, selling, 1)
		accept_event()


func _render_totals() -> void:
	var gold := inventory.gold() if inventory != null else 0
	var load_now := inventory.carried_weight() if inventory != null else 0
	var limit := inventory.carry_limit() if inventory != null else 0
	var load_after := load_now + _weight_change()

	var settled := _quote != null and _quote.RefusalName == ""
	var bought := _side_total(false) if settled else 0
	var sold := _side_total(true) if settled else 0
	var balance := int(_quote.Balance) if settled else 0
	var pending := not _basket.is_empty() and _quote == null

	_buy_total.text = tr("SHOP_BUY_TOTAL") % _amount_text(bought, pending)
	_sell_total.text = tr("SHOP_SELL_TOTAL") % _amount_text(sold, pending)

	for child in _balance.get_children():
		child.queue_free()
	if balance < 0:
		_balance_label.text = tr("SHOP_YOU_PAY")
		_balance.add_child(AuctionFormat.price(-balance, 0, UiStyle.DANGER))
	elif balance > 0:
		_balance_label.text = tr("SHOP_YOU_RECEIVE")
		_balance.add_child(AuctionFormat.price(balance, 0, UiStyle.ONLINE))
	else:
		_balance_label.text = tr("SHOP_BALANCE")
		_balance.add_child(UiStyle.label("…" if pending else "0"))

	_gold.text = tr("SHOP_GOLD") % [AuctionFormat.coins(gold), AuctionFormat.coins(gold + balance)]
	_load.visible = limit > 0
	_load.text = tr("SHOP_LOAD") % [load_now, load_after, limit]
	_load.add_theme_color_override("font_color", UiStyle.DANGER if limit > 0 and load_after > limit else UiStyle.TEXT)

	var reason := _blocked_reason()
	_reason.text = reason
	_confirm.disabled = _basket.is_empty() or reason != "" or _awaiting_trade


func _amount_text(amount: int, pending: bool) -> String:
	return "…" if pending else AuctionFormat.coins(amount)


## Why Confirm would be refused, or "" when it would not. Checked here first so the player is told before the
## server has to refuse it.
func _blocked_reason() -> String:
	if _basket.is_empty():
		return ""
	if _quote == null:
		return tr("SHOP_UPDATING")
	if _quote.RefusalName != "":
		return _refusal_text(_quote)

	var gold := inventory.gold() if inventory != null else 0
	if gold + _quote.Balance < 0:
		return tr("SHOP_CANT_AFFORD")

	var limit := inventory.carry_limit() if inventory != null else 0
	if limit > 0 and _weight_change() > 0 and inventory.carried_weight() + _weight_change() > limit:
		return tr("ERROR_SHOP_TOO_HEAVY")

	return ""


func _refusal_text(quote: ShopQuoteSMSG) -> String:
	var text := tr("ERROR_%s" % quote.RefusalName.to_upper())
	var lines := _basket.lines()
	if quote.RefusedLine >= 0 and quote.RefusedLine < lines.size():
		var item := ItemDB.get_instance().get_item(int(lines[quote.RefusedLine].ItemId))
		text = "%s: %s" % [tr(item.name_key), text]

	return text


func _side_total(selling: bool) -> int:
	var total := 0
	for item_id in _basket.items_on(selling):
		var index := _basket.line_index_of(item_id)
		if index < _quote.LineCoins.size():
			total += int(_quote.LineCoins[index])

	return total


func _weight_change() -> int:
	var change := 0
	var item_db := ItemDB.get_instance()
	for selling in [false, true]:
		for item_id in _basket.items_on(selling):
			var weight: int = item_db.get_item(item_id).weight * _basket.amount_of(item_id, selling)
			change += -weight if selling else weight

	return change


func _sell_prices() -> Dictionary[int, int]:
	var prices: Dictionary[int, int] = {}
	for item_id in _entries:
		prices[item_id] = int(_entries[item_id].SellPrice)

	return prices


# ------------------------------------------------------------------ dropping goods from the bag

func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
	return is_shopping() and typeof(data) == TYPE_DICTIONARY and data.get("source") == "inventory_item" \
		and _entries.has(int(data.get("id", 0)))


func _drop_data(_at_position: Vector2, data: Variant) -> void:
	var item_id := int(data.get("id"))
	if int(data.get("amount", 1)) > 1:
		_ask(item_id, true)
	else:
		_add(item_id, true, 1)


static func _clock(minute_of_day: int) -> String:
	return "%d:%02d" % [floori(minute_of_day / 60.0), minute_of_day % 60]


func _player() -> Entity:
	var manager := EntityManager.get_instance()
	return manager.get_controlled_entity() if manager != null else null
