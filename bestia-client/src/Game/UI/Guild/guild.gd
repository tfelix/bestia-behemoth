class_name GuildPanel
extends PanelContainer

## The guild window: its level and EXP, its members, its ranks, its skill tree and its log. Only offered while the
## player is in a guild - [signal membership_changed] tells ui.gd when that changes.
##
## Every control shows only what the player's own rank may do, by the server's rules: the leader acts on
## everyone, anybody else only on members and ranks below their own. The server checks again, and the guild it
## pushes back is what the window then draws, so a refusal puts it right by itself.
##
## Also where a guild is founded: using a Founder's Seal asks [method ask_to_found]. And it greets the member with
## the guild's message of the day after every loading screen - the login's and each teleport's - as Ragnarok Online does.

signal notice(text: String)
signal refused(text: String)
signal membership_changed(in_guild: bool)

## Set by ui.gd; where an invitation is asked.
var invitations: InvitationTray

var _info: GuildInfoSMSG
## False until the first answer, which only says what the player was already in and is not news.
var _synced := false
var _member_rows: Dictionary[int, GuildMemberRow] = {}
var _rank_rows: Dictionary[int, GuildRankRow] = {}
var _last_invited := ""
## A loading screen ended before the first guild arrived, so the message of the day is shown when it does.
var _motd_due := false

var _name: Label
var _level_badge: Label
var _member_count: Label
var _tabs: TabContainer
var _exp_bar: ProgressBar
var _exp_value: Label
var _members_value: Label
var _points_value: Label
var _motd_title: Label
var _motd_body: Label
var _edit_motd: Button
var _motd_popup: PopupPanel
var _motd_title_edit: LineEdit
var _motd_body_edit: TextEdit
var _skills: GuildSkillsPage
var _log: GuildLogPage
var _members: VBoxContainer
var _ranks: VBoxContainer
var _add_rank: Button
var _invite_row: Control
var _invite_name: LineEdit
var _leave: Button
var _disband: Button
var _found_popup: PopupPanel
var _found_name: LineEdit
var _disband_dialog: ConfirmationDialog
var _disband_name: LineEdit


## The guild window wherever it is, for the seal's use script; null before the game scene exists.
static func get_instance() -> GuildPanel:
	var loop := Engine.get_main_loop() as SceneTree
	return loop.get_first_node_in_group("guild_panel") as GuildPanel


func _enter_tree() -> void:
	add_to_group("guild_panel")


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(800, 620)
	_build()
	_build_found_popup()
	_build_disband_dialog()
	_build_motd_popup()

	ConnectionManager.guild_message_received.connect(_on_guild_message)
	ConnectionManager.operation_success.connect(_on_operation_success)
	visibility_changed.connect(_refresh_log)
	SceneManager.loading_finished.connect(_on_loading_finished)
	# Asked once loaded: a guild pushed while the game scene was still coming up reached nobody.
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.request_guild_info()


func is_in_guild() -> bool:
	return _info != null


## Asks for a name and founds a guild with it, spending a Founder's Seal. Refused here when already in one,
## with the server's own wording for it.
func ask_to_found() -> void:
	if is_in_guild():
		refused.emit(tr("GUILD_ALREADY_IN_GUILD"))
		return

	_found_name.text = ""
	_found_popup.popup_centered()


