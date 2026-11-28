extends PanelContainer
## The chat window's Bestias tab: everything the owned bestias did, newest first, from their diary and live while the
## master plays. At login a summary of what happened while the master was away tops it. One bestia can be picked to
## see only its lines. It keeps bestia news out of the chat, which is busy enough; what a bestia does right now is on
## its party row.

## How many lines arrived since the tab was last looked at, for the tab's title.
signal unread_changed(count: int)

const _MAX_NEWS_LINES := 300
const _FONT_SIZE := 13
const _SUMMARY_NAME_FONT_SIZE := 14
const _ALL_BESTIAS := 0
const _WEEKDAYS: Array[String] = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"]

var _filter: OptionButton
var _summary: VBoxContainer
var _news: VBoxContainer
var _empty: Label
var _name_of: Dictionary = {}
var _unread := 0


func _ready() -> void:
	_build()
	var entity_manager := EntityManager.get_instance()
	if entity_manager != null:
		entity_manager.owned_bestias_changed.connect(_on_owned_bestias_changed)
		_on_owned_bestias_changed(entity_manager.get_owned_bestias())
	ConnectionManager.bestia_news_received.connect(_on_bestia_news)
	ConnectionManager.bestia_diary_received.connect(_on_bestia_diary)
	ConnectionManager.away_report_received.connect(_on_away_report)
	visibility_changed.connect(_on_visibility_changed)
	if ConnectionManager.last_away_report != null:
		_on_away_report(ConnectionManager.last_away_report)


func _build() -> void:
	var margin := MarginContainer.new()
	margin.add_theme_constant_override("margin_left", 4)
	add_child(margin)
	var column := VBoxContainer.new()
	margin.add_child(column)

	_filter = OptionButton.new()
	_filter.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	_filter.item_selected.connect(func(_index: int) -> void: _apply_filter())
	column.add_child(_filter)

	var scroll := ScrollContainer.new()
	scroll.custom_minimum_size = Vector2(0, 222)
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	column.add_child(scroll)
	var lines := VBoxContainer.new()
	lines.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(lines)

	_summary = VBoxContainer.new()
	_summary.visible = false
	lines.add_child(_summary)
	_empty = _line(tr("BESTIA_LOG_EMPTY"), UiStyle.MUTED)
	lines.add_child(_empty)
	_news = VBoxContainer.new()
	lines.add_child(_news)


func _on_owned_bestias_changed(bestias: Array[BestiaInfo]) -> void:
	var picked := _filter.get_selected_id()
	_filter.clear()
	_filter.add_item(tr("BESTIA_LOG_ALL"), _ALL_BESTIAS)
	_name_of.clear()
	for bestia in bestias:
		_name_of[bestia.PlayerBestiaId] = OwnedSlots.name_of(bestia)
		_filter.add_item(OwnedSlots.name_of(bestia), bestia.PlayerBestiaId)
	var index := _filter.get_item_index(picked)
	_filter.select(index if index >= 0 else 0)
	_apply_filter()


## The master is back: what happened while away goes on top, and the diary fills in the lines.
func _on_away_report(report: AwayReportSMSG) -> void:
	_show_summary(report)
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.get_bestia_diary()


## The past lines, newest first. Live news that came in before them is in the diary too.
func _on_bestia_diary(diary: BestiaDiarySMSG) -> void:
	for child in _news.get_children():
		_news.remove_child(child)
		child.queue_free()
	for news in diary.Lines:
		_news.add_child(_news_line(news))
	_after_change()


func _on_bestia_news(message: BestiaNewsSMSG) -> void:
	var line := _news_line(message)
	_news.add_child(line)
	_news.move_child(line, 0)
	while _news.get_child_count() > _MAX_NEWS_LINES:
		var oldest := _news.get_child(_news.get_child_count() - 1)
		_news.remove_child(oldest)
		oldest.queue_free()
	_after_change()
	_mark_unread(1)


