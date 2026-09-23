extends PanelContainer
class_name Shop

## Buying from and selling to one merchant.
##
## Authoritative from the server throughout. Nothing is applied locally and every ShopOfferSMSG redraws the
## whole list, which is also how a refusal corrects itself - the server answers with the truth and there is
## no per-refusal handling here.
##
## Like the crafting and trade windows this has no toggle of its own: asking a merchant for their wares is
## what opens it, so the message is what shows it and walking away is what closes it.
##
## Prices are the settlement's and the merchant only decides the counter, so a smith and a baker in one town
## quote the same loaf the same - if they both had one, which they do not.

signal shop_opened()
signal shop_closed()

## Assigned at runtime by Game/UI/ui.gd - the window only instantiates its content in _ready(), so this
## cannot be reached through an editor-wired NodePath.
var inventory: Inventory = null

@onready var _rows: VBoxContainer = %Rows
@onready var _amount: SpinBox = %Amount
@onready var _empty: Label = %Empty

## Whose counter is open. Zero while there is none, which is what says whether the buttons mean anything.
var _merchant_entity_id: int = 0


func _ready() -> void:
	ConnectionManager.shop_offer_received.connect(_on_shop_offer)
	# The window's own close button only hides it, so this is how walking away from a counter is noticed.
	visibility_changed.connect(_on_visibility_changed)


func _on_visibility_changed() -> void:
	if not is_visible_in_tree():
		close_shop()


func is_shopping() -> bool:
	return _merchant_entity_id != 0


## Shuts the counter without telling the server. There is nothing to end: opening a shop reads and writes
## nothing, so a window nobody closed costs the server nothing either.
func close_shop() -> void:
	if not is_shopping():
		return

	_merchant_entity_id = 0
	shop_closed.emit()


func _on_shop_offer(msg: ShopOfferSMSG) -> void:
	var was_open := is_shopping()
	_merchant_entity_id = msg.MerchantEntityId
	_render(msg)

	if not was_open:
		shop_opened.emit()


func _render(msg: ShopOfferSMSG) -> void:
	for child in _rows.get_children():
		child.queue_free()

	var item_db := ItemDB.get_instance()
	var drawn := 0

	for entry in msg.Entries:
		var item: ItemResource = item_db.get_item(entry.ItemId)
		if item == null:
			printerr("Shop: item with ID %s not found in ItemDB" % [entry.ItemId])
			continue

		_rows.add_child(_row_for(item, entry))
		drawn += 1

	# A merchant with an empty counter is an ordinary thing here rather than an error: a town sells what is
	# left after its own people have eaten, and in a bad month that is nothing.
	_empty.visible = drawn == 0


func _row_for(item: ItemResource, entry: ShopEntry) -> Control:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 6)

	var icon := TextureRect.new()
	icon.texture = item.get_icon()
	icon.custom_minimum_size = Vector2(24, 24)
	icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	row.add_child(icon)

	var name_label := Label.new()
	name_label.text = tr(item.name_key)
	name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(name_label)

	var stock := Label.new()
	stock.text = "%d" % entry.Offered
	stock.custom_minimum_size = Vector2(40, 0)
	stock.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	row.add_child(stock)

	row.add_child(_buy_button(item, entry))
	row.add_child(_sell_button(item, entry))

	return row


func _buy_button(item: ItemResource, entry: ShopEntry) -> Button:
	var button := Button.new()
	button.text = "Buy %d" % entry.BuyPrice
	# Priced per unit, and every unit costs a little more than the last, so the total is the server's to
	# work out - showing amount x price here would be a number the player is then not charged.
	button.tooltip_text = tr("SHOP_BUY_TOOLTIP")
	button.disabled = entry.Offered <= 0
	button.pressed.connect(_on_trade.bind(item.item_id, false))

	return button


func _sell_button(item: ItemResource, entry: ShopEntry) -> Button:
	var button := Button.new()
	button.text = "Sell %d" % entry.SellPrice
	button.tooltip_text = tr("SHOP_SELL_TOOLTIP")
	button.disabled = _held(item.item_id) <= 0
	button.pressed.connect(_on_trade.bind(item.item_id, true))

	return button


func _held(item_id: int) -> int:
	if inventory == null:
		return 0

	return inventory.get_item_count(item_id)


func _on_trade(item_id: int, selling: bool) -> void:
	if not is_shopping():
		return

	ConnectionManager.shop_trade(_merchant_entity_id, item_id, int(_amount.value), selling)