func _build() -> void:
	var body := UiStyle.vbox(8)
	add_child(UiStyle.margin(body, 10, 8, 10, 10))

	var header := UiStyle.hbox(8)
	_name = UiStyle.label("", 17)
	_name.clip_text = true
	_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	header.add_child(_name)
	_level_badge = UiStyle.label("", 12, UiStyle.PANEL_BG)
	var badge := PanelContainer.new()
	badge.add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ACCENT, 8, 8))
	badge.add_child(_level_badge)
	header.add_child(badge)
	_member_count = UiStyle.label("", 12, UiStyle.MUTED)
	header.add_child(_member_count)
	body.add_child(header)

	_tabs = TabContainer.new()
	_tabs.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_tabs.add_child(_build_overview())
	_tabs.add_child(_build_members())
	_tabs.add_child(_build_ranks())
	_skills = GuildSkillsPage.new()
	_tabs.add_child(UiStyle.margin(_skills, 4, 6, 4, 4))
	_log = GuildLogPage.new()
	_tabs.add_child(UiStyle.margin(_log, 4, 6, 4, 4))
	_tabs.set_tab_title(0, tr("GUILD_TAB_OVERVIEW"))
	_tabs.set_tab_title(1, tr("GUILD_TAB_MEMBERS"))
	_tabs.set_tab_title(2, tr("GUILD_TAB_RANKS"))
	_tabs.set_tab_title(3, tr("GUILD_TAB_SKILLS"))
	_tabs.set_tab_title(4, tr("GUILD_TAB_LOG"))
	_tabs.tab_changed.connect(func(_tab: int) -> void: _refresh_log())
	body.add_child(_tabs)

	var footer := UiStyle.hbox(6)
	var invite_row := UiStyle.hbox(6)
	invite_row.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_invite_name = LineEdit.new()
	_invite_name.placeholder_text = tr("GUILD_INVITE_PLACEHOLDER")
	_invite_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_invite_name.text_submitted.connect(func(_text: String) -> void: _invite())
	invite_row.add_child(_invite_name)
	var invite := Button.new()
	invite.text = tr("GUILD_INVITE")
	invite.pressed.connect(_invite)
	invite_row.add_child(invite)
	footer.add_child(invite_row)
	_invite_row = invite_row

	footer.add_child(UiStyle.spacer())
	_leave = Button.new()
	_leave.text = tr("GUILD_LEAVE")
	_leave.pressed.connect(ConnectionManager.leave_guild)
	footer.add_child(_leave)
	_disband = Button.new()
	_disband.text = tr("GUILD_DISBAND")
	_disband.add_theme_color_override("font_color", UiStyle.DANGER)
	_disband.pressed.connect(_ask_to_disband)
	footer.add_child(_disband)
	body.add_child(footer)


func _build_overview() -> Control:
	var overview := UiStyle.vbox(10)

	var exp_row := UiStyle.hbox(8)
	exp_row.add_child(UiStyle.label(tr("GUILD_EXP"), 12, UiStyle.MUTED))
	_exp_bar = UiStyle.bar(UiStyle.EXP_FILL, 10)
	_exp_bar.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	exp_row.add_child(_exp_bar)
	_exp_value = UiStyle.label("", 11, UiStyle.MUTED)
	exp_row.add_child(_exp_value)
	overview.add_child(exp_row)

	var facts := GridContainer.new()
	facts.columns = 2
	facts.add_theme_constant_override("h_separation", 16)
	facts.add_theme_constant_override("v_separation", 4)
	facts.add_child(UiStyle.label(tr("GUILD_MEMBERS_COUNT"), 12, UiStyle.MUTED))
	_members_value = UiStyle.label("", 12)
	facts.add_child(_members_value)
	facts.add_child(UiStyle.label(tr("GUILD_SKILL_POINTS"), 12, UiStyle.MUTED))
	_points_value = UiStyle.label("", 12)
	facts.add_child(_points_value)
	overview.add_child(facts)

	overview.add_child(HSeparator.new())
	var motd_header := UiStyle.hbox(8)
	var motd_label := UiStyle.label(tr("GUILD_MOTD"), 12, UiStyle.MUTED)
	motd_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	motd_header.add_child(motd_label)
	_edit_motd = Button.new()
	_edit_motd.text = tr("GUILD_MOTD_EDIT")
	_edit_motd.theme_type_variation = &"ChipButton"
	_edit_motd.pressed.connect(_ask_for_motd)
	motd_header.add_child(_edit_motd)
	overview.add_child(motd_header)
	_motd_title = UiStyle.label("", 14, UiStyle.ACCENT)
	_motd_title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	overview.add_child(_motd_title)
	_motd_body = UiStyle.label("", 12)
	_motd_body.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	overview.add_child(_motd_body)
	return UiStyle.margin(overview, 6, 8, 6, 6)


func _build_members() -> Control:
	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	_members = UiStyle.vbox(4)
	_members.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(_members)
	return UiStyle.margin(scroll, 4, 6, 4, 4)


