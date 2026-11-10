class_name AuctionOwnerPage
extends VBoxContainer

## The Owner tab of a player's own auction house: the owner fee, the till and tearing the house down.
##
## Every button asks the server, which answers with the house window again, so this only ever shows what the
## server sent last.

var house_entity_id: int = 0

var _fee: SpinBox
var _set_fee: Button
var _fee_note: Label
var _till: Label
var _collect: Button
var _tear_down: Button


func _ready() -> void:
	add_theme_constant_override("separation", 8)
	_build()


func show_house(msg: AuctionHouseOpenSMSG) -> void:
	house_entity_id = msg.HouseEntityId
	_fee.min_value = 1
	_fee.max_value = maxi(1, msg.MaxOwnerFeePercent)
	_fee.value = msg.OwnerFeePercent

	var waiting := msg.FeeChangeableAtEpochSeconds > 0
	_set_fee.disabled = waiting
	_fee_note.text = tr("AUCTION_OWNER_FEE_WAIT") % Time.get_datetime_string_from_unix_time(msg.FeeChangeableAtEpochSeconds, true) \
		if waiting else tr("AUCTION_OWNER_FEE_NOTE") % msg.MaxOwnerFeePercent

	_till.text = tr("AUCTION_OWNER_TILL") % AuctionFormat.coins(msg.Till)
	_collect.disabled = msg.Till <= 0
	_tear_down.disabled = not msg.CanTearDown
	_tear_down.tooltip_text = "" if msg.CanTearDown else tr("ERROR_AUCTION_HOUSE_NOT_EMPTY")


func _build() -> void:
	var fee_row := UiStyle.hbox(6)
	add_child(fee_row)
	fee_row.add_child(UiStyle.label(tr("AUCTION_OWNER_FEE"), 13))
	_fee = SpinBox.new()
	_fee.suffix = "%"
	fee_row.add_child(_fee)
	_set_fee = Button.new()
	_set_fee.text = tr("AUCTION_OWNER_SET_FEE")
	_set_fee.pressed.connect(func() -> void: ConnectionManager.set_auction_fee(house_entity_id, int(_fee.value)))
	fee_row.add_child(_set_fee)
	_fee_note = UiStyle.label("", 11, UiStyle.MUTED)
	add_child(_fee_note)

	var till_row := UiStyle.hbox(6)
	add_child(till_row)
	_till = UiStyle.label("", 13, UiStyle.ACCENT)
	till_row.add_child(_till)
	_collect = Button.new()
	_collect.text = tr("AUCTION_OWNER_COLLECT")
	_collect.pressed.connect(func() -> void: ConnectionManager.collect_auction_till(house_entity_id))
	till_row.add_child(_collect)

	add_child(UiStyle.spacer())
	_tear_down = Button.new()
	_tear_down.text = tr("AUCTION_OWNER_TEAR_DOWN")
	_tear_down.pressed.connect(func() -> void: ConnectionManager.tear_down_auction_house(house_entity_id))
	add_child(_tear_down)
