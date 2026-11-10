class_name CitySignPanel
extends PanelContainer

## The sign of a player town: what the town has grown to, and the founder's hand-over and tear-down.
##
## Opened by the server when the player clicks a standing sign in reach, closed by walking away. Also hears
## the town offers and notices while closed, like the guild panel. Built in code, like [AuctionHousePanel].

signal sign_opened()
signal sign_closed()
signal notice(text: String)

const _WALK_AWAY_METRES := 8.0

## Assigned by ui.gd: a hand-over offer is answered in the invitation tray.
var invitations: InvitationTray = null

## The sign the window is open at. Zero while closed.
var _sign_entity_id: int = 0
var _opened_at := Vector3.ZERO

var _name: Label
var _founder: Label
var _size: Label
var _founder_box: VBoxContainer
var _hand_over_name: LineEdit
var _tear_down: Button


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(340, 220)
	_build()

	ConnectionManager.settlement_sign_opened.connect(_on_opened)
	ConnectionManager.settlement_offered.connect(_on_offered)
	ConnectionManager.settlement_notice.connect(_on_notice)
	visibility_changed.connect(func() -> void: if not visible: close_sign())
	set_process(false)


func close_sign() -> void:
	if _sign_entity_id == 0:
		return
	_sign_entity_id = 0
	set_process(false)
	sign_closed.emit()


func _process(_delta: float) -> void:
	var player := _player()
	if player == null or player.global_position.distance_to(_opened_at) > _WALK_AWAY_METRES:
		close_sign()


func _on_opened(msg: SettlementSignSMSG) -> void:
	_sign_entity_id = msg.SignEntityId
	var player := _player()
	_opened_at = player.global_position if player != null else Vector3.ZERO
	set_process(player != null)

	_name.text = msg.Name
	_founder.text = tr("SETTLEMENT_FOUNDER") % msg.FounderName
	_size.text = tr("SETTLEMENT_SIZE") % [msg.RadiusMetres, msg.BuildingPointsTenths / 10.0, msg.SignDefense]
	_founder_box.visible = msg.IsFounder
	_tear_down.disabled = not msg.CanTearDown
	_tear_down.tooltip_text = "" if msg.CanTearDown else tr("ERROR_SETTLEMENT_NOT_EMPTY")
	sign_opened.emit()


func _on_offered(msg: SettlementOfferSMSG) -> void:
	if invitations == null:
		return
	var settlement_id: int = msg.SettlementId
	invitations.offer(
		&"settlement",
		tr("SETTLEMENT_OFFER_PROMPT") % [msg.FromName, msg.Name],
		_OFFER_SECONDS,
		func(accepted: bool) -> void: ConnectionManager.answer_settlement_offer(settlement_id, accepted)
	)


func _on_notice(msg: SettlementNoticeSMSG) -> void:
	notice.emit(tr("SETTLEMENT_NOTICE_%s" % msg.NoticeName.to_upper()) % msg.Name)


## How long a hand-over offer waits in the tray. The server keeps it until answered; this only tidies the tray.
const _OFFER_SECONDS := 60.0


func _hand_over() -> void:
	var master_name := _hand_over_name.text.strip_edges()
	if master_name.is_empty() or _sign_entity_id == 0:
		return
	ConnectionManager.offer_settlement(_sign_entity_id, master_name)
	_hand_over_name.clear()


func _tear_down_sign() -> void:
	if _sign_entity_id == 0:
		return
	ConnectionManager.tear_down_settlement(_sign_entity_id)
	close_sign()


func _player() -> Entity:
	var manager := EntityManager.get_instance()
	return manager.get_controlled_entity() if manager != null else null


func _build() -> void:
	var body := UiStyle.vbox(8)
	add_child(UiStyle.margin(body, 12, 10, 12, 12))

	_name = UiStyle.label("", 16, UiStyle.ACCENT)
	body.add_child(_name)
	_founder = UiStyle.label("", 12, UiStyle.MUTED)
	body.add_child(_founder)
	_size = UiStyle.label("", 13)
	_size.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	body.add_child(_size)

	_founder_box = UiStyle.vbox(6)
	body.add_child(_founder_box)

	var hand_over := UiStyle.hbox(6)
	_founder_box.add_child(hand_over)
	_hand_over_name = LineEdit.new()
	_hand_over_name.placeholder_text = tr("SETTLEMENT_HAND_OVER_NAME")
	_hand_over_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_hand_over_name.text_submitted.connect(func(_text: String) -> void: _hand_over())
	hand_over.add_child(_hand_over_name)
	var offer := Button.new()
	offer.text = tr("SETTLEMENT_HAND_OVER")
	offer.pressed.connect(_hand_over)
	hand_over.add_child(offer)

	_tear_down = Button.new()
	_tear_down.text = tr("SETTLEMENT_TEAR_DOWN")
	_tear_down.pressed.connect(_tear_down_sign)
	_founder_box.add_child(_tear_down)