func _build_ranks() -> Control:
	var page := UiStyle.vbox(6)

	var columns := UiStyle.hbox(6)
	var name_col := UiStyle.label(tr("GUILD_COL_NAME"), 11, UiStyle.MUTED)
	name_col.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	columns.add_child(name_col)
	columns.add_child(UiStyle.label(tr("GUILD_COL_TAX"), 11, UiStyle.MUTED))
	columns.add_child(UiStyle.label(tr("GUILD_COL_PERMISSIONS"), 11, UiStyle.MUTED))
	page.add_child(UiStyle.margin(columns, 6, 0, 120, 0))

	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	_ranks = UiStyle.vbox(4)
	_ranks.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(_ranks)
	page.add_child(scroll)

	_add_rank = Button.new()
	_add_rank.text = tr("GUILD_RANK_ADD")
	_add_rank.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	_add_rank.pressed.connect(_add_new_rank)
	page.add_child(_add_rank)

	return UiStyle.margin(page, 4, 6, 4, 4)


func _build_found_popup() -> void:
	_found_popup = PopupPanel.new()
	_found_popup.add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 6, 12, UiStyle.ACCENT, 1))
	var rows := UiStyle.vbox(8)
	rows.custom_minimum_size = Vector2(280, 0)
	rows.add_child(UiStyle.label(tr("GUILD_FOUND_TITLE"), 15, UiStyle.ACCENT))
	var prompt := UiStyle.label(tr("GUILD_FOUND_PROMPT"), 12, UiStyle.MUTED)
	prompt.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	rows.add_child(prompt)
	_found_name = LineEdit.new()
	_found_name.max_length = 24
	_found_name.text_submitted.connect(func(_text: String) -> void: _found())
	rows.add_child(_found_name)
	var buttons := UiStyle.hbox(8)
	buttons.alignment = BoxContainer.ALIGNMENT_END
	var cancel := Button.new()
	cancel.text = tr("GUILD_FOUND_CANCEL")
	cancel.pressed.connect(_found_popup.hide)
	buttons.add_child(cancel)
	var confirm := Button.new()
	confirm.text = tr("GUILD_FOUND_CONFIRM")
	confirm.theme_type_variation = &"PrimaryButton"
	confirm.pressed.connect(_found)
	buttons.add_child(confirm)
	rows.add_child(buttons)
	_found_popup.add_child(rows)
	# Focus when shown, not when built, so a second founding attempt can be typed into straight away.
	_found_popup.about_to_popup.connect(func() -> void: _found_name.call_deferred("grab_focus"))
	add_child(_found_popup)


func _build_motd_popup() -> void:
	_motd_popup = PopupPanel.new()
	_motd_popup.add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 6, 12, UiStyle.ACCENT, 1))
	var rows := UiStyle.vbox(8)
	rows.custom_minimum_size = Vector2(360, 0)
	rows.add_child(UiStyle.label(tr("GUILD_MOTD_EDIT_TITLE"), 15, UiStyle.ACCENT))
	_motd_title_edit = LineEdit.new()
	_motd_title_edit.placeholder_text = tr("GUILD_MOTD_TITLE_PLACEHOLDER")
	rows.add_child(_motd_title_edit)
	_motd_body_edit = TextEdit.new()
	_motd_body_edit.placeholder_text = tr("GUILD_MOTD_BODY_PLACEHOLDER")
	_motd_body_edit.wrap_mode = TextEdit.LINE_WRAPPING_BOUNDARY
	_motd_body_edit.custom_minimum_size = Vector2(0, 110)
	_motd_body_edit.text_changed.connect(_limit_motd_body)
	rows.add_child(_motd_body_edit)
	var buttons := UiStyle.hbox(8)
	buttons.alignment = BoxContainer.ALIGNMENT_END
	var cancel := Button.new()
	cancel.text = tr("GUILD_FOUND_CANCEL")
	cancel.pressed.connect(_motd_popup.hide)
	buttons.add_child(cancel)
	var save := Button.new()
	save.text = tr("GUILD_RANK_SAVE")
	save.theme_type_variation = &"PrimaryButton"
	save.pressed.connect(_save_motd)
	buttons.add_child(save)
	rows.add_child(buttons)
	_motd_popup.add_child(rows)
	_motd_popup.about_to_popup.connect(func() -> void: _motd_title_edit.call_deferred("grab_focus"))
	add_child(_motd_popup)


