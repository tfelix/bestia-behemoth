class_name AuctionBrowsePage
extends VBoxContainer

## The Browse tab: filter the market, page through it, buy.
##
## Every page comes from the server; nothing is filtered or sorted here except turning a typed name into the
## items it matches, because names live only on the client.

## The most item ids a name search sends; a name that matches more is too short to mean anything.
const _MAX_NAME_MATCHES := 200

## Mirrors the server's AuctionCategory; 0 is any.
const _CATEGORIES := ["AUCTION_CATEGORY_ANY", "AUCTION_CATEGORY_WEAPON", "AUCTION_CATEGORY_ARMOR",
	"AUCTION_CATEGORY_ACCESSORY", "AUCTION_CATEGORY_CONSUMABLE", "AUCTION_CATEGORY_MATERIAL"]

## The window's house, which every request names. Set by [AuctionHousePanel].
var house_entity_id: int = 0

var _page := 0
var _page_count := 0

var _name: LineEdit
var _category: OptionButton
var _min_level: SpinBox
var _max_level: SpinBox
var _rows: VBoxContainer
var _empty: Label
var _page_label: Label
var _previous: Button
var _next: Button
var _gold: Label
var _confirm: ConfirmationDialog
var _pending_buy: AuctionListingEntry
var _bid_dialog: ConfirmationDialog
var _bid_amount: SpinBox
var _bid_note: Label
var _pending_bid: AuctionListingEntry


func _ready() -> void:
	add_theme_constant_override("separation", 6)
	_build()
	ConnectionManager.auction_listings_received.connect(_on_listings)
	ConnectionManager.auction_purchased.connect(_on_purchased)


## Searches again from the first page, as the Search button does.
func search() -> void:
	_request(0)


func set_gold(amount: int) -> void:
	_gold.text = AuctionFormat.coins(amount)


func _request(page: int) -> void:
	if house_entity_id == 0 or not ConnectionManager.is_ready_to_send():
		return

	var item_ids := _matching_item_ids(_name.text.strip_edges())
	# A name that matches nothing has to find nothing, not everything.
	if not _name.text.strip_edges().is_empty() and item_ids.is_empty():
		_show([], 0, 0)
		return

	ConnectionManager.search_auctions(
		house_entity_id, _category.selected, int(_min_level.value), int(_max_level.value), item_ids, false, page
	)


func _matching_item_ids(text: String) -> Array[int]:
	var ids: Array[int] = []
	if text.is_empty():
		return ids

	var needle := text.to_lower()
	for item in ItemDB.get_instance().all_items():
		if tr(item.name_key).to_lower().contains(needle):
			ids.append(item.item_id)
			if ids.size() >= _MAX_NAME_MATCHES:
				break
	return ids


func _on_listings(msg: AuctionListingsSMSG) -> void:
	if msg.Mine:
		return

	_show(msg.Listings, msg.Page, msg.PageCount)


func _show(listings: Array, page: int, page_count: int) -> void:
	_page = page
	_page_count = page_count

	for child in _rows.get_children():
		child.queue_free()

	var item_db := ItemDB.get_instance()
	for listing in listings:
		var item := item_db.get_item(listing.ItemId)
		if item == null:
			printerr("Auction: item with ID %s not found in ItemDB" % [listing.ItemId])
			continue
		var row := AuctionListingRow.new(listing, item, "AUCTION_BID" if listing.IsAuction else "AUCTION_BUY")
		row.action_pressed.connect(_ask_to_bid if listing.IsAuction else _ask_to_buy)
		_rows.add_child(row)

	_empty.visible = listings.is_empty()
	_page_label.text = tr("AUCTION_PAGE") % [page + 1, maxi(1, page_count)]
	_previous.disabled = page <= 0
	_next.disabled = page + 1 >= page_count


func _ask_to_buy(listing: AuctionListingEntry) -> void:
	_pending_buy = listing
	var item := ItemDB.get_instance().get_item(listing.ItemId)
	_confirm.dialog_text = tr("AUCTION_BUY_CONFIRM") % [listing.Amount, tr(item.name_key), AuctionFormat.coins(listing.Price)]
	_confirm.popup_centered()


