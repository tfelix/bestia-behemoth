class_name GuildLogPage
extends ScrollContainer

## The guild window's Log tab: what happened in the guild, newest first. Asked for each time the tab is shown,
## since the server does not push it.

var _entries: VBoxContainer


func _init() -> void:
	horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	_entries = UiStyle.vbox(2)
	_entries.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	add_child(_entries)


func show_log(log: GuildLogSMSG) -> void:
	for child in _entries.get_children():
		child.queue_free()

	if log.Entries.is_empty():
		_entries.add_child(UiStyle.label(tr("GUILD_LOG_EMPTY"), 12, UiStyle.MUTED))
		return

	for entry in log.Entries:
		_entries.add_child(_row(entry))


func _row(entry: GuildLogEntryInfo) -> Control:
	var line := UiStyle.hbox(10)
	var when := UiStyle.label(_format_time(entry.AtEpochMs), 11, UiStyle.MUTED)
	when.custom_minimum_size = Vector2(110, 0)
	line.add_child(when)

	var text := UiStyle.label(_describe(entry), 12)
	text.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	line.add_child(text)
	return line


func _describe(entry: GuildLogEntryInfo) -> String:
	var detail := entry.Detail
	if entry.KindName == "skill_learned":
		var attack := AttackDB.get_instance().get_attack(int(detail))
		if attack != null:
			detail = attack.name

	return tr("GUILD_LOG_%s" % entry.KindName.to_upper()).format({
		"actor": entry.ActorName,
		"target": entry.TargetName,
		"detail": detail,
	})


## Local time, as the player reads their own clock.
func _format_time(epoch_ms: int) -> String:
	var bias_seconds: int = Time.get_time_zone_from_system().get("bias", 0) * 60
	var local := Time.get_datetime_dict_from_unix_time(epoch_ms / 1000 + bias_seconds)
	return "%02d.%02d. %02d:%02d" % [local.day, local.month, local.hour, local.minute]