func _ask_for_motd() -> void:
	if _info == null:
		return
	_motd_title_edit.max_length = int(_info.MotdTitleMaxLength)
	_motd_title_edit.text = _info.MotdTitle
	_motd_body_edit.text = _info.MotdBody
	_motd_popup.popup_centered()


## TextEdit has no max_length of its own, and the server refuses a message of the day that is too long.
func _limit_motd_body() -> void:
	var limit := int(_info.MotdBodyMaxLength) if _info != null else 0
	if limit > 0 and _motd_body_edit.text.length() > limit:
		_motd_body_edit.text = _motd_body_edit.text.left(limit)
		_motd_body_edit.set_caret_line(_motd_body_edit.get_line_count() - 1)
		_motd_body_edit.set_caret_column(_motd_body_edit.get_line(_motd_body_edit.get_line_count() - 1).length())


func _save_motd() -> void:
	_motd_popup.hide()
	ConnectionManager.set_guild_motd(_motd_title_edit.text.strip_edges(), _motd_body_edit.text.strip_edges())


## The guild may not have been pushed yet when the login's loading screen ends; then it is shown on arrival.
func _on_loading_finished() -> void:
	if _info != null:
		_show_motd_dialog()
	elif not _synced:
		_motd_due = true


func _show_motd_dialog() -> void:
	if _info.MotdTitle.is_empty() and _info.MotdBody.is_empty():
		return
	var content := DialogContent.new("")
	content.title_override = _info.MotdTitle if not _info.MotdTitle.is_empty() else _info.Name
	content.text_override = _info.MotdBody
	DialogManager.show_content(content)


func _found() -> void:
	var guild_name := _found_name.text.strip_edges()
	_found_popup.hide()
	if not guild_name.is_empty():
		ConnectionManager.create_guild(guild_name)


## A typed name, as for deleting a master: a disbanded guild cannot be brought back.
func _build_disband_dialog() -> void:
	_disband_dialog = ConfirmationDialog.new()
	_disband_dialog.title = tr("GUILD_DISBAND_TITLE")
	_disband_dialog.ok_button_text = tr("GUILD_DISBAND")
	var rows := UiStyle.vbox(8)
	var text := UiStyle.label("", 12)
	text.name = "Text"
	text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	text.custom_minimum_size = Vector2(300, 0)
	rows.add_child(text)
	_disband_name = LineEdit.new()
	_disband_name.text_changed.connect(func(typed: String) -> void: _disband_dialog.get_ok_button().disabled = not _matches_guild_name(typed))
	rows.add_child(_disband_name)
	_disband_dialog.add_child(rows)
	_disband_dialog.register_text_enter(_disband_name)
	_disband_dialog.confirmed.connect(_on_disband_confirmed)
	add_child(_disband_dialog)


func _ask_to_disband() -> void:
	if _info == null:
		return
	(_disband_dialog.find_child("Text", true, false) as Label).text = tr("GUILD_DISBAND_TEXT") % _info.Name
	_disband_name.text = ""
	_disband_dialog.get_ok_button().disabled = true
	_disband_dialog.popup_centered()
	_disband_name.call_deferred("grab_focus")


func _matches_guild_name(typed: String) -> bool:
	return _info != null and typed.strip_edges() == _info.Name


## Checked again rather than trusted from the button: Enter submits the dialog whether OK is enabled or not.
func _on_disband_confirmed() -> void:
	var typed := _disband_name.text.strip_edges()
	if _matches_guild_name(typed):
		ConnectionManager.disband_guild(typed)


func _invite() -> void:
	var master_name := _invite_name.text.strip_edges()
	if master_name.is_empty():
		return
	_last_invited = master_name
	_invite_name.text = ""
	ConnectionManager.invite_to_guild(master_name)


func _add_new_rank() -> void:
	if _info == null:
		return
	ConnectionManager.save_guild_rank(0, tr("GUILD_RANK_DEFAULT_NAME"), int(_info.MinTaxPercent), false, false, false, false, false)


## Every change to the guild may have added an entry, so an open Log tab asks again whenever the guild is pushed.
func _refresh_log() -> void:
	if _info != null and _is_log_shown() and ConnectionManager.is_ready_to_send():
		ConnectionManager.request_guild_log()


