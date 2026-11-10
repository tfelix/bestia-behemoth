class_name WonBoughtPanel
extends PanelContainer

## Won & Bought: what this master bought or won at a linked house, where it waits and for how long. It can be
## collected in person at that house, by the master or one of their bestias, or sent home by post from anywhere.
##
## Also hears Trade Agreement offers and notices while closed, and says where a purchase waits.

signal notice(text: String)

## Assigned by ui.gd: a Trade Agreement offer is answered in the invitation tray.
var invitations: InvitationTray = null

var _rows: VBoxContainer
var _empty: Label


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(460, 300)
	_build()

	ConnectionManager.pickups_received.connect(_show)
	ConnectionManager.auction_purchased.connect(_on_purchased)
	ConnectionManager.trade_agreement_offered.connect(_on_offered)
	ConnectionManager.trade_link_notice.connect(_on_link_notice)
	visibility_changed.connect(func() -> void: if visible: refresh())


func refresh() -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.request_pickups()


func _show(msg: PickupsSMSG) -> void:
	for child in _rows.get_children():
		child.queue_free()

	var item_db := ItemDB.get_instance()
	for pickup in msg.Pickups:
		var item := item_db.get_item(pickup.ItemId)
		if item != null:
			_rows.add_child(_row(pickup, item))
	_empty.visible = msg.Pickups.is_empty()


func _row(pickup: PickupEntry, item: ItemResource) -> Control:
	var row := UiStyle.hbox(8)

	var icon := TextureRect.new()
	icon.texture = item.get_icon()
	icon.custom_minimum_size = Vector2(28, 28)
	icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	row.add_child(icon)

	var lines := UiStyle.vbox(0)
	lines.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	lines.add_child(UiStyle.label("%s ×%d" % [tr(item.name_key), pickup.Amount], 13))
	lines.add_child(UiStyle.label(
		tr("WON_BOUGHT_WAITS") % [pickup.TownName, AuctionFormat.time_left(pickup.ExpiresAtEpochSeconds)], 11, UiStyle.MUTED
	))
	row.add_child(lines)

	var collect := Button.new()
	collect.text = tr("WON_BOUGHT_COLLECT")
	collect.tooltip_text = tr("WON_BOUGHT_COLLECT_HINT")
	collect.pressed.connect(func() -> void: ConnectionManager.collect_pickup(pickup.PickupId))
	row.add_child(collect)

	var post := Button.new()
	post.text = tr("WON_BOUGHT_POST") % AuctionFormat.coins(pickup.PostFee)
	post.pressed.connect(func() -> void: ConnectionManager.post_pickup(pickup.PickupId))
	row.add_child(post)

	return row


func _on_purchased(msg: AuctionPurchasedSMSG) -> void:
	if msg.WaitingAt.is_empty():
		return
	var item := ItemDB.get_instance().get_item(msg.ItemId)
	var item_name := tr(item.name_key) if item != null else "?"
	notice.emit(tr("WON_BOUGHT_PURCHASED") % [item_name, msg.WaitingAt])
	if visible:
		refresh()


func _on_offered(msg: TradeAgreementOfferSMSG) -> void:
	if invitations == null:
		return
	var offer_id: int = msg.OfferId
	invitations.offer(
		&"trade_agreement",
		tr("TRADE_AGREEMENT_OFFER_PROMPT") % msg.FromName,
		_OFFER_SECONDS,
		func(accepted: bool) -> void: ConnectionManager.answer_trade_agreement(offer_id, accepted)
	)


func _on_link_notice(msg: TradeLinkNoticeSMSG) -> void:
	notice.emit(tr("TRADE_LINK_NOTICE_%s" % msg.NoticeName.to_upper()) % msg.PartnerName)


## The server lets an offer lapse after a minute.
const _OFFER_SECONDS := 60.0


func _build() -> void:
	var body := UiStyle.vbox(8)
	add_child(UiStyle.margin(body, 12, 10, 12, 12))
	body.add_child(UiStyle.label(tr("WON_BOUGHT_TITLE"), 16, UiStyle.ACCENT))
	body.add_child(UiStyle.label(tr("WON_BOUGHT_HELP"), 11, UiStyle.MUTED))

	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	body.add_child(scroll)
	var list := UiStyle.vbox(4)
	list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(list)
	_rows = UiStyle.vbox(4)
	list.add_child(_rows)
	_empty = UiStyle.label(tr("WON_BOUGHT_EMPTY"), 12, UiStyle.MUTED)
	list.add_child(_empty)