func _show_summary(report: AwayReportSMSG) -> void:
	for child in _summary.get_children():
		_summary.remove_child(child)
		child.queue_free()

	var header := HBoxContainer.new()
	var title := _line(tr("AWAY_REPORT_TITLE") % _when(report.SinceEpochMs), UiStyle.ACCENT)
	title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	header.add_child(title)
	var dismiss := Button.new()
	dismiss.text = tr("AWAY_REPORT_DISMISS")
	dismiss.flat = true
	dismiss.pressed.connect(func() -> void:
		_summary.visible = false
		_after_change()
	)
	header.add_child(dismiss)
	_summary.add_child(header)

	var told := 0
	for bestia in report.Bestias:
		if BestiaWords.is_eventful(bestia):
			_summary.add_child(_summary_of(bestia))
			told += 1
	_summary.add_child(HSeparator.new())
	_summary.visible = told > 0
	_after_change()
	_mark_unread(told)


func _summary_of(report: BestiaAwayReport) -> Control:
	var bestia_name := report.Name if not report.Name.is_empty() else tr("BESTIA_" + report.Species.to_upper())
	var text := RichTextLabel.new()
	text.bbcode_enabled = true
	text.fit_content = true
	text.add_theme_font_size_override("normal_font_size", _FONT_SIZE)
	text.add_theme_font_size_override("bold_font_size", _SUMMARY_NAME_FONT_SIZE)
	var told := " ".join(BestiaWords.report_lines(report, bestia_name)).replace("[", "[lb]")
	# The report mostly opens with the name; bold it there rather than say it twice.
	if told.begins_with(bestia_name):
		text.text = "[b]%s[/b]%s" % [bestia_name, told.substr(bestia_name.length())]
	else:
		text.text = "[b]%s:[/b] %s" % [bestia_name, told]
	text.set_meta("player_bestia_id", report.PlayerBestiaId)
	return text


func _news_line(message: BestiaNewsSMSG) -> Label:
	var who: String = _name_of.get(message.PlayerBestiaId, "")
	var said := BestiaWords.news_line(message)
	var color := UiStyle.DANGER if BestiaWords.is_problem(message.Kind) else UiStyle.TEXT
	var line := _line("%s  %s" % [_when(message.AtEpochMs), said if who.is_empty() else "%s: %s" % [who, said]], color)
	line.set_meta("player_bestia_id", message.PlayerBestiaId)
	return line


## Today's lines show the time, older ones the weekday too: the diary reaches back a week.
func _when(epoch_ms: int) -> String:
	var bias: int = Time.get_time_zone_from_system()["bias"] * 60
	var at := Time.get_datetime_dict_from_unix_time(epoch_ms / 1000 + bias)
	var today := Time.get_datetime_dict_from_unix_time(int(Time.get_unix_time_from_system()) + bias)
	var clock := "%d:%02d" % [at["hour"], at["minute"]]
	var same_day: bool = at["year"] == today["year"] and at["month"] == today["month"] and at["day"] == today["day"]
	return clock if same_day else "%s %s" % [tr("WEEKDAY_" + _WEEKDAYS[at["weekday"]]), clock]


func _apply_filter() -> void:
	var picked := _filter.get_selected_id()
	for line in _news.get_children() + _summary.get_children():
		if line.has_meta("player_bestia_id"):
			line.visible = picked == _ALL_BESTIAS or line.get_meta("player_bestia_id") == picked


func _after_change() -> void:
	_empty.visible = _news.get_child_count() == 0 and not _summary.visible
	_apply_filter()


func _mark_unread(count: int) -> void:
	if is_visible_in_tree() or count == 0:
		return
	_unread += count
	unread_changed.emit(_unread)


func _on_visibility_changed() -> void:
	if visible and _unread > 0:
		_unread = 0
		unread_changed.emit(0)


func _line(text: String, color: Color) -> Label:
	var label := Label.new()
	label.text = text
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	label.add_theme_font_size_override("font_size", _FONT_SIZE)
	label.add_theme_color_override("font_color", color)
	return label