func _is_log_shown() -> bool:
	return is_visible_in_tree() and _tabs.get_current_tab_control() == _log.get_parent()


func _on_operation_success(message: OperationSuccess) -> void:
	if message.CodeName == "guild_invitation_sent":
		notice.emit(tr("GUILD_INVITE_SENT") % _last_invited)


func _on_guild_message(message: GuildSMSG) -> void:
	if message is GuildInfoSMSG:
		if message.GuildId == 0:
			_clear(false, "")
		else:
			_show_guild(message)
	elif message is GuildLeftSMSG:
		if _info != null and message.GuildId == _info.GuildId:
			_clear(true, message.ReasonName)
	elif message is GuildInvitationSMSG:
		_offer_invitation(message)
	elif message is GuildSkillCooldownSMSG:
		SkillCooldowns.start(message.SkillId, message.RemainingMs)
	elif message is GuildNoticeSMSG:
		notice.emit(tr("GUILD_NOTICE_%s" % message.NoticeName.to_upper()))
	elif message is GuildLogSMSG:
		_log.show_log(message)


func _offer_invitation(message: GuildInvitationSMSG) -> void:
	if invitations == null:
		return
	var invitation_id: int = message.InvitationId
	invitations.offer(
		&"guild",
		tr("GUILD_INVITE_PROMPT") % [message.InvitedByMaster, message.GuildName],
		float(message.ExpiresInSeconds),
		func(accepted: bool) -> void: ConnectionManager.answer_guild_invite(invitation_id, accepted)
	)


func _show_guild(info: GuildInfoSMSG) -> void:
	var joined := _info == null or _info.GuildId != info.GuildId
	_info = info

	var me := _member(info.OwnMasterId)
	var my_rank := _rank(me.RankId) if me != null else null
	var is_leader := me != null and me.IsLeader
	var may_invite := is_leader or (my_rank != null and my_rank.CanInvite)
	var may_kick := is_leader or (my_rank != null and my_rank.CanKick)
	var may_manage := is_leader or (my_rank != null and my_rank.CanManageRanks)
	var may_cast := is_leader or (my_rank != null and my_rank.CanUseGuildSkills)
	var may_edit_motd := is_leader or (my_rank != null and my_rank.CanEditMotd)
	var my_position: int = my_rank.Position if my_rank != null else 1 << 30

	for skill in info.Skills:
		if skill.CooldownRemainingMs > 0:
			SkillCooldowns.start(skill.SkillId, skill.CooldownRemainingMs)

	_name.text = info.Name
	_level_badge.text = tr("GUILD_LEVEL") % info.Level
	_member_count.text = "%d / %d" % [info.Members.size(), info.MaxMembers]

	_show_overview(info, may_edit_motd)
	_skills.show_skills(info, is_leader, may_cast)
	_show_members(info, is_leader, may_kick, may_manage, my_position)
	_show_ranks(info, is_leader, may_manage, my_position)

	_invite_row.visible = may_invite
	_leave.visible = not is_leader
	_disband.visible = is_leader
	_refresh_log()
	if _motd_due:
		_motd_due = false
		_show_motd_dialog()

	if joined:
		if _synced:
			notice.emit(tr("GUILD_JOINED") % info.Name)
		membership_changed.emit(true)
	_synced = true


func _show_overview(info: GuildInfoSMSG, may_edit_motd: bool) -> void:
	if info.ExpNextLevel > 0:
		UiStyle.set_bar(_exp_bar, int(info.Exp), int(info.ExpNextLevel))
		_exp_value.text = "%s / %s" % [UiStyle.group_digits(info.Exp), UiStyle.group_digits(info.ExpNextLevel)]
	else:
		UiStyle.set_bar(_exp_bar, 1, 1)
		_exp_value.text = tr("GUILD_EXP_MAX")

	_members_value.text = "%d / %d" % [info.Members.size(), info.MaxMembers]
	_points_value.text = str(info.SkillPoints)

	var has_motd := not (info.MotdTitle.is_empty() and info.MotdBody.is_empty())
	_motd_title.text = info.MotdTitle if has_motd else tr("GUILD_MOTD_NONE")
	_motd_title.modulate = Color.WHITE if has_motd else Color(1, 1, 1, 0.6)
	_motd_body.text = info.MotdBody
	_motd_body.visible = not info.MotdBody.is_empty()
	_edit_motd.visible = may_edit_motd


