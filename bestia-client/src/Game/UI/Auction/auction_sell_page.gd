class_name AuctionSellPage
extends VBoxContainer

## The Sell tab: put a held pile up at a fixed price or as a timed auction, and see and take down your own
## listings at this house.
##
## The fee shown is the one the server charges: a share of the asking price by how long it runs, plus the house
## owner's fee.

## Mirrors the server's AuctionFees: index 0 is a fixed price, the rest are auctions of that many days.
const _DURATIONS := [0, 1, 2, 5]
const _RATES := {0: 0.10, 1: 0.06, 2: 0.10, 5: 0.20}

## The window's house, which every request names. Set by [AuctionHousePanel].
var house_entity_id: int = 0

## The owner's fee at this house in percent; 0 at a start town. Set by [AuctionHousePanel].
var owner_fee_percent: int = 0

## Assigned by [AuctionHousePanel]: what the bag holds is what can be put up.
var inventory: Inventory = null

var _piles: Array = []
var _pile: OptionButton
var _amount: SpinBox
var _price: SpinBox
var _duration: OptionButton
var _buyout: SpinBox
var _fee: Label
var _list: Button
var _rows: VBoxContainer
var _empty: Label


func _ready() -> void:
	add_theme_constant_override("separation", 6)
	_build()
	ConnectionManager.auction_listings_received.connect(_on_listings)


## Refills the pile list from the bag and asks for this player's listings here.
func refresh() -> void:
	_fill_piles()
	if house_entity_id != 0 and ConnectionManager.is_ready_to_send():
		ConnectionManager.search_auctions(house_entity_id, 0, 0, 0, [] as Array[int], true, 0)


func _fill_piles() -> void:
	_piles = inventory.sellable_piles() if inventory != null else []
	_pile.clear()
	for held in _piles:
		_pile.add_item("%s ×%d" % [tr(held.item.name_key), held.amount])
	_on_pile_selected(_pile.selected)


func _on_pile_selected(index: int) -> void:
	var has_pile := index >= 0 and index < _piles.size()
	_amount.max_value = _piles[index].amount if has_pile else 1
	_amount.value = _amount.max_value
	_list.disabled = not has_pile
	_show_fee()


func _show_fee() -> void:
	var price := int(_price.value)
	var days: int = _DURATIONS[maxi(0, _duration.selected)]
	var listing_fee := maxi(1, ceili(price * _RATES[days]))
	_buyout.editable = days != 0
	var owner_fee := 0 if owner_fee_percent <= 0 else maxi(1, ceili(price * owner_fee_percent / 100.0))
	_fee.text = tr("AUCTION_SELL_FEE") % [AuctionFormat.coins(listing_fee + owner_fee), owner_fee_percent]


func _on_list_pressed() -> void:
	var index := _pile.selected
	if index < 0 or index >= _piles.size() or house_entity_id == 0:
		return
	var days: int = _DURATIONS[maxi(0, _duration.selected)]
	var buyout := int(_buyout.value) if days != 0 else 0
	ConnectionManager.list_auction_item(
		house_entity_id, _piles[index].player_item_id, int(_amount.value), int(_price.value), days, buyout
	)


func _on_listings(msg: AuctionListingsSMSG) -> void:
	if not msg.Mine:
		return

	for child in _rows.get_children():
		child.queue_free()
	var item_db := ItemDB.get_instance()
	for listing in msg.Listings:
		var item := item_db.get_item(listing.ItemId)
		if item == null:
			continue
		var row := AuctionListingRow.new(listing, item, "AUCTION_TAKE_DOWN")
		row.action_pressed.connect(func(taken: AuctionListingEntry) -> void:
			ConnectionManager.cancel_auction_listing(house_entity_id, taken.ListingId)
		)
		_rows.add_child(row)
	_empty.visible = msg.Listings.is_empty()
	_fill_piles()


func _build() -> void:
	var form := UiStyle.hbox(6)
	add_child(form)

	_pile = OptionButton.new()
	_pile.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_pile.item_selected.connect(_on_pile_selected)
	form.add_child(_pile)

	form.add_child(UiStyle.label(tr("AUCTION_SELL_AMOUNT"), 12, UiStyle.MUTED))
	_amount = SpinBox.new()
	_amount.min_value = 1
	_amount.custom_minimum_size = Vector2(70, 0)
	form.add_child(_amount)

	form.add_child(UiStyle.label(tr("AUCTION_SELL_PRICE"), 12, UiStyle.MUTED))
	_price = SpinBox.new()
	_price.min_value = 1
	_price.max_value = 1_000_000_000
	_price.value = 100
	_price.custom_minimum_size = Vector2(110, 0)
	_price.value_changed.connect(func(_value: float) -> void: _show_fee())
	form.add_child(_price)

	_duration = OptionButton.new()
	for days in _DURATIONS:
		_duration.add_item(tr("AUCTION_SELL_FIXED") if days == 0 else tr("AUCTION_SELL_DAYS") % days)
	_duration.item_selected.connect(func(_index: int) -> void: _show_fee())
	form.add_child(_duration)

	form.add_child(UiStyle.label(tr("AUCTION_SELL_BUYOUT"), 12, UiStyle.MUTED))
	_buyout = SpinBox.new()
	_buyout.min_value = 0
	_buyout.max_value = 1_000_000_000
	_buyout.custom_minimum_size = Vector2(110, 0)
	_buyout.tooltip_text = tr("AUCTION_SELL_BUYOUT_HINT")
	form.add_child(_buyout)

	_list = Button.new()
	_list.text = tr("AUCTION_SELL_LIST")
	_list.pressed.connect(_on_list_pressed)
	form.add_child(_list)

	_fee = UiStyle.label("", 12, UiStyle.MUTED)
	add_child(_fee)

	add_child(UiStyle.label(tr("AUCTION_SELL_YOURS"), 13, UiStyle.ACCENT))
	add_child(AuctionListingRow.header())

	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	add_child(scroll)
	var list := UiStyle.vbox(3)
	list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(list)
	_rows = UiStyle.vbox(3)
	list.add_child(_rows)
	_empty = UiStyle.label(tr("AUCTION_SELL_NONE"), 12, UiStyle.MUTED)
	_empty.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	list.add_child(_empty)