func _ask_to_bid(listing: AuctionListingEntry) -> void:
	_pending_bid = listing
	_bid_amount.min_value = listing.NextMinBid
	_bid_amount.max_value = listing.Buyout if listing.Buyout > 0 else 1_000_000_000
	_bid_amount.value = listing.NextMinBid
	_bid_note.text = tr("AUCTION_BID_NOTE") % AuctionFormat.coins(listing.NextMinBid)
	if listing.Buyout > 0:
		_bid_note.text += "\n" + tr("AUCTION_BID_BUYOUT") % AuctionFormat.coins(listing.Buyout)
	_bid_dialog.popup_centered()


func _on_bid_confirmed() -> void:
	if _pending_bid != null:
		ConnectionManager.bid_auction(house_entity_id, _pending_bid.ListingId, int(_bid_amount.value))
		_pending_bid = null


func _on_buy_confirmed() -> void:
	if _pending_buy != null:
		ConnectionManager.buy_auction_listing(house_entity_id, _pending_buy.ListingId)
		_pending_buy = null


func _on_purchased(_msg: AuctionPurchasedSMSG) -> void:
	_request(_page)


func _build() -> void:
	var filters := UiStyle.hbox(6)
	add_child(filters)

	_name = LineEdit.new()
	_name.placeholder_text = tr("AUCTION_SEARCH_NAME")
	_name.clear_button_enabled = true
	_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_name.text_submitted.connect(func(_text: String) -> void: search())
	filters.add_child(_name)

	_category = OptionButton.new()
	for key in _CATEGORIES:
		_category.add_item(tr(key))
	filters.add_child(_category)

	filters.add_child(UiStyle.label(tr("AUCTION_LEVEL"), 12, UiStyle.MUTED))
	_min_level = _level_box()
	filters.add_child(_min_level)
	filters.add_child(UiStyle.label("–", 12, UiStyle.MUTED))
	_max_level = _level_box()
	filters.add_child(_max_level)

	var search_button := Button.new()
	search_button.text = tr("AUCTION_SEARCH")
	search_button.pressed.connect(search)
	filters.add_child(search_button)

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
	_empty = UiStyle.label(tr("AUCTION_BROWSE_EMPTY"), 12, UiStyle.MUTED)
	_empty.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	list.add_child(_empty)

	var footer := UiStyle.hbox(6)
	add_child(footer)
	footer.add_child(UiStyle.label(tr("AUCTION_YOUR_GOLD"), 12, UiStyle.MUTED))
	_gold = UiStyle.label("0", 12, UiStyle.ACCENT)
	footer.add_child(_gold)
	footer.add_child(AuctionFormat.coin_icon())
	footer.add_child(UiStyle.spacer())
	_previous = Button.new()
	_previous.text = "‹"
	_previous.pressed.connect(func() -> void: _request(_page - 1))
	footer.add_child(_previous)
	_page_label = UiStyle.label("", 12, UiStyle.MUTED)
	footer.add_child(_page_label)
	_next = Button.new()
	_next.text = "›"
	_next.pressed.connect(func() -> void: _request(_page + 1))
	footer.add_child(_next)

	_confirm = ConfirmationDialog.new()
	_confirm.title = tr("AUCTION_BUY")
	_confirm.confirmed.connect(_on_buy_confirmed)
	add_child(_confirm)

	_bid_dialog = ConfirmationDialog.new()
	_bid_dialog.title = tr("AUCTION_BID")
	var bid_body := UiStyle.vbox(6)
	_bid_note = UiStyle.label("", 12, UiStyle.MUTED)
	bid_body.add_child(_bid_note)
	_bid_amount = SpinBox.new()
	bid_body.add_child(_bid_amount)
	_bid_dialog.add_child(bid_body)
	_bid_dialog.confirmed.connect(_on_bid_confirmed)
	add_child(_bid_dialog)

	_show([], 0, 0)


func _level_box() -> SpinBox:
	var box := SpinBox.new()
	box.min_value = 0
	box.max_value = 999
	box.custom_minimum_size = Vector2(64, 0)
	box.tooltip_text = tr("AUCTION_LEVEL_OPEN")
	return box