func _show_members(info: GuildInfoSMSG, is_leader: bool, may_kick: bool, may_manage: bool, my_position: int) -> void:
	var seen: Dictionary[int, bool] = {}

	for i in info.Members.size():
		var member: GuildMemberInfo = info.Members[i]
		seen[member.MasterId] = true

		var row: GuildMemberRow = _member_rows.get(member.MasterId)
		if row == null:
			row = GuildMemberRow.new()
			row.rank_chosen.connect(ConnectionManager.set_guild_member_rank)
			row.kick_requested.connect(ConnectionManager.kick_guild_member)
			_members.add_child(row)
			_member_rows[member.MasterId] = row
		_members.move_child(row, i)

		var rank := _rank(member.RankId)
		var is_self: bool = member.MasterId == info.OwnMasterId
		var below_me: bool = not member.IsLeader and not is_self and (is_leader or (rank != null and rank.Position > my_position))

		var assignable: Array[GuildRankInfo] = []
		if may_manage and below_me:
			for offered in info.Ranks:
				if offered.Position != 0 and (is_leader or offered.Position > my_position):
					assignable.append(offered)

		row.show_member(member, rank, is_self, assignable, may_kick and below_me)

	for master_id in _member_rows.keys():
		if not seen.has(master_id):
			_member_rows[master_id].queue_free()
			_member_rows.erase(master_id)


func _show_ranks(info: GuildInfoSMSG, is_leader: bool, may_manage: bool, my_position: int) -> void:
	var seen: Dictionary[int, bool] = {}
	var lower_ranks := 0
	for rank in info.Ranks:
		if rank.Position != 0:
			lower_ranks += 1

	for i in info.Ranks.size():
		var rank: GuildRankInfo = info.Ranks[i]
		seen[rank.RankId] = true

		var row: GuildRankRow = _rank_rows.get(rank.RankId)
		if row == null:
			row = GuildRankRow.new()
			row.save_requested.connect(ConnectionManager.save_guild_rank)
			row.delete_requested.connect(ConnectionManager.delete_guild_rank)
			_ranks.add_child(row)
			_rank_rows[rank.RankId] = row
		_ranks.move_child(row, i)

		var editable: bool = is_leader if rank.Position == 0 else (may_manage and (is_leader or rank.Position > my_position))
		var deletable: bool = editable and rank.Position != 0 and lower_ranks > 1 and not _rank_in_use(info, rank.RankId)
		row.show_rank(rank, editable, deletable, int(info.MinTaxPercent), int(info.MaxTaxPercent))

	for rank_id in _rank_rows.keys():
		if not seen.has(rank_id):
			_rank_rows[rank_id].queue_free()
			_rank_rows.erase(rank_id)

	_add_rank.visible = may_manage
	_add_rank.disabled = info.Ranks.size() >= info.MaxRanks


func _rank_in_use(info: GuildInfoSMSG, rank_id: int) -> bool:
	for member in info.Members:
		if member.RankId == rank_id:
			return true
	return false


## [param reason] is GuildLeftSMSG's snake_case reason, empty when there is nothing to tell.
func _clear(announce: bool, reason: String) -> void:
	var was_in_guild := _info != null
	var guild_name := _info.Name if _info != null else ""
	_info = null
	_motd_due = false

	for row in _member_rows.values():
		row.queue_free()
	_member_rows.clear()
	for row in _rank_rows.values():
		row.queue_free()
	_rank_rows.clear()

	if announce and was_in_guild and not reason.is_empty():
		notice.emit(tr("GUILD_LEFT_REASON_%s" % reason.to_upper()) % guild_name)
	if was_in_guild:
		membership_changed.emit(false)
	_synced = true


func _member(master_id: int) -> GuildMemberInfo:
	if _info == null:
		return null
	for member in _info.Members:
		if member.MasterId == master_id:
			return member
	return null


func _rank(rank_id: int) -> GuildRankInfo:
	if _info == null:
		return null
	for rank in _info.Ranks:
		if rank.RankId == rank_id:
			return rank
	return null
