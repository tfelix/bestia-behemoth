class_name StallPanel
extends PanelContainer

## A Trader's street stall. The seller lists goods from the bag and takes them back; everyone else buys.
##
## Every StreetStallSMSG is a full snapshot, so each one simply redraws the window. Built in code, like
## [AuctionHousePanel].

signal stall_opened(mine: bool)
signal stall_closed()

## How far a buyer may wander from where the window opened before it closes. Only the seller hears from the
## server when the stall packs up, so a buyer's window watches the distance and the stall entity itself.
const _WALK_AWAY_METRES := 5.0

## Assigned by ui.gd, like the auction house's: the window only exists once its WidgetWindow is ready.
var inventory: Inventory = null:
	set(value):
		inventory = value
		inventory.gold_changed.connect(_show_gold)
		inventory.inventory_updated.connect(_fill_goods)

## The stall the window shows. Zero while closed. Every request names it.
var _stall_entity_id: int = 0
var _mine := false
var _max_listings := 0
var _listing_count := 0
var _closes_at_ms := 0
var _opened_at := Vector3.ZERO

var _title: Label
var _closes_in: Label
var _rows: VBoxContainer
var _empty: Label
var _gold_row: HBoxContainer
var _gold: Label
var _seller_area: VBoxContainer
var _free_places: Label
var _goods: OptionButton
var _amount: SpinBox
## The pile the amount box was last set for.
var _amount_for := 0
var _price: SpinBox
var _list: Button
var _confirm: ConfirmationDialog
var _pending_buy: StallListingEntry


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(600, 440)
	_build()

	ConnectionManager.street_stall_received.connect(_on_stall)
	visibility_changed.connect(_on_visibility_changed)
	set_process(false)


func is_open() -> bool:
	return _stall_entity_id != 0


func close_stall() -> void:
	if not is_open():
		return

	_stall_entity_id = 0
	set_process(false)
	stall_closed.emit()


func _on_stall(msg: StreetStallSMSG) -> void:
	if msg.Closed:
		if msg.StallEntityId == _stall_entity_id:
			close_stall()
		return

	var was_open := is_open()
	_stall_entity_id = msg.StallEntityId
	_mine = msg.Mine
	_max_listings = msg.MaxListings
	_listing_count = msg.Listings.size()
	_closes_at_ms = Time.get_ticks_msec() + msg.ClosesInSeconds * 1000

	var player := _player()
	if not was_open:
		_opened_at = player.global_position if player != null else Vector3.ZERO
	set_process(true)

	_title.text = tr("STALL_TITLE") % msg.SellerName
	_show_closes_in()
	_show_listings(msg.Listings)

	_seller_area.visible = _mine
	_gold_row.visible = not _mine
	_free_places.text = tr("STALL_FREE_PLACES") % maxi(0, _max_listings - _listing_count)
	_fill_goods()
	_show_gold()

	if not was_open:
		stall_opened.emit(_mine)


func _process(_delta: float) -> void:
	_show_closes_in()
	if Time.get_ticks_msec() >= _closes_at_ms:
		close_stall()
		return

	if _mine:
		return
	var player := _player()
	var manager := EntityManager.get_instance()
	var stall_gone := manager == null or manager.get_entity(_stall_entity_id) == null
	if stall_gone or player == null or player.global_position.distance_to(_opened_at) > _WALK_AWAY_METRES:
		close_stall()


## The window's own close button only hides it.
func _on_visibility_changed() -> void:
	if not is_visible_in_tree():
		close_stall()


func _show_closes_in() -> void:
	var minutes := ceili(maxi(0, _closes_at_ms - Time.get_ticks_msec()) / 60000.0)
	var text := tr("STALL_CLOSES_IN") % minutes
	if _closes_in.text != text:
		_closes_in.text = text


func _show_listings(listings: Array) -> void:
	for child in _rows.get_children():
		child.queue_free()

	var item_db := ItemDB.get_instance()
	var action_text := tr("STALL_TAKE_BACK") if _mine else tr("STALL_BUY")
	for listing in listings:
		var item := item_db.get_item(listing.ItemId)
		if item == null:
			printerr("Stall: item with ID %s not found in ItemDB" % [listing.ItemId])
			continue
		var row := StallListingRow.new(listing, item, action_text)
		row.action_pressed.connect(_take_back if _mine else _ask_to_buy)
		_rows.add_child(row)

	_empty.visible = listings.is_empty()


func _take_back(listing: StallListingEntry) -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.unlist_from_stall(_stall_entity_id, listing.ListingId)


func _ask_to_buy(listing: StallListingEntry) -> void:
	_pending_buy = listing
	var item := ItemDB.get_instance().get_item(listing.ItemId)
	_confirm.dialog_text = tr("STALL_BUY_CONFIRM") % [listing.Amount, tr(item.name_key), AuctionFormat.coins(listing.Price)]
	_confirm.popup_centered()


func _on_buy_confirmed() -> void:
	if _pending_buy != null and is_open() and ConnectionManager.is_ready_to_send():
		ConnectionManager.buy_from_stall(_stall_entity_id, _pending_buy.ListingId)
	_pending_buy = null


