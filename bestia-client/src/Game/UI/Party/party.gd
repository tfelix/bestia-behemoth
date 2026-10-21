class_name PartyPanel
extends PanelContainer

## The party window: every member with their master's health and mana and the bestia they play, and for the
## owner the EXP mode, inviting and kicking. Outside a party it offers to found one.
##
## Nothing is applied on a click. Every change comes back as a fresh roster, which is also how a refused one
## puts the window right again - the EXP mode selector snaps back to the mode the party really has.
##
## Built in code, like [BestiaParty]: the rows come and go with the members.

## Something happened the player should be told, in the chat's notice colour.
signal notice(text: String)
## Whether the player is in a party at all.
signal membership_changed(in_party: bool)

const _EXP_EACH_TAKE := 0
const _EXP_EVEN_SHARE := 1

## Set by ui.gd; where an invitation is asked.
var invitations: InvitationTray

var _party_id: int = 0
var _is_owner := false
## master id -> PartyMemberRow
var _rows: Dictionary[int, PartyMemberRow] = {}
## entity id -> the row showing it
var _rows_by_entity: Dictionary[int, PartyMemberRow] = {}
var _known_members: Dictionary[int, String] = {}
var _has_roster := false
## The name last typed into the invite field, to name in the "sent" notice.
var _last_invited := ""

var _no_party: Control
var _create_name: LineEdit
var _in_party: Control
var _party_name: Label
var _exp_share: OptionButton
var _members: VBoxContainer
var _invite_row: Control
var _invite_name: LineEdit
var _leave: Button


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(320, 380)
	_build()
	_show_no_party()

	ConnectionManager.party_message_received.connect(_on_party_message)
	ConnectionManager.entity_received.connect(_on_entity_received)
	ConnectionManager.operation_success.connect(_on_operation_success)
	# Asked once loaded: a roster pushed while the game scene was still coming up reached nobody.
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.request_party_info()


func is_in_party() -> bool:
	return _party_id != 0


func _build() -> void:
	var body := UiStyle.vbox(8)
	add_child(UiStyle.margin(body, 10, 8, 10, 10))

	# Outside a party.
	var no_party := UiStyle.vbox(8)
	var hint := UiStyle.label(tr("PARTY_NONE_HINT"), 12, UiStyle.MUTED)
	hint.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	no_party.add_child(hint)
	var create_row := UiStyle.hbox(6)
	_create_name = LineEdit.new()
	_create_name.placeholder_text = tr("PARTY_NAME_PLACEHOLDER")
	_create_name.max_length = 20
	_create_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_create_name.text_submitted.connect(func(_text: String) -> void: _create())
	create_row.add_child(_create_name)
	var create := Button.new()
	create.text = tr("PARTY_CREATE")
	create.theme_type_variation = &"PrimaryButton"
	create.pressed.connect(_create)
	create_row.add_child(create)
	no_party.add_child(create_row)
	body.add_child(no_party)
	_no_party = no_party

	# Inside one.
	var in_party := UiStyle.vbox(8)
	in_party.size_flags_vertical = Control.SIZE_EXPAND_FILL

	var header := UiStyle.hbox(6)
	_party_name = UiStyle.label("", 15)
	_party_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_party_name.clip_text = true
	header.add_child(_party_name)
	header.add_child(UiStyle.label(tr("PARTY_EXP_MODE"), 11, UiStyle.MUTED))
	_exp_share = OptionButton.new()
	_exp_share.add_item(tr("PARTY_EXP_EACH_TAKE"), _EXP_EACH_TAKE)
	_exp_share.add_item(tr("PARTY_EXP_EVEN_SHARE"), _EXP_EVEN_SHARE)
	_exp_share.set_item_tooltip(_EXP_EACH_TAKE, tr("PARTY_EXP_EACH_TAKE_HINT"))
	_exp_share.set_item_tooltip(_EXP_EVEN_SHARE, tr("PARTY_EXP_EVEN_SHARE_HINT"))
	_exp_share.add_theme_font_size_override("font_size", 12)
	_exp_share.item_selected.connect(_on_exp_share_selected)
	header.add_child(_exp_share)
	in_party.add_child(header)

	in_party.add_child(HSeparator.new())

	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	_members = UiStyle.vbox(4)
	_members.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(_members)
	in_party.add_child(scroll)

	var invite_row := UiStyle.hbox(6)
	_invite_name = LineEdit.new()
	_invite_name.placeholder_text = tr("PARTY_INVITE_PLACEHOLDER")
	_invite_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_invite_name.text_submitted.connect(func(_text: String) -> void: _invite())
	invite_row.add_child(_invite_name)
	var invite := Button.new()
	invite.text = tr("PARTY_INVITE")
	invite.pressed.connect(_invite)
	invite_row.add_child(invite)
	in_party.add_child(invite_row)
	_invite_row = invite_row

	var footer := UiStyle.hbox(6)
	footer.add_child(UiStyle.spacer())
	_leave = Button.new()
	_leave.pressed.connect(ConnectionManager.leave_party)
	footer.add_child(_leave)
	in_party.add_child(footer)

	body.add_child(in_party)
	_in_party = in_party


func _create() -> void:
	var party_name := _create_name.text.strip_edges()
	if party_name.is_empty():
		return
	ConnectionManager.create_party(party_name)


