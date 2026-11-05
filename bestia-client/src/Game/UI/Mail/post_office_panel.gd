class_name PostOfficePanel
extends PanelContainer

## The post office: parcels waiting or on the road, sending one, and the home office. Letters need no office
## and live in [LetterPanel].
##
## Opened by the server when the player talks to a postmaster at work, and closed by walking away, like the
## shop. Built in code, like [AuctionHousePanel].

signal post_office_opened()
signal post_office_closed()
signal notice(text: String)

## How far the player may wander from where the window opened before it closes. The shop's distance: the
## postmaster stands behind a counter, not in a hall.
const _WALK_AWAY_METRES := 10.0

## Hours between coaches, by office level. Must match the server's CoachTimetable and the design docs.
const _COACH_HOURS := {1: 6, 2: 3, 3: 2}

## Must match the server's Freight: 10 kg, in item weight units.
const _UNITS_PER_KG := 100.0

## Waits for typing to settle before weighing again, so a name is not quoted a letter at a time.
const _QUOTE_DELAY := 0.4

enum Tab { MAILBOX, SEND }

var _drop_amount_dialog := preload("res://Game/UI/Inventory/DropAmountDialog/DropAmountDialog.tscn")

## The postmaster the window is open at. Zero while closed. Every request names them.
var _postmaster_id: int = 0
var _opened_at := Vector3.ZERO

## The parcel being packed: player item id to [code]{item_id, amount}[/code].
var _packed: Dictionary = {}

var _office: Label
var _coach: Label
var _home: Label
var _make_home: Button
var _tabs: TabContainer
var _parcels: VBoxContainer
var _no_parcels: Label
var _recipient: LineEdit
var _drop_zone: PanelContainer
var _lines: VBoxContainer
var _coach_only: CheckBox
var _quote: Label
var _send: Button
var _quote_timer: Timer


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(560, 420)
	_build()

	ConnectionManager.post_office_opened.connect(_on_opened)
	ConnectionManager.parcels_received.connect(_on_parcels)
	ConnectionManager.parcel_quote_received.connect(_on_quote)
	ConnectionManager.parcel_sent.connect(_on_sent)
	ConnectionManager.operation_error.connect(func(_msg: OperationError) -> void: _update_send())
	visibility_changed.connect(_on_visibility_changed)
	set_process(false)


func is_open() -> bool:
	return _postmaster_id != 0


func close_office() -> void:
	if not is_open():
		return

	_postmaster_id = 0
	set_process(false)
	post_office_closed.emit()


func _process(_delta: float) -> void:
	var player := _player()
	if player == null or player.global_position.distance_to(_opened_at) > _WALK_AWAY_METRES:
		close_office()


func _on_opened(msg: PostOfficeOpenSMSG) -> void:
	var was_open := is_open()
	_postmaster_id = msg.PostmasterEntityId

	var player := _player()
	if not was_open:
		_opened_at = player.global_position if player != null else Vector3.ZERO
	set_process(player != null)

	_office.text = tr("POST_OFFICE_TITLE") % msg.OfficeName
	_coach.text = tr("POST_OFFICE_COACH") % _COACH_HOURS.get(msg.Level, 3)
	_home.text = tr("POST_OFFICE_HOME") % msg.HomeOfficeName if not msg.HomeOfficeName.is_empty() else tr("POST_OFFICE_NO_HOME")
	_make_home.disabled = msg.IsHome
	_make_home.text = tr("POST_OFFICE_IS_HOME") if msg.IsHome else tr("POST_OFFICE_MAKE_HOME")

	if not was_open:
		_tabs.current_tab = Tab.MAILBOX
		post_office_opened.emit()


## The window's own close button only hides it.
func _on_visibility_changed() -> void:
	if not is_visible_in_tree():
		close_office()


func _on_make_home_pressed() -> void:
	if is_open():
		_make_home.disabled = true
		ConnectionManager.set_home_post_office(_postmaster_id)


func _player() -> Entity:
	var manager := EntityManager.get_instance()
	return manager.get_controlled_entity() if manager != null else null


# ----------------------------------------------------------------- mailbox

func _on_parcels(msg: ParcelsSMSG) -> void:
	for row in _parcels.get_children():
		row.queue_free()

	_no_parcels.visible = msg.Parcels.is_empty()
	for parcel in msg.Parcels:
		_parcels.add_child(_parcel_row(parcel))


