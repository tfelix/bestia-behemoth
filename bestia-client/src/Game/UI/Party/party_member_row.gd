class_name PartyMemberRow
extends PanelContainer

## One member in the party window: online light, name and level, the master's health and mana, and beneath
## them the bestia the member is playing, when they play one.
##
## Seeded from the roster and then kept live from the entity updates the server sends every member, wherever
## they stand - see [method apply_vitals].

signal kick_requested(master_id: int)

var master_id: int = 0

var _master_entity_id: int = 0
var _bestia_entity_id: int = 0

var _dot: Panel
var _owner_badge: Label
var _name: Label
var _level: Label
var _kick: Button
var _master_bars: Control
var _master_hp: ProgressBar
var _master_mana: ProgressBar
var _offline: Label
var _bestia_line: Control
var _bestia_name: Label
var _bestia_hp: ProgressBar
var _bestia_mana: ProgressBar


func _init() -> void:
	var rows := UiStyle.vbox(3)
	add_child(rows)

	var line := UiStyle.hbox(6)
	rows.add_child(line)

	_dot = UiStyle.dot(UiStyle.OFFLINE)
	line.add_child(_dot)

	_owner_badge = UiStyle.label("★", 12, UiStyle.ACCENT)
	_owner_badge.tooltip_text = tr("PARTY_OWNER")
	_owner_badge.mouse_filter = Control.MOUSE_FILTER_PASS
	line.add_child(_owner_badge)

	_name = UiStyle.label("", 13)
	_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_name.clip_text = true
	_name.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	line.add_child(_name)

	_level = UiStyle.label("", 11, UiStyle.MUTED)
	line.add_child(_level)

	_kick = UiStyle.glyph_button("✕", tr("PARTY_KICK"))
	_kick.pressed.connect(func() -> void: kick_requested.emit(master_id))
	line.add_child(_kick)

	var master_bars := UiStyle.vbox(2)
	_master_hp = UiStyle.bar(UiStyle.HP_FILL, 6)
	_master_mana = UiStyle.bar(UiStyle.MANA_FILL, 4)
	master_bars.add_child(_master_hp)
	master_bars.add_child(_master_mana)
	_master_bars = UiStyle.margin(master_bars, 14, 0, 2, 0)
	rows.add_child(_master_bars)

	_offline = UiStyle.label(tr("PARTY_MEMBER_OFFLINE"), 11, UiStyle.MUTED)
	rows.add_child(UiStyle.margin(_offline, 14, 0, 0, 0))

	var bestia := UiStyle.hbox(6)
	_bestia_name = UiStyle.label("", 11, UiStyle.MUTED)
	_bestia_name.custom_minimum_size = Vector2(96, 0)
	_bestia_name.clip_text = true
	_bestia_name.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	bestia.add_child(_bestia_name)

	var bestia_bars := UiStyle.vbox(2)
	bestia_bars.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bestia_bars.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	_bestia_hp = UiStyle.bar(UiStyle.HP_FILL, 4)
	_bestia_mana = UiStyle.bar(UiStyle.MANA_FILL, 3)
	bestia_bars.add_child(_bestia_hp)
	bestia_bars.add_child(_bestia_mana)
	bestia.add_child(bestia_bars)
	_bestia_line = UiStyle.margin(bestia, 22, 0, 2, 0)
	rows.add_child(_bestia_line)


## Draws [param member]. The kick cross only for the owner looking at somebody else's row.
func show_member(member: PartyMemberInfo, is_owner_row: bool, is_self: bool, can_kick: bool) -> void:
	master_id = member.MasterId
	_master_entity_id = member.EntityId if member.IsOnline else 0
	_bestia_entity_id = member.ActiveBestiaEntityId if member.IsOnline else 0

	var border := 1 if is_self else 0
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ROW_BG, 4, 7, UiStyle.SELF_BORDER, border))
	modulate = Color.WHITE if member.IsOnline else Color(1, 1, 1, 0.55)

	UiStyle.set_dot(_dot, UiStyle.ONLINE if member.IsOnline else UiStyle.OFFLINE)
	_owner_badge.visible = is_owner_row
	_name.text = member.MasterName
	_level.text = tr("PARTY_LEVEL") % member.Level
	_kick.visible = can_kick and not is_self

	_master_bars.visible = member.IsOnline
	_offline.visible = not member.IsOnline
	if member.IsOnline:
		_name.tooltip_text = member.AreaName
		UiStyle.set_bar(_master_hp, member.HpCurrent, member.HpMax)
		UiStyle.set_bar(_master_mana, member.ManaCurrent, member.ManaMax)

	_bestia_line.visible = _bestia_entity_id != 0
	if _bestia_entity_id != 0:
		_bestia_name.text = "%s  %s" % [_bestia_label(member), tr("PARTY_LEVEL") % member.ActiveBestiaLevel]
		UiStyle.set_bar(_bestia_hp, member.ActiveBestiaHpCurrent, member.ActiveBestiaHpMax)
		UiStyle.set_bar(_bestia_mana, member.ActiveBestiaManaCurrent, member.ActiveBestiaManaMax)


## The entities whose vitals this row shows.
func entity_ids() -> Array[int]:
	var ids: Array[int] = []
	if _master_entity_id != 0:
		ids.append(_master_entity_id)
	if _bestia_entity_id != 0:
		ids.append(_bestia_entity_id)
	return ids


## A live Health or Mana update for one of [method entity_ids].
func apply_vitals(msg: EntitySMSG) -> void:
	var is_master: bool = msg.EntityId == _master_entity_id
	if not is_master and msg.EntityId != _bestia_entity_id:
		return

	if msg is HealthComponentSMSG:
		UiStyle.set_bar(_master_hp if is_master else _bestia_hp, msg.Current, msg.Max)
	elif msg is ManaComponentSMSG:
		UiStyle.set_bar(_master_mana if is_master else _bestia_mana, msg.Current, msg.Max)


## An unnamed bestia goes by its species, as it does in the player's own list.
func _bestia_label(member: PartyMemberInfo) -> String:
	if member.ActiveBestiaName != "":
		return member.ActiveBestiaName

	var species := BestiaDB.get_instance().get_bestia(member.ActiveBestiaSpeciesId)
	return str(TranslationServer.translate(species.name_key)) if species != null else "?"
