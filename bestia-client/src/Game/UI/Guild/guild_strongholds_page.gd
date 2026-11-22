class_name GuildStrongholdsPage
extends ScrollContainer

## The guild window's Strongholds tab: one card per Stronghold with its levels, steward, watchers and waiting
## treasure drops, and the guild storage below. Asked for each time the tab is shown; the leader collects a card's
## drops into the guild storage from here, and sends a sealed storage to their home post office.

## The proto's StrongholdTabAction.
const _COLLECT := 1
const _SEND_STORAGE_AWAY := 2

var _content: VBoxContainer
## One per card: the label counting down to the next drop, and its seconds when the tab came.
var _countdowns: Array[Dictionary] = []
var _received_ms := 0


func _init() -> void:
	horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	_content = UiStyle.vbox(10)
	_content.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	add_child(_content)
	set_process(false)


func show_tab(tab: StrongholdTabSMSG) -> void:
	for child in _content.get_children():
		child.queue_free()
	_countdowns.clear()
	_received_ms = Time.get_ticks_msec()

	if tab.Strongholds.is_empty():
		_content.add_child(UiStyle.label(tr("GUILD_STRONGHOLDS_NONE"), 12, UiStyle.MUTED))
	for card in tab.Strongholds:
		_content.add_child(_card(card, tab.IsLeader))

	_content.add_child(_storage_line(tab))
	set_process(not _countdowns.is_empty())
	_process(0.0)


func _process(_delta: float) -> void:
	var elapsed := floori((Time.get_ticks_msec() - _received_ms) / 1000.0)
	for countdown in _countdowns:
		var left := maxi(0, int(countdown["seconds"]) - elapsed)
		(countdown["label"] as Label).text = tr("GUILD_STRONGHOLDS_NEXT_DROP") % _clock(left)


func _card(card: Dictionary, is_leader: bool) -> Control:
	var panel := PanelContainer.new()
	panel.add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ROW_BG, 4, 8))
	var body := UiStyle.vbox(3)
	panel.add_child(body)

	var title := str(card["name"])
	if bool(card["is_site"]):
		title = tr("GUILD_STRONGHOLDS_SITE") % title
	body.add_child(UiStyle.label(title, 15, UiStyle.ACCENT))
	if bool(card["is_site"]):
		return panel

	body.add_child(UiStyle.label(tr("GUILD_STRONGHOLDS_EMPYRITE") % [
		UiStyle.group_digits(int(card["hp"])), UiStyle.group_digits(int(card["max_hp"]))], 12))
	body.add_child(UiStyle.label(tr("GUILD_STRONGHOLDS_LEVELS") % [card["defense"], card["storage"], card["economy"]], 12))
	var steward := tr("GUILD_STRONGHOLDS_STEWARD_YES") if bool(card["has_steward"]) else tr("GUILD_STRONGHOLDS_STEWARD_NO")
	body.add_child(UiStyle.label(tr("GUILD_STRONGHOLDS_GUARDS") % [steward, card["watchers"], card["watcher_posts"]], 12))

	var drops: Array = card["drops"]
	body.add_child(UiStyle.label(tr("GUILD_STRONGHOLDS_DROPS") % [drops.size(), card["max_drops"]], 13))
	for drop in drops:
		var line := UiStyle.hbox(10)
		line.add_child(UiStyle.label(_format_time(int(drop["made_at_ms"])), 12, UiStyle.MUTED))
		var what := UiStyle.label(_describe(drop), 12)
		what.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		what.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		line.add_child(what)
		body.add_child(UiStyle.margin(line, 12, 0, 0, 0))

	var foot := UiStyle.hbox(8)
	var next := UiStyle.label("", 12, UiStyle.MUTED)
	next.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	foot.add_child(next)
	_countdowns.append({"label": next, "seconds": int(card["next_drop_seconds"])})
	if is_leader and not drops.is_empty():
		var collect := Button.new()
		collect.text = tr("GUILD_STRONGHOLDS_COLLECT")
		var rank := int(card["seed_rank"])
		collect.pressed.connect(func() -> void: ConnectionManager.stronghold_tab(_COLLECT, rank))
		foot.add_child(collect)
	body.add_child(foot)

	return panel


func _storage_line(tab: StrongholdTabSMSG) -> Control:
	if not tab.HasGuildStorage:
		return UiStyle.label(tr("GUILD_STRONGHOLDS_STORAGE_NOT_LEARNED"), 12, UiStyle.MUTED)
	if tab.StorageSealed:
		return _sealed_storage(tab)
	return UiStyle.label(tr("GUILD_STRONGHOLDS_STORAGE") % [tab.StorageUsed, tab.StorageSlots], 12)


## Nobody can open it; the leader may pay to have its goods posted home.
func _sealed_storage(tab: StrongholdTabSMSG) -> Control:
	var rows := UiStyle.vbox(4)
	rows.add_child(UiStyle.label(tr("GUILD_STRONGHOLDS_STORAGE_SEALED") % tab.StorageUsed, 12))
	if not tab.IsLeader or tab.StorageUsed == 0:
		return rows

	var line := UiStyle.hbox(8)
	var cost := UiStyle.label(tr("GUILD_STRONGHOLDS_SEND_AWAY_COST") % UiStyle.group_digits(tab.SendAwayCoins), 12)
	cost.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	cost.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	line.add_child(cost)
	var send := Button.new()
	send.text = tr("GUILD_STRONGHOLDS_SEND_AWAY")
	send.pressed.connect(func() -> void: ConnectionManager.stronghold_tab(_SEND_STORAGE_AWAY))
	line.add_child(send)
	rows.add_child(line)
	return rows


## "60,000 coins, 6 Iron Ore, 4 Tin Ore"
func _describe(drop: Dictionary) -> String:
	var parts: Array[String] = [tr("GUILD_STRONGHOLDS_COINS") % UiStyle.group_digits(int(drop["coins"]))]
	for good in drop["goods"]:
		var item := ItemDB.get_instance().get_item(int(good["item_id"]))
		var item_name := tr(item.name_key) if item != null else "?"
		parts.append("%d %s" % [int(good["amount"]), item_name])
	return ", ".join(parts)


func _clock(seconds: int) -> String:
	return "%d:%02d:%02d" % [floori(seconds / 3600.0), floori(seconds % 3600 / 60.0), seconds % 60]


## Local time, as the player reads their own clock.
func _format_time(epoch_ms: int) -> String:
	var bias_seconds: int = Time.get_time_zone_from_system().get("bias", 0) * 60
	var local := Time.get_datetime_dict_from_unix_time(floori(epoch_ms / 1000.0) + bias_seconds)
	return "%02d.%02d. %02d:%02d" % [local.day, local.month, local.hour, local.minute]