func _invite() -> void:
	var master_name := _invite_name.text.strip_edges()
	if master_name.is_empty():
		return
	_last_invited = master_name
	_invite_name.text = ""
	ConnectionManager.invite_to_party(master_name)


func _on_exp_share_selected(index: int) -> void:
	ConnectionManager.set_party_exp_share(_exp_share.get_item_id(index) == _EXP_EVEN_SHARE)


func _on_party_message(message: PartySMSG) -> void:
	if message is PartyInfoSMSG:
		if message.PartyId == 0:
			_leave_party_view(false)
		else:
			_show_roster(message)
	elif message is DisbandPartySMSG:
		if message.PartyId == _party_id:
			_leave_party_view(true)
	elif message is PartyInvitationSMSG:
		_offer_invitation(message)


func _offer_invitation(message: PartyInvitationSMSG) -> void:
	if invitations == null:
		return
	var invitation_id: int = message.InvitationId
	invitations.offer(
		&"party",
		tr("PARTY_INVITE_PROMPT") % [message.InvitedByMaster, message.PartyName],
		float(message.ExpiresInSeconds),
		func(accepted: bool) -> void: ConnectionManager.answer_party_invite(invitation_id, accepted)
	)


func _on_operation_success(message: OperationSuccess) -> void:
	if message.CodeName == "party_invitation_sent":
		notice.emit(tr("PARTY_INVITE_SENT") % _last_invited)


func _show_roster(roster: PartyInfoSMSG) -> void:
	var joined := _party_id != roster.PartyId
	_party_id = roster.PartyId

	var own_master_id := _own_master_id()
	_is_owner = roster.OwnerMasterId == own_master_id

	_party_name.text = roster.PartyName
	_exp_share.select(_EXP_EVEN_SHARE if roster.EvenShare else _EXP_EACH_TAKE)
	_exp_share.disabled = not _is_owner
	_invite_row.visible = _is_owner
	_leave.text = tr("PARTY_DISBAND") if _is_owner else tr("PARTY_LEAVE")
	_leave.tooltip_text = tr("PARTY_DISBAND_HINT") if _is_owner else ""

	_announce_changes(roster, joined)
	_update_rows(roster, own_master_id)

	_no_party.visible = false
	_in_party.visible = true
	if joined:
		membership_changed.emit(true)


## Says who came and went, but not on the roster that only tells us what we were already in.
func _announce_changes(roster: PartyInfoSMSG, joined: bool) -> void:
	var now: Dictionary[int, String] = {}
	for member in roster.Members:
		now[member.MasterId] = member.MasterName

	if _has_roster and joined:
		notice.emit(tr("PARTY_JOINED") % roster.PartyName)
	elif _has_roster:
		for master_id in now:
			if not _known_members.has(master_id):
				notice.emit(tr("PARTY_MEMBER_JOINED") % now[master_id])
		for master_id in _known_members:
			if not now.has(master_id):
				notice.emit(tr("PARTY_MEMBER_LEFT") % _known_members[master_id])

	_known_members = now
	_has_roster = true


## Rows are kept and redrawn rather than rebuilt, so a roster that only moved a bar does not flicker the list.
func _update_rows(roster: PartyInfoSMSG, own_master_id: int) -> void:
	var seen: Dictionary[int, bool] = {}
	_rows_by_entity.clear()

	for i in roster.Members.size():
		var member: PartyMemberInfo = roster.Members[i]
		seen[member.MasterId] = true

		var row: PartyMemberRow = _rows.get(member.MasterId)
		if row == null:
			row = PartyMemberRow.new()
			row.kick_requested.connect(ConnectionManager.kick_party_member)
			_members.add_child(row)
			_rows[member.MasterId] = row
		_members.move_child(row, i)

		var is_owner_row: bool = member.MasterId == roster.OwnerMasterId
		row.show_member(member, is_owner_row, member.MasterId == own_master_id, _is_owner)
		for entity_id in row.entity_ids():
			_rows_by_entity[entity_id] = row

	for master_id in _rows.keys():
		if not seen.has(master_id):
			_rows[master_id].queue_free()
			_rows.erase(master_id)


func _leave_party_view(announce: bool) -> void:
	var was_in_party := _party_id != 0
	_party_id = 0
	_is_owner = false
	for row in _rows.values():
		row.queue_free()
	_rows.clear()
	_rows_by_entity.clear()
	_known_members.clear()
	_has_roster = true

	_show_no_party()
	if announce and was_in_party:
		notice.emit(tr("PARTY_LEFT"))
	if was_in_party:
		membership_changed.emit(false)


func _show_no_party() -> void:
	_no_party.visible = true
	_in_party.visible = false
	var master_info = ConnectionManager.selected_master_info
	if master_info != null and _create_name.text.is_empty():
		_create_name.text = tr("PARTY_DEFAULT_NAME") % master_info.Name


func _on_entity_received(msg: EntitySMSG) -> void:
	if not (msg is HealthComponentSMSG or msg is ManaComponentSMSG):
		return
	var row: PartyMemberRow = _rows_by_entity.get(msg.EntityId)
	if row != null:
		row.apply_vitals(msg)


func _own_master_id() -> int:
	var master_info = ConnectionManager.selected_master_info
	return int(master_info.MasterId) if master_info != null else 0