## Refilled whenever the bag changes, keeping the chosen pile selected while it is still held.
func _fill_goods() -> void:
	if _goods == null or not is_open() or not _mine or inventory == null:
		return

	var chosen := _chosen_player_item_id()
	_goods.clear()
	for held in inventory.sellable_held():
		var label := tr(held.item.name_key)
		if held.upgrade_level > 0:
			label += " +%d" % held.upgrade_level
		label += " ×%d" % held.amount
		_goods.add_item(label)
		var index := _goods.item_count - 1
		_goods.set_item_metadata(index, {"player_item_id": held.player_item_id, "held": held.amount})
		if held.player_item_id == chosen:
			_goods.select(index)

	if _goods.selected < 0 and _goods.item_count > 0:
		_goods.select(0)
	_on_goods_selected(_goods.selected)


func _chosen_player_item_id() -> int:
	if _goods.selected < 0:
		return 0
	return _goods.get_item_metadata(_goods.selected)["player_item_id"]


## The amount starts at the whole pile and is capped at what is held.
func _on_goods_selected(index: int) -> void:
	var held: int = _goods.get_item_metadata(index)["held"] if index >= 0 else 0
	var picked_another := _chosen_player_item_id() != _amount_for
	_amount_for = _chosen_player_item_id()
	_amount.max_value = maxi(1, held)
	_amount.value = held if picked_another else clampi(int(_amount.value), 1, maxi(1, held))
	_amount.editable = held > 1
	_list.disabled = held <= 0 or _listing_count >= _max_listings


func _on_list_pressed() -> void:
	var player_item_id := _chosen_player_item_id()
	if player_item_id == 0 or not is_open() or not ConnectionManager.is_ready_to_send():
		return

	ConnectionManager.list_on_stall(_stall_entity_id, player_item_id, int(_amount.value), int(_price.value))


func _show_gold(_amount_now: int = 0) -> void:
	if inventory != null:
		_gold.text = AuctionFormat.coins(inventory.gold())


func _player() -> Entity:
	var manager := EntityManager.get_instance()
	return manager.get_controlled_entity() if manager != null else null


func _build() -> void:
	var body := UiStyle.vbox(8)
	add_child(UiStyle.margin(body, 12, 10, 12, 12))

	var heading := UiStyle.hbox(8)
	body.add_child(heading)
	_title = UiStyle.label("", 16, UiStyle.ACCENT)
	heading.add_child(_title)
	heading.add_child(UiStyle.spacer())
	_closes_in = UiStyle.label("", 11, UiStyle.MUTED)
	heading.add_child(_closes_in)

	body.add_child(StallListingRow.header())

	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	body.add_child(scroll)

	var list := UiStyle.vbox(3)
	list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(list)
	_rows = UiStyle.vbox(3)
	list.add_child(_rows)
	_empty = UiStyle.label(tr("STALL_EMPTY"), 12, UiStyle.MUTED)
	_empty.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	list.add_child(_empty)

	_gold_row = UiStyle.hbox(6)
	body.add_child(_gold_row)
	_gold_row.add_child(UiStyle.label(tr("STALL_YOUR_GOLD"), 12, UiStyle.MUTED))
	_gold = UiStyle.label("0", 12, UiStyle.ACCENT)
	_gold_row.add_child(_gold)
	_gold_row.add_child(AuctionFormat.coin_icon())

	_build_seller_area(body)

	_confirm = ConfirmationDialog.new()
	_confirm.title = tr("STALL_BUY")
	_confirm.confirmed.connect(_on_buy_confirmed)
	add_child(_confirm)


func _build_seller_area(body: VBoxContainer) -> void:
	_seller_area = UiStyle.vbox(6)
	_seller_area.visible = false
	body.add_child(_seller_area)

	var caption := UiStyle.hbox(6)
	_seller_area.add_child(caption)
	caption.add_child(UiStyle.label(tr("STALL_ADD"), 12, UiStyle.MUTED))
	caption.add_child(UiStyle.spacer())
	_free_places = UiStyle.label("", 12, UiStyle.MUTED)
	caption.add_child(_free_places)

	var add := UiStyle.hbox(6)
	_seller_area.add_child(add)

	_goods = OptionButton.new()
	_goods.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_goods.clip_text = true
	_goods.item_selected.connect(_on_goods_selected)
	add.add_child(_goods)

	add.add_child(UiStyle.label(tr("STALL_AMOUNT"), 12, UiStyle.MUTED))
	_amount = SpinBox.new()
	_amount.min_value = 1
	_amount.custom_minimum_size = Vector2(72, 0)
	add.add_child(_amount)

	add.add_child(UiStyle.label(tr("STALL_PRICE"), 12, UiStyle.MUTED))
	_price = SpinBox.new()
	_price.min_value = 1
	_price.max_value = 999_999_999
	_price.custom_minimum_size = Vector2(110, 0)
	_price.tooltip_text = tr("STALL_PRICE_HINT")
	add.add_child(_price)
	add.add_child(AuctionFormat.coin_icon())

	_list = Button.new()
	_list.text = tr("STALL_LIST")
	_list.pressed.connect(_on_list_pressed)
	add.add_child(_list)
