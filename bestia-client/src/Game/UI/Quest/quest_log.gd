class_name QuestLogPanel
extends PanelContainer

## The played master's quest log: what it accepted and what it posted, grouped by where each quest came from, with
## one quest's details beside the list. Holds the log for the tracker too, so both read one copy.
##
## The server sends the whole log once a master is selected and one quest at a time after that. Quests are made
## at runtime, so every update carries everything shown.

## The log changed: a quest arrived, moved, ended or left. The tracker redraws on it.
signal quests_changed()
signal notice(text: String)

## The most quests the tracker shows. More would cover the screen.
const MAX_TRACKED := 5
const _TRACKING_FILE := "user://quest_tracking.cfg"

## The list's groups, in order: a quest's own group first, then the posted ones.
## One entry per origin a quest can have, and one for the quests the player posted. A quest of an origin that is
## missing here would not be listed at all.
const _GROUPS := [
	["world", "QUEST_LOG_GROUP_WORLD"], ["town", "QUEST_LOG_GROUP_TOWN"], ["blackboard", "QUEST_LOG_GROUP_BLACKBOARD"],
	["posted", "QUEST_LOG_GROUP_POSTED"],
]

## Set by the UI, which owns the inventory window: objectives show what the master carries.
var inventory: Inventory

## Quest id to its [QuestEntry].
var _quests: Dictionary = {}
var _tracked: Dictionary = {}
var _selected_id: int = 0

var _list: VBoxContainer
var _empty: Label
var _detail: VBoxContainer
var _detail_title: Label
var _detail_subtitle: Label
var _detail_text: Label
var _objectives: VBoxContainer
var _reward: Label
var _track: Button
var _drop: Button


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(620, 400)
	_load_tracking()
	_build()

	ConnectionManager.quest_log_received.connect(_on_log)
	ConnectionManager.quest_updated.connect(_on_updated)


## The quests the tracker shows, in log order.
func tracked_quests() -> Array:
	return _quests.values().filter(func(quest) -> bool: return _tracked.has(quest.QuestId))


func _build() -> void:
	var columns := UiStyle.hbox(10)
	add_child(UiStyle.margin(columns, 10, 8, 10, 10))

	var scroll := ScrollContainer.new()
	scroll.custom_minimum_size = Vector2(250, 0)
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	columns.add_child(scroll)

	var left := UiStyle.vbox(3)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(left)

	_list = UiStyle.vbox(3)
	left.add_child(_list)

	_empty = UiStyle.label(tr("QUEST_LOG_EMPTY"), 12, UiStyle.MUTED)
	_empty.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	left.add_child(_empty)

	columns.add_child(VSeparator.new())

	_detail = UiStyle.vbox(6)
	_detail.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	columns.add_child(_detail)

	_detail_title = UiStyle.label("", 15)
	_detail_title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_detail.add_child(_detail_title)

	_detail_subtitle = UiStyle.label("", 11, UiStyle.MUTED)
	_detail_subtitle.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_detail.add_child(_detail_subtitle)

	_detail_text = UiStyle.label("", 12)
	_detail_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_detail.add_child(_detail_text)

	_objectives = UiStyle.vbox(4)
	_detail.add_child(_objectives)

	_reward = UiStyle.label("", 12, UiStyle.MUTED)
	_detail.add_child(_reward)

	var actions := UiStyle.hbox(6)
	_detail.add_child(actions)

	_track = Button.new()
	_track.toggle_mode = true
	_track.text = tr("QUEST_LOG_TRACK")
	_track.toggled.connect(_on_track_toggled)
	actions.add_child(_track)

	_drop = Button.new()
	_drop.pressed.connect(func() -> void: ConnectionManager.abandon_quest(_selected_id))
	actions.add_child(_drop)

	_detail.visible = false


func _on_log(msg: QuestLogSMSG) -> void:
	_quests.clear()
	for quest in msg.Quests:
		_quests[quest.QuestId] = quest
	_forget_untracked_gone()
	_refresh()


func _on_updated(msg: QuestUpdatedSMSG) -> void:
	var before = _quests.get(msg.QuestId)
	if msg.Quest == null:
		_quests.erase(msg.QuestId)
		_tracked.erase(msg.QuestId)
		_save_tracking()
	else:
		_quests[msg.QuestId] = msg.Quest
		_tell_status_change(before, msg.Quest)
		if before == null and msg.Quest.RoleName == "accepted" and _tracked.size() < MAX_TRACKED:
			_tracked[msg.QuestId] = true
			_save_tracking()
	_refresh()


## The server sends no notice of its own: a quest that moved to a new state is the news.
func _tell_status_change(before, after) -> void:
	if before == null or before.StatusName == after.StatusName:
		return
	var key := "QUEST_NOTICE_%s" % after.StatusName.to_upper()
	if tr(key) != key:
		notice.emit(tr(key) % QuestStyle.title_of(after))


func _refresh() -> void:
	for child in _list.get_children():
		child.queue_free()

	var quests := _quests.values()
	for group in _GROUPS:
		var members := quests.filter(func(quest) -> bool: return _group_of(quest) == group[0])
		if members.is_empty():
			continue
		_list.add_child(UiStyle.label(tr(group[1]), 11, UiStyle.MUTED))
		for quest in members:
			_list.add_child(_row(quest))

	_empty.visible = quests.is_empty()
	_show(_quests.get(_selected_id, quests[0] if not quests.is_empty() else null))
	quests_changed.emit()