func _parcel_row(parcel: ParcelEntry) -> Control:
	var row := UiStyle.hbox(8)

	for goods in parcel.Goods:
		row.add_child(_goods_icon(goods.ItemId, goods.Amount))

	var where: String
	if not parcel.Arrived:
		where = tr("PARCEL_ON_THE_ROAD") % [parcel.DestinationName, AuctionFormat.time_left(parcel.LatestArrivalEpochSeconds)]
	elif parcel.CollectableHere:
		where = tr("PARCEL_HERE")
	else:
		where = tr("PARCEL_WAITS_AT") % parcel.DestinationName

	var text := UiStyle.vbox(0)
	var sender := tr("PARCEL_RETURNED") % parcel.SenderName if parcel.Returning else tr("PARCEL_FROM") % parcel.SenderName
	text.add_child(UiStyle.label(sender, 13))
	text.add_child(UiStyle.label(where, 11, UiStyle.MUTED))
	text.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(text)

	if not parcel.Returning:
		var refuse := Button.new()
		refuse.text = tr("PARCEL_REFUSE")
		refuse.pressed.connect(func() -> void: ConnectionManager.refuse_parcel(_postmaster_id, parcel.ParcelId))
		row.add_child(refuse)

	if parcel.CollectableHere:
		var collect := Button.new()
		collect.text = tr("PARCEL_COLLECT")
		collect.pressed.connect(func() -> void: ConnectionManager.collect_parcel(_postmaster_id, parcel.ParcelId))
		row.add_child(collect)

	return row


func _goods_icon(item_id: int, amount: int) -> Control:
	var item := ItemDB.get_instance().get_item(item_id)
	var icon := TextureRect.new()
	icon.custom_minimum_size = Vector2(28, 28)
	icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	if item != null:
		icon.texture = item.get_icon()
		icon.tooltip_text = "%d × %s" % [amount, tr(item.name_key)]
	return icon


# ----------------------------------------------------------------- sending

## Drops count only over the packing area of the Send tab, as the trade window counts only its own column.
func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
	if not is_open() or _tabs.current_tab != Tab.SEND:
		return false
	if typeof(data) != TYPE_DICTIONARY or data.get("source") != "inventory_item":
		return false
	return _drop_zone.get_global_rect().has_point(get_global_mouse_position())


func _drop_data(_at_position: Vector2, data: Variant) -> void:
	var item_id: int = data.get("id")
	var player_item_id: int = data.get("player_item_id", 0)
	var held: int = data.get("amount", 0)
	if held <= 0:
		return

	if held == 1:
		_pack(player_item_id, item_id, 1)
		return

	var dialog = _drop_amount_dialog.instantiate() as DropAmountDialog
	add_child(dialog)
	dialog.amount_confirmed.connect(func(_id: int, amount: int) -> void: _pack(player_item_id, item_id, amount))
	dialog.confirmed.connect(dialog.queue_free)
	dialog.canceled.connect(dialog.queue_free)
	dialog.open_for(ItemDB.get_instance().get_item(item_id), held, tr("PARCEL_PACK_HOW_MANY"))


func _pack(player_item_id: int, item_id: int, amount: int) -> void:
	_packed[player_item_id] = {"item_id": item_id, "amount": amount}
	_show_packed()


func _unpack(player_item_id: int) -> void:
	_packed.erase(player_item_id)
	_show_packed()


func _show_packed() -> void:
	for line in _lines.get_children():
		line.queue_free()

	for player_item_id in _packed:
		var line := UiStyle.hbox(6)
		var packed: Dictionary = _packed[player_item_id]
		line.add_child(_goods_icon(packed["item_id"], packed["amount"]))
		var item := ItemDB.get_instance().get_item(packed["item_id"])
		var label := UiStyle.label("%d × %s" % [packed["amount"], tr(item.name_key) if item != null else "?"], 13)
		label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		line.add_child(label)
		var remove := UiStyle.glyph_button("×", tr("PARCEL_UNPACK"))
		remove.pressed.connect(_unpack.bind(player_item_id))
		line.add_child(remove)
		_lines.add_child(line)

	_quote.text = ""
	_update_send()
	_quote_timer.start()


func _ask_quote() -> void:
	if not is_open() or _packed.is_empty() or _recipient.text.strip_edges().is_empty():
		return

	var ids: Array[int] = []
	var amounts: Array[int] = []
	for player_item_id in _packed:
		ids.append(player_item_id)
		amounts.append(_packed[player_item_id]["amount"])
	ConnectionManager.quote_parcel(_postmaster_id, _recipient.text.strip_edges(), ids, amounts)


