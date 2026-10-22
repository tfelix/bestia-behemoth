class_name GuildMemberRow
extends PanelContainer

## One member in the guild window: online light, the leader's crown, name, level, where they are (or were, and
## when, while offline), rank and the EXP paid in.
## The rank becomes a selector and a kick cross appears only where the viewer's own rank allows it - the
## window decides that, see [method show_member]; the server checks it again.

signal rank_chosen(master_id: int, rank_id: int)
signal kick_requested(master_id: int)

var master_id: int = 0

var _dot: Panel
var _crown: Label
var _name: Label
var _level: Label
var _where: Label
var _rank_label: Label
var _rank_select: OptionButton
var _exp: Label
var _kick: Button
## The rank ids in [member _rank_select], by item index.
var _offered_rank_ids: Array[int] = []


func _init() -> void:
	var line := UiStyle.hbox(8)
	add_child(line)

	_dot = UiStyle.dot(UiStyle.OFFLINE)
	line.add_child(_dot)

	_crown = UiStyle.label("♛", 13, UiStyle.ACCENT)
	_crown.tooltip_text = tr("GUILD_LEADER")
	_crown.mouse_filter = Control.MOUSE_FILTER_PASS
	_crown.custom_minimum_size = Vector2(14, 0)
	line.add_child(_crown)

	_name = UiStyle.label("", 13)
	_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_name.clip_text = true
	_name.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	line.add_child(_name)

	_level = UiStyle.label("", 11, UiStyle.MUTED)
	_level.custom_minimum_size = Vector2(44, 0)
	line.add_child(_level)

	_where = UiStyle.label("", 11, UiStyle.MUTED)
	_where.custom_minimum_size = Vector2(170, 0)
	_where.clip_text = true
	_where.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	line.add_child(_where)

	_rank_label = UiStyle.label("", 12, UiStyle.MUTED)
	_rank_label.custom_minimum_size = Vector2(110, 0)
	_rank_label.clip_text = true
	line.add_child(_rank_label)

	_rank_select = OptionButton.new()
	_rank_select.custom_minimum_size = Vector2(110, 0)
	_rank_select.add_theme_font_size_override("font_size", 12)
	_rank_select.item_selected.connect(_on_rank_selected)
	line.add_child(_rank_select)

	_exp = UiStyle.label("", 11, UiStyle.MUTED)
	_exp.custom_minimum_size = Vector2(70, 0)
	_exp.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	_exp.tooltip_text = tr("GUILD_COL_EXP")
	_exp.mouse_filter = Control.MOUSE_FILTER_PASS
	line.add_child(_exp)

	_kick = UiStyle.glyph_button("✕", tr("GUILD_KICK"))
	_kick.pressed.connect(func() -> void: kick_requested.emit(master_id))
	line.add_child(_kick)


## [param assignable] are the ranks the viewer may put this member on, empty when they may not move them.
func show_member(member: GuildMemberInfo, rank: GuildRankInfo, is_self: bool, assignable: Array[GuildRankInfo], can_kick: bool) -> void:
	master_id = member.MasterId

	var border := 1 if is_self else 0
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ROW_BG, 4, 7, UiStyle.SELF_BORDER, border))
	modulate = Color.WHITE if member.IsOnline else Color(1, 1, 1, 0.6)

	UiStyle.set_dot(_dot, UiStyle.ONLINE if member.IsOnline else UiStyle.OFFLINE)
	_dot.tooltip_text = tr("GUILD_MEMBER_ONLINE") if member.IsOnline else tr("GUILD_MEMBER_OFFLINE")
	_crown.modulate = Color.WHITE if member.IsLeader else Color.TRANSPARENT
	_name.text = member.MasterName
	_level.text = tr("GUILD_LEVEL") % member.Level
	_exp.text = UiStyle.group_digits(member.ExpContributed)
	if member.IsOnline:
		show_whereabouts("", "")
	else:
		show_whereabouts(member.LastAreaName, _seen_ago(member.LastSeenEpochMs))
	_kick.visible = can_kick

	var rank_name := rank.Name if rank != null else "?"
	_rank_label.text = rank_name
	_rank_label.visible = assignable.is_empty()
	_rank_select.visible = not assignable.is_empty()

	_rank_select.clear()
	_offered_rank_ids.clear()
	for offered in assignable:
		_rank_select.add_item(offered.Name)
		_offered_rank_ids.append(offered.RankId)
		if rank != null and offered.RankId == rank.RankId:
			# select() does not emit item_selected, so drawing the current rank sends nothing.
			_rank_select.select(_offered_rank_ids.size() - 1)


## [param detail] follows the area, such as how long ago an offline member was seen.
func show_whereabouts(area_name: String, detail: String) -> void:
	var parts := PackedStringArray()
	for part in [area_name, detail]:
		if not part.is_empty():
			parts.append(part)
	_where.text = " · ".join(parts)
	_where.tooltip_text = _where.text


func _seen_ago(epoch_ms: int) -> String:
	if epoch_ms <= 0:
		return ""
	var minutes := maxi(0, int(Time.get_unix_time_from_system()) - epoch_ms / 1000) / 60
	if minutes < 1:
		return tr("GUILD_SEEN_JUST_NOW")
	if minutes < 60:
		return tr("GUILD_SEEN_MINUTES") % minutes
	if minutes < 60 * 24:
		return tr("GUILD_SEEN_HOURS") % (minutes / 60)
	return tr("GUILD_SEEN_DAYS") % (minutes / (60 * 24))


func _on_rank_selected(index: int) -> void:
	if index >= 0 and index < _offered_rank_ids.size():
		rank_chosen.emit(master_id, _offered_rank_ids[index])