func _group_of(quest) -> String:
	return "posted" if quest.RoleName == "posted" else quest.OriginName


func _row(quest) -> Button:
	var row := Button.new()
	row.flat = true
	row.alignment = HORIZONTAL_ALIGNMENT_LEFT
	row.focus_mode = Control.FOCUS_NONE
	var suffix := " · %s" % tr("QUEST_STATUS_%s" % quest.StatusName.to_upper()) if quest.StatusName != "open" else ""
	row.text = "[%d] %s%s" % [quest.SuggestedLevel, QuestStyle.title_of(quest), suffix]
	var colour := UiStyle.MUTED if QuestStyle.has_ended(quest) else QuestStyle.level_colour(quest.SuggestedLevel)
	row.add_theme_color_override("font_color", colour)
	row.pressed.connect(func() -> void: _show(quest))
	return row


func _show(quest) -> void:
	_detail.visible = quest != null
	if quest == null:
		_selected_id = 0
		return

	_selected_id = quest.QuestId
	_detail_title.text = QuestStyle.title_of(quest)
	_detail_subtitle.text = tr("QUEST_LOG_SUBTITLE") % [
		tr("QUEST_ORIGIN_%s" % quest.OriginName.to_upper()), quest.PosterName, QuestStyle.time_left(quest), quest.SuggestedLevel
	]
	_detail_text.text = QuestStyle.text_of(quest)
	if not quest.WonBy.is_empty():
		_detail_text.text = tr("QUEST_LOG_WON_BY") % quest.WonBy
	_detail_text.visible = not _detail_text.text.is_empty()

	for child in _objectives.get_children():
		child.queue_free()
	for objective in quest.Objectives:
		_objectives.add_child(_objective_row(quest, objective))

	_reward.visible = not quest.Rewards.is_empty()
	if _reward.visible:
		_reward.text = tr("QUEST_LOG_REWARD_EACH") % quest.Rewards[0].Amount

	var ended := QuestStyle.has_ended(quest)
	_track.visible = not ended and quest.RoleName == "accepted"
	_track.set_pressed_no_signal(_tracked.has(quest.QuestId))
	_track.disabled = not _tracked.has(quest.QuestId) and _tracked.size() >= MAX_TRACKED
	# A running quest of one's own is withdrawn at a board; everything else may leave the log here.
	_drop.visible = quest.RoleName == "accepted" or ended
	_drop.text = tr("QUEST_LOG_CLEAR") if ended else tr("QUEST_LOG_DROP")


func _objective_row(quest, objective) -> Control:
	if not objective.Text.Key.is_empty():
		return _worded_objective_row(objective)

	var box := UiStyle.vbox(2)
	var item := ItemDB.get_instance().get_item(objective.ItemId)
	var item_name := tr(item.name_key) if item != null else "?"
	var line := tr("QUEST_LOG_OBJECTIVE") % [item_name, objective.Progress, objective.Required]
	if quest.RoleName == "accepted":
		line += " " + tr("QUEST_LOG_OBJECTIVE_YOURS") % [objective.Yours, carried(objective)]
	box.add_child(UiStyle.label(line, 12))

	var bar := UiStyle.bar(UiStyle.EXP_FILL, 6)
	UiStyle.set_bar(bar, objective.Progress, objective.Required)
	box.add_child(bar)
	return box


## A step the server words itself, such as "read the epitaph". Done steps of earlier stages stay, muted.
func _worded_objective_row(objective) -> Control:
	var done: bool = objective.Progress >= objective.Required
	var label := UiStyle.label("", 12, UiStyle.MUTED if done else UiStyle.TEXT)
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	# What was picked is said with the step, so a log read later still tells what the master decided.
	var picked = objective.Choices.filter(func(choice) -> bool: return choice.Id == objective.Chosen)
	var suffix := "" if picked.is_empty() else " - " + DialogText.resolve_ref(picked[0].Text)
	TextSource.show_ref(objective.Text, func(text: String):
		# The row may be redrawn before a written text arrives.
		if is_instance_valid(label):
			label.text = text + suffix
	)
	return label


## What the master carries of what the objective wants.
func carried(objective) -> int:
	return inventory.get_item_count(objective.ItemId) if inventory != null else 0


func _on_track_toggled(on: bool) -> void:
	if on:
		_tracked[_selected_id] = true
	else:
		_tracked.erase(_selected_id)
	_save_tracking()
	quests_changed.emit()


# ----------------------------------------------------------------- tracking, kept on this machine only

func _load_tracking() -> void:
	var file := ConfigFile.new()
	if file.load(_TRACKING_FILE) != OK:
		return
	for key in file.get_section_keys("tracked") if file.has_section("tracked") else []:
		_tracked[int(key)] = true


func _save_tracking() -> void:
	var file := ConfigFile.new()
	for quest_id in _tracked:
		file.set_value("tracked", str(quest_id), true)
	file.save(_TRACKING_FILE)


func _forget_untracked_gone() -> void:
	for quest_id in _tracked.keys():
		if not _quests.has(quest_id):
			_tracked.erase(quest_id)
	_save_tracking()