func _on_quote(msg: ParcelQuoteSMSG) -> void:
	_quote.text = tr("PARCEL_QUOTE") % [
		msg.WeightUnits / _UNITS_PER_KG, UiStyle.group_digits(msg.Fee), msg.DestinationName,
		AuctionFormat.time_left(msg.LatestArrivalEpochSeconds),
	]


## Disabled until the answer, so a double click cannot pay twice. Chat reports a refusal.
func _on_send_pressed() -> void:
	var ids: Array[int] = []
	var amounts: Array[int] = []
	for player_item_id in _packed:
		ids.append(player_item_id)
		amounts.append(_packed[player_item_id]["amount"])

	_send.disabled = true
	ConnectionManager.send_parcel(_postmaster_id, _recipient.text.strip_edges(), ids, amounts, _coach_only.button_pressed)


func _on_sent(msg: ParcelSentSMSG) -> void:
	notice.emit(tr("PARCEL_SENT") % [msg.RecipientName, AuctionFormat.time_left(msg.LatestArrivalEpochSeconds)])
	_packed.clear()
	_recipient.text = ""
	_show_packed()


func _update_send() -> void:
	_send.disabled = _packed.is_empty() or _recipient.text.strip_edges().is_empty()


# ----------------------------------------------------------------- layout

func _build() -> void:
	var body := UiStyle.vbox(8)
	add_child(UiStyle.margin(body, 12, 10, 12, 12))

	_office = UiStyle.label("", 16, UiStyle.ACCENT)
	body.add_child(_office)
	_coach = UiStyle.label("", 11, UiStyle.MUTED)
	body.add_child(_coach)

	var home_row := UiStyle.hbox(8)
	_home = UiStyle.label("", 13)
	home_row.add_child(_home)
	home_row.add_child(UiStyle.spacer())
	_make_home = Button.new()
	_make_home.pressed.connect(_on_make_home_pressed)
	home_row.add_child(_make_home)
	body.add_child(home_row)

	_tabs = TabContainer.new()
	_tabs.size_flags_vertical = Control.SIZE_EXPAND_FILL
	body.add_child(_tabs)
	_tabs.add_child(_build_mailbox())
	_tabs.add_child(_build_send())
	_tabs.set_tab_title(Tab.MAILBOX, tr("POST_OFFICE_TAB_MAILBOX"))
	_tabs.set_tab_title(Tab.SEND, tr("POST_OFFICE_TAB_SEND"))

	_quote_timer = Timer.new()
	_quote_timer.one_shot = true
	_quote_timer.wait_time = _QUOTE_DELAY
	_quote_timer.timeout.connect(_ask_quote)
	add_child(_quote_timer)


func _build_mailbox() -> Control:
	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED

	var stack := UiStyle.vbox(6)
	stack.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var padded := UiStyle.margin(stack, 4, 6, 4, 4)
	padded.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(padded)

	_parcels = UiStyle.vbox(6)
	_parcels.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	stack.add_child(_parcels)
	_no_parcels = UiStyle.label(tr("POST_OFFICE_NO_PARCELS"), 13, UiStyle.MUTED)
	stack.add_child(_no_parcels)
	return scroll


func _build_send() -> Control:
	var page := UiStyle.vbox(8)

	_recipient = LineEdit.new()
	_recipient.placeholder_text = tr("PARCEL_TO")
	_recipient.text_changed.connect(func(_text: String) -> void:
		_update_send()
		_quote_timer.start())
	page.add_child(_recipient)

	_drop_zone = PanelContainer.new()
	_drop_zone.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_drop_zone.mouse_filter = Control.MOUSE_FILTER_PASS
	_drop_zone.add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ROW_BG, 4, 8))
	var zone := UiStyle.vbox(4)
	zone.mouse_filter = Control.MOUSE_FILTER_IGNORE
	zone.add_child(UiStyle.label(tr("PARCEL_DROP_HERE"), 11, UiStyle.MUTED))
	_lines = UiStyle.vbox(4)
	zone.add_child(_lines)
	_drop_zone.add_child(zone)
	page.add_child(_drop_zone)

	_coach_only = CheckBox.new()
	_coach_only.text = tr("PARCEL_COACH_ONLY")
	_coach_only.tooltip_text = tr("PARCEL_COACH_ONLY_TIP")
	page.add_child(_coach_only)

	var actions := UiStyle.hbox(8)
	_quote = UiStyle.label("", 12, UiStyle.MUTED)
	_quote.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_quote.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	actions.add_child(_quote)
	_send = Button.new()
	_send.text = tr("PARCEL_SEND")
	_send.disabled = true
	_send.pressed.connect(_on_send_pressed)
	actions.add_child(_send)
	page.add_child(actions)
	return page
